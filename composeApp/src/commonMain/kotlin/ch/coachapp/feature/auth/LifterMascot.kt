package ch.coachapp.feature.auth

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import ch.coachapp.core.ui.LocalAppColors
import kotlin.math.sqrt

/** What the lifter on the sign-in screen is doing, driven by which field has focus. */
enum class LifterPose {
    /** Bar at the hips, breathing. */
    Idle,

    /** Watching the email being typed. */
    Watching,

    /** Bar raised across the eyes while the password is typed. */
    Covering,

    /** Pressing the bar overhead while signing in. */
    Lifting,
}

/** Where the bar sits for each pose, on the lift scale used below (0 hips, 1 eyes, 2 overhead). */
private fun LifterPose.lift(): Float = when (this) {
    LifterPose.Idle -> 0f
    LifterPose.Watching -> 0.08f
    LifterPose.Covering -> 1f
    LifterPose.Lifting -> 2f
}

private val Skin = Color(0xFFF2C29B)
/** Warm brown: visible against both the black disc (light mode) and the white one (dark mode). */
private val Hair = Color(0xFF8B5A2B)
private val Ink = Color(0xFF1C1C1E)

/**
 * The sign-in mascot: a lifter drawn on a 200-unit grid, so it scales to any size, on a disc
 * that inverts the page (black in light mode, white in dark) with the brand colour that reads on it. The arms are solved from
 * the bar position every frame, so any point between two poses looks like a real lift.
 *
 * [look] (0..1) moves the pupils along as the email grows; [shakeKey] changing makes the head
 * shake, e.g. on a failed sign-in. Purely decorative: it carries no semantics.
 */
@Composable
fun LifterMascot(pose: LifterPose, look: Float, shakeKey: Any?, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val lift by animateFloatAsState(
        pose.lift(),
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow),
        label = "lift",
    )
    val lookX by animateFloatAsState(if (pose == LifterPose.Watching) look * 2f - 1f else 0f, tween(220), label = "look")
    val idle = rememberInfiniteTransition(label = "idle")
    val breath by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    // Eyes open most of the time, with a quick blink every few seconds.
    val blink by idle.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 3900
                0.1f at 4000 using LinearEasing
                1f at 4200
            },
        ),
        label = "blink",
    )
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey == null) return@LaunchedEffect
        listOf(-7f, 7f, -5f, 5f, -2f, 0f).forEach { shake.animateTo(it, tween(70)) }
    }

    Canvas(modifier) {
        val u = size.minDimension / 200f
        translate(left = (size.width - 200 * u) / 2, top = (size.height - 200 * u) / 2) {
            drawLifter(
                u = u,
                lift = lift,
                lookX = lookX,
                breath = breath,
                eyesOpen = if (lift > 0.75f) 0f else blink,
                strain = (lift - 1f).coerceIn(0f, 1f),
                shake = shake.value,
                // The disc is the page colour inverted: black on a light page, white on a dark one.
                disc = colors.label,
                // Whatever sits straight on the disc takes the page colour, so it always contrasts.
                detail = colors.bg,
                // The accent's partner, because the accent itself is chosen to read on the page,
                // which is exactly what the disc is not (iris on black, yellow on white both vanish).
                shirt = colors.onAcc,
            )
        }
    }
}

