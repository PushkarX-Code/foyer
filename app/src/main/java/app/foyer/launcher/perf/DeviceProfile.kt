package app.foyer.launcher.perf

import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

enum class Tier(val label: String) { LITE("Lite"), STANDARD("Standard"), PREMIUM("Premium") }

/**
 * What this TV can afford. Detected once at startup from RAM and the physical panel
 * resolution; the user can force a tier in Settings.
 *
 * RAM decides effects and how much is kept in memory. The panel resolution decides
 * artwork size, so a 1366x768 TV never decodes 4K images.
 */
data class DeviceProfile(
    val totalRamMb: Long,
    val lowRamFlag: Boolean,
    val panelWidth: Int,
    val panelHeight: Int,
    val detected: Tier,
    val tier: Tier,
) {
    val isForced: Boolean get() = tier != detected

    /** TMDB backdrop size for the full-screen hero. */
    val heroImageSize: String
        get() = if (panelWidth > 1920 && tier == Tier.PREMIUM) "original" else "w1280"

    /** TMDB backdrop size for row cards (about 200dp wide on screen). */
    val cardImageSize: String
        get() = if (tier == Tier.LITE || panelWidth <= 1366) "w300" else "w780"

    val logoImageSize: String
        get() = if (tier == Tier.LITE) "w300" else "w500"

    /** Wait this long after focus settles before swapping the hero image, so fast
     *  scrolling does not decode every backdrop it passes. */
    val heroDebounceMs: Long
        get() = when (tier) { Tier.LITE -> 350L; Tier.STANDARD -> 200L; Tier.PREMIUM -> 120L }

    val crossfade: Boolean get() = tier != Tier.LITE

    val focusScale: Float get() = if (tier == Tier.LITE) 1.06f else 1.1f

    /** Share of the app's memory budget the image cache may use. */
    val memoryCachePercent: Double
        get() = when (tier) { Tier.LITE -> 0.12; Tier.STANDARD -> 0.2; Tier.PREMIUM -> 0.25 }

    /** Use 16-bit images on Lite: half the memory, invisible on photos at TV distance. */
    val allowRgb565: Boolean get() = tier == Tier.LITE

    companion object {
        fun detect(context: Context, override: Tier?): DeviceProfile {
            val am = context.getSystemService(ActivityManager::class.java)
            val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val ramMb = info.totalMem / (1024 * 1024)
            val lowRam = am.isLowRamDevice

            val display = context.getSystemService(DisplayManager::class.java)
                .getDisplay(Display.DEFAULT_DISPLAY)
            val mode = display.mode
            val w = maxOf(mode.physicalWidth, mode.physicalHeight)
            val h = minOf(mode.physicalWidth, mode.physicalHeight)

            // Cut-offs are first guesses, to be tuned on real TVs. totalMem reports a
            // little under the marketed size (a "2 GB" TV shows about 1.8 GB).
            val detected = when {
                lowRam || ramMb < 2300 -> Tier.LITE
                ramMb < 3600 -> Tier.STANDARD
                else -> Tier.PREMIUM
            }
            return DeviceProfile(ramMb, lowRam, w, h, detected, override ?: detected)
        }
    }
}
