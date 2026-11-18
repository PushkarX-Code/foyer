package app.foyer.launcher.data

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri

class WatchNextItem(
    val title: String,
    val subtitle: String,
    val image: String?,
    val pkg: String,
    val intent: Intent?,
    val progress: Float,
)

enum class WatchNextAccess { AVAILABLE, OWN_ONLY, BLOCKED, MISSING }

class WatchNextResult(val access: WatchNextAccess, val items: List<WatchNextItem>)

/**
 * Reads the system "Watch Next" list that streaming apps publish to. Android may only
 * let the system launcher see other apps' entries; this reports what this TV allows.
 */
object WatchNext {
    private val uri = Uri.parse("content://android.media.tv/watch_next_program")

    fun read(context: Context): WatchNextResult {
        val cursor = try {
            context.contentResolver.query(uri, null, null, null, "last_engagement_time_utc_millis DESC")
        } catch (e: SecurityException) {
            return WatchNextResult(WatchNextAccess.BLOCKED, emptyList())
        } catch (e: Exception) {
            return WatchNextResult(WatchNextAccess.MISSING, emptyList())
        } ?: return WatchNextResult(WatchNextAccess.MISSING, emptyList())

        val items = ArrayList<WatchNextItem>()
        cursor.use { c ->
            while (c.moveToNext() && items.size < 20) {
                val pkg = c.str("package_name") ?: continue
                if (pkg == context.packageName) continue
                val pos = c.long("last_playback_position_millis")
                val dur = c.long("duration_millis")
                val season = c.str("season_display_number")
                val episode = c.str("episode_display_number")
                val sub = if (season != null && episode != null) "S$season · E$episode" else c.str("episode_title") ?: ""
                val intent = c.str("intent_uri")?.let {
                    runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull()
                }
                items += WatchNextItem(
                    title = c.str("title") ?: continue,
                    subtitle = sub,
                    image = c.str("poster_art_uri") ?: c.str("thumbnail_uri"),
                    pkg = pkg,
                    intent = intent,
                    progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                )
            }
        }
        return WatchNextResult(if (items.isEmpty()) WatchNextAccess.OWN_ONLY else WatchNextAccess.AVAILABLE, items)
    }

    private fun Cursor.str(col: String): String? =
        getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getString(it) }

    private fun Cursor.long(col: String): Long =
        getColumnIndex(col).takeIf { it >= 0 && !isNull(it) }?.let { getLong(it) } ?: 0L
}
