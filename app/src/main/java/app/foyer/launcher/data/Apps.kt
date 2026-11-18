package app.foyer.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** An installed TV app. [art] is its 16:9 TV banner, or its icon when it has none. */
class TvApp(val pkg: String, val label: String, val art: ImageBitmap, val isBanner: Boolean)

object Apps {

    /** Installed apps that can open on a TV, streaming services first. Run off the main thread. */
    fun load(context: Context): List<TvApp> {
        val pm = context.packageManager
        val tv = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val seen = HashSet<String>()
        val out = ArrayList<TvApp>()
        for (ri in pm.queryIntentActivities(tv, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) continue
            val label = ri.loadLabel(pm).toString()
            val banner: Drawable? = ri.activityInfo.loadBanner(pm) ?: ri.activityInfo.applicationInfo.loadBanner(pm)
            val app = if (banner != null) {
                TvApp(pkg, label, banner.toBitmap(320, 180, Bitmap.Config.ARGB_8888).asImageBitmap(), true)
            } else {
                TvApp(pkg, label, ri.loadIcon(pm).toBitmap(128, 128, Bitmap.Config.ARGB_8888).asImageBitmap(), false)
            }
            out += app
        }
        return out.sortedWith(compareBy<TvApp> { Services.rank(it.pkg) }.thenBy { it.label.lowercase() })
    }

    fun isInstalled(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    fun launch(context: Context, pkg: String): Boolean {
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return start(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Opens the app's Play Store page with the Install button ready. */
    fun openStorePage(context: Context, pkg: String): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

    /** The TV's own search screen (with voice), optionally pre-filled. */
    fun openSearch(context: Context, query: String? = null): Boolean {
        val intent = Intent("android.search.action.GLOBAL_SEARCH").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (query != null) intent.putExtra("query", query)
        return start(context, intent)
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent); true
    } catch (e: Exception) { false }
}
