package ch.coachapp.feature.client.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.ui.SegmentedControl
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.ActivityCard
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.data.model.WorkoutLog
import ch.coachapp.feature.progress.ProgressPane
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HistoryRoute(viewModel: HistoryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    HistoryScreen(state, onRetry = viewModel::load, onSelectSection = viewModel::selectSection)
}

@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onRetry: () -> Unit,
    onSelectSection: (HistorySection) -> Unit,
) {
    Centered {
        Column(Modifier.fillMaxSize()) {
            SegmentedControl(
                options = HistorySection.entries,
                selected = state.section,
                onSelect = { onSelectSection(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { section -> section.label },
            )
            when {
                // Progress loads its own data, so it doesn't wait for the history lists.
                state.section == HistorySection.Progress -> state.clientId?.let { id ->
                    ProgressPane(id, contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp + bottomBarInset()))
                }
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
                        HistorySection.Progress -> Unit
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
