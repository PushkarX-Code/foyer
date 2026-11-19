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
    /** False for titles straight from a browse list: logo, length and providers are
     *  fetched when the title is focused or opened. */
    val detailed: Boolean = true,
)

/** A named row of titles on the Movies or Shows tab. */
data class Row(val title: String, val items: List<Title>)

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

    /**
     * Rows for the Movies (type "movie") or Shows ("tv") tab, all streamable in
     * [region]. Cache-first like the home feed; titles arrive without details.
     */
    suspend fun browse(type: String, region: String, cache: File): List<Row> = withContext(Dispatchers.IO) {
        if (cache.exists() && System.currentTimeMillis() - cache.lastModified() < 6 * 3600_000L) {
            runCatching { return@withContext parseRows(JSONArray(cache.readText())) }
        }
        val base = "/discover/$type?watch_region=$region&with_watch_monetization_types=flatrate&language=en-US"
        val specs = buildList {
            add("Popular to stream" to "$base&sort_by=popularity.desc")
            add("Top rated" to "$base&sort_by=vote_average.desc&vote_count.gte=${if (type == "movie") 1000 else 300}")
            if (region == "IN") add("Hindi" to "$base&sort_by=popularity.desc&with_original_language=hi")
            val genres = if (type == "movie") listOf(28 to "Action", 35 to "Comedy", 53 to "Thriller", 878 to "Science fiction")
            else listOf(80 to "Crime", 18 to "Drama", 35 to "Comedy", 10765 to "Sci-fi and fantasy")
            genres.forEach { (id, name) -> add(name to "$base&sort_by=popularity.desc&with_genres=$id") }
        }
        val rows = coroutineScope {
            specs.map { (name, path) ->
                async {
                    runCatching {
                        val res = get(path).getJSONArray("results")
                        val items = (0 until res.length()).map { res.getJSONObject(it) }
                            .filter { it.optString("backdrop_path").isNotBlank() }
                            .map { brief(it, type) }
                        Row(name, items)
                    }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull().filter { it.items.size >= 4 }
        if (rows.isNotEmpty()) cache.writeText(JSONArray().apply {
            rows.forEach { r -> put(JSONObject().put("title", r.title).put("items", serialize(r.items))) }
        }.toString())
        rows
    }

    private fun brief(o: JSONObject, type: String): Title {
        val date = o.optString(if (type == "movie") "release_date" else "first_air_date")
        val genreId = o.optJSONArray("genre_ids")?.optInt(0) ?: 0
        return Title(
            id = o.getInt("id"), type = type,
            name = o.optString(if (type == "movie") "title" else "name"),
            overview = o.optString("overview"), year = date.take(4),
            genre = GENRES[genreId] ?: "", length = "",
            backdrop = o.getString("backdrop_path"), logo = null, providers = emptyList(), detailed = false,
        )
    }

    private fun parseRows(arr: JSONArray) = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Row(o.getString("title"), parse(o.getJSONArray("items")))
    }

    private fun details(item: JSONObject, region: String): Title =
        details(item.getString("media_type"), item.getInt("id"), region)

    /** Full details for one title: logo, genre, length and where it streams. */
    fun details(type: String, id: Int, region: String): Title {
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

    fun serialize(list: List<Title>) = JSONArray().apply {
        list.forEach { t ->
            put(JSONObject().apply {
                put("id", t.id); put("type", t.type); put("name", t.name); put("overview", t.overview)
                put("year", t.year); put("genre", t.genre); put("length", t.length)
                put("backdrop", t.backdrop); put("logo", t.logo ?: JSONObject.NULL)
                put("providers", JSONArray(t.providers))
                put("detailed", t.detailed)
            })
        }
    }

    fun parse(arr: JSONArray) = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Title(
            id = o.getInt("id"), type = o.getString("type"), name = o.getString("name"),
            overview = o.getString("overview"), year = o.getString("year"), genre = o.getString("genre"),
            length = o.getString("length"), backdrop = o.getString("backdrop"),
            logo = if (o.isNull("logo")) null else o.getString("logo"),
            providers = o.getJSONArray("providers").let { p -> (0 until p.length()).map { p.getString(it) } },
            detailed = o.optBoolean("detailed", true),
        )
    }
}

private val GENRES = mapOf(
    28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime",
    99 to "Documentary", 18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History",
    27 to "Horror", 10402 to "Music", 9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction",
    53 to "Thriller", 10752 to "War", 37 to "Western", 10759 to "Action & Adventure", 10762 to "Kids",
    10763 to "News", 10764 to "Reality", 10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk",
    10768 to "War & Politics",
)
