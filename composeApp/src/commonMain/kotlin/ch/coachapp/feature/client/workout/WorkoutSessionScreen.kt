package ch.coachapp.feature.client.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.Celebration
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.CheckMark
import ch.coachapp.core.ui.ConfirmDialog
import ch.coachapp.core.ui.ContentMaxWidth
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.HairlineWidth
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.NumberField
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.Tag
import ch.coachapp.data.model.SetLog
import ch.coachapp.feature.client.prescription
import ch.coachapp.feature.exercise.ExerciseDemo
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun WorkoutSessionRoute(
    dayId: String,
    onDone: () -> Unit,
    onOpenExercise: (workoutExerciseId: String) -> Unit = {},
    viewModel: WorkoutSessionViewModel = koinViewModel(key = "session-$dayId") { parametersOf(dayId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }
    val onBack = { if (state.hasEnteredData && !state.finished) confirmDiscard = true else onDone() }

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
        onRestoreSet = viewModel::restoreSet,
        onNotesChange = viewModel::setNotes,
        onFinish = viewModel::finish,
        onOpenExercise = onOpenExercise,
    )

    // The peak of the session: a short summary before handing back to Today.
    if (state.finished) {
        Celebration(
            title = "Workout done",
            subtitle = listOfNotNull(
                "${state.doneCount} set${if (state.doneCount == 1) "" else "s"}",
                state.volumeKg.takeIf { it > 0 }?.let { "${it.fmt(0)} kg lifted" },
            ).joinToString(" · "),
            onFinished = onDone,
        )
    }
}

@Composable
fun WorkoutSessionScreen(
    state: WorkoutSessionUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onUpdateSet: (exerciseIndex: Int, setIndex: Int, (SetInput) -> SetInput) -> Unit,
    onAddSet: (Int) -> Unit,
    onRemoveSet: (exerciseIndex: Int, setIndex: Int) -> Unit,
    onRestoreSet: (exerciseIndex: Int, setIndex: Int, SetInput) -> Unit,
    onNotesChange: (String) -> Unit,
    onFinish: () -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Removing a set is one swipe away, so it is undoable rather than confirmed.
    val removeSet: (Int, Int) -> Unit = { exIndex, setIndex ->
        val removed = state.exercises.getOrNull(exIndex)?.sets?.getOrNull(setIndex)
        if (removed != null) {
            onRemoveSet(exIndex, setIndex)
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val result = snackbar.showSnackbar("Set ${setIndex + 1} removed", actionLabel = "Undo", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) onRestoreSet(exIndex, setIndex, removed)
            }
        }
    }
    Scaffold(
        topBar = {
            BackTopBar(
                title = state.day?.title ?: "Workout",
                onBack = onBack,
                actions = listOfNotNull(state.day?.let { BarAction("progress", "${state.doneCount}/${state.totalCount} sets") }),
                interceptBack = state.hasEnteredData && !state.finished,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!state.loading && state.error == null) {
                FinishBar(saveError = state.saveError, saving = state.saving, onFinish = onFinish)
            }
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
                                onRemoveSet = { setIndex -> removeSet(exIndex, setIndex) },
                                onOpenDetails = { onOpenExercise(ex.exercise.id) },
                            )
                        }
                        item {
                            AppTextField(
                                value = state.notes,
                                onValueChange = onNotesChange,
                                label = { Text("Session notes (optional)") },
                                minLines = 2,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pinned under the list, so finishing never means scrolling past every exercise first. */
@Composable
private fun FinishBar(saveError: String?, saving: Boolean, onFinish: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgEl)
            .drawBehind { drawLine(colors.sep, Offset.Zero, Offset(size.width, 0f), strokeWidth = HairlineWidth.toPx()) }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = ContentMaxWidth), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            saveError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            PrimaryButton("Finish workout", onClick = onFinish, loading = saving)
        }
    }
}

