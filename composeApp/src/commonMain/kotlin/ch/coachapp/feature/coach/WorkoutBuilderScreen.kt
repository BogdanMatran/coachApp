package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.NeutralButton
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.AppCard
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.data.model.Exercise
import ch.coachapp.data.repository.LibraryRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.domain.WorkoutDayDraft
import ch.coachapp.domain.WorkoutDraft
import ch.coachapp.domain.WorkoutExerciseDraft
import ch.coachapp.domain.moveDown
import ch.coachapp.domain.moveUp
import ch.coachapp.feature.exercise.ExerciseThumbnail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class WorkoutBuilderUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val draft: WorkoutDraft = WorkoutDraft(),
    val library: List<Exercise> = emptyList(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
) {
    val title: String
        get() = when {
            draft.id != null -> "Edit workout plan"
            draft.clientId != null -> "New client workout plan"
            else -> "New workout template"
        }
}

class WorkoutBuilderViewModel(
    private val route: WorkoutBuilderRoute,
    private val plans: PlanRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutBuilderUiState(draft = WorkoutDraft(clientId = route.clientId)))
    val state: StateFlow<WorkoutBuilderUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val exercises = library.exercises()
            val draft = route.planId?.let { id -> plans.workoutPlan(id).map(WorkoutDraft::from) }
            val error = exercises.exceptionOrNull() ?: draft?.exceptionOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    loadError = error?.message,
                    library = exercises.getOrDefault(emptyList()),
                    draft = draft?.getOrNull() ?: it.draft,
                )
            }
        }
    }

    private fun edit(transform: (WorkoutDraft) -> WorkoutDraft) = _state.update { it.copy(draft = transform(it.draft), saveError = null) }

    private fun editDay(dayKey: Long, transform: (WorkoutDayDraft) -> WorkoutDayDraft) =
        edit { d -> d.copy(days = d.days.map { if (it.key == dayKey) transform(it) else it }) }

    fun setName(value: String) = edit { it.copy(name = value) }
    fun setNotes(value: String) = edit { it.copy(notes = value) }

    fun addDay() = edit { it.copy(days = it.days + WorkoutDayDraft(title = "Day ${it.days.size + 1}")) }
    fun setDayTitle(dayKey: Long, title: String) = editDay(dayKey) { it.copy(title = title) }
    fun setDayNotes(dayKey: Long, notes: String) = editDay(dayKey) { it.copy(notes = notes) }
    fun removeDay(dayKey: Long) = edit { d -> d.copy(days = d.days.filterNot { it.key == dayKey }) }
    fun moveDayUp(index: Int) = edit { it.copy(days = it.days.moveUp(index)) }
    fun moveDayDown(index: Int) = edit { it.copy(days = it.days.moveDown(index)) }

    fun addExercise(dayKey: Long, exercise: Exercise) =
        editDay(dayKey) { it.copy(exercises = it.exercises + WorkoutExerciseDraft(exercise = exercise)) }

    fun updateExercise(dayKey: Long, exerciseKey: Long, transform: (WorkoutExerciseDraft) -> WorkoutExerciseDraft) =
        editDay(dayKey) { day -> day.copy(exercises = day.exercises.map { if (it.key == exerciseKey) transform(it) else it }) }

    fun removeExercise(dayKey: Long, exerciseKey: Long) =
        editDay(dayKey) { day -> day.copy(exercises = day.exercises.filterNot { it.key == exerciseKey }) }

    fun moveExerciseUp(dayKey: Long, index: Int) = editDay(dayKey) { it.copy(exercises = it.exercises.moveUp(index)) }
    fun moveExerciseDown(dayKey: Long, index: Int) = editDay(dayKey) { it.copy(exercises = it.exercises.moveDown(index)) }

    fun save() {
        val draft = _state.value.draft
        if (draft.name.isBlank()) {
            _state.update { it.copy(saveError = "Give the plan a name") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            plans.saveWorkoutPlan(draft)
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}

@Composable
fun WorkoutBuilderDestination(
    route: WorkoutBuilderRoute,
    onDone: () -> Unit,
    viewModel: WorkoutBuilderViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    WorkoutBuilderScreen(state, viewModel, onBack = onDone)
}

@Composable
fun WorkoutBuilderScreen(state: WorkoutBuilderUiState, actions: WorkoutBuilderViewModel, onBack: () -> Unit) {
    // Day currently picking an exercise from the library
    var pickingFor by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            BackTopBar(
                state.title,
                onBack,
                actions = listOf(
                    BarAction("save", if (state.saving) "Saving…" else "Save", enabled = !state.saving && !state.loading, onClick = actions::save),
                ),
            )
        },
    ) { padding ->
        Centered(Modifier.padding(padding).imePadding()) {
            when {
                state.loading -> LoadingBox()
                state.loadError != null -> ErrorBox(state.loadError, onRetry = actions::load)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            AppTextField(state.draft.name, actions::setName, label = { Text("Plan name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            AppTextField(state.draft.notes, actions::setNotes, label = { Text("Notes for the client") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    itemsIndexed(state.draft.days, key = { _, day -> day.key }) { index, day ->
                        DayCard(
                            day = day,
                            index = index,
                            isLast = index == state.draft.days.lastIndex,
                            actions = actions,
                            onAddExercise = { pickingFor = day.key },
                        )
                    }
                    item {
                        NeutralButton("Add day", { actions.addDay() }, icon = Icons.Default.Add)
                    }
                }
            }
        }
    }

    pickingFor?.let { dayKey ->
        LibraryPickerDialog(
            title = "Add exercise",
            items = state.library,
            itemLabel = { it.name },
            itemSubtitle = { it.attributes.joinToString(" · ").ifEmpty { null } },
            itemLeading = { ExerciseThumbnail(it, size = 44.dp) },
            onPick = { actions.addExercise(dayKey, it); pickingFor = null },
            onDismiss = { pickingFor = null },
        )
    }
}

@Composable
private fun DayCard(
    day: WorkoutDayDraft,
    index: Int,
    isLast: Boolean,
    actions: WorkoutBuilderViewModel,
    onAddExercise: () -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppTextField(
                    value = day.title,
                    onValueChange = { actions.setDayTitle(day.key, it) },
                    label = { Text("Day ${index + 1}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.moveDayUp(index) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move day up") }
                IconButton(onClick = { actions.moveDayDown(index) }, enabled = !isLast) { Icon(Icons.Default.KeyboardArrowDown, "Move day down") }
                IconButton(onClick = { actions.removeDay(day.key) }) { Icon(Icons.Default.Delete, "Remove day") }
            }
            AppTextField(
                value = day.notes,
                onValueChange = { actions.setDayNotes(day.key, it) },
                label = { Text("Day notes") },
                modifier = Modifier.fillMaxWidth(),
            )
            day.exercises.forEachIndexed { exIndex, exercise ->
                HorizontalDivider()
                ExerciseEditor(
                    exercise = exercise,
                    canMoveUp = exIndex > 0,
                    canMoveDown = exIndex < day.exercises.lastIndex,
                    onChange = { transform -> actions.updateExercise(day.key, exercise.key, transform) },
                    onMoveUp = { actions.moveExerciseUp(day.key, exIndex) },
                    onMoveDown = { actions.moveExerciseDown(day.key, exIndex) },
                    onRemove = { actions.removeExercise(day.key, exercise.key) },
                )
            }
            GhostButton("Add exercise", onAddExercise, size = ButtonSize.Sm, icon = Icons.Default.Add)
        }
    }
}

