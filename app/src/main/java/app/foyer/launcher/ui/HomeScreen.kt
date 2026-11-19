package app.foyer.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.foyer.launcher.ArtworkState
import app.foyer.launcher.HomeViewModel
import app.foyer.launcher.Screen
import app.foyer.launcher.Watch
import app.foyer.launcher.data.Apps
import app.foyer.launcher.data.Title
import app.foyer.launcher.data.TmdbImages
import app.foyer.launcher.data.TvApp
import app.foyer.launcher.data.WatchNextResult
import app.foyer.launcher.data.WatchNextAccess
import app.foyer.launcher.data.WatchNextItem
import app.foyer.launcher.perf.DeviceProfile
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: HomeViewModel, profile: DeviceProfile, loader: ImageLoader) {
    val ctx = LocalContext.current
    HomeContent(
        state = HomeState(
            apps = vm.apps,
            titles = vm.titles,
            hero = vm.hero,
            artwork = vm.artwork,
            watchNext = vm.watchNext,
            homePresses = vm.homePresses,
        ),
        profile = profile,
        loader = loader,
        actionFor = vm::watchAction,
        onPreview = vm::preview,
        onPerform = vm::perform,
        onNavigate = { vm.screen = it },
        onLaunchApp = { Apps.launch(ctx, it) },
        onSearch = { Apps.openSearch(ctx) },
    )
}

/** Everything the home screen shows. Kept separate from the view model so the screen
 *  can be rendered in tests and previews without a TV. */
data class HomeState(
    val apps: List<TvApp>,
    val titles: List<Title>,
    val hero: Title?,
    val artwork: ArtworkState,
    val watchNext: WatchNextResult,
    val homePresses: Int = 0,
)

