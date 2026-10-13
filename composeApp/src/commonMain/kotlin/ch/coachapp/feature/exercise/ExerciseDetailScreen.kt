package ch.coachapp.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.Tag
import ch.coachapp.core.ui.underBarsInset
import ch.coachapp.data.model.SetLog
import ch.coachapp.data.model.WorkoutExercise
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.LogRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.domain.Progress
import ch.coachapp.feature.client.prescription
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The client's best set of an exercise, by estimated one-rep max. */
data class BestSet(val weight: Double, val reps: Int, val estimatedMax: Double, val date: String)

data class ExerciseDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val item: WorkoutExercise? = null,
    /** Sets from the most recent session with this exercise, in order. */
    val lastSession: List<SetLog> = emptyList(),
    val lastSessionDate: String? = null,
    val best: BestSet? = null,
)

/** How to do one exercise of the client's plan: demo, steps, the coach's prescription, past sets. */
class ExerciseDetailViewModel(
    private val workoutExerciseId: String,
    private val auth: AuthRepository,
    private val plans: PlanRepository,
    private val logs: LogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ExerciseDetailUiState())
    val state: StateFlow<ExerciseDetailUiState> = _state.asStateFlow()

    fun load() {
        val clientId = auth.currentProfile?.id ?: return
        _state.update { it.copy(loading = it.item == null, error = null) }
        viewModelScope.launch {
            val item = async { plans.workoutExercise(workoutExerciseId) }
            val history = async { logs.workoutHistory(clientId, limit = 60) }
            item.await()
                .onSuccess { we ->
                    // History is a bonus: without it the steps and the demo still help.
                    val sessions = history.await().getOrNull().orEmpty()
                        .mapNotNull { log -> log.sets.filter { it.exerciseId == we.exerciseId }.takeIf { it.isNotEmpty() }?.let { log to it } }
                    val last = sessions.firstOrNull()
                    val best = sessions.flatMap { (log, sets) -> sets.map { log to it } }
                        .mapNotNull { (log, set) ->
                            val weight = set.weight ?: return@mapNotNull null
                            val reps = set.reps?.takeIf { it in 1..30 } ?: return@mapNotNull null
                            if (weight <= 0) return@mapNotNull null
                            BestSet(weight, reps, Progress.estimatedOneRepMax(weight, reps), log.performedAt)
                        }
                        .maxByOrNull { it.estimatedMax }
                    _state.update {
                        it.copy(
                            loading = false,
                            item = we,
                            lastSession = last?.second?.sortedBy { set -> set.setNo }.orEmpty(),
                            lastSessionDate = last?.first?.performedAt,
                            best = best,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load the exercise") } }
        }
    }
}

@Composable
fun ExerciseDetailRoute(
    workoutExerciseId: String,
    onBack: () -> Unit,
    viewModel: ExerciseDetailViewModel = koinViewModel(key = "exercise-$workoutExerciseId") { parametersOf(workoutExerciseId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(workoutExerciseId) { viewModel.load() }
    ExerciseDetailScreen(state, onBack = onBack, onRetry = viewModel::load)
}

@Composable
fun ExerciseDetailScreen(state: ExerciseDetailUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    val we = state.item
    // Runs under the tab bar like the tab screens; the scroll content pads itself clear of it.
    Scaffold(
        topBar = { BackTopBar(we?.exercise?.name ?: "Exercise", onBack) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null || we == null -> ErrorBox(state.error ?: "Exercise not found", onRetry)
                else -> Centered {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + underBarsInset()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExerciseOverview(we)
                        CoachPrescription(we)
                        Instructions(we.exercise?.instructions)
                        History(state)
                    }
                }
            }
        }
    }
}

/** The demo, then what the movement trains and needs. */
@Composable
private fun ExerciseOverview(we: WorkoutExercise) {
    val exercise = we.exercise ?: return
    ExerciseDemo(exercise)
    Text(exercise.name, style = MaterialTheme.typography.headlineSmall)
    val muscles = listOfNotNull(exercise.target ?: exercise.muscleGroup) + exercise.secondaryMuscles
    if (muscles.isNotEmpty() || exercise.equipment != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            muscles.distinct().forEachIndexed { index, muscle -> Tag(muscle.replaceFirstChar { it.uppercase() }, accent = index == 0) }
            exercise.equipment?.let { Tag(it.replaceFirstChar { c -> c.uppercase() }) }
        }
    }
}

/** What the coach asked for: sets, reps, rest, tempo, effort, and any note. */
@Composable
private fun CoachPrescription(we: WorkoutExercise) {
    val colors = LocalAppColors.current
    SectionHeader("Your coach's plan")
    InfoCard {
        Text(prescription(we), style = MaterialTheme.typography.titleMedium, color = colors.accText)
        we.tempo?.takeIf { it.isNotBlank() }?.let {
            Text("Tempo $it: seconds down, pause, up, pause.", style = MaterialTheme.typography.labelMedium, color = colors.label2)
        }
        we.notes?.takeIf { it.isNotBlank() }?.let { Text("“$it”", style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Numbered steps. Library instructions are one step per line. */
@Composable
private fun Instructions(instructions: String?) {
    val colors = LocalAppColors.current
    SectionHeader("How to do it")
    val steps = instructions?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList().orEmpty()
    InfoCard {
        if (steps.isEmpty()) {
            Text("No instructions for this exercise yet. Ask your coach if you're unsure.", color = colors.label2)
        }
        steps.forEachIndexed { index, step ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(colors.accSoft), contentAlignment = Alignment.Center) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = colors.accText)
                }
                Text(step, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The last session and the best set so far, so the client knows what to aim for. */
@Composable
private fun History(state: ExerciseDetailUiState) {
    val colors = LocalAppColors.current
    SectionHeader("Your history")
    InfoCard {
        if (state.lastSession.isEmpty()) {
            Text("You haven't logged this exercise yet.", color = colors.label2)
            return@InfoCard
        }
        Text("Last time · ${Dates.formatTimestamp(state.lastSessionDate)}", style = MaterialTheme.typography.labelMedium, color = colors.label2)
        Text(
            state.lastSession.joinToString("   ") { set ->
                listOfNotNull(set.weight?.let { "${it.fmt()} kg" }, set.reps?.let { "× $it" }).joinToString(" ")
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        state.best?.let { best ->
            Text(
                "Best set · ${Dates.formatTimestamp(best.date)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.label2,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${best.weight.fmt()} kg × ${best.reps}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
                Tag("≈ ${best.estimatedMax.fmt(0)} kg 1RM", accent = true)
            }
        }
    }
}
