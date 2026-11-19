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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.foyer.launcher.HomeViewModel
import app.foyer.launcher.Screen
import app.foyer.launcher.data.Apps
import app.foyer.launcher.data.Title
import app.foyer.launcher.data.WatchNextAccess
import app.foyer.launcher.data.WatchNextResult
import app.foyer.launcher.perf.DeviceProfile
import coil3.ImageLoader

@Composable
fun LibraryScreen(vm: HomeViewModel, profile: DeviceProfile, loader: ImageLoader) {
    val ctx = LocalContext.current
    LibraryContent(
        myList = vm.myList,
        watchNext = vm.watchNext,
        profile = profile,
        loader = loader,
        onOpen = vm::open,
        onNavigate = { vm.screen = it },
        onSearch = { Apps.openSearch(ctx) },
        tabs = if (vm.hasArtwork) AllTabs else AppsOnlyTabs,
    )
}

@Composable
fun LibraryContent(
    myList: List<Title>,
    watchNext: WatchNextResult,
    profile: DeviceProfile,
    loader: ImageLoader,
    onOpen: (Title) -> Unit,
    onNavigate: (Screen) -> Unit,
    onSearch: () -> Unit,
    tabs: List<Screen> = AllTabs,
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(myList.isNotEmpty()) { if (myList.isNotEmpty()) first.requestFocusWhenReady() }

    Column(Modifier.fillMaxSize().background(Palette.Ink)) {
        TopBar(Screen.LIBRARY, onNavigate = onNavigate, onSearch = onSearch, tabs = tabs)
        Spacer(Modifier.height(22.dp))
        if (watchNext.access == WatchNextAccess.AVAILABLE) {
            RowTitle("Continue watching")
            WatchNextRow(watchNext.items, profile, loader)
            Spacer(Modifier.height(18.dp))
        }
        if (myList.isEmpty()) {
            RowTitle("My list")
            Message("Press + on any film or series and it's saved here. Your list stays on this TV.")
        } else {
            RowTitle("My list")
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 0.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(myList, key = { "${it.type}:${it.id}" }) { t ->
                    TitleCard(
                        t, profile, loader, onFocus = {}, onClick = { onOpen(t) },
                        modifier = if (t == myList.first()) Modifier.focusRequester(first) else Modifier,
                    )
                }
            }
        }
    }
}
