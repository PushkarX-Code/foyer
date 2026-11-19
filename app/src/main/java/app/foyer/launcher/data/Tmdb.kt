package app.foyer.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A film or series for the home screen. Image fields are TMDB paths; the size is
 *  chosen at display time from the device profile. */
data class Title(
    val id: Int,
    val type: String,
    val name: String,
    val overview: String,
    val year: String,
    val genre: String,
    val length: String,
    val backdrop: String,
    val logo: String?,
    val providers: List<String>,
)

object TmdbImages {
    fun url(path: String, size: String) = "https://image.tmdb.org/t/p/$size$path"
}

/**
 * Trending titles with artwork, title logos and where they stream in the TV's region.
 * Cache-first: the last result is shown instantly on boot and refreshed in the
 * background every few hours.
 */
class Tmdb(private val token: String, private val http: OkHttpClient, private val cacheFile: File) {

    val enabled: Boolean get() = token.isNotBlank()

    fun cached(): List<Title> = runCatching { parse(JSONArray(cacheFile.readText())) }.getOrDefault(emptyList())

    fun cacheIsFresh(): Boolean =
        cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 6 * 3600_000L

    /**
     * What's popular and actually streamable in [region]: this week's trending titles
     * first, topped up with the most popular streaming films and series. Cinema-only
     * releases are left out, since every card has to open in an app.
     */
    suspend fun fetch(region: String): List<Title> = withContext(Dispatchers.IO) {
        val streaming = "watch_region=$region&with_watch_monetization_types=flatrate&sort_by=popularity.desc&language=en-US"
        val candidates = LinkedHashMap<String, JSONObject>()
        fun collect(path: String, type: String?) {
            val results = runCatching { get(path).getJSONArray("results") }.getOrNull() ?: return
            for (i in 0 until results.length()) {
                val item = results.getJSONObject(i)
                val t = type ?: item.optString("media_type")
                if (t !in setOf("movie", "tv") || item.optString("backdrop_path").isBlank()) continue
                item.put("media_type", t)
                candidates.putIfAbsent("$t:${item.getInt("id")}", item)
            }
        }
        collect("/trending/all/week?language=en-US", null)
        collect("/discover/movie?$streaming", "movie")
        collect("/discover/tv?$streaming", "tv")

        val gate = Semaphore(6)
        val titles = coroutineScope {
            candidates.values.take(40).map { item ->
                async { gate.withPermit { runCatching { details(item, region) }.getOrNull() } }
            }.awaitAll()
        }.filterNotNull().filter { it.providers.isNotEmpty() }.take(20)

        if (titles.isNotEmpty()) cacheFile.writeText(serialize(titles).toString())
        titles
    }

    private fun details(item: JSONObject, region: String): Title {
        val type = item.getString("media_type")
        val id = item.getInt("id")
        val d = get("/$type/$id?language=en-US&append_to_response=images,watch/providers&include_image_language=en,null")

        val logo = d.optJSONObject("images")?.optJSONArray("logos")?.let { logos ->
            (0 until logos.length()).map { logos.getJSONObject(it) }
                .firstOrNull { it.optString("iso_639_1") == "en" }?.optString("file_path")
        }
        val flatrate = d.optJSONObject("watch/providers")?.optJSONObject("results")
            ?.optJSONObject(region)?.optJSONArray("flatrate")
        val providers = flatrate?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).getString("provider_name") } }
            ?: emptyList()

        val date = d.optString(if (type == "movie") "release_date" else "first_air_date")
        val length = if (type == "movie") {
            d.optInt("runtime").takeIf { it > 0 }?.let { "${it / 60}h ${it % 60}m" } ?: ""
        } else {
            d.optInt("number_of_seasons").takeIf { it > 0 }?.let { if (it == 1) "1 season" else "$it seasons" } ?: ""
        }
        return Title(
            id = id,
            type = type,
            name = d.optString(if (type == "movie") "title" else "name"),
            overview = d.optString("overview"),
            year = date.take(4),
            genre = d.optJSONArray("genres")?.optJSONObject(0)?.optString("name") ?: "",
            length = length,
            backdrop = d.getString("backdrop_path"),
            logo = logo,
            providers = providers,
        )
    }

    private fun get(path: String): JSONObject {
        // TMDB issues two kinds of credential: a 32-character v3 API key (sent as a
        // query parameter) and a long v4 read token (sent as a Bearer header).
        val isV3Key = token.length == 32 && token.all { it.isDigit() || it in 'a'..'f' }
        val url = "https://api.themoviedb.org/3$path" +
            if (isV3Key) (if ('?' in path) "&" else "?") + "api_key=$token" else ""
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (!isV3Key) header("Authorization", "Bearer $token") }
            .build()
        http.newCall(req).execute().use { res ->
            check(res.isSuccessful) { "TMDB ${res.code}" }
            return JSONObject(res.body!!.string())
        }
    }

    private fun serialize(list: List<Title>) = JSONArray().apply {
        list.forEach { t ->
            put(JSONObject().apply {
                put("id", t.id); put("type", t.type); put("name", t.name); put("overview", t.overview)
                put("year", t.year); put("genre", t.genre); put("length", t.length)
                put("backdrop", t.backdrop); put("logo", t.logo ?: JSONObject.NULL)
                put("providers", JSONArray(t.providers))
            })
        }
    }

    private fun parse(arr: JSONArray) = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Title(
            id = o.getInt("id"), type = o.getString("type"), name = o.getString("name"),
            overview = o.getString("overview"), year = o.getString("year"), genre = o.getString("genre"),
            length = o.getString("length"), backdrop = o.getString("backdrop"),
            logo = if (o.isNull("logo")) null else o.getString("logo"),
            providers = o.getJSONArray("providers").let { p -> (0 until p.length()).map { p.getString(it) } },
        )
    }
}
