package ch.coachapp.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design system palette. Each hue has 400 (strong) to 100 (tint) steps; neutrals cover text,
 * borders and surfaces. Dark-mode neutrals are derived from "Headings Black".
 */
object Palette {
    val Magenta400 = Color(0xFF8A35F9)
    val Magenta300 = Color(0xFFC197F7)
    val Magenta200 = Color(0xFFDCC9F9)
    val Magenta100 = Color(0xFFECE5FA)

    val Blue400 = Color(0xFF3A5CF6)
    val Blue300 = Color(0xFF9AABF6)
    val Blue200 = Color(0xFFCCD3FB)
    val Blue100 = Color(0xFFE7EBFA)

    val Green400 = Color(0xFF5FCC3C)
    val Green300 = Color(0xFF9BE38C)
    val Green200 = Color(0xFFC7EDC5)
    val Green100 = Color(0xFFE4F5E5)

    val Yellow400 = Color(0xFFFBEC62)
    val Yellow300 = Color(0xFFFCF5AC)
    val Yellow200 = Color(0xFFFDF9D3)
    val Yellow100 = Color(0xFFFFFDEE)

    val Red400 = Color(0xFFEE7A8C)
    val Red300 = Color(0xFFF2B9C4)
    val Red200 = Color(0xFFF6D7DF)
    val Red100 = Color(0xFFFCECEE)

    val HeadingsBlack = Color(0xFF0D0A2C)
    val TextGray = Color(0xFF615E83)
    val Neutral600 = Color(0xFF807E9A)
    val Neutral500 = Color(0xFFE5E5EF)
    val Neutral400 = Color(0xFFF2F1FF)
    val Neutral300 = Color(0xFFF7F7FB)
    val Neutral200 = Color(0xFFF8F8FF)

    // Dark-mode surfaces, stepping up from Headings Black.
    val Night = HeadingsBlack
    val NightCard = Color(0xFF1A1740)
    val NightRaised = Color(0xFF26225A)
    val NightBorder = Color(0xFF3A3670)
    val NightText = Color(0xFFB7B5D6)
}

/** Colors for charts and data visualisation; series hues stay the same in both themes. */
@Immutable
data class ChartColors(
    val workouts: Color,
    val meals: Color,
    val supplements: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    /** Unfilled part of a ring. */
    val track: Color,
    /** Activity heatmap, from no activity (0) to most active (4). */
    val heat: List<Color>,
)

private val LightChartColors = ChartColors(
    workouts = Palette.Magenta400,
    meals = Palette.Green400,
    supplements = Palette.Blue400,
    protein = Palette.Blue400,
    carbs = Palette.Yellow400,
    fat = Palette.Red400,
    track = Palette.Neutral400,
    heat = listOf(Palette.Neutral500, Palette.Magenta200, Palette.Magenta300, Color(0xFFA766F8), Palette.Magenta400),
)

private val DarkChartColors = ChartColors(
    workouts = Palette.Magenta300,
    meals = Palette.Green400,
    supplements = Palette.Blue300,
    protein = Palette.Blue300,
    carbs = Palette.Yellow400,
    fat = Palette.Red400,
    track = Palette.NightRaised,
    heat = listOf(Palette.NightRaised, Color(0xFF3B2A78), Color(0xFF5B34A8), Color(0xFF7E3FE0), Palette.Magenta300),
)

val LocalChartColors = staticCompositionLocalOf { LightChartColors }

private val LightColors = lightColorScheme(
    primary = Palette.Magenta400,
    onPrimary = Color.White,
    primaryContainer = Palette.Magenta100,
    onPrimaryContainer = Color(0xFF3A0E78),
    // Material uses secondary for selected nav labels, chips and indicators: keep it in the violet family.
    secondary = Color(0xFF6C2BD0),
    onSecondary = Color.White,
    secondaryContainer = Palette.Magenta100,
    onSecondaryContainer = Color(0xFF3A0E78),
    tertiary = Palette.Blue400,
    onTertiary = Color.White,
    tertiaryContainer = Palette.Blue100,
    onTertiaryContainer = Color(0xFF0F1F6B),
    background = Palette.Neutral200,
    onBackground = Palette.HeadingsBlack,
    surface = Palette.Neutral200,
    onSurface = Palette.HeadingsBlack,
    surfaceVariant = Palette.Neutral400,
    onSurfaceVariant = Palette.TextGray,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Palette.Neutral200,
    surfaceContainerHigh = Color.White,
    // Cards use surfaceContainerHighest: white cards on the near-white page.
    surfaceContainerHighest = Color.White,
    outline = Palette.Neutral600,
    outlineVariant = Palette.Neutral500,
    error = Color(0xFFD93A4E),
    onError = Color.White,
    errorContainer = Palette.Red100,
    onErrorContainer = Color(0xFF7A1222),
)

private val DarkColors = darkColorScheme(
    primary = Palette.Magenta300,
    onPrimary = Palette.HeadingsBlack,
    primaryContainer = Color(0xFF4A2496),
    onPrimaryContainer = Palette.Magenta100,
    secondary = Palette.Magenta300,
    onSecondary = Palette.HeadingsBlack,
    secondaryContainer = Color(0xFF3B2A78),
    onSecondaryContainer = Palette.Magenta100,
    tertiary = Palette.Blue300,
    onTertiary = Palette.HeadingsBlack,
    tertiaryContainer = Color(0xFF26337A),
    onTertiaryContainer = Palette.Blue100,
    background = Palette.Night,
    onBackground = Palette.Neutral200,
    surface = Palette.Night,
    onSurface = Palette.Neutral200,
    surfaceVariant = Palette.NightRaised,
    onSurfaceVariant = Palette.NightText,
    surfaceContainerLowest = Palette.Night,
    surfaceContainerLow = Color(0xFF141136),
    surfaceContainer = Palette.NightCard,
    surfaceContainerHigh = Color(0xFF201C4C),
    surfaceContainerHighest = Palette.NightCard,
    outline = Palette.NightBorder,
    outlineVariant = Color(0xFF2B2760),
    error = Palette.Red400,
    onError = Palette.HeadingsBlack,
    errorContainer = Color(0xFF5A1A28),
    onErrorContainer = Palette.Red100,
)

/** Large, soft corners for cards. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun CoachTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalChartColors provides if (darkTheme) DarkChartColors else LightChartColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, shapes = AppShapes, content = content)
    }
}
