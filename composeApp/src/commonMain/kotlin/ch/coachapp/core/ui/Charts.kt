package ch.coachapp.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.coachapp.core.Dates
import ch.coachapp.data.model.DayActivity
import ch.coachapp.domain.level
import ch.coachapp.domain.weekColumns
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlin.math.floor

// ---------------------------------------------------------------------------
// Rings
// ---------------------------------------------------------------------------

/** Animated progress ring with rounded ends; [progress] is 0..1. [center] is drawn inside. */
@Composable
fun ProgressRing(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    strokeWidth: Dp = 12.dp,
    trackColor: Color = LocalChartColors.current.track,
    center: @Composable () -> Unit = {},
) {
    val animated = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        animated.animateTo(progress.coerceIn(0f, 1f), tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(trackColor, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
            if (animated.value > 0f) {
                drawArc(
                    color, startAngle = -90f, sweepAngle = 360f * animated.value, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        center()
    }
}

/** A ring with a big percentage in the middle and a caption below it. */
@Composable
fun StatRing(
    label: String,
    progress: Float,
    color: Color,
    centerText: String,
    modifier: Modifier = Modifier,
    size: Dp = 88.dp,
    caption: String? = null,
) {
    Column(
        modifier.semantics { contentDescription = "$label: $centerText" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProgressRing(progress = progress, color = color, size = size, strokeWidth = size / 8) {
            Text(centerText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

data class RingSegment(val label: String, val value: Double, val color: Color, val detail: String? = null)

/**
 * Multi-colour donut (e.g. protein/carbs/fat) with a small gap between rounded segments,
 * and a legend next to it.
 */
@Composable
fun SegmentedRing(
    segments: List<RingSegment>,
    modifier: Modifier = Modifier,
    size: Dp = 128.dp,
    strokeWidth: Dp = 16.dp,
    center: @Composable () -> Unit = {},
) {
    val total = segments.sumOf { it.value.coerceAtLeast(0.0) }
    val track = LocalChartColors.current.track
    val animated = remember { Animatable(0f) }
    LaunchedEffect(segments) { animated.snapTo(0f); animated.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }

    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val stroke = strokeWidth.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                if (total <= 0.0) return@Canvas
                // Round caps extend past the arc ends, so leave room for them plus a visible gap.
                val radius = arcSize.width / 2
                val capDegrees = radiansToDegrees(stroke / 2 / radius)
                val gap = if (segments.count { it.value > 0 } > 1) capDegrees * 2 + 4f else 0f
                var start = -90f
                segments.filter { it.value > 0 }.forEach { segment ->
                    val sweep = (360f * (segment.value / total).toFloat())
                    val visible = (sweep - gap).coerceAtLeast(0.5f) * animated.value
                    drawArc(
                        segment.color, start + gap / 2, visible, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    start += sweep
                }
            }
            center()
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            segments.forEach { segment -> LegendRow(segment) }
        }
    }
}

private fun radiansToDegrees(radians: Float): Float = radians * 180f / kotlin.math.PI.toFloat()

@Composable
private fun LegendRow(segment: RingSegment) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(segment.color))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(segment.label, style = MaterialTheme.typography.bodyMedium)
            if (segment.detail != null) {
                Text(segment.detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Activity heatmap
// ---------------------------------------------------------------------------

private val HeatCell = 11.dp
private val HeatGap = 3.dp
private val DayLabelWidth = 28.dp
private val MonthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * GitHub-style activity grid: one column per week (Monday on top), as many weeks as fit
 * the available width. Tapping a day shows its details below the grid.
 */
@Composable
fun ActivityHeatmap(
    activity: List<DayActivity>,
    modifier: Modifier = Modifier,
    today: LocalDate = Dates.today(),
) {
    val heat = LocalChartColors.current.heat
    var selected by remember(activity) { mutableStateOf<DayActivity?>(null) }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val step = HeatCell + HeatGap
        val weeks = floor(((maxWidth - DayLabelWidth + HeatGap) / step)).toInt().coerceIn(4, 53)
        val columns = remember(activity, today, weeks) { weekColumns(activity, today, weeks) }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MonthLabels(columns)
            Row {
                Column(Modifier.width(DayLabelWidth), verticalArrangement = Arrangement.spacedBy(HeatGap)) {
                    listOf("Mon", "", "Wed", "", "Fri", "", "").forEach { label ->
                        Box(Modifier.height(HeatCell), contentAlignment = Alignment.CenterStart) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HeatGrid(columns, heat, selected?.day, onSelect = { selected = if (selected?.day == it?.day) null else it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.let { describe(it) } ?: "Tap a day for details",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                HeatLegend(heat)
            }
        }
    }
}

@Composable
private fun MonthLabels(columns: List<List<DayActivity?>>) {
    Box(Modifier.fillMaxWidth().height(16.dp)) {
        var lastMonth = -1
        columns.forEachIndexed { index, week ->
            val first = week.firstOrNull { it != null }?.day ?: return@forEachIndexed
            val month = first.month.number
            // Label a column when its week starts a new month (skip a cut-off first column).
            if (month != lastMonth && (index > 0 || first.day <= 7)) {
                if (index < columns.size - 2) {
                    val x = DayLabelWidth + (HeatCell + HeatGap) * index
                    Text(
                        MonthNames[month - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = x),
                    )
                }
            }
            lastMonth = month
        }
    }
}

@Composable
private fun HeatGrid(columns: List<List<DayActivity?>>, heat: List<Color>, selectedDay: LocalDate?, onSelect: (DayActivity?) -> Unit) {
    val outline = MaterialTheme.colorScheme.onSurface
    val width = (HeatCell + HeatGap) * columns.size - HeatGap
    val height = (HeatCell + HeatGap) * 7 - HeatGap
    Canvas(
        Modifier
            .size(width, height)
            .semantics { contentDescription = "Activity over the last ${columns.size} weeks" }
            .pointerInput(columns) {
                detectTapGestures { offset ->
                    val step = (HeatCell + HeatGap).toPx()
                    val col = (offset.x / step).toInt()
                    val row = (offset.y / step).toInt()
                    onSelect(columns.getOrNull(col)?.getOrNull(row))
                }
            },
    ) {
        val cell = HeatCell.toPx()
        val step = (HeatCell + HeatGap).toPx()
        val radius = CornerRadius(3.dp.toPx())
        columns.forEachIndexed { c, week ->
            week.forEachIndexed { r, day ->
                if (day == null) return@forEachIndexed
                val topLeft = Offset(c * step, r * step)
                drawRoundRect(heat[day.level()], topLeft, Size(cell, cell), radius)
                if (day.day == selectedDay) {
                    drawRoundRect(outline, topLeft, Size(cell, cell), radius, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
    }
}

@Composable
private fun HeatLegend(heat: List<Color>) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        heat.forEach { Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(it)) }
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun describe(day: DayActivity): String = buildString {
    append("${Dates.dayName(day.day).take(3)} ${Dates.format(day.day)}")
    append(" · ${day.workouts} workout${if (day.workouts == 1) "" else "s"}")
    if (day.plannedMeals > 0) append(" · ${day.meals}/${day.plannedMeals} meals")
    if (day.plannedSupplements > 0) append(" · ${day.supplements}/${day.plannedSupplements} supps")
}

/** Wraps chart cards consistently: title, optional subtitle, content. */
@Composable
fun ChartCard(title: String, modifier: Modifier = Modifier, subtitle: String? = null, content: @Composable () -> Unit) {
    InfoCard(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Evenly spaced rings that wrap on narrow screens. */
@Composable
fun RingRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        content()
    }
}
