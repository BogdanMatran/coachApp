package ch.coachapp.feature.client.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.fmt
import ch.coachapp.core.toDoubleOrNullLenient
import ch.coachapp.data.model.SetLog
import ch.coachapp.data.model.WorkoutDay
import ch.coachapp.data.model.WorkoutExercise
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.LogRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.data.repository.SetEntry
import ch.coachapp.domain.DraftKeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetInput(
    val key: Long = DraftKeys.next(),
    val reps: String = "",
    val weight: String = "",
    val rpe: String = "",
    val done: Boolean = false,
    /** True once the user edited the row, so pre-filled values alone aren't saved. */
    val touched: Boolean = false,
)

data class ExerciseSession(
    val exercise: WorkoutExercise,
    /** Sets from the previous session, shown as a hint. */
    val previous: List<SetLog>,
    val sets: List<SetInput>,
)

data class WorkoutSessionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val day: WorkoutDay? = null,
    val exercises: List<ExerciseSession> = emptyList(),
    val notes: String = "",
    val saving: Boolean = false,
    val saveError: String? = null,
    val finished: Boolean = false,
) {
    val hasEnteredData: Boolean get() = notes.isNotBlank() || exercises.any { ex -> ex.sets.any { it.done || it.touched } }
    val doneCount: Int get() = exercises.sumOf { ex -> ex.sets.count { it.done } }
    val totalCount: Int get() = exercises.sumOf { it.sets.size }

    /** Weight × reps over the completed sets, for the finish summary. */
    val volumeKg: Double
        get() = exercises.sumOf { ex ->
            ex.sets.filter { it.done }.sumOf { set ->
                (set.weight.toDoubleOrNullLenient() ?: 0.0) * (set.reps.toIntOrNull() ?: 0)
            }
        }
}

class WorkoutSessionViewModel(
    private val dayId: String,
    private val auth: AuthRepository,
    private val plans: PlanRepository,
    private val logs: LogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(WorkoutSessionUiState())
    val state: StateFlow<WorkoutSessionUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val clientId = auth.currentProfile?.id ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val plan = plans.activeWorkoutPlan(clientId).getOrThrow()
                val day = plan?.days?.firstOrNull { it.id == dayId } ?: error("This workout is no longer part of your plan.")
                val exercises = day.sortedExercises
                val last = logs.lastSets(clientId, exercises.map { it.exerciseId }.toSet()).getOrDefault(emptyMap())
                day to exercises.map { we -> sessionFor(we, last[we.exerciseId].orEmpty()) }
            }.onSuccess { (day, exercises) ->
                _state.update { it.copy(loading = false, day = day, exercises = exercises) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load workout") }
            }
        }
    }

    private fun sessionFor(we: WorkoutExercise, previous: List<SetLog>): ExerciseSession {
        val count = maxOf(we.sets, 1)
        val sets = List(count) { index ->
            val prev = previous.getOrNull(index) ?: previous.lastOrNull()
            SetInput(
                reps = prev?.reps?.toString() ?: we.reps.trim().toIntOrNull()?.toString().orEmpty(),
                weight = prev?.weight?.fmt(2).orEmpty(),
            )
        }
        return ExerciseSession(we, previous, sets)
    }

    fun updateSet(exerciseIndex: Int, setIndex: Int, transform: (SetInput) -> SetInput) = updateExercise(exerciseIndex) { ex ->
        ex.copy(sets = ex.sets.mapIndexed { i, set -> if (i == setIndex) transform(set).copy(touched = true) else set })
    }

    fun addSet(exerciseIndex: Int) = updateExercise(exerciseIndex) { ex ->
        val last = ex.sets.lastOrNull()
        ex.copy(sets = ex.sets + SetInput(reps = last?.reps.orEmpty(), weight = last?.weight.orEmpty()))
    }

    fun removeSet(exerciseIndex: Int, setIndex: Int) = updateExercise(exerciseIndex) { ex ->
        ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != setIndex })
    }

    /** Puts a set removed by [removeSet] back where it was, for the snackbar's Undo. */
    fun restoreSet(exerciseIndex: Int, setIndex: Int, set: SetInput) = updateExercise(exerciseIndex) { ex ->
        val at = setIndex.coerceIn(0, ex.sets.size)
        ex.copy(sets = ex.sets.take(at) + set + ex.sets.drop(at))
    }

    fun setNotes(notes: String) = _state.update { it.copy(notes = notes) }

    private fun updateExercise(index: Int, transform: (ExerciseSession) -> ExerciseSession) = _state.update { s ->
        s.copy(exercises = s.exercises.mapIndexed { i, ex -> if (i == index) transform(ex) else ex })
    }

    fun finish() {
        val s = _state.value
        val day = s.day ?: return
        val entries = s.exercises.flatMap { ex ->
            ex.sets.filter { it.done || it.touched }.mapIndexed { i, set ->
                SetEntry(
                    workoutExerciseId = ex.exercise.id,
                    exerciseId = ex.exercise.exerciseId,
                    setNo = i + 1,
                    reps = set.reps.trim().toIntOrNull(),
                    weight = set.weight.toDoubleOrNullLenient(),
                    rpe = set.rpe.toDoubleOrNullLenient(),
                )
            }
        }.filter { it.reps != null || it.weight != null }
        if (entries.isEmpty()) {
            _state.update { it.copy(saveError = "Complete at least one set before finishing.") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            logs.logWorkout(day.id, day.title, s.notes, entries)
                .onSuccess { _state.update { it.copy(saving = false, finished = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}
