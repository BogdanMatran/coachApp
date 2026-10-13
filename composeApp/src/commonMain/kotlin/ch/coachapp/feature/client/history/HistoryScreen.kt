package ch.coachapp.feature.client.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.ActivityCard
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.core.ui.weekLabel
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.WorkoutLog
import ch.coachapp.feature.client.checkin.StatusChip
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryRoute(onOpenCheckin: (String) -> Unit, viewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    HistoryScreen(state, onRetry = viewModel::load, onSelectSection = viewModel::selectSection, onOpenCheckin = onOpenCheckin)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onRetry: () -> Unit,
    onSelectSection: (HistorySection) -> Unit,
    onOpenCheckin: (String) -> Unit,
) {
    Centered {
        Column(Modifier.fillMaxSize()) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                HistorySection.entries.forEachIndexed { index, section ->
                    SegmentedButton(
                        selected = state.section == section,
                        onClick = { onSelectSection(section) },
                        shape = SegmentedButtonDefaults.itemShape(index, HistorySection.entries.size),
                    ) { Text(section.label) }
                }
            }
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error, onRetry)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp + bottomBarInset()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.activity.isNotEmpty()) {
                        item(key = "activity") { ActivityCard(state.activity, Modifier.padding(bottom = 8.dp)) }
                    }
                    when (state.section) {
                        HistorySection.Checkins -> {
                            if (state.checkins.isEmpty()) item { EmptyState("No check-ins yet", "Submit your first one from the Check-in tab.") }
                            items(state.checkins, key = { it.id }) { CheckinRow(it, onClick = { onOpenCheckin(it.id) }) }
                        }
                        HistorySection.Workouts -> {
                            if (state.workouts.isEmpty()) item { EmptyState("No workouts logged yet", "Start one from the Today tab.") }
                            items(state.workouts, key = { it.id }) { WorkoutLogCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckinRow(checkin: Checkin, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(checkin.weekLabel(), style = MaterialTheme.typography.titleMedium)
                    checkin.weight?.let { Text("${it.fmt()} kg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                StatusChip(checkin)
            }
            checkin.coachFeedback?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WorkoutLogCard(log: WorkoutLog) {
    InfoCard {
        Row {
            Text(log.dayTitle ?: "Workout", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(Dates.formatTimestamp(log.performedAt), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        log.sets.groupBy { it.exerciseId }.values.forEach { sets ->
            val name = sets.first().exercise?.name ?: "Exercise"
            val summary = sets.sortedBy { it.setNo }.joinToString("  ") { set ->
                listOfNotNull(set.weight?.let { "${it.fmt()}kg" }, set.reps?.let { "×$it" }).joinToString("")
            }
            Column {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        log.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
