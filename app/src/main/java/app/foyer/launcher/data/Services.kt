package app.foyer.launcher.data

/**
 * Streaming services Froyer knows how to hand off to. [match] is compared against
 * TMDB provider names; [packages] are the Android TV app ids, first one preferred.
 *
 * Package ids must be checked on a real TV before release: some services ship a
 * different id on TV than on phones.
 */
data class Service(val label: String, val packages: List<String>, val match: List<String>)

object Services {
    val all = listOf(
        Service("Netflix", listOf("com.netflix.ninja"), listOf("netflix")),
        Service("Prime Video", listOf("com.amazon.amazonvideo.livingroom"), listOf("amazon prime video", "prime video")),
        Service("JioHotstar", listOf("in.startv.hotstar"), listOf("hotstar", "jiocinema")),
        Service("YouTube", listOf("com.google.android.youtube.tv"), listOf("youtube")),
        Service("Apple TV", listOf("com.apple.atve.androidtv.appletv"), listOf("apple tv")),
        Service("ZEE5", listOf("com.graymatrix.did"), listOf("zee5")),
        Service("Sony LIV", listOf("com.sonyliv"), listOf("sony liv", "sonyliv")),
        Service("Disney+", listOf("com.disney.disneyplus"), listOf("disney plus", "disney+")),
        Service("Plex", listOf("com.plexapp.android"), listOf("plex")),
        Service("Spotify", listOf("com.spotify.tv.android"), emptyList()),
    )

    fun forProviderName(name: String): Service? {
        val n = name.lowercase()
        return all.firstOrNull { s -> s.match.any { n.contains(it) } }
    }

    /** Position used to put streaming apps first in the dock. */
    fun rank(pkg: String): Int =
        all.indexOfFirst { pkg in it.packages }.let { if (it < 0) Int.MAX_VALUE else it }
}
