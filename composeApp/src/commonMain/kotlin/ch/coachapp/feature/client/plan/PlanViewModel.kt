package ch.coachapp.feature.client.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.data.model.MealPlan
import ch.coachapp.data.model.SupplementPlan
import ch.coachapp.data.model.WorkoutPlan
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.PlanRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlanSection(val label: String) { Workout("Workout"), Meals("Meals"), Supplements("Supplements") }

data class PlanUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val section: PlanSection = PlanSection.Workout,
    val workout: WorkoutPlan? = null,
    val meal: MealPlan? = null,
    val supplement: SupplementPlan? = null,
)

class PlanViewModel(
    private val auth: AuthRepository,
    private val plans: PlanRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val clientId = auth.currentProfile?.id ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val workout = async { plans.activeWorkoutPlan(clientId) }
            val meal = async { plans.activeMealPlan(clientId) }
            val supplement = async { plans.activeSupplementPlan(clientId) }
            runCatching {
                Triple(workout.await().getOrThrow(), meal.await().getOrThrow(), supplement.await().getOrThrow())
            }.onSuccess { (w, m, s) ->
                _state.update { it.copy(loading = false, workout = w, meal = m, supplement = s) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load your plan") }
            }
        }
    }

    fun selectSection(section: PlanSection) = _state.update { it.copy(section = section) }
}
