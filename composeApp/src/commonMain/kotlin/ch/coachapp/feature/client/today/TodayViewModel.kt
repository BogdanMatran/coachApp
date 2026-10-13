package ch.coachapp.feature.client.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.data.model.MealPlan
import ch.coachapp.data.model.SupplementPlan
import ch.coachapp.data.model.WorkoutDay
import ch.coachapp.data.model.WorkoutPlan
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.LogRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.domain.CheckinDue
import ch.coachapp.domain.CheckinSchedule
import kotlin.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class CelebrationMessage(val title: String, val subtitle: String?)

data class TodayUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val date: LocalDate = Dates.today(),
    val workoutPlan: WorkoutPlan? = null,
    /** Day suggested from the workout history. */
    val nextDayId: String? = null,
    /** Day the user picked; defaults to [nextDayId]. */
    val selectedDayId: String? = null,
    val mealPlan: MealPlan? = null,
    val mealsDone: Set<String> = emptySet(),
    val supplementPlan: SupplementPlan? = null,
    val supplementsTaken: Set<String> = emptySet(),
    /** Workouts logged since Monday, for the weekly ring. */
    val workoutsThisWeek: Int = 0,
    /** This week's check-in; null until loaded. */
    val checkinDue: CheckinDue? = null,
    /** A workout was logged today, so the Start workout call to action steps back. */
    val workoutDoneToday: Boolean = false,
    /** A finished goal waiting to be celebrated; cleared once the overlay has run. */
    val celebration: CelebrationMessage? = null,
    val message: String? = null,
) {
    val mealsDoneCount: Int get() = mealPlan?.meals?.count { it.id in mealsDone } ?: 0
    val supplementsTakenCount: Int get() = supplementPlan?.items?.count { it.id in supplementsTaken } ?: 0

    val selectedDay: WorkoutDay?
        get() = workoutPlan?.sortedDays?.let { days -> days.firstOrNull { it.id == selectedDayId } ?: days.firstOrNull() }
    val hasAnyPlan: Boolean get() = workoutPlan != null || mealPlan != null || supplementPlan != null
}

class TodayViewModel(
    private val auth: AuthRepository,
    private val plans: PlanRepository,
    private val logs: LogRepository,
    private val checkins: CheckinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    fun load() {
        val clientId = auth.currentProfile?.id ?: return
        val today = Dates.today()
        // Keep showing previous data while refreshing.
        _state.update { it.copy(loading = it.workoutPlan == null && it.mealPlan == null && it.supplementPlan == null, error = null, date = today) }
        viewModelScope.launch {
            val workout = async { plans.activeWorkoutPlan(clientId) }
            val meal = async { plans.activeMealPlan(clientId) }
            val supplement = async { plans.activeSupplementPlan(clientId) }
            val history = async { logs.workoutHistory(clientId, limit = 10) }
            val mealLogs = async { logs.mealLogs(clientId, today, today) }
            val supplementLogs = async { logs.supplementLogs(clientId, today, today) }
            val schedule = async { checkins.schedule(clientId, Dates.weekStart(today)) }

            val result = runCatching {
                val workoutPlan = workout.await().getOrThrow()
                val recent = history.await().getOrNull().orEmpty()
                val nextDay = workoutPlan?.let { nextDayId(it, recent.mapNotNull { log -> log.workoutDayId }) }
                val weekStart = Dates.weekStart(today).toString()
                TodayUiState(
                    loading = false,
                    date = today,
                    workoutPlan = workoutPlan,
                    nextDayId = nextDay,
                    selectedDayId = _state.value.selectedDayId?.takeIf { id -> workoutPlan?.days?.any { it.id == id } == true } ?: nextDay,
                    mealPlan = meal.await().getOrThrow(),
                    mealsDone = mealLogs.await().getOrThrow().filter { it.completed }.map { it.mealId }.toSet(),
                    supplementPlan = supplement.await().getOrThrow(),
                    supplementsTaken = supplementLogs.await().getOrThrow().filter { it.taken }.map { it.supplementItemId }.toSet(),
                    // ISO dates compare correctly as strings.
                    workoutsThisWeek = recent.count { it.performedAt.take(10) >= weekStart },
                    workoutDoneToday = recent.any { log ->
                        runCatching { Instant.parse(log.performedAt).toLocalDateTime(TimeZone.currentSystemDefault()).date == today }
                            .getOrDefault(false)
                    },
                    // Optional: a failed lookup just hides the banner.
                    checkinDue = schedule.await().getOrNull()?.let {
                        CheckinSchedule.status(today, it.checkinDay, it.submittedThisWeek, it.joinedOn)
                    },
                )
            }
            result
                .onSuccess { newState -> _state.value = newState }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load today") } }
        }
    }

    fun selectDay(dayId: String) = _state.update { it.copy(selectedDayId = dayId) }

    fun toggleMeal(mealId: String) {
        val done = mealId !in _state.value.mealsDone
        _state.update { s ->
            val updated = s.copy(mealsDone = s.mealsDone.toggle(mealId, done))
            val total = s.mealPlan?.meals?.size ?: 0
            if (done && total > 0 && updated.mealsDoneCount == total) {
                updated.copy(celebration = CelebrationMessage("All meals done", "That's every meal on your plan for today."))
            } else {
                updated
            }
        }
        viewModelScope.launch {
            logs.setMealDone(mealId, _state.value.date, done).onFailure { e ->
                _state.update { it.copy(mealsDone = it.mealsDone.toggle(mealId, !done), message = e.message) }
            }
        }
    }

    fun toggleSupplement(itemId: String) {
        val taken = itemId !in _state.value.supplementsTaken
        _state.update { s ->
            val updated = s.copy(supplementsTaken = s.supplementsTaken.toggle(itemId, taken))
            val total = s.supplementPlan?.items?.size ?: 0
            if (taken && total > 0 && updated.supplementsTakenCount == total) {
                updated.copy(celebration = CelebrationMessage("Supplements done", "Everything for today is taken."))
            } else {
                updated
            }
        }
        viewModelScope.launch {
            logs.setSupplementTaken(itemId, _state.value.date, taken).onFailure { e ->
                _state.update { it.copy(supplementsTaken = it.supplementsTaken.toggle(itemId, !taken), message = e.message) }
            }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun celebrationShown() = _state.update { it.copy(celebration = null) }

    private fun Set<String>.toggle(id: String, add: Boolean) = if (add) this + id else this - id

    companion object {
        /** The day after the most recently logged day of [plan], wrapping around; else the first day. */
        fun nextDayId(plan: WorkoutPlan, loggedDayIdsNewestFirst: List<String>): String? {
            val days = plan.sortedDays
            if (days.isEmpty()) return null
            val lastIndex = loggedDayIdsNewestFirst.firstNotNullOfOrNull { id -> days.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
                ?: return days.first().id
            return days[(lastIndex + 1) % days.size].id
        }
    }
}
