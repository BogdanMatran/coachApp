package ch.coachapp.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.coachapp.core.KeyValueStore
import ch.coachapp.core.rememberKeyValueStore
import androidx.compose.ui.unit.sp

/**
 * The palette is a four-step surface ramp plus four label alphas, in the style of the iOS system
 * colours. Elevation is expressed by moving up the ramp, never by a shadow: cards are flat.
 *
 * Everything here is a value rather than a top-level constant so a coach-specific palette can be
 * built at runtime later (see docs/PLAN.md, phase 3) without reworking the theme.
 */
@Immutable
data class AppColors(
    /** Page ground. */
    val bg: Color,
    /** Elevated backdrop: bars and sheets. */
    val bgEl: Color,
    /** Card and grouped-list fill. */
    val surface: Color,
    /** Pressed state, nested fill, text fields. */
    val surface2: Color,
    /** Controls: segmented track, slider track. */
    val surface3: Color,
    /** The raised thumb that sits on a [surface3] control track. */
    val thumb: Color,
    val label: Color,
    val label2: Color,
    val label3: Color,
    val label4: Color,
    /** Hairline separator; draw it [HairlineWidth] thick. */
    val sep: Color,
    /** Accent (Royal Iris in light mode, Butter Yellow in dark), and the label colour on top of it. */
    val acc: Color,
    /** Accent in its pressed state. */
    val acc2: Color,
    val onAcc: Color,
    /** Accent at 10% (light) or 16% (dark): tinted buttons, tags and chips. */
    val accSoft: Color,
    /** Accent at 38%: the inset outline on a "current" card. */
    val accLine: Color,
    /** The accent as text or a glyph on a ground; clears 4.5:1 on every surface and on [accSoft]. */
    val accText: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    /** [red] as text or a glyph, darkened in light mode for 4.5:1 as [accText] is. */
    val redText: Color,
    /** The label colour on a [red] fill. */
    val onRed: Color,
    val orange: Color,
    val yellow: Color,
    val teal: Color,
    val purple: Color,
    val grey: Color,
) {
    /**
     * The five heat steps shared by the activity heatmap and any other density scale. Unlike
     * [accSoft] these are opaque blends towards [surface2], so they stay legible on any ground.
     */
    val heat: List<Color> get() = listOf(
        surface2,
        lerp(surface2, acc, 0.30f),
        lerp(surface2, acc, 0.55f),
        lerp(surface2, acc, 0.78f),
        acc,
    )
}

/** Separators are sub-pixel, so a list reads as one object rather than a stack of boxes. */
val HairlineWidth = 0.5.dp

/**
 * Blends two palettes. Switching theme animates this one fraction rather than cross-fading a
 * snapshot of the screen, so every surface, label and accent moves together and the app stays
 * fully interactive while it happens. `heat` is derived, so it follows for free.
 */
fun lerp(start: AppColors, stop: AppColors, fraction: Float): AppColors = AppColors(
    bg = lerp(start.bg, stop.bg, fraction),
    bgEl = lerp(start.bgEl, stop.bgEl, fraction),
    surface = lerp(start.surface, stop.surface, fraction),
    surface2 = lerp(start.surface2, stop.surface2, fraction),
    surface3 = lerp(start.surface3, stop.surface3, fraction),
    thumb = lerp(start.thumb, stop.thumb, fraction),
    label = lerp(start.label, stop.label, fraction),
    label2 = lerp(start.label2, stop.label2, fraction),
    label3 = lerp(start.label3, stop.label3, fraction),
    label4 = lerp(start.label4, stop.label4, fraction),
    sep = lerp(start.sep, stop.sep, fraction),
    acc = lerp(start.acc, stop.acc, fraction),
    acc2 = lerp(start.acc2, stop.acc2, fraction),
    onAcc = lerp(start.onAcc, stop.onAcc, fraction),
    accSoft = lerp(start.accSoft, stop.accSoft, fraction),
    accLine = lerp(start.accLine, stop.accLine, fraction),
    accText = lerp(start.accText, stop.accText, fraction),
    blue = lerp(start.blue, stop.blue, fraction),
    green = lerp(start.green, stop.green, fraction),
    red = lerp(start.red, stop.red, fraction),
    redText = lerp(start.redText, stop.redText, fraction),
    onRed = lerp(start.onRed, stop.onRed, fraction),
    orange = lerp(start.orange, stop.orange, fraction),
    yellow = lerp(start.yellow, stop.yellow, fraction),
    teal = lerp(start.teal, stop.teal, fraction),
    purple = lerp(start.purple, stop.purple, fraction),
    grey = lerp(start.grey, stop.grey, fraction),
)