@Composable
private fun ExerciseEditor(
    exercise: WorkoutExerciseDraft,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: ((WorkoutExerciseDraft) -> WorkoutExerciseDraft) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExerciseThumbnail(exercise.exercise, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(exercise.exercise.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                exercise.exercise.muscleGroup?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
            IconButton(onClick = onMoveDown, enabled = canMoveDown) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove exercise") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField(exercise.sets, { v -> v?.let { onChange { e -> e.copy(sets = it.coerceIn(1, 20)) } } }, "Sets", Modifier.weight(1f))
            AppTextField(
                value = exercise.reps,
                onValueChange = { v -> onChange { it.copy(reps = v.take(12)) } },
                label = { Text("Reps") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IntField(exercise.restSec, { v -> v?.let { onChange { e -> e.copy(restSec = it) } } }, "Rest", Modifier.weight(1f), suffix = "s")
            DecimalField(exercise.targetRpe, { v -> onChange { it.copy(targetRpe = v) } }, "RPE", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(
                value = exercise.tempo,
                onValueChange = { v -> onChange { it.copy(tempo = v.take(12)) } },
                label = { Text("Tempo") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = exercise.notes,
                onValueChange = { v -> onChange { it.copy(notes = v) } },
                label = { Text("Notes") },
                singleLine = true,
                modifier = Modifier.weight(3f),
            )
        }
    }
}
