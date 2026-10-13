package ch.coachapp.feature.client.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ConfirmDialog
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.ExerciseAnimation
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.NumberField
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.Tag
import ch.coachapp.data.model.SetLog
import ch.coachapp.feature.client.prescription
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WorkoutSessionRoute(
    dayId: String,
    onDone: () -> Unit,
    viewModel: WorkoutSessionViewModel = koinViewModel(key = "session-$dayId") { parametersOf(dayId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val onBack = { if (state.hasEnteredData && !state.finished) confirmDiscard = true else onDone() }

    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    // System back gesture gets the same discard confirmation as the top-bar back button.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.hasEnteredData && !state.finished,
        onBackCompleted = { confirmDiscard = true },
    )

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            text = "The sets you've entered will be lost.",
            confirmLabel = "Discard",
            onConfirm = onDone,
            onDismiss = { confirmDiscard = false },
        )
    }

    WorkoutSessionScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onUpdateSet = viewModel::updateSet,
        onAddSet = viewModel::addSet,
        onRemoveSet = viewModel::removeSet,
        onNotesChange = viewModel::setNotes,
        onFinish = viewModel::finish,
    )
}

@Composable
fun WorkoutSessionScreen(
    state: WorkoutSessionUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdateSet: (exerciseIndex: Int, setIndex: Int, (SetInput) -> SetInput) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (exerciseIndex: Int, setIndex: Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(
                title = state.day?.title ?: "Workout",
                onBack = onBack,
                actions = listOfNotNull(state.day?.let { BarAction("progress", "${state.doneCount}/${state.totalCount} sets") }),
                interceptBack = state.hasEnteredData && !state.finished,
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error, onRetry)
                else -> Centered {
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        state.day?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                            item { Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        itemsIndexed(state.exercises, key = { _, ex -> ex.exercise.id }) { exIndex, ex ->
                            ExerciseCard(
                                session = ex,
                                onUpdateSet = { setIndex, transform -> onUpdateSet(exIndex, setIndex, transform) },
                                onAddSet = { onAddSet(exIndex) },
                                onRemoveSet = { setIndex -> onRemoveSet(exIndex, setIndex) },
                            )
                        }
                        item {
                            OutlinedTextField(
                                value = state.notes,
                                onValueChange = onNotesChange,
                                label = { Text("Session notes (optional)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        item {
                            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                            PrimaryButton("Finish workout", onClick = onFinish, loading = state.saving, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    session: ExerciseSession,
    onUpdateSet: (Int, (SetInput) -> SetInput) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
) {
    val we = session.exercise
    InfoCard {
        // The demo plate: how the movement looks, before the numbers for it.
        we.exercise?.let { ExerciseAnimation(it) }
        Text(we.exercise?.name ?: "Exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        we.exercise?.attributes?.takeIf { it.isNotEmpty() }?.let { attributes ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { attributes.forEach { Tag(it) } }
        }
        Text(prescription(we), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        we.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (session.previous.isNotEmpty()) {
            Text("Last time: ${previousSummary(session.previous)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        session.sets.forEachIndexed { index, set ->
            key(set.key) {
                SetRow(
                    number = index + 1,
                    set = set,
                    onChange = { transform -> onUpdateSet(index, transform) },
                    onRemove = { onRemoveSet(index) },
                )
            }
        }
        TextButton(onClick = onAddSet) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add set", modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun SetRow(number: Int, set: SetInput, onChange: ((SetInput) -> SetInput) -> Unit, onRemove: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("$number", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(20.dp))
        NumberField(set.reps, { v -> onChange { it.copy(reps = v) } }, "Reps", Modifier.weight(1f), decimal = false)
        NumberField(set.weight, { v -> onChange { it.copy(weight = v) } }, "kg", Modifier.weight(1.2f))
        NumberField(set.rpe, { v -> onChange { it.copy(rpe = v) } }, "RPE", Modifier.weight(1f))
        Checkbox(checked = set.done, onCheckedChange = { checked -> onChange { it.copy(done = checked) } })
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "Remove set") }
    }
}

private fun previousSummary(sets: List<SetLog>): String =
    sets.joinToString(", ") { set ->
        listOfNotNull(set.weight?.let { "${it.fmt()}kg" }, set.reps?.let { "×$it" }).joinToString("")
    }
