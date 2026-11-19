package app.foyer.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.foyer.launcher.Screen
import app.foyer.launcher.data.Title
import app.foyer.launcher.data.TmdbImages
import app.foyer.launcher.data.TvApp
import app.foyer.launcher.perf.DeviceProfile
import coil3.ImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.util.Date

private val Pill = RoundedCornerShape(50)

val AllTabs = listOf(Screen.HOME, Screen.MOVIES, Screen.SHOWS, Screen.APPS, Screen.LIBRARY)
/** Without an artwork key there is nothing to browse, so only these show. */
val AppsOnlyTabs = listOf(Screen.HOME, Screen.APPS, Screen.LIBRARY)

private fun tabLabel(s: Screen) = when (s) {
    Screen.HOME -> "Home"; Screen.MOVIES -> "Movies"; Screen.SHOWS -> "Shows"
    Screen.APPS -> "Apps"; Screen.LIBRARY -> "Library"; Screen.SETTINGS -> "Settings"
}

@Composable
fun GlassBox(modifier: Modifier = Modifier, shape: Shape = Pill, content: @Composable () -> Unit) {
    Box(
        modifier
            .clip(shape)
            .background(Palette.Glass)
            .border(1.dp, Palette.Stroke, shape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Focusable pill used for tabs and setting choices. */
@Composable
fun PillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 26.dp,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = ClickableSurfaceDefaults.shape(Pill),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White else Color.Transparent,
            contentColor = if (selected) Color(0xFF111111) else Palette.Dim,
            focusedContainerColor = if (selected) Color.White else Palette.GlassFocused,
            focusedContentColor = if (selected) Color(0xFF111111) else Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        }
    }
}

@Composable
fun RoundIconButton(icon: ImageVector, label: String, onClick: () -> Unit, size: Dp = 28.dp) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111111),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun Clock() {
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - now % 60_000 + 200)
            now = System.currentTimeMillis()
        }
    }
    Text(DateFormat.getTimeFormat(ctx).format(Date(now)), fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun TopBar(current: Screen, onNavigate: (Screen) -> Unit, onSearch: () -> Unit, tabs: List<Screen> = AllTabs) {
    Row(
        Modifier.fillMaxWidth().padding(start = 44.dp, end = 44.dp, top = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassBox(Modifier.height(34.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                tabs.forEach { tab -> PillButton(tabLabel(tab), current == tab, { onNavigate(tab) }) }
            }
        }
        Spacer(Modifier.weight(1f))
        GlassBox(Modifier.height(34.dp)) {
            Row(
                Modifier.padding(start = 4.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                RoundIconButton(Icons.Search, "Search", onSearch)
                RoundIconButton(Icons.Settings, "Settings", { onNavigate(Screen.SETTINGS) })
                Box(Modifier.padding(start = 6.dp).width(1.dp).height(14.dp).background(Palette.Stroke))
                Spacer(Modifier.width(6.dp))
                Clock()
            }
        }
    }
}

@Composable
fun RowTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 48.dp, bottom = 10.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White.copy(alpha = 0.92f),
    )
}

@Composable
private fun focusBorder(shape: Shape) = ClickableSurfaceDefaults.border(
    focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
)

@Composable
fun AppTile(app: TvApp, profile: DeviceProfile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 160.dp, height = 90.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1C22), focusedContainerColor = Color(0xFF24272F)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = profile.focusScale),
        border = focusBorder(shape),
    ) {
        if (app.isBanner) {
            Image(app.art, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Image(app.art, null, Modifier.size(36.dp))
                Spacer(Modifier.height(6.dp))
                Text(app.label, fontSize = 10.sp, color = Palette.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
            }
        }
    }
}

@Composable
fun TitleCard(
    title: Title,
    profile: DeviceProfile,
    loader: ImageLoader,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(width = 200.dp, height = 112.dp)
            .onFocusGained(onFocus),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color(0xFF1A1C22), focusedContainerColor = Color(0xFF1A1C22)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = profile.focusScale),
        border = focusBorder(shape),
    ) {
        AsyncImage(
            model = TmdbImages.url(title.backdrop, profile.cardImageSize),
            contentDescription = title.name,
            imageLoader = loader,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f)),
            ),
        )
        if (title.logo != null) {
            AsyncImage(
                model = TmdbImages.url(title.logo, "w185"),
                contentDescription = null,
                imageLoader = loader,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).width(96.dp).height(30.dp),
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
            )
        } else {
            Text(
                title.name,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
