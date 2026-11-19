package app.foyer.launcher

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.cash.paparazzi.detectEnvironment
import app.foyer.launcher.data.Title
import app.foyer.launcher.data.TvApp
import app.foyer.launcher.data.WatchNextAccess
import app.foyer.launcher.data.WatchNextResult
import app.foyer.launcher.perf.DeviceProfile
import app.foyer.launcher.perf.Tier
import app.foyer.launcher.ui.AppsContent
import app.foyer.launcher.ui.BrowseContent
import app.foyer.launcher.ui.LibraryContent
import app.foyer.launcher.data.Row
import app.foyer.launcher.ui.HomeContent
import app.foyer.launcher.ui.SettingsContent
import app.foyer.launcher.ui.SettingsState
import app.foyer.launcher.ui.HomeState
import app.foyer.launcher.ui.FroyerTheme
import coil3.ImageLoader
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Renders the real home screen code at 1080p without a TV. Run with
 * FOYER_ART_DIR pointing at sample artwork:  ./gradlew recordPaparazziDebug
 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920,
            screenHeight = 1080,
            xdpi = 320,
            ydpi = 320,
            density = Density.XHIGH,
            orientation = ScreenOrientation.LANDSCAPE,
        ),
        theme = "android:Theme.DeviceDefault.NoActionBar",
        // The renderer bundled with Paparazzi 1.3 is Android 14; render as 34 so Compose
        // does not call text APIs that renderer lacks.
        environment = detectEnvironment().copy(compileSdkVersion = 34),
        useDeviceResolution = true,
    )

    private val artDir = System.getProperty("foyer.art")?.let(::File)

    private fun art(name: String): Bitmap =
        artDir?.resolve(name)?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            ?: Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888).apply {
                eraseColor(if (name == "none.png") AColor.TRANSPARENT else AColor.DKGRAY)
            }

    private val apps by lazy { listOf(
        "netflix" to "Netflix", "youtube" to "YouTube", "primevideo" to "Prime Video", "appletv" to "Apple TV",
        "spotify" to "Spotify", "plex" to "Plex", "hbo" to "HBO", "crunchyroll" to "Crunchyroll",
    ).map { (id, label) -> TvApp(id, label, art("banner_$id.png").asImageBitmap(), true) } }

    private fun title(id: Int, name: String, type: String, file: String, logo: String?, genre: String, year: String, length: String, providers: List<String>, overview: String = "") =
        Title(id, type, name, overview, year, genre, length, "/$file", logo?.let { "/$it" }, providers)

    private val titles = listOf(
        title(693134, "Dune: Part Two", "movie", "hero_dune.jpg", "logo_dune_white.png", "Science Fiction", "2024", "2h 47m",
            listOf("Amazon Prime Video"),
            "Paul Atreides unites with Chani and the Fremen while on a path of revenge against the conspirators who destroyed his family."),
        title(95396, "Severance", "tv", "c_severance.jpg", "none.png", "Drama", "2022", "2 seasons", listOf("Apple TV Plus")),
        title(84105, "Mirzapur", "tv", "c_mirzapur.jpg", "none.png", "Crime", "2018", "3 seasons", listOf("Amazon Prime Video")),
        title(157336, "Interstellar", "movie", "c_interstellar.jpg", "none.png", "Adventure", "2014", "2h 49m", listOf("Netflix")),
        title(126308, "Shōgun", "tv", "c_shogun.jpg", "none.png", "Drama", "2024", "1 season", listOf("JioHotstar")),
        title(136315, "The Bear", "tv", "c_bear.jpg", "none.png", "Comedy", "2022", "4 seasons", listOf("JioHotstar")),
        title(569094, "Spider-Man: Across the Spider-Verse", "movie", "c_spiderverse.jpg", "none.png", "Animation", "2023", "2h 20m", listOf("Netflix")),
    )

    private fun profile(tier: Tier) = DeviceProfile(1536, false, 1366, 768, Tier.LITE, tier)

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        val handler = AsyncImagePreviewHandler { request ->
            art(request.data.toString().substringAfterLast('/')).asImage()
        }
        FroyerTheme {
            CompositionLocalProvider(
                LocalAsyncImagePreviewHandler provides handler,
                LocalInspectionMode provides true,
            ) { content() }
        }
    }

    private fun home(state: HomeState, tier: Tier) {
        paparazzi.snapshot {
            Frame {
                HomeContent(
                    state = state,
                    profile = profile(tier),
                    loader = ImageLoader.Builder(paparazzi.context).build(),
                    actionFor = { t ->
                        when {
                            "Prime" in t.providers.first() -> Watch.Open("Prime Video", "primevideo")
                            "Netflix" in t.providers.first() -> Watch.Open("Netflix", "netflix")
                            else -> Watch.Install("JioHotstar", "in.startv.hotstar")
                        }
                    },
                    onPreview = {}, onPerform = {}, onNavigate = {}, onLaunchApp = {}, onSearch = {},
                )
            }
        }
    }

    @Test
    fun homeWithArtwork() = home(
        HomeState(apps, titles.drop(1), titles.first(), ArtworkState.READY, WatchNextResult(WatchNextAccess.BLOCKED, emptyList())),
        Tier.LITE,
    )

    @Test
    fun homeWithoutArtwork() = home(
        HomeState(apps, emptyList(), null, ArtworkState.NO_KEY, WatchNextResult(WatchNextAccess.BLOCKED, emptyList())),
        Tier.LITE,
    )

    @Test
    fun appsGrid() = paparazzi.snapshot {
        Frame {
            val more = listOf("twitch", "applemusic", "kodi", "mubi", "tubi", "jellyfin", "youtubemusic")
                .map { TvApp(it, it, art("banner_$it.png").asImageBitmap(), true) }
            AppsContent(apps + more, profile(Tier.LITE), {}, {}, {})
        }
    }

    @Test
    fun settings() = paparazzi.snapshot {
        Frame {
            SettingsContent(
                state = SettingsState(
                    profile = profile(Tier.LITE),
                    tierOverride = null,
                    isHome = true,
                    canChooseHome = false,
                    watchNext = WatchNextAccess.BLOCKED,
                    watchNextCount = 0,
                    artwork = ArtworkState.READY,
                    region = "IN",
                    version = "0.1.3",
                ),
                onSetTier = {}, onChooseHome = {}, onOpenTvSettings = {}, onNavigate = {}, onSearch = {},
            )
        }
    }

    /** Real Movies-tab data saved from TMDB into FOYER_ART_DIR/movies.tsv. */
    private val movies by lazy {
        val lines = artDir?.resolve("movies.tsv")?.takeIf { it.exists() }?.readLines().orEmpty().map { it.split('\t') }
        val hero = lines.firstOrNull { it[0] == "HERO" }?.let {
            Title(it[1].toInt(), "movie", it[2], it[9], it[3], it[4], it[5], "/${it[6]}", it[7].ifBlank { null }?.let { l -> "/$l" }, listOf(it[8]))
        }
        val rows = lines.filter { it[0] == "ROW" }.groupBy { it[1] }.map { (name, items) ->
            Row(name, items.map { Title(it[2].toInt(), "movie", it[3], it[7], it[4], it[5], "", "/${it[6]}", null, emptyList(), detailed = false) })
        }
        hero to rows
    }

    @Test
    fun moviesTab() = paparazzi.snapshot {
        val (hero, rows) = movies
        Frame {
            BrowseContent(
                screen = Screen.MOVIES, rows = rows, state = ArtworkState.READY, hero = hero, myList = emptyList(),
                profile = profile(Tier.LITE), loader = ImageLoader.Builder(paparazzi.context).build(),
                actionFor = { Watch.Open("Prime Video", "primevideo") },
                onPreview = {}, onOpen = {}, onPerform = {}, onToggleList = {}, onNavigate = {}, onSearch = {},
            )
        }
    }

    @Test
    fun library() = paparazzi.snapshot {
        val saved = movies.second.flatMap { it.items }.distinctBy { it.id }.take(7)
        Frame {
            LibraryContent(
                myList = saved, watchNext = WatchNextResult(WatchNextAccess.BLOCKED, emptyList()),
                profile = profile(Tier.LITE), loader = ImageLoader.Builder(paparazzi.context).build(),
                onOpen = {}, onNavigate = {}, onSearch = {},
            )
        }
    }
}
