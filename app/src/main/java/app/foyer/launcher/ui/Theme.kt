package app.foyer.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import app.foyer.launcher.R
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object Palette {
    val Ink = Color(0xFF08090C)
    val Text = Color.White
    val Dim = Color.White.copy(alpha = 0.72f)
    val Faint = Color.White.copy(alpha = 0.5f)
    /** Pre-tinted fill used instead of live blur: looks the same at TV distance, costs nothing. */
    val Glass = Color(0xB8151619)
    val GlassFocused = Color(0x40FFFFFF)
    val Stroke = Color(0x26FFFFFF)
    val Accent = Color(0xFFF2C27A)
    val Good = Color(0xFF8FD6A0)
}

object Icons {
    private fun stroke(name: String, vararg paths: String): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        paths.forEach {
            b.addPath(
                pathData = PathParser().parsePathString(it).toNodes(),
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
        return b.build()
    }

    val Search = stroke("search", "M11 4a7 7 0 1 1 0 14a7 7 0 0 1 0-14z", "M20 20l-4-4")
    val Settings = stroke(
        "settings",
        "M4 7h9", "M17 7h3", "M15 5v4",
        "M4 17h3", "M11 17h9", "M9 15v4",
    )
    val Play: ImageVector = ImageVector.Builder("play", 24.dp, 24.dp, 24f, 24f).addPath(
        pathData = PathParser().parsePathString("M7 4.5v15l12.5-7.5z").toNodes(),
        fill = SolidColor(Color.White),
    ).build()
    val Plus = stroke("plus", "M12 5v14", "M5 12h14")
    val Check = stroke("check", "M5 12.5l4.5 4.5L19 7")
    val Download = stroke("download", "M12 4v11", "M7 10l5 5 5-5", "M5 20h14")
}

/** Inter, bundled (Latin subset) so every TV renders the same type regardless of its system font. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

@Composable
fun FroyerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(background = Palette.Ink, surface = Palette.Ink)) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
            // Material's TV text styles add wide letter spacing meant for Roboto; Inter
            // is designed to sit tight.
            LocalTextStyle provides TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        ) { content() }
    }
}