private fun appColors(
    bg: Color,
    bgEl: Color,
    surface: Color,
    surface2: Color,
    surface3: Color,
    thumb: Color,
    label: Color,
    labelTint: Color,
    label2Alpha: Float,
    label3Alpha: Float,
    label4Alpha: Float,
    sep: Color,
    acc: Color,
    acc2: Color,
    onAcc: Color,
    accSoftAlpha: Float,
    blue: Color,
    green: Color,
    red: Color,
    accText: Color,
    redText: Color,
    onRed: Color,
    orange: Color,
    yellow: Color,
    teal: Color,
    purple: Color,
) = AppColors(
    bg = bg,
    bgEl = bgEl,
    surface = surface,
    surface2 = surface2,
    surface3 = surface3,
    thumb = thumb,
    label = label,
    label2 = labelTint.copy(alpha = label2Alpha),
    label3 = labelTint.copy(alpha = label3Alpha),
    label4 = labelTint.copy(alpha = label4Alpha),
    sep = sep,
    acc = acc,
    acc2 = acc2,
    onAcc = onAcc,
    accSoft = acc.copy(alpha = accSoftAlpha),
    accLine = acc.copy(alpha = 0.38f),
    accText = accText,
    blue = blue,
    green = green,
    red = red,
    redText = redText,
    onRed = onRed,
    orange = orange,
    yellow = yellow,
    teal = teal,
    purple = purple,
    grey = Color(0xFF8E8E93),
)

/** The brand pair. Each theme leads with one and sets the other on top of it. */
val ButterYellow = Color(0xFFFFF275)
val RoyalIris = Color(0xFF3A0CA3)

val DarkAppColors = appColors(
    bg = Color(0xFF000000),
    bgEl = Color(0xFF0E0E10),
    surface = Color(0xFF1C1C1E),
    surface2 = Color(0xFF2C2C2E),
    surface3 = Color(0xFF3A3A3C),
    thumb = Color(0xFF636366),
    label = Color(0xFFFFFFFF),
    labelTint = Color(0xFFEBEBF5),
    label2Alpha = 0.60f,
    label3Alpha = 0.32f,
    label4Alpha = 0.18f,
    sep = Color(0xFF545458).copy(alpha = 0.60f),
    // Dark mode leads with Butter Yellow and sets Royal Iris on it: yellow is 18:1 on black, while
    // the iris would be 1.4:1 on a dark card and disappear.
    acc = ButterYellow,
    acc2 = Color(0xFFE6D85E),
    onAcc = RoyalIris,
    accSoftAlpha = 0.16f,
    blue = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    accText = ButterYellow,
    redText = Color(0xFFFF6961),
    onRed = Color.Black,
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    teal = Color(0xFF40C8E0),
    purple = Color(0xFFBF5AF2),
)

val LightAppColors = appColors(
    bg = Color(0xFFF2F2F7),
    bgEl = Color(0xFFF7F7FA),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFECECEF),
    surface3 = Color(0xFFE3E3E8),
    thumb = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    labelTint = Color(0xFF3C3C43),
    // 0.75 rather than the iOS 0.60: secondary text carries real content here (subtitles, values,
    // section headers), and 0.60 measures 3.4:1 on white.
    label2Alpha = 0.75f,
    label3Alpha = 0.30f,
    label4Alpha = 0.16f,
    sep = Color(0xFF3C3C43).copy(alpha = 0.29f),
    // Light mode leads with Royal Iris and sets Butter Yellow on it (10.4:1); the iris is 11.9:1 on
    // white, so it also works as text. Yellow on white is 1.15:1, so it never appears as text here.
    acc = RoyalIris,
    acc2 = Color(0xFF2B0880),
    onAcc = ButterYellow,
    accSoftAlpha = 0.10f,
    blue = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    accText = RoyalIris,
    redText = Color(0xFFC4000F),
    onRed = Color.White,
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFFFCC00),
    teal = Color(0xFF30B0C7),
    purple = Color(0xFFAF52DE),
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

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

private fun AppColors.toChartColors() = ChartColors(
    workouts = acc,
    meals = teal,
    // Not blue: next to the Royal Iris accent the two read as one series.
    supplements = orange,
    protein = blue,
    carbs = yellow,
    fat = red,
    track = surface3,
    heat = heat,
)

val LocalChartColors = staticCompositionLocalOf { DarkAppColors.toChartColors() }

/**
 * Material reads cards from `surfaceContainerHighest` and fields from `surfaceVariant`, so the
 * ramp maps onto the container slots in order. Everything Material has no slot for — the two
 * faintest label steps, the hairline, the accent tints, the status hues — lives in [AppColors].
 */
private fun AppColors.toColorScheme(dark: Boolean): ColorScheme =
    (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = acc,
        onPrimary = onAcc,
        primaryContainer = accSoft,
        onPrimaryContainer = accText,
        inversePrimary = acc,
        secondary = acc,
        onSecondary = onAcc,
        secondaryContainer = accSoft,
        onSecondaryContainer = accText,
        tertiary = blue,
        onTertiary = onAcc,
        tertiaryContainer = blue.copy(alpha = 0.16f),
        onTertiaryContainer = blue,
        background = bg,
        onBackground = label,
        surface = bg,
        onSurface = label,
        surfaceVariant = surface2,
        onSurfaceVariant = label2,
        // No tonal elevation: height is the surface ramp, not a tint or a shadow.
        surfaceTint = Color.Transparent,
        inverseSurface = label,
        inverseOnSurface = bg,
        error = redText,
        onError = onRed,
        errorContainer = red.copy(alpha = 0.15f),
        onErrorContainer = red,
        outline = label3,
        outlineVariant = sep,
        scrim = Color.Black.copy(alpha = 0.4f),
        surfaceBright = surface2,
        surfaceDim = bg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = bgEl,
        surfaceContainer = surface,
        surfaceContainerHigh = surface2,
        surfaceContainerHighest = surface,
    )

/**
 * One scale, mostly regular weight: size carries the hierarchy and weight carries meaning (600 for
 * a title, 400 for its content). Tracking is negative and tightens as the size grows. Every style
 * is tabular so a number never jitters as it changes.
 */
private const val TabularNums = "tnum"

private fun appType(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFeatureSettings = TabularNums,
)

private val AppTypography = Typography(
    displayLarge = appType(34, 36, FontWeight.Bold, -0.95f),
    displayMedium = appType(34, 36, FontWeight.Bold, -0.95f),
    displaySmall = appType(34, 36, FontWeight.Bold, -0.95f),
    headlineLarge = appType(34, 36, FontWeight.Bold, -0.95f),
    headlineMedium = appType(28, 31, FontWeight.Bold, -0.67f),
    headlineSmall = appType(22, 26, FontWeight.Bold, -0.46f),
    titleLarge = appType(22, 26, FontWeight.Bold, -0.46f),
    titleMedium = appType(17, 22, FontWeight.SemiBold, -0.20f),
    titleSmall = appType(15, 20, FontWeight.SemiBold, -0.09f),
    bodyLarge = appType(17, 22, FontWeight.Normal, -0.20f),
    bodyMedium = appType(16, 21, FontWeight.Normal, -0.14f),
    bodySmall = appType(15, 20, FontWeight.Normal, -0.09f),
    labelLarge = appType(15, 20, FontWeight.Medium, -0.09f),
    labelMedium = appType(13, 18, FontWeight.Normal, -0.01f),
    labelSmall = appType(12, 16, FontWeight.Normal, 0f),
)

/** Card corners are [Shapes.medium]; the ramp runs 8 / 12 / 14 / 16 / 22. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/** What the user picked in Settings. [System] follows the device. */
enum class ThemeMode(val label: String) {
    Light("Light"),
    Dark("Dark"),
    System("System"),
    ;

    companion object {
        fun from(stored: String?): ThemeMode = entries.firstOrNull { it.name == stored } ?: System
    }
}

/** Holds the chosen theme and writes it straight through, so it survives a restart. */
@Stable
class ThemeController(private val store: KeyValueStore) {

    var mode: ThemeMode by mutableStateOf(ThemeMode.from(store.getString(StoreKey)))
        private set

    fun select(mode: ThemeMode) {
        this.mode = mode
        store.putString(StoreKey, mode.name)
    }

    private companion object {
        const val StoreKey = "theme_mode"
    }
}

val LocalThemeController = staticCompositionLocalOf<ThemeController> {
    error("No ThemeController: wrap the content in CoachTheme")
}

/**
 * Brings what Compose does not draw in line with the chosen mode: the system bars on Android, and
 * on iOS the SwiftUI shell (tab bar, navigation bars, status bar). [dark] is the resolved value.
 */
@Composable
internal expect fun PlatformChromeAppearance(mode: ThemeMode, dark: Boolean)

/** Long enough to read as a deliberate transition, short enough not to be in the way. */
private const val ThemeAnimationMillis = 450

/**
 * One controller per process. iOS hosts every screen in its own Compose view controller, each with
 * its own [CoachTheme]; if each remembered its own controller, a choice made in Settings would only
 * reach the Settings screen while every other screen kept the old mode.
 */
private var sharedThemeController: ThemeController? = null

@Composable
private fun rememberThemeController(): ThemeController {
    val store = rememberKeyValueStore()
    return remember { sharedThemeController ?: ThemeController(store).also { sharedThemeController = it } }
}

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val controller = rememberThemeController()
    val dark = when (controller.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Settles on the target without animating on the first composition, so a persisted dark theme
    // does not fade in from light at launch.
    val fraction by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(ThemeAnimationMillis, easing = FastOutSlowInEasing),
        label = "themeMode",
    )
    val colors = lerp(LightAppColors, DarkAppColors, fraction)
    PlatformChromeAppearance(controller.mode, dark)
    CompositionLocalProvider(
        LocalThemeController provides controller,
        LocalAppColors provides colors,
        LocalChartColors provides colors.toChartColors(),
        // Presses acknowledge with a scale, not a ripple: see Modifier.pressScale in Press.kt.
        LocalIndication provides NoIndication,
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(dark),
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
