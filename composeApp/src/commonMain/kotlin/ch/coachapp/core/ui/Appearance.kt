package ch.coachapp.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The theme picker. Choosing a mode writes it through immediately — there is nothing to save — and
 * the whole app tweens to the new palette. The scene above the control previews the choice, and
 * runs on the same clock and curve as the theme itself, so the two move as one.
 */
@Composable
fun AppearanceCard(modifier: Modifier = Modifier) {
    val controller = LocalThemeController.current
    val colors = LocalAppColors.current
    val dark = when (controller.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val night by animateFloatAsState(
        targetValue = if (dark) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "themePreview",
    )
    InfoCard(modifier) {
        ThemeScene(
            night = night,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small),
        )
        Spacer(Modifier.height(4.dp))
        SegmentedControl(
            options = ThemeMode.entries,
            selected = controller.mode,
            onSelect = controller::select,
            label = ThemeMode::label,
            icon = ThemeMode::icon,
            thumbColor = { mode -> mode.tint(colors) },
            selectedLabelColor = { mode -> mode.tint(colors) },
        )
        Text(
            when (controller.mode) {
                ThemeMode.System -> "Appearance follows your device, and changes with it."
                ThemeMode.Light -> "Light appearance stays on, day and night."
                ThemeMode.Dark -> "Dark appearance stays on, day and night."
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.label2,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The thumb and its label take the mood of the mode: warm for light, cool for dark. */
private fun ThemeMode.tint(colors: AppColors): Color = when (this) {
    ThemeMode.System -> colors.surface2
    ThemeMode.Light -> colors.yellow.copy(alpha = 0.22f)
    ThemeMode.Dark -> colors.purple.copy(alpha = 0.30f)
}

private val ThemeMode.icon: ImageVector
    get() = when (this) {
        ThemeMode.System -> HalfDiscIcon
        ThemeMode.Light -> SunIcon
        ThemeMode.Dark -> MoonIcon
    }

// ------------------------------------------------------------------------- the scene --

private val DaySkyTop = Color(0xFF6FBDF0)
private val DaySkyBottom = Color(0xFFCCEAFA)
private val NightSkyTop = Color(0xFF0D1333)
private val NightSkyBottom = Color(0xFF2B3372)
private val DayHillBack = Color(0xFF86CC72)
private val DayHillFront = Color(0xFF5CA557)
private val NightHillBack = Color(0xFF2B3576)
private val NightHillFront = Color(0xFF1B2254)

/** Fixed so the sky does not reshuffle on every recomposition: x, y and radius, all relative. */
private val Stars = listOf(
    Triple(0.10f, 0.22f, 0.009f), Triple(0.18f, 0.47f, 0.006f), Triple(0.27f, 0.14f, 0.007f),
    Triple(0.38f, 0.35f, 0.010f), Triple(0.46f, 0.11f, 0.006f), Triple(0.57f, 0.30f, 0.008f),
    Triple(0.64f, 0.52f, 0.006f), Triple(0.72f, 0.18f, 0.007f), Triple(0.86f, 0.42f, 0.009f),
    Triple(0.93f, 0.24f, 0.006f), Triple(0.33f, 0.60f, 0.005f), Triple(0.79f, 0.63f, 0.005f),
)

/**
 * A plain landscape that crossfades between day and night: sky, a sun or a moon, two hills and a
 * house whose window lights up after dark. Drawn rather than shipped as an asset, so it recolours
 * with the palette and costs nothing to bundle.
 */
@Composable
private fun ThemeScene(night: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        drawRect(
            Brush.verticalGradient(
                listOf(lerp(DaySkyTop, NightSkyTop, night), lerp(DaySkyBottom, NightSkyBottom, night)),
            ),
        )

        Stars.forEach { (x, y, r) ->
            drawCircle(Color.White.copy(alpha = night * 0.85f), r * h, Offset(x * w, y * h))
        }

        val bodyCentre = Offset(w * 0.76f, h * 0.30f)
        val bodyRadius = h * 0.15f
        if (night < 1f) drawSun(bodyCentre, bodyRadius, 1f - night)
        if (night > 0f) drawMoon(bodyCentre, bodyRadius, night)

        drawPath(hill(w, h, 0.28f, 0.62f), lerp(DayHillBack, NightHillBack, night))
        drawPath(hill(w, h, 0.72f, 0.78f), lerp(DayHillFront, NightHillFront, night))
        drawHouse(w, h, night)
    }
}

/** A hill whose crest sits at [peak] across and [top] down, both relative. */
private fun hill(w: Float, h: Float, peak: Float, top: Float): Path = Path().apply {
    moveTo(0f, h)
    lineTo(0f, h * (top + 0.12f))
    quadraticTo(w * peak, h * top, w, h * (top + 0.08f))
    lineTo(w, h)
    close()
}

private fun DrawScope.drawSun(centre: Offset, radius: Float, alpha: Float) {
    val sun = Color(0xFFFFD233).copy(alpha = alpha)
    repeat(8) { i ->
        val angle = i * (PiTimesTwo / 8)
        val from = Offset(centre.x + cos(angle) * radius * 1.45f, centre.y + sin(angle) * radius * 1.45f)
        val to = Offset(centre.x + cos(angle) * radius * 1.95f, centre.y + sin(angle) * radius * 1.95f)
        drawLine(sun, from, to, strokeWidth = radius * 0.17f, cap = StrokeCap.Round)
    }
    drawCircle(sun, radius, centre)
}

/** The crescent is the disc minus a second disc offset up and to the left. */
private fun DrawScope.drawMoon(centre: Offset, radius: Float, alpha: Float) {
    val disc = Path().apply { addOval(Rect(centre, radius)) }
    val bite = Path().apply {
        addOval(Rect(Offset(centre.x - radius * 0.46f, centre.y - radius * 0.30f), radius * 0.92f))
    }
    val crescent = Path().apply { op(disc, bite, PathOperation.Difference) }
    drawPath(crescent, Color(0xFFF3F4FA).copy(alpha = alpha))
}

private fun DrawScope.drawHouse(w: Float, h: Float, night: Float) {
    val wall = lerp(Color(0xFF3C4A3A), Color(0xFF151A3D), night)
    val left = w * 0.14f
    val bodyTop = h * 0.74f
    val bodyW = w * 0.085f
    val bodyH = h * 0.14f
    drawRect(wall, Offset(left, bodyTop), Size(bodyW, bodyH))
    val roof = Path().apply {
        moveTo(left - bodyW * 0.22f, bodyTop)
        lineTo(left + bodyW / 2f, bodyTop - bodyH * 0.55f)
        lineTo(left + bodyW * 1.22f, bodyTop)
        close()
    }
    drawPath(roof, wall)
    // The window only glows once it is dark enough to notice.
    drawRect(
        Color(0xFFFFC94D).copy(alpha = night),
        Offset(left + bodyW * 0.32f, bodyTop + bodyH * 0.3f),
        Size(bodyW * 0.36f, bodyH * 0.34f),
    )
}

private const val PiTimesTwo = 6.2831855f

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
