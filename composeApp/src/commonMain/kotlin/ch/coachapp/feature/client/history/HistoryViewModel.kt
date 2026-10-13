package ch.coachapp.feature.client.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.data.model.DayActivity
import ch.coachapp.data.model.WorkoutLog
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.LogRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HistorySection(val label: String) { Progress("Progress"), Workouts("Workouts") }

data class HistoryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val clientId: String? = null,
    val section: HistorySection = HistorySection.Progress,
    val workouts: List<WorkoutLog> = emptyList(),
    val activity: List<DayActivity> = emptyList(),
)

class HistoryViewModel(
    private val auth: AuthRepository,
    private val logs: LogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun load() {
        val clientId = auth.currentProfile?.id ?: return
        _state.update { it.copy(clientId = clientId, loading = it.workouts.isEmpty(), error = null) }
        viewModelScope.launch {
            val workoutsResult = async { logs.workoutHistory(clientId, limit = 30) }
            val activityResult = async { logs.dailyActivity(clientId, days = 371) }
            workoutsResult.await()
                .onSuccess { w ->
                    // The heatmap is a bonus: keep the history usable if it fails to load.
                    val activity = activityResult.await().getOrNull().orEmpty()
                    _state.update { it.copy(loading = false, workouts = w, activity = activity) }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load history") } }
        }
    }

    fun selectSection(section: HistorySection) = _state.update { it.copy(section = section) }
}
