package app.foyer.launcher.data

import android.content.Context
import app.foyer.launcher.perf.Tier

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("foyer", Context.MODE_PRIVATE)

    /** null means Auto. */
    var tierOverride: Tier?
        get() = sp.getString("tier", null)?.let { name -> Tier.entries.firstOrNull { it.name == name } }
        set(value) { sp.edit().putString("tier", value?.name).apply() }
}
