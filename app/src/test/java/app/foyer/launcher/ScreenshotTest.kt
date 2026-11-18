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
import app.foyer.launcher.ui.HomeContent
import app.foyer.launcher.ui.HomeState
import app.foyer.launcher.ui.Palette
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
        MaterialTheme(colorScheme = darkColorScheme(background = Palette.Ink, surface = Palette.Ink)) {
            CompositionLocalProvider(
                LocalContentColor provides Color.White,
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
}
