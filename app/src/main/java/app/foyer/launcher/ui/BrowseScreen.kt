package app.foyer.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import app.foyer.launcher.ArtworkState
import app.foyer.launcher.HomeViewModel
import app.foyer.launcher.Screen
import app.foyer.launcher.Watch
import app.foyer.launcher.data.Apps
import app.foyer.launcher.data.Row
import app.foyer.launcher.data.Title
import app.foyer.launcher.perf.DeviceProfile
import coil3.ImageLoader
import kotlinx.coroutines.launch

/** The Movies (type "movie") or Shows (type "tv") tab. */
@Composable
fun BrowseScreen(vm: HomeViewModel, type: String, profile: DeviceProfile, loader: ImageLoader) {
    val ctx = LocalContext.current
    LaunchedEffect(type) { vm.loadRows(type) }
    BrowseContent(
        screen = if (type == "movie") Screen.MOVIES else Screen.SHOWS,
        rows = vm.rows[type].orEmpty(),
        state = vm.rowsState[type] ?: ArtworkState.LOADING,
        hero = vm.browseHero[type],
        myList = vm.myList,
        profile = profile,
        loader = loader,
        actionFor = vm::watchAction,
        onPreview = { vm.previewBrowse(type, it) },
        onOpen = vm::open,
        onPerform = vm::perform,
        onToggleList = vm::toggleMyList,
        onNavigate = { vm.screen = it },
        onSearch = { Apps.openSearch(ctx) },
    )
}

@Composable
fun BrowseContent(
    screen: Screen,
    rows: List<Row>,
    state: ArtworkState,
    hero: Title?,
    myList: List<Title>,
    profile: DeviceProfile,
    loader: ImageLoader,
    actionFor: (Title) -> Watch,
    onPreview: (Title) -> Unit,
    onOpen: (Title) -> Unit,
    onPerform: (Watch) -> Unit,
    onToggleList: (Title) -> Unit,
    onNavigate: (Screen) -> Unit,
    onSearch: () -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val primary = remember { FocusRequester() }
    LaunchedEffect(screen, hero != null) { if (hero != null) primary.requestFocusWhenReady() }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        if (hero != null) HeroBackdrop(hero, profile, loader)
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            TopBar(screen, onNavigate = onNavigate, onSearch = onSearch)
            when {
                hero != null -> {
                    Spacer(Modifier.height(58.dp))
                    val action = actionFor(hero)
                    HeroInfo(
                        title = hero,
                        action = action,
                        loader = loader,
                        profile = profile,
                        buttonModifier = Modifier
                            .focusRequester(primary)
                            .onFocusGained { scope.launch { scroll.animateScrollTo(0) } },
                        onAction = { onPerform(action) },
                        inList = myList.any { it.id == hero.id && it.type == hero.type },
                        onToggleList = { onToggleList(hero) },
                    )
                    Spacer(Modifier.height(26.dp))
                }
                state == ArtworkState.FAILED -> Message("Can't reach the artwork service. Check the TV's internet connection.")
                else -> Spacer(Modifier.height(80.dp))
            }
            rows.forEach { row ->
                RowTitle(row.title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(row.items, key = { "${row.title}:${it.id}" }) { t ->
                        TitleCard(t, profile, loader, onFocus = { onPreview(t) }, onClick = { onOpen(t) })
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun Message(text: String) {
    Text(text, modifier = Modifier.padding(start = 48.dp, top = 40.dp, end = 48.dp), fontSize = 13.sp, color = Palette.Dim)
}
