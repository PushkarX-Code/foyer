package app.foyer.launcher

import android.app.Application
import android.content.ComponentCallbacks2
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foyer.launcher.data.Prefs
import app.foyer.launcher.data.Tmdb
import app.foyer.launcher.perf.DeviceProfile
import app.foyer.launcher.perf.Tier
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.util.concurrent.TimeUnit

class FoyerApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var http: OkHttpClient
        private set
    lateinit var tmdb: Tmdb
        private set

    var profile by mutableStateOf<DeviceProfile?>(null)
        private set
    var imageLoader by mutableStateOf<ImageLoader?>(null)
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        http = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        tmdb = Tmdb(BuildConfig.TMDB_TOKEN, http, cacheDir.resolve("tmdb.json"))
        applyProfile(DeviceProfile.detect(this, prefs.tierOverride))
    }

    /** Switch tier at runtime: rebuilds the image pipeline with the new limits. */
    fun setTier(override: Tier?) {
        prefs.tierOverride = override
        applyProfile(DeviceProfile.detect(this, override))
    }

    private fun applyProfile(p: DeviceProfile) {
        val old = imageLoader
        profile = p
        imageLoader = ImageLoader.Builder(this)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { http })) }
            .memoryCache { MemoryCache.Builder().maxSizePercent(this, p.memoryCachePercent).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("img").toOkioPath())
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .crossfade(p.crossfade)
            .allowRgb565(p.allowRgb565)
            .build()
        old?.shutdown()
    }

    /**
     * When the user opens Netflix or another app, Foyer is hidden: give its image
     * memory back so the streaming app has room. Artwork reloads from disk on return.
     */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val cache = imageLoader?.memoryCache ?: return
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> cache.clear()
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ->
                if (profile?.tier == Tier.LITE) cache.clear() else cache.trimToSize(cache.size / 2)
        }
    }
}
