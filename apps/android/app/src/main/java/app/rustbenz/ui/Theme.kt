package app.rustbenz.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** "Dark luxury" palette: layered near-blacks, warm champagne accent, muted status hues. */
object Lux {
    val Background = Color(0xFF0B0B0D)
    val Surface = Color(0xFF141417)
    val SurfaceHigh = Color(0xFF1C1C21)
    val SurfaceHighest = Color(0xFF26262C)
    val Hairline = Color(0xFF2C2C33)

    val Text = Color(0xFFF4F1EA)
    val TextMuted = Color(0xFFA39F96)
    val TextFaint = Color(0xFF6E6B65)

    val Champagne = Color(0xFFC9A96E)
    val ChampagneBright = Color(0xFFE9D4A4)
    val ChampagneDeep = Color(0xFF8C7446)

    val Positive = Color(0xFF86BE8F)
    val Caution = Color(0xFFE2AE62)
    val Critical = Color(0xFFE5766B)
    val Cool = Color(0xFF86AEE6)
    val Warm = Color(0xFFE79A6B)

    val AccentBrush = Brush.linearGradient(listOf(ChampagneDeep, Champagne, ChampagneBright))
}

private val Numeric = TextStyle(fontFeatureSettings = "tnum, lnum")

/** Typography: light, large numerals; spaced small labels; calm body text. */
object LuxType {
    val Hero = Numeric.copy(fontSize = 88.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.04).em, lineHeight = 88.sp)
    val Display = Numeric.copy(fontSize = 48.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.03).em, lineHeight = 52.sp)
    val Metric = Numeric.copy(fontSize = 28.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.01).em)
    val MetricSmall = Numeric.copy(fontSize = 20.sp, fontWeight = FontWeight.Normal)
    val Title = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.em)
    val Heading = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val Body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 22.sp)
    val BodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp)
    /** Use with uppercase text. */
    val Overline = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    val Unit = Numeric.copy(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.04.em)
}

@Composable
fun RustBenzTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Lux.Champagne,
        onPrimary = Lux.Background,
        primaryContainer = Lux.ChampagneDeep,
        onPrimaryContainer = Lux.Text,
        secondary = Lux.TextMuted,
        background = Lux.Background,
        onBackground = Lux.Text,
        surface = Lux.Background,
        onSurface = Lux.Text,
        surfaceVariant = Lux.SurfaceHigh,
        onSurfaceVariant = Lux.TextMuted,
        surfaceContainer = Lux.Surface,
        surfaceContainerHigh = Lux.SurfaceHigh,
        surfaceContainerHighest = Lux.SurfaceHighest,
        outline = Lux.Hairline,
        outlineVariant = Lux.Hairline,
        error = Lux.Critical,
        errorContainer = Color(0xFF3A1F1C),
        onErrorContainer = Color(0xFFF6D6D1),
    )
    val base = Typography()
    val typography = base.copy(
        bodyLarge = LuxType.Body,
        bodyMedium = LuxType.Body,
        bodySmall = LuxType.BodySmall,
        titleLarge = LuxType.Title,
        titleMedium = LuxType.Heading,
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Default, letterSpacing = 0.02.em),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