@Composable
fun HomeContent(
    state: HomeState,
    profile: DeviceProfile,
    loader: ImageLoader,
    actionFor: (Title) -> Watch,
    onPreview: (Title) -> Unit,
    onPerform: (Watch) -> Unit,
    onNavigate: (Screen) -> Unit,
    onLaunchApp: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val primaryFocus = remember { FocusRequester() }
    val hero = state.hero
    val showHero = state.artwork == ArtworkState.READY && hero != null

    LaunchedEffect(state.homePresses, showHero, state.apps.isNotEmpty()) {
        scroll.scrollTo(0)
        primaryFocus.requestFocusWhenReady()
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        if (showHero) HeroBackdrop(hero!!, profile, loader)

        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            TopBar(Screen.HOME, onNavigate = onNavigate, onSearch = onSearch)

            if (showHero) {
                Spacer(Modifier.height(58.dp))
                val action = remember(hero, state.apps) { actionFor(hero!!) }
                HeroInfo(
                    title = hero!!,
                    action = action,
                    loader = loader,
                    profile = profile,
                    buttonModifier = Modifier
                        .focusRequester(primaryFocus)
                        .onFocusGained { scope.launch { scroll.animateScrollTo(0) } },
                    onAction = { onPerform(action) },
                )
                Spacer(Modifier.height(26.dp))
            } else {
                Spacer(Modifier.height(36.dp))
            }

            if (state.watchNext.access == WatchNextAccess.AVAILABLE) {
                RowTitle("Continue watching")
                WatchNextRow(state.watchNext.items, profile, loader)
                Spacer(Modifier.height(18.dp))
            }

            if (showHero) {
                RowTitle("Popular to stream")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(state.titles, key = { _, t -> t.id }) { _, t ->
                        TitleCard(
                            title = t,
                            profile = profile,
                            loader = loader,
                            onFocus = { onPreview(t) },
                            onClick = { onPerform(actionFor(t)) },
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            RowTitle("Your apps")
            if (showHero) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(state.apps, key = { _, a -> a.pkg }) { _, app ->
                        AppTile(app = app, profile = profile, onClick = { onLaunchApp(app.pkg) })
                    }
                }
            } else {
                // No artwork: the apps are the home screen, laid out as a grid.
                Column(
                    Modifier.padding(horizontal = 48.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.apps.chunked(5).forEachIndexed { r, row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEachIndexed { c, app ->
                                AppTile(
                                    app = app,
                                    profile = profile,
                                    onClick = { onLaunchApp(app.pkg) },
                                    modifier = if (r == 0 && c == 0) Modifier.focusRequester(primaryFocus) else Modifier,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** Full-bleed artwork behind the home screen. The previous image stays on screen
 *  until the next one is decoded, so switching never flashes black. */
@Composable
private fun HeroBackdrop(title: Title, profile: DeviceProfile, loader: ImageLoader) {
    val ctx = LocalContext.current
    var lastKey by remember { mutableStateOf<MemoryCache.Key?>(null) }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = ImageRequest.Builder(ctx)
                .data(TmdbImages.url(title.backdrop, profile.heroImageSize))
                .placeholderMemoryCacheKey(lastKey)
                .build(),
            contentDescription = null,
            imageLoader = loader,
            onSuccess = { lastKey = it.result.memoryCacheKey },
            modifier = Modifier.fillMaxWidth().height(470.dp),
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.3f),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Palette.Ink.copy(alpha = 0.6f), 0.18f to Color.Transparent)))
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Palette.Ink.copy(alpha = 0.9f),
                    0.24f to Palette.Ink.copy(alpha = 0.62f),
                    0.5f to Palette.Ink.copy(alpha = 0.1f),
                    0.62f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.38f to Color.Transparent,
                    0.55f to Palette.Ink.copy(alpha = 0.55f),
                    0.68f to Palette.Ink.copy(alpha = 0.92f),
                    0.8f to Palette.Ink,
                ),
            ),
        )
    }
}

@Composable
private fun HeroInfo(
    title: Title,
    action: Watch,
    loader: ImageLoader,
    profile: DeviceProfile,
    buttonModifier: Modifier,
    onAction: () -> Unit,
) {
    Column(Modifier.padding(start = 48.dp).width(380.dp)) {
        if (title.logo != null) {
            AsyncImage(
                model = TmdbImages.url(title.logo, profile.logoImageSize),
                contentDescription = title.name,
                imageLoader = loader,
                modifier = Modifier.heightIn(max = 64.dp).widthIn(max = 320.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
            )
        } else {
            Text(title.name, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 36.sp)
        }
        Spacer(Modifier.height(14.dp))
        val service = when (action) {
            is Watch.Open -> action.label
            is Watch.Install -> action.label
            is Watch.Search -> null
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val parts = listOfNotNull(
                if (title.type == "movie") "Movie" else "Series",
                title.genre.ifBlank { null },
                title.year.ifBlank { null },
                title.length.ifBlank { null },
            )
            if (service != null) {
                Text(service, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Dot()
            }
            parts.forEachIndexed { i, p ->
                if (i > 0) Dot()
                Text(p, fontSize = 11.sp, color = Palette.Dim, fontWeight = FontWeight.Medium)
            }
        }
        if (title.overview.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                title.overview,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(16.dp))
        WatchButton(action, buttonModifier, onAction)
    }
}

@Composable
private fun Dot() {
    Box(Modifier.padding(horizontal = 7.dp).size(3.dp).background(Palette.Faint, CircleShape))
}

@Composable
private fun WatchButton(action: Watch, modifier: Modifier, onClick: () -> Unit) {
    val (icon, label) = when (action) {
        is Watch.Open -> Icons.Play to "Watch on ${action.label}"
        is Watch.Install -> Icons.Download to "Get ${action.label}"
        is Watch.Search -> Icons.Search to "Find where to watch"
    }
    Surface(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.16f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B0B0B),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Row(
            Modifier.fillMaxHeight().padding(start = 16.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun WatchNextRow(items: List<WatchNextItem>, profile: DeviceProfile, loader: ImageLoader) {
    val ctx = LocalContext.current
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(items) { _, item ->
            val shape = RoundedCornerShape(10.dp)
            Surface(
                onClick = {
                    val ok = item.intent?.let { Apps.start(ctx, it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } ?: false
                    if (!ok) Apps.launch(ctx, item.pkg)
                },
                modifier = Modifier.size(width = 200.dp, height = 112.dp),
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = profile.focusScale),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1C22), focusedContainerColor = Color(0xFF1A1C22)),
            ) {
                AsyncImage(item.image, item.title, loader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f))))
                Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Text(item.title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (item.progress > 0f) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                            Box(Modifier.fillMaxWidth(item.progress).height(3.dp).background(Color.White, RoundedCornerShape(2.dp)))
                        }
                    }
                }
            }
        }
    }
}
