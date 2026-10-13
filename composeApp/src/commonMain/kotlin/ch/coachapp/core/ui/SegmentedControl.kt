package ch.coachapp.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * An iOS-style segmented control: a [AppColors.surface3] track with a raised [AppColors.thumb]
 * that slides to the selected segment. Matches UISegmentedControl's metrics — 32dp tall, a 2dp
 * inset, 9dp/7dp corners, 13sp labels that turn semibold when selected, and hairline dividers that
 * fade out next to the thumb.
 *
 * Like the native control, the thumb can be grabbed and dragged across segments; the choice is
 * committed on release. A tap on any other segment selects it. [selected] may be null, in which
 * case no thumb is shown (UISegmentedControl's `noSegment`) and the first choice fades it in place
 * rather than sliding it in from the left.
 *
 * The thumb carries the app's only shadow: on iOS it is what tells the thumb apart from the track
 * in light mode. In dark mode the lighter fill does that job and the shadow is dropped.
 *
 * Pass [icon] to put a glyph before each label; it takes the label's colour and weight.
 *
 * Pass [thumbColor] to fill the thumb with a per-option colour instead (see
 * [YesNoSegmentedControl]); [selectedLabelColor] then sets the label on top of it (unspecified keeps the theme label). A coloured thumb
 * stands out on its own, so it is drawn without the shadow, and its colour cross-fades as it slides.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = SegmentedControlHeight,
    label: (T) -> String = { it.toString() },
    icon: ((T) -> ImageVector)? = null,
    thumbColor: ((T) -> Color)? = null,
    selectedLabelColor: (T) -> Color = { Color.Unspecified },
) {
    require(options.isNotEmpty()) { "SegmentedControl needs at least one option" }
    val colors = LocalAppColors.current
    val haptics = LocalHapticFeedback.current
    val count = options.size
    val selectedIndex = options.indexOf(selected).takeIf { it >= 0 }

    // While the thumb is being dragged it follows the finger; the parent only hears on release.
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var thumbPressed by remember { mutableStateOf(false) }
    var pressedIndex by remember { mutableStateOf<Int?>(null) }
    val shownIndex = dragIndex ?: selectedIndex

    val position = remember { Animatable(shownIndex?.toFloat() ?: 0f) }
    var thumbPlaced by remember { mutableStateOf(shownIndex != null) }
    LaunchedEffect(shownIndex) {
        when {
            shownIndex == null -> thumbPlaced = false
            !thumbPlaced -> {
                position.snapTo(shownIndex.toFloat())
                thumbPlaced = true
            }
            else -> position.animateTo(shownIndex.toFloat(), ThumbSpring)
        }
    }
    val thumbAlpha by animateFloatAsState(
        targetValue = if (shownIndex != null) 1f else 0f,
        animationSpec = tween(PressDuration),
        label = "thumbAlpha",
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (thumbPressed) ThumbPressScale else 1f,
        animationSpec = tween(PressDuration),
        label = "thumbScale",
    )
    // Divider i sits between segment i and i + 1, and hides when the thumb touches either side.
    val dividerAlphas = (0 until count - 1).map { i ->
        animateFloatAsState(
            targetValue = if (shownIndex == i || shownIndex == i + 1) 0f else 1f,
            animationSpec = tween(PressDuration),
            label = "divider$i",
        )
    }

    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentOptions by rememberUpdatedState(options)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val isDark = colors.bg.luminance() < 0.5f
    val tinted = thumbColor != null
    // Hold the last colour while the thumb fades out, so it does not flash back to the default.
    var lastTintIndex by remember { mutableStateOf(shownIndex) }
    if (shownIndex != null) lastTintIndex = shownIndex
    val thumbFill by animateColorAsState(
        targetValue = lastTintIndex?.let { thumbColor?.invoke(options[it]) } ?: colors.thumb,
        animationSpec = tween(PressDuration),
        label = "thumbFill",
    )
    val dividerColor = colors.label4

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (enabled) 1f else DisabledAlpha)
            .background(colors.surface3, RoundedCornerShape(TrackCorner))
            .drawBehind {
                val segment = (size.width - 2 * TrackInset.toPx()) / count
                val dividerHeight = size.height - 2 * DividerInset.toPx()
                val dividerWidth = 1.dp.toPx()
                dividerAlphas.forEachIndexed { i, alpha ->
                    drawRoundRect(
                        color = dividerColor.copy(alpha = dividerColor.alpha * alpha.value),
                        topLeft = Offset(TrackInset.toPx() + segment * (i + 1) - dividerWidth / 2, DividerInset.toPx()),
                        size = Size(dividerWidth, dividerHeight),
                        cornerRadius = CornerRadius(dividerWidth / 2),
                    )
                }
            }
            .pointerInput(count, enabled) {
                if (!enabled) return@pointerInput
                val inset = TrackInset.toPx()
                fun indexAt(x: Float) = ((x - inset) / ((size.width - 2 * inset) / count)).toInt().coerceIn(0, count - 1)

                awaitEachGesture {
                    val down = awaitFirstDown()
                    val startIndex = indexAt(down.position.x)
                    val onThumb = startIndex == currentSelectedIndex
                    if (onThumb) thumbPressed = true else pressedIndex = startIndex

                    var cancelled = false
                    var released = false
                    while (!released && !cancelled) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            cancelled = true
                        } else if (change.changedToUp()) {
                            released = true
                        } else if (onThumb) {
                            // Hold on to the gesture so a surrounding scroll does not steal the drag.
                            if (change.positionChanged()) change.consume()
                            val index = indexAt(change.position.x)
                            if (index != (dragIndex ?: startIndex)) {
                                dragIndex = index
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                        } else if (change.isConsumed) {
                            cancelled = true // A parent scroll took over.
                        } else {
                            val inside = change.position.y in 0f..size.height.toFloat() &&
                                indexAt(change.position.x) == startIndex
                            pressedIndex = if (inside) startIndex else null
                        }
                    }

                    val committed = when {
                        cancelled -> null
                        onThumb -> dragIndex
                        else -> pressedIndex
                    }
                    thumbPressed = false
                    pressedIndex = null
                    dragIndex = null
                    if (committed != null && committed != currentSelectedIndex) {
                        if (!onThumb) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        currentOnSelect(currentOptions[committed])
                    }
                }
            },
    ) {
        val segmentWidth = (maxWidth - TrackInset * 2) / count
        val segmentWidthPx = with(LocalDensity.current) { segmentWidth.toPx() }

        Box(
            Modifier
                .padding(TrackInset)
                .width(segmentWidth)
                .fillMaxHeight()
                .offset { IntOffset((position.value * segmentWidthPx).roundToInt(), 0) }
                .graphicsLayer {
                    alpha = thumbAlpha
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .then(
                    if (isDark || tinted) Modifier
                    else Modifier.shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(ThumbCorner),
                        ambientColor = Color.Black.copy(alpha = 0.04f),
                        spotColor = Color.Black.copy(alpha = 0.12f),
                    ),
                )
                .background(thumbFill, RoundedCornerShape(ThumbCorner)),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(TrackInset)
                .selectableGroup(),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == shownIndex
                val text = label(option)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .semantics(mergeDescendants = true) {
                            role = Role.Tab
                            this.selected = index == selectedIndex
                            if (enabled) {
                                onClick(label = text) {
                                    currentOnSelect(currentOptions[index])
                                    true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val labelColor by animateColorAsState(
                        targetValue = if (isSelected) selectedLabelColor(option).takeOrElse { colors.label } else colors.label,
                        animationSpec = tween(PressDuration),
                        label = "label$index",
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.alpha(if (pressedIndex == index && !isSelected) PressedLabelAlpha else 1f),
                    ) {
                        icon?.let {
                            Icon(
                                imageVector = it(option),
                                contentDescription = null,
                                tint = labelColor,
                                modifier = Modifier.size(SegmentIconSize),
                            )
                        }
                        Text(
                            text = text,
                            style = SegmentLabelStyle,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = labelColor,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** A [SegmentedControl] for picking a whole number, such as a 1-to-5 check-in rating. */
@Composable
fun NumberSegmentedControl(
    value: Int?,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 1..5,
    enabled: Boolean = true,
) {
    SegmentedControl(
        options = range.toList(),
        selected = value,
        onSelect = onValueChange,
        modifier = modifier,
        enabled = enabled,
    )
}

/**
 * A Yes/No [SegmentedControl] whose thumb takes the answer's colour: green for yes, red for no.
 * Labels on the green are black and on the red white, which keeps both readable at 13sp semibold.
 */
@Composable
fun YesNoSegmentedControl(
    value: Boolean?,
    onValueChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    yesLabel: String = "Yes",
    noLabel: String = "No",
) {
    val colors = LocalAppColors.current
    SegmentedControl(
        options = listOf(true, false),
        selected = value,
        onSelect = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { if (it) yesLabel else noLabel },
        thumbColor = { if (it) colors.green else colors.red },
        selectedLabelColor = { if (it) colors.onAcc else Color.White },
    )
}

val SegmentedControlHeight = 32.dp

private val TrackInset = 2.dp
private val TrackCorner = 9.dp
private val ThumbCorner = 7.dp
private val DividerInset = 8.dp
private const val ThumbPressScale = 0.95f
private const val PressedLabelAlpha = 0.35f
private const val DisabledAlpha = 0.4f

/** Critically damped, like UIKit's: the thumb glides and settles without overshoot. */
private val ThumbSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f)

private val SegmentIconSize = 14.dp

private val SegmentLabelStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.08).sp)
