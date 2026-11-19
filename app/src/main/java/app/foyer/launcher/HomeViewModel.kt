package app.foyer.launcher

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.foyer.launcher.data.Apps
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
import java.util.Locale

enum class Screen { HOME, APPS, SETTINGS }

enum class ArtworkState { LOADING, READY, NO_KEY, FAILED }

/** What the hero's main button does for a title. */
sealed interface Watch {
    data class Open(val label: String, val pkg: String) : Watch
    data class Install(val label: String, val pkg: String) : Watch
    data class Search(val query: String) : Watch
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

    private var heroJob: Job? = null

    init {
        refreshApps()
        loadTitles()
        refreshWatchNext()
    }

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
        }
    }

    fun region(): String = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
}