@Composable
private fun ExerciseCard(
    session: ExerciseSession,
    onUpdateSet: (Int, (SetInput) -> SetInput) -> Unit,
    onAddSet: () -> Unit,
    onRemoveSet: (Int) -> Unit,
    onOpenDetails: () -> Unit,
) {
    val we = session.exercise
    InfoCard {
        // The demo plate: how the movement looks, before the numbers for it.
        we.exercise?.let { ExerciseDemo(it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                we.exercise?.name ?: "Exercise",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            // The steps live one tap away, so the set grid stays the focus mid-workout.
            GhostButton("How to", onOpenDetails, size = ButtonSize.Sm, icon = Icons.Outlined.Info)
        }
        we.exercise?.attributes?.takeIf { it.isNotEmpty() }?.let { attributes ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { attributes.forEach { Tag(it) } }
        }
        Text(prescription(we), style = MaterialTheme.typography.bodyMedium, color = LocalAppColors.current.accText)
        we.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (session.previous.isNotEmpty()) {
            Text("Last time: ${previousSummary(session.previous)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SetHeader()
        session.sets.forEachIndexed { index, set ->
            key(set.key) {
                val previous = session.previous.getOrNull(index) ?: session.previous.lastOrNull()
                SetRow(
                    number = index + 1,
                    set = set,
                    repsHint = we.reps.takeIf { it.isNotBlank() } ?: previous?.reps?.toString(),
                    weightHint = previous?.weight?.fmt(),
                    rpeHint = we.targetRpe?.fmt() ?: previous?.rpe?.fmt(),
                    onChange = { transform -> onUpdateSet(index, transform) },
                    onRemove = { onRemoveSet(index) },
                )
            }
        }
        GhostButton("Add set", onAddSet, size = ButtonSize.Sm, icon = Icons.Default.Add)
    }
}

private val SetNumberWidth = 28.dp
private val DoneWidth = 48.dp

/** Column titles for the set grid, so each field can drop its own label and stay wide enough. */
@Composable
private fun SetHeader() {
    val style = MaterialTheme.typography.labelSmall
    val color = LocalAppColors.current.label2
    // Purely visual: every field already carries its own accessibility label.
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(SetGridGap),
    ) {
        Text("Set", style = style, color = color, modifier = Modifier.width(SetNumberWidth), textAlign = TextAlign.Center)
        Text("Reps", style = style, color = color, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text("kg", style = style, color = color, modifier = Modifier.weight(1.2f).padding(start = 4.dp))
        Text("RPE", style = style, color = color, modifier = Modifier.weight(1f).padding(start = 4.dp))
        Text("Done", style = style, color = color, modifier = Modifier.width(DoneWidth), textAlign = TextAlign.Center)
    }
}

private val SetGridGap = 6.dp

/**
 * One set: three label-less fields under [SetHeader] and a done toggle. Removing is a swipe to the
 * left (or the "Remove set" accessibility action), so no delete button sits next to the checkbox.
 */
@Composable
private fun SetRow(
    number: Int,
    set: SetInput,
    repsHint: String?,
    weightHint: String?,
    rpeHint: String?,
    onChange: ((SetInput) -> SetInput) -> Unit,
    onRemove: () -> Unit,
) {
    val colors = LocalAppColors.current
    val dismiss = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        onDismiss = { onRemove() },
        modifier = Modifier.semantics {
            customActions = listOf(CustomAccessibilityAction("Remove set $number") { onRemove(); true })
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(MaterialTheme.shapes.small).background(colors.red).padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = colors.onRed)
            }
        },
    ) {
        Row(
            Modifier.fillMaxWidth().background(colors.surface),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SetGridGap),
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelLarge,
                color = colors.label2,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(SetNumberWidth).clearAndSetSemantics {},
            )
            NumberField(
                set.reps, { v -> onChange { it.copy(reps = v) } }, label = null, modifier = Modifier.weight(1f),
                decimal = false, placeholder = repsHint, accessibilityLabel = "Set $number reps",
            )
            NumberField(
                set.weight, { v -> onChange { it.copy(weight = v) } }, label = null, modifier = Modifier.weight(1.2f),
                placeholder = weightHint, accessibilityLabel = "Set $number weight in kilograms",
            )
            NumberField(
                set.rpe, { v -> onChange { it.copy(rpe = v) } }, label = null, modifier = Modifier.weight(1f),
                placeholder = rpeHint, accessibilityLabel = "Set $number RPE",
            )
            Box(
                Modifier
                    .size(DoneWidth)
                    .toggleable(
                        value = set.done,
                        role = Role.Checkbox,
                        onValueChange = { checked -> onChange { it.copy(done = checked) } },
                    )
                    .semantics { contentDescription = "Set $number done" },
                contentAlignment = Alignment.Center,
            ) {
                CheckMark(checked = set.done)
            }
        }
    }
}

private fun previousSummary(sets: List<SetLog>): String =
    sets.joinToString(", ") { set ->
        listOfNotNull(set.weight?.let { "${it.fmt()}kg" }, set.reps?.let { "×$it" }).joinToString("")
    }
