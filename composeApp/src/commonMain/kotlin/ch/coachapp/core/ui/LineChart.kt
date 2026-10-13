package ch.coachapp.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.domain.Series
import kotlin.math.abs

/**
 * A metric over time: line, soft fill and dots, with the value of the selected point (the latest
 * by default) above it. Tap or drag to inspect another point. Points are spaced by date, so a
 * missed week leaves a visible gap rather than compressing the trend.
 */
@Composable
fun LineChart(series: Series, color: Color, modifier: Modifier = Modifier, height: Dp = 140.dp) {
    val colors = LocalAppColors.current
    val points = series.points
    if (points.isEmpty()) return
    var selected by remember(series) { mutableStateOf(points.lastIndex) }
    val point = points[selected.coerceIn(points.indices)]

    val minValue = points.minOf { it.value }
    val maxValue = points.maxOf { it.value }
    // Pad the range so a flat or tiny change doesn't fill the whole height.
    val span = (maxValue - minValue).coerceAtLeast(maxOf(abs(maxValue) * 0.02, 1.0))
    val low = minValue - span * 0.15
    val high = maxValue + span * 0.15
    val firstDay = points.first().date.toEpochDays()
    val days = (points.last().date.toEpochDays() - firstDay).coerceAtLeast(1)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${point.value.fmt()} ${series.unit}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                listOfNotNull(Dates.format(point.date), point.detail).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = colors.label2,
            )
        }
        series.change?.let { change ->
            Text(
                "${if (change > 0) "+" else if (change < 0) "−" else ""}${abs(change).fmt()} ${series.unit} since ${Dates.format(points.first().date)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.label2,
            )
        }

        fun xFor(index: Int, width: Float): Float =
            if (points.size == 1) width / 2 else (points[index].date.toEpochDays() - firstDay).toFloat() / days * width

        fun nearest(x: Float, width: Float): Int = points.indices.minBy { abs(xFor(it, width) - x) }

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics {
                    contentDescription = "${series.label}: ${points.first().value.fmt()} to ${points.last().value.fmt()} ${series.unit}"
                }
                .pointerInput(series) {
                    detectTapGestures { offset -> selected = nearest(offset.x - 6.dp.toPx(), size.width - 12.dp.toPx()) }
                }
                .pointerInput(series) {
                    // Horizontal only, so a vertical swipe that starts on the chart still scrolls the page.
                    detectHorizontalDragGestures { change, _ ->
                        selected = nearest(change.position.x - 6.dp.toPx(), size.width - 12.dp.toPx())
                    }
                },
        ) {
            val inset = 6.dp.toPx()
            val width = size.width - inset * 2
            val chartHeight = size.height - inset * 2
            fun y(value: Double): Float = inset + ((high - value) / (high - low)).toFloat() * chartHeight
            val offsets = points.indices.map { Offset(inset + xFor(it, width), y(points[it].value)) }

            // Quiet guides at the top, middle and bottom of the range.
            listOf(0f, 0.5f, 1f).forEach { f ->
                val gy = inset + chartHeight * f
                drawLine(colors.sep, Offset(inset, gy), Offset(inset + width, gy), strokeWidth = 1f)
            }

            if (offsets.size > 1) {
                val line = Path().apply {
                    moveTo(offsets.first().x, offsets.first().y)
                    offsets.drop(1).forEach { lineTo(it.x, it.y) }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(offsets.last().x, inset + chartHeight)
                    lineTo(offsets.first().x, inset + chartHeight)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
                drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            offsets.forEachIndexed { index, o ->
                val isSelected = index == selected
                if (isSelected) {
                    drawLine(color.copy(alpha = 0.4f), Offset(o.x, inset), Offset(o.x, inset + chartHeight), strokeWidth = 1.dp.toPx())
                }
                drawCircle(colors.surface, radius = if (isSelected) 6.dp.toPx() else 3.5.dp.toPx(), center = o)
                drawCircle(color, radius = if (isSelected) 4.5.dp.toPx() else 2.5.dp.toPx(), center = o)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Text(Dates.format(points.first().date), style = MaterialTheme.typography.labelSmall, color = colors.label2, modifier = Modifier.weight(1f))
            Text("${minValue.fmt()}–${maxValue.fmt()} ${series.unit}", style = MaterialTheme.typography.labelSmall, color = colors.label2)
            Text(
                Dates.format(points.last().date),
                style = MaterialTheme.typography.labelSmall,
                color = colors.label2,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End,
            )
        }
    }
}
