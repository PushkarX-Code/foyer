package app.foyer.launcher.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.foyer.launcher.ArtworkState
import app.foyer.launcher.BuildConfig
import app.foyer.launcher.FoyerApp
import app.foyer.launcher.HomeViewModel
import app.foyer.launcher.Screen
import app.foyer.launcher.data.Apps
import app.foyer.launcher.data.WatchNextAccess
import app.foyer.launcher.perf.DeviceProfile
import app.foyer.launcher.perf.Tier

@Composable
fun SettingsScreen(vm: HomeViewModel, profile: DeviceProfile) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as FoyerApp
    val pm = ctx.packageManager
    val isHome = remember {
        pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == ctx.packageName
    }
    val homeSettings = remember {
        Intent(Settings.ACTION_HOME_SETTINGS).takeIf { it.resolveActivity(pm) != null }
    }
    SettingsContent(
        state = SettingsState(
            profile = profile,
            tierOverride = app.prefs.tierOverride,
            isHome = isHome,
            canChooseHome = homeSettings != null,
            watchNext = vm.watchNext.access,
            watchNextCount = vm.watchNext.items.size,
            artwork = vm.artwork,
            region = vm.region(),
            version = BuildConfig.VERSION_NAME,
        ),
        onSetTier = app::setTier,
        onChooseHome = { homeSettings?.let { Apps.start(ctx, it) } },
        onOpenTvSettings = { Apps.start(ctx, Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
        onNavigate = { vm.screen = it },
        onSearch = { Apps.openSearch(ctx) },
        tabs = if (vm.hasArtwork) AllTabs else AppsOnlyTabs,
    )
}

data class SettingsState(
    val profile: DeviceProfile,
    val tierOverride: Tier?,
    val isHome: Boolean,
    val canChooseHome: Boolean,
    val watchNext: WatchNextAccess,
    val watchNextCount: Int,
    val artwork: ArtworkState,
    val region: String,
    val version: String,
)

@Composable
fun SettingsContent(
    state: SettingsState,
    onSetTier: (Tier?) -> Unit,
    onChooseHome: () -> Unit,
    onOpenTvSettings: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onSearch: () -> Unit,
    tabs: List<Screen> = AllTabs,
) {
    val profile = state.profile
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestFocusWhenReady() }

    Column(Modifier.fillMaxSize().background(Palette.Ink).verticalScroll(rememberScrollState())) {
        TopBar(Screen.SETTINGS, onNavigate = onNavigate, onSearch = onSearch, tabs = tabs)
        Column(Modifier.padding(start = 48.dp, end = 48.dp, top = 28.dp, bottom = 40.dp).widthIn(max = 620.dp)) {

            Heading("Performance")
            Body(
                "This TV: ${"%.1f".format(profile.totalRamMb / 1024f)} GB RAM, " +
                    "${profile.panelWidth}×${profile.panelHeight}. Recommended: ${profile.detected.label}.",
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Auto", state.tierOverride == null, { onSetTier(null) },
                    Modifier.focusRequester(first), height = 30.dp)
                Tier.entries.forEach { t ->
                    PillButton(t.label, state.tierOverride == t, { onSetTier(t) }, height = 30.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Body(
                when (profile.tier) {
                    Tier.LITE -> "Lite: smaller artwork, no fades, image memory released whenever another app is open."
                    Tier.STANDARD -> "Standard: full-size artwork with fades."
                    Tier.PREMIUM -> "Premium: largest artwork for this screen, faster transitions, more kept in memory."
                },
                faint = true,
            )

            Spacer(Modifier.height(28.dp))
            Heading("Home screen")
            Body(if (state.isHome) "Froyer opens when you press Home." else "Froyer is not your home screen yet.")
            if (!state.isHome) {
                Spacer(Modifier.height(10.dp))
                if (state.canChooseHome) {
                    PillButton("Choose home app", false, onChooseHome, height = 30.dp)
                    Spacer(Modifier.height(6.dp))
                }
                Body(
                    "Google TV hides this choice. Run scripts/set-default.sh once from a computer on the same " +
                        "Wi-Fi: it switches the built-in home screen off, and scripts/restore.sh switches it back.",
                    faint = true,
                )
            }

            Spacer(Modifier.height(28.dp))
            Heading("This TV")
            Info("Continue watching from other apps", when (state.watchNext) {
                WatchNextAccess.AVAILABLE -> "Shared (${state.watchNextCount} items)"
                WatchNextAccess.OWN_ONLY -> "Not shared with Froyer"
                WatchNextAccess.BLOCKED -> "Blocked by this TV"
                WatchNextAccess.MISSING -> "Not supported"
            })
            Info("Artwork", when (state.artwork) {
                ArtworkState.READY -> "Connected"
                ArtworkState.LOADING -> "Loading"
                ArtworkState.NO_KEY -> "Not set up in this build"
                ArtworkState.FAILED -> "Can't reach the artwork service"
            })
            Info("Region for streaming availability", state.region)
            Info("Low-RAM device flag", if (profile.lowRamFlag) "Yes" else "No")
            Info("Version", state.version)
            Spacer(Modifier.height(10.dp))
            Body(
                "Film and series data and artwork from TMDB. This product uses the TMDB API " +
                    "but is not endorsed or certified by TMDB.",
                faint = true,
            )
            Spacer(Modifier.height(14.dp))
            PillButton("Open TV settings", false, onOpenTvSettings, height = 30.dp)
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Body(text: String, faint: Boolean = false) {
    Text(text, fontSize = 11.5.sp, lineHeight = 16.sp, color = if (faint) Palette.Faint else Palette.Dim)
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 11.5.sp, color = Palette.Dim, modifier = Modifier.width(260.dp))
        Text(value, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
    }
}
