package ch.coachapp.feature.client.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.Pose
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckinDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val checkin: Checkin? = null,
    val previous: Checkin? = null,
    val questions: List<CheckinQuestion> = emptyList(),
    val photoUrls: Map<Pose, String> = emptyMap(),
    val previousPhotoUrls: Map<Pose, String> = emptyMap(),
)

class CheckinDetailViewModel(
    private val checkinId: String,
    private val auth: AuthRepository,
    private val checkins: CheckinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckinDetailUiState())
    val state: StateFlow<CheckinDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val profile = auth.currentProfile ?: return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val template = async { profile.coachId?.let { checkins.template(it).getOrNull() } }
            runCatching {
                val all = checkins.checkins(profile.id).getOrThrow()
                val checkin = all.firstOrNull { it.id == checkinId } ?: error("Check-in not found")
                val previous = all.firstOrNull { it.weekStart < checkin.weekStart }
                CheckinDetailUiState(
                    loading = false,
                    checkin = checkin,
                    previous = previous,
                    questions = template.await()?.questions.orEmpty(),
                    photoUrls = signedUrls(checkin),
                    previousPhotoUrls = signedUrls(previous),
                )
            }.onSuccess { _state.value = it }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load check-in") } }
        }
    }

    private suspend fun signedUrls(checkin: Checkin?): Map<Pose, String> =
        checkin?.photos.orEmpty().mapNotNull { photo ->
            checkins.photoUrl(photo.storagePath).getOrNull()?.let { photo.pose to it }
        }.toMap()
}
