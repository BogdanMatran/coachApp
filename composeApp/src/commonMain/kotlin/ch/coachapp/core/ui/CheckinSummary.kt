package ch.coachapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.MeasurementKeys
import ch.coachapp.data.model.Pose
import ch.coachapp.data.model.QuestionType
import coil3.compose.AsyncImage

/** Weight, body fat and measurements, with the change against [previous] when given. */
@Composable
fun CheckinMetrics(checkin: Checkin, previous: Checkin?, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StatChip("Weight", metric(checkin.weight, previous?.weight, "kg"))
        StatChip("Body fat", metric(checkin.bodyFat, previous?.bodyFat, "%"))
        MeasurementKeys.forEach { key ->
            val value = checkin.measurements[key] ?: return@forEach
            StatChip(key.replaceFirstChar { it.uppercase() }, metric(value, previous?.measurements?.get(key), "cm"))
        }
    }
}

private fun metric(value: Double?, previous: Double?, unit: String): String {
    if (value == null) return "–"
    val base = "${value.fmt()} $unit"
    if (previous == null) return base
    val delta = value - previous
    val sign = if (delta > 0) "+" else ""
    return "$base ($sign${delta.fmt()})"
}

@Composable
fun CheckinAnswers(checkin: Checkin, questions: List<CheckinQuestion>, modifier: Modifier = Modifier) =
    AnswerList(checkin.answers, questions, modifier)

/** Answers keyed by question id, used for check-ins and the onboarding form. */
@Composable
fun AnswerList(answers: Map<String, String>, questions: List<CheckinQuestion>, modifier: Modifier = Modifier) {
    // Show template questions first, then any answers to questions that were removed since.
    val known = questions.map { it.id }.toSet()
    val extra = answers.keys.filter { it !in known }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        questions.forEach { question ->
            AnswerRow(question.label, formatAnswer(question.type, answers[question.id]))
        }
        extra.forEach { key -> AnswerRow(key, answers[key].orEmpty()) }
        if (questions.isEmpty() && extra.isEmpty()) {
            Text("No questions answered.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatAnswer(type: QuestionType, value: String?): String = when {
    value.isNullOrBlank() -> "–"
    type == QuestionType.Scale -> "$value / 5"
    type == QuestionType.YesNo -> if (value == "yes") "Yes" else "No"
    else -> value
}

@Composable
private fun AnswerRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One row per pose; with [previousUrls] the photos are shown side by side for comparison. */
@Composable
fun CheckinPhotos(
    urls: Map<Pose, String>,
    previousUrls: Map<Pose, String> = emptyMap(),
    previousLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    if (urls.isEmpty() && previousUrls.isEmpty()) {
        Text("No photos.", modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val compare = previousUrls.isNotEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Pose.entries.forEach { pose ->
            if (urls[pose] == null && previousUrls[pose] == null) return@forEach
            Text(pose.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (compare) PhotoTile(previousUrls[pose], previousLabel ?: "Previous", Modifier.weight(1f))
                PhotoTile(urls[pose], if (compare) "This week" else null, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PhotoTile(url: String?, caption: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                AsyncImage(model = url, contentDescription = caption, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f))
            } else {
                Text("No photo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
    }
}

fun Checkin.weekLabel(): String = "Week of ${Dates.format(weekStart)}"