private fun DrawScope.drawLifter(
    u: Float,
    lift: Float,
    lookX: Float,
    breath: Float,
    eyesOpen: Float,
    strain: Float,
    shake: Float,
    disc: Color,
    detail: Color,
    shirt: Color,
) {
    fun p(x: Float, y: Float) = Offset(x * u, y * u)
    fun shape(vararg points: Pair<Float, Float>) = Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * u, y * u) else lineTo(x * u, y * u) }
        close()
    }

    // Backdrop disc and floor shadow.
    drawCircle(disc, radius = 92 * u, center = p(100f, 104f))
    drawOval(detail.copy(alpha = 0.18f), topLeft = p(62f, 184f), size = Size(76 * u, 8 * u))

    // Bare legs under shorts: thick, so the lifter stands planted.
    listOf(81f, 102f).forEach { x ->
        drawRoundRect(Skin, topLeft = p(x, 146f), size = Size(17 * u, 40 * u), cornerRadius = CornerRadius(7 * u))
    }
    drawRoundRect(detail, topLeft = p(78f, 138f), size = Size(44 * u, 22 * u), cornerRadius = CornerRadius(8 * u))

    val r = breath * 1.5f
    // Torso and head shake together on a failed attempt.
    rotate(shake, pivot = p(100f, 140f)) {
        // Traps and a V-shaped back (the lats show either side of the tank top).
        drawPath(shape(88f to 78f - r, 112f to 78f - r, 126f to 94f - r, 74f to 94f - r), Skin)
        drawPath(shape(64f to 94f - r, 136f to 94f - r, 120f to 146f, 80f to 146f), Skin)
        // Tank top, tapering to the waist, with its straps over the traps.
        drawPath(shape(76f to 98f - r, 124f to 98f - r, 119f to 146f, 81f to 146f), shirt)
        drawOval(shirt, topLeft = p(76f, 90f - r), size = Size(48 * u, 16 * u))
        drawPath(shape(84f to 94f - r, 91f to 94f - r, 92f to 82f - r, 87f to 82f - r), shirt)
        drawPath(shape(109f to 94f - r, 116f to 94f - r, 113f to 82f - r, 108f to 82f - r), shirt)
        drawArc(
            Ink.copy(alpha = 0.25f), startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = p(86f, 96f - r), size = Size(28 * u, 16 * u), style = Stroke(width = 1.2f * u),
        )

        val head = p(100f, 60f - r)
        drawCircle(Skin, radius = 24 * u, center = head)
        // Upright, spiky hair over a short cap.
        drawPath(
            shape(
                76f to 49f - r, 77f to 38f - r, 82f to 44f - r, 84f to 28f - r, 91f to 38f - r, 96f to 22f - r,
                101f to 36f - r, 107f to 22f - r, 111f to 37f - r, 118f to 28f - r, 119f to 44f - r,
                124f to 38f - r, 124f to 49f - r, 100f to 42f - r,
            ),
            Hair,
        )
        drawArc(Hair, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = p(76f, 36f - r), size = Size(48 * u, 26 * u))

        // Brows, then eyes: pupils follow the email; closed (a line) when the bar comes up or mid-blink.
        listOf(91f, 109f).forEach { x ->
            drawLine(Hair, p(x - 5f, 54f - r), p(x + 4f, 54f - r), strokeWidth = 2 * u, cap = StrokeCap.Round)
            val eye = p(x, 61.5f - r)
            if (eyesOpen < 0.3f) {
                drawLine(Ink, eye + p(-4f, 0f), eye + p(4f, 0f), strokeWidth = 2 * u, cap = StrokeCap.Round)
            } else {
                drawOval(Color.White, topLeft = eye - p(4.5f, 4.5f * eyesOpen), size = Size(9 * u, 9 * u * eyesOpen))
                drawCircle(Ink, radius = 2.4f * u * eyesOpen.coerceAtLeast(0.5f), center = eye + p(lookX * 2f, 0.5f))
            }
        }

        // Mouth: a smile at rest, an effortful "o" under the heaviest part of the press.
        val mouth = p(100f, 72f - r)
        if (strain > 0.4f) {
            drawCircle(Ink, radius = 3 * u, center = mouth)
        } else {
            drawArc(
                Ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
                topLeft = mouth - p(6f, 5f), size = Size(12 * u, 8 * u),
                style = Stroke(width = 2 * u, cap = StrokeCap.Round),
            )
        }
    }

    // The bar: hanging at arm's length at lift 0, across the eyes at 1, overhead at 2.
    val barY = if (lift <= 1f) lerp(154f, 61f - r, lift) else lerp(61f - r, 24f, lift - 1f)
    val shoulders = listOf(p(70f, 98f - r), p(130f, 98f - r))
    val hands = listOf(p(62f, barY), p(138f, barY))
    val centre = p(100f, 98f)

    // Arms first, so the bar and hands sit on top of them.
    shoulders.zip(hands).forEachIndexed { side, (shoulder, hand) ->
        val elbow = elbow(shoulder, hand, upperArm = 30 * u, outward = if (side == 0) -1f else 1f)
        drawLine(Skin, shoulder, elbow, strokeWidth = 14 * u, cap = StrokeCap.Round)
        drawCircle(Skin, radius = 7 * u, center = elbow)
        drawLine(Skin, elbow, hand, strokeWidth = 11 * u, cap = StrokeCap.Round)
        // Biceps: halfway down the upper arm, bulging towards the body.
        val mid = Offset((shoulder.x + elbow.x) / 2, (shoulder.y + elbow.y) / 2)
        val along = elbow - shoulder
        val length = along.getDistance().coerceAtLeast(0.001f)
        var normal = Offset(-along.y / length, along.x / length)
        if (normal.x * (centre.x - mid.x) < 0) normal = -normal
        drawCircle(Skin, radius = 7.5f * u, center = mid + normal * (2.5f * u))
        // Delt cap over the arm root.
        drawCircle(Skin, radius = 9 * u, center = shoulder)
    }

    drawLine(detail, p(28f, barY), p(172f, barY), strokeWidth = 5 * u, cap = StrokeCap.Round)
    listOf(34f, 156f).forEach { x ->
        drawRoundRect(shirt, topLeft = p(x, barY - 18f), size = Size(10 * u, 36 * u), cornerRadius = CornerRadius(3 * u))
    }
    listOf(44f, 150f).forEach { x ->
        drawRoundRect(detail, topLeft = p(x, barY - 10f), size = Size(6 * u, 20 * u), cornerRadius = CornerRadius(2 * u))
    }
    hands.forEach { drawCircle(Skin, radius = 7 * u, center = it) }
}

/**
 * Two-bone arm: the elbow sits where two segments of [upperArm] meet between shoulder and hand,
 * bent away from the body. Past full reach the arm simply straightens.
 */
private fun elbow(shoulder: Offset, hand: Offset, upperArm: Float, outward: Float): Offset {
    val dx = hand.x - shoulder.x
    val dy = hand.y - shoulder.y
    val distance = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
    val mid = Offset((shoulder.x + hand.x) / 2, (shoulder.y + hand.y) / 2)
    val half = (distance / 2).coerceAtMost(upperArm)
    val bend = sqrt(upperArm * upperArm - half * half)
    // Perpendicular to the shoulder-hand line, flipped so the elbow points outward.
    var nx = -dy / distance
    var ny = dx / distance
    if (nx * outward < 0) {
        nx = -nx
        ny = -ny
    }
    return Offset(mid.x + nx * bend, mid.y + ny * bend)
}

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction
