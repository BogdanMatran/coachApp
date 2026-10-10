package ch.coachapp.feature.client.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.CheckinAnswers
import ch.coachapp.core.ui.CheckinMetrics
import ch.coachapp.core.ui.CheckinPhotos
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.weekLabel
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.Pose
import ch.coachapp.data.repository.isReviewed

/** Read-only check-in: coach feedback first, then metrics, answers and photos. */
@Composable
fun CheckinReadOnly(
    checkin: Checkin,
    previous: Checkin?,
    questions: List<CheckinQuestion>,
    photoUrls: Map<Pose, String>,
    previousPhotoUrls: Map<Pose, String>,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(checkin.weekLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        StatusChip(checkin)
        if (checkin.isReviewed) CoachFeedbackCard(checkin)
        SectionHeader("Metrics")
        CheckinMetrics(checkin, previous)
        SectionHeader("Answers")
        CheckinAnswers(checkin, questions)
        SectionHeader("Photos")
        CheckinPhotos(
            urls = photoUrls,
            previousUrls = previousPhotoUrls,
            previousLabel = previous?.let { "Week of ${Dates.format(it.weekStart)}" },
        )
    }
}

@Composable
fun StatusChip(checkin: Checkin) {
    AssistChip(
        onClick = {},
        label = { Text(if (checkin.isReviewed) "Reviewed" else "Waiting for coach") },
    )
}

@Composable
fun CoachFeedbackCard(checkin: Checkin) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Coach feedback", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                checkin.coachFeedback?.takeIf { it.isNotBlank() } ?: "Reviewed without comments.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            checkin.reviewedAt?.let {
                Text("Reviewed ${Dates.formatTimestamp(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}
