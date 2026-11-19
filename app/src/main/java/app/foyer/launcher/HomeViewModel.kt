package app.foyer.launcher

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.foyer.launcher.data.Apps
import app.foyer.launcher.data.Row
import app.foyer.launcher.data.Services
import app.foyer.launcher.data.Title
import app.foyer.launcher.data.TvApp
import app.foyer.launcher.data.WatchNext
import app.foyer.launcher.data.WatchNextAccess
import app.foyer.launcher.data.WatchNextResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

enum class Screen { HOME, MOVIES, SHOWS, APPS, LIBRARY, SETTINGS }

enum class ArtworkState { LOADING, READY, NO_KEY, FAILED }

/** What the hero's main button does for a title. */
sealed interface Watch {
    data class Open(val label: String, val pkg: String) : Watch
    data class Install(val label: String, val pkg: String) : Watch
    data class Search(val query: String) : Watch
    /** Details still loading: pressing it looks them up, then opens the right app. */
    data class Pending(val title: Title) : Watch
}

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val foyer = app as FoyerApp

    var screen by mutableStateOf(Screen.HOME)
    var apps by mutableStateOf<List<TvApp>>(emptyList())
        private set
    var titles by mutableStateOf<List<Title>>(emptyList())
        private set
    var hero by mutableStateOf<Title?>(null)
        private set
    var artwork by mutableStateOf(ArtworkState.LOADING)
        private set
    var watchNext by mutableStateOf(WatchNextResult(WatchNextAccess.MISSING, emptyList()))
        private set
    /** Bumped each time the user presses Home, so the screen can reset focus. */
    var homePresses by mutableStateOf(0)
        private set

    /** Movies ("movie") and Shows ("tv") tabs: rows, load state and the title in the hero. */
    var rows by mutableStateOf<Map<String, List<Row>>>(emptyMap())
        private set
    var rowsState by mutableStateOf<Map<String, ArtworkState>>(emptyMap())
        private set
    var browseHero by mutableStateOf<Map<String, Title?>>(emptyMap())
        private set
    var myList by mutableStateOf<List<Title>>(emptyList())
        private set

    private var heroJob: Job? = null
    private var browseJob: Job? = null
    private val detailCache = HashMap<String, Title>()

    init {
        refreshApps()
        loadTitles()
        refreshWatchNext()
        myList = runCatching { foyer.tmdb.parse(JSONArray(foyer.prefs.myList)) }.getOrDefault(emptyList())
    }

    val hasArtwork: Boolean get() = foyer.tmdb.enabled

    fun loadRows(type: String) {
        if (!foyer.tmdb.enabled || rowsState[type] == ArtworkState.READY || rowsState[type] == ArtworkState.LOADING) return
        rowsState = rowsState + (type to ArtworkState.LOADING)
        viewModelScope.launch {
            val result = runCatching { foyer.tmdb.browse(type, region(), foyer.cacheDir.resolve("browse_$type.json")) }.getOrNull()
            if (result.isNullOrEmpty()) {
                rowsState = rowsState + (type to ArtworkState.FAILED)
            } else {
                rows = rows + (type to result)
                rowsState = rowsState + (type to ArtworkState.READY)
                if (browseHero[type] == null) showInBrowseHero(type, result.first().items.first(), 0L)
            }
        }
    }

    /** Focus landed on a card in the Movies or Shows tab. */
    fun previewBrowse(type: String, t: Title) = showInBrowseHero(type, t, foyer.profile?.heroDebounceMs ?: 200L)

    private fun showInBrowseHero(type: String, t: Title, wait: Long) {
        browseJob?.cancel()
        browseJob = viewModelScope.launch {
            delay(wait)
            browseHero = browseHero + (type to (detailCache[key(t)] ?: t))
            val full = detailed(t)
            if (browseHero[type]?.id == t.id) browseHero = browseHero + (type to full)
        }
    }

    /** Fetches logo, length and providers for a browse title, once. */
    suspend fun detailed(t: Title): Title {
        if (t.detailed) return t
        detailCache[key(t)]?.let { return it }
        val full = withContext(Dispatchers.IO) {
            runCatching { foyer.tmdb.details(t.type, t.id, region()) }.getOrNull()
        } ?: return t
        detailCache[key(t)] = full
        return full
    }

    /** Pressing a card: make sure we know where it streams, then open it. */
    fun open(t: Title) {
        viewModelScope.launch { perform(watchAction(detailed(t))) }
    }

    fun inMyList(t: Title): Boolean = myList.any { it.id == t.id && it.type == t.type }

    fun toggleMyList(t: Title) {
        myList = if (inMyList(t)) myList.filterNot { it.id == t.id && it.type == t.type } else listOf(t) + myList
        foyer.prefs.myList = foyer.tmdb.serialize(myList).toString()
    }

    private fun key(t: Title) = "${t.type}:${t.id}"

    fun refreshApps() {
        viewModelScope.launch { apps = withContext(Dispatchers.IO) { Apps.load(foyer) } }
    }

    fun refreshWatchNext() {
        viewModelScope.launch { watchNext = withContext(Dispatchers.IO) { WatchNext.read(foyer) } }
    }

    private fun loadTitles() {
        val tmdb = foyer.tmdb
        if (!tmdb.enabled) { artwork = ArtworkState.NO_KEY; return }
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { tmdb.cached() }
            if (cached.isNotEmpty()) show(cached)
            if (cached.isEmpty() || !tmdb.cacheIsFresh()) {
                val fresh = runCatching { tmdb.fetch(region()) }.getOrNull()
                when {
                    !fresh.isNullOrEmpty() -> show(fresh)
                    cached.isEmpty() -> artwork = ArtworkState.FAILED
                }
            }
        }
    }

    private fun show(list: List<Title>) {
        titles = list
        if (hero == null || list.none { it.id == hero?.id }) hero = list.first()
        artwork = ArtworkState.READY
    }

    /** Focus landed on a card: show it in the hero once focus settles. */
    fun preview(t: Title) {
        heroJob?.cancel()
        heroJob = viewModelScope.launch {
            delay(foyer.profile?.heroDebounceMs ?: 200L)
            hero = t
        }
    }

    fun onHomePressed() {
        screen = Screen.HOME
        homePresses++
        refreshWatchNext()
    }

    fun watchAction(t: Title): Watch {
        if (!t.detailed) return Watch.Pending(t)
        val services = t.providers.mapNotNull { Services.forProviderName(it) }.distinct()
        val installed = services.firstNotNullOfOrNull { s ->
            s.packages.firstOrNull { Apps.isInstalled(foyer, it) }?.let { Watch.Open(s.label, it) }
        }
        if (installed != null) return installed
        services.firstOrNull()?.let { return Watch.Install(it.label, it.packages.first()) }
        return Watch.Search(t.name)
    }

    fun perform(w: Watch) {
        when (w) {
            is Watch.Open -> Apps.launch(foyer, w.pkg)
            is Watch.Install -> Apps.openStorePage(foyer, w.pkg)
            is Watch.Search -> Apps.openSearch(foyer, w.query)
            is Watch.Pending -> open(w.title)
        }
    }

    fun region(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
}
