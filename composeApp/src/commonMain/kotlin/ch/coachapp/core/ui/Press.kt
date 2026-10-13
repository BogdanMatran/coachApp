package ch.coachapp.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode

/**
 * Motion acknowledges, it does not animate. A press scales the control by a couple of percent over
 * [PressDuration]; nothing bounces or slides for decoration. Ripples are off app-wide
 * ([NoIndication], installed by `CoachTheme`), so this is what a tap looks like.
 *
 * Buttons settle at 0.975, icon buttons at [IconPressScale], card-sized tiles at [TilePressScale].
 * List rows do not scale at all — they swap their background to `surface2` instead.
 */
const val PressDuration = 140

const val ButtonPressScale = 0.975f
const val IconPressScale = 0.92f
const val TilePressScale = 0.98f

@Composable
fun rememberPressScale(
    interactionSource: InteractionSource,
    pressed: Float = ButtonPressScale,
): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = tween(PressDuration),
        label = "pressScale",
    )
    return scale
}

/** Applies [rememberPressScale] for [interactionSource] to whatever it modifies. */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressed: Float = ButtonPressScale,
): Modifier = composed {
    val scale = rememberPressScale(interactionSource, pressed)
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** The interaction source to hand to both `clickable` and [pressScale]. */
@Composable
fun rememberPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/** Draws nothing: the app's press feedback is a scale, not a ripple. */
object NoIndication : IndicationNodeFactory {

    private class DrawNothingNode : Modifier.Node(), DrawModifierNode {
        override fun ContentDrawScope.draw() = drawContent()
    }

    override fun create(interactionSource: InteractionSource): DelegatableNode = DrawNothingNode()

    override fun hashCode(): Int = -1

    override fun equals(other: Any?) = other === this
}
