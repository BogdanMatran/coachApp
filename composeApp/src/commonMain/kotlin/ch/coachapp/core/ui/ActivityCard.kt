package ch.coachapp.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.coachapp.core.Dates
import ch.coachapp.data.model.DayActivity
import ch.coachapp.domain.completion
import ch.coachapp.domain.currentStreak

/**
 * Activity overview shared by the client's history and the coach's client detail:
 * a heatmap plus 7-day plan completion, recent workouts and the current streak.
 */
@Composable
fun ActivityCard(activity: List<DayActivity>, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val today = Dates.today()
    val lastWeek = activity.takeLast(7)
    val completion = lastWeek.completion()
    val workouts28 = activity.takeLast(28).sumOf { it.workouts }

    ChartCard(title = "Activity", subtitle = "Workouts, meals and supplements per day", modifier = modifier) {
        ActivityHeatmap(activity, today = today)
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressRing(
                progress = (completion ?: 0.0).toFloat(),
                color = colors.meals,
                size = 72.dp,
                strokeWidth = 9.dp,
            ) {
                Text(
                    completion?.let { "${(it * 100).toInt()}%" } ?: "–",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Plan completion, last 7 days",
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip("Workouts · 4 wks", "$workouts28")
                    StatChip("Day streak", "${activity.currentStreak(today)}")
                }
            }
        }
    }
}
