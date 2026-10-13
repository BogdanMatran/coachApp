package ch.coachapp.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A round check that only draws state; the row around it owns the tap and the semantics. Empty, it
 * is a quiet ring; checked, the accent disc pops in with a tick, so ticking something off feels
 * like a small win rather than a form control.
 */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    val colors = LocalAppColors.current
    val fill by animateColorAsState(if (checked) colors.acc else Color.Transparent, tween(160), label = "checkFill")
    val scale by animateFloatAsState(
        if (checked) 1f else 0.92f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale",
    )
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(fill)
            .then(if (checked) Modifier else Modifier.border(2.dp, colors.label3, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Default.Check, contentDescription = null, tint = colors.onAcc, modifier = Modifier.size(size * 0.62f))
    }
}

/** How long a [Celebration] stays up before it hands back with onFinished. */
private const val CelebrationMillis = 1_900L

/**
 * The peak moment after finishing something that matters — a workout, a check-in, the last meal of
 * the day. A card pops in over a dimmed screen with a burst of accent sparks, then dismisses itself
 * and calls [onFinished]. Tapping does not dismiss it early: it is short, and a stray tap mid-set
 * should not swallow the moment.
 */
@Composable
fun Celebration(title: String, subtitle: String?, onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val finished by rememberUpdatedState(onFinished)
    val pop = remember { Animatable(0.6f) }
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(180)) }
        launch { pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow)) }
        launch { burst.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(CelebrationMillis)
        fade.animateTo(0f, tween(220))
        finished()
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color.Black.copy(alpha = 0.35f))
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        // Sparks in the brand pair, flying out from behind the badge and fading as they go.
        val sparkColors = listOf(colors.acc, colors.onAcc, colors.acc, colors.yellow)
        Canvas(Modifier.size(280.dp)) {
            val progress = burst.value
            val radius = size.minDimension / 2 * (0.25f + 0.75f * progress)
            repeat(16) { i ->
                val angle = (i / 16f) * 2 * PI.toFloat() + (i % 2) * 0.2f
                val distance = radius * (if (i % 3 == 0) 1f else 0.82f)
                val center = Offset(size.width / 2 + cos(angle) * distance, size.height / 2 + sin(angle) * distance)
                drawCircle(
                    color = sparkColors[i % sparkColors.size].copy(alpha = (1f - progress).coerceIn(0f, 1f)),
                    radius = (if (i % 2 == 0) 6.dp else 4.dp).toPx(),
                    center = center,
                )
            }
        }
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(MaterialTheme.shapes.extraLarge)
                .background(colors.surface)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(colors.acc), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.onAcc, modifier = Modifier.size(36.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.label2, textAlign = TextAlign.Center)
            }
        }
    }
}
