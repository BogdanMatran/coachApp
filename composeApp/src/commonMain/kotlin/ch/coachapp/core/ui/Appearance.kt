package ch.coachapp.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The theme picker. Choosing a mode writes it through immediately — there is nothing to save — and
 * the whole app tweens to the new palette.
 */
@Composable
fun AppearanceCard(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    val colors = LocalAppColors.current
    val systemDark = isSystemInDarkTheme()
    InfoCard(modifier) {
        Text(
            when (controller.mode) {
                ThemeMode.Light -> "Light appearance stays on, day and night."
                ThemeMode.Dark -> "Dark appearance stays on, day and night."
                ThemeMode.System -> "Appearance follows your device, and changes with it."
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        SegmentedControl(
            options = ThemeMode.entries,
            selected = controller.mode,
            onSelect = controller::select,
            label = ThemeMode::label,
            icon = ThemeMode::icon,
            // Sun yellow, moon purple; System takes whichever the device is showing right now.
            selectedIconColor = { mode ->
                val dark = when (mode) {
                    ThemeMode.Light -> false
                    ThemeMode.Dark -> true
                    ThemeMode.System -> systemDark
                }
                if (dark) colors.purple else colors.yellow
            },
        )
    }
}

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.Light -> SunIcon
        ThemeMode.Dark -> MoonIcon
        ThemeMode.System -> HalfDiscIcon
    }

// ------------------------------------------------------------------------- the glyphs --
// material-icons-core carries no sun or moon, so they are drawn on the same 24dp grid as the
// dumbbell placeholder. Icon tints them, so the colour here is only a placeholder.

private fun icon(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

private val SunIcon = icon("Sun") {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 7.4f)
        arcToRelative(4.6f, 4.6f, 0f, true, true, 0f, 9.2f)
        arcToRelative(4.6f, 4.6f, 0f, true, true, 0f, -9.2f)
        close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round) {
        moveTo(12f, 1.7f); lineTo(12f, 3.7f)
        moveTo(12f, 20.3f); lineTo(12f, 22.3f)
        moveTo(1.7f, 12f); lineTo(3.7f, 12f)
        moveTo(20.3f, 12f); lineTo(22.3f, 12f)
        moveTo(4.7f, 4.7f); lineTo(6.1f, 6.1f)
        moveTo(17.9f, 17.9f); lineTo(19.3f, 19.3f)
        moveTo(19.3f, 4.7f); lineTo(17.9f, 6.1f)
        moveTo(6.1f, 17.9f); lineTo(4.7f, 19.3f)
    }
}

private val MoonIcon = icon("Moon") {
    path(fill = SolidColor(Color.Black)) {
        moveTo(21f, 13.1f)
        arcToRelative(9f, 9f, 0f, true, true, -10.1f, -10.1f)
        arcToRelative(7f, 7f, 0f, false, false, 10.1f, 10.1f)
        close()
    }
}

private val HalfDiscIcon = icon("HalfDisc") {
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, false, false, 0f, 18f)
        close()
    }
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f) {
        moveTo(12f, 3f)
        arcToRelative(9f, 9f, 0f, true, true, 0f, 18f)
        arcToRelative(9f, 9f, 0f, true, true, 0f, -18f)
        close()
    }
}
