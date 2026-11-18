package app.foyer.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.foyer.launcher.HomeViewModel
import app.foyer.launcher.Screen
import app.foyer.launcher.data.Apps
import app.foyer.launcher.perf.DeviceProfile

@Composable
fun AppsScreen(vm: HomeViewModel, profile: DeviceProfile) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    LaunchedEffect(vm.apps.isNotEmpty()) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        TopBar(Screen.APPS, onNavigate = { vm.screen = it }, onSearch = { Apps.openSearch(ctx) })
        Spacer(Modifier.height(22.dp))
        RowTitle("All apps")
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 6.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            itemsIndexed(vm.apps, key = { _, a -> a.pkg }) { i, app ->
                AppTile(
                    app = app,
                    profile = profile,
                    onClick = { Apps.launch(ctx, app.pkg) },
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                )
            }
        }
    }
}
