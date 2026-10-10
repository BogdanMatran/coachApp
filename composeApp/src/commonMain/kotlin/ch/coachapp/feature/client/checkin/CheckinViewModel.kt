package ch.coachapp.feature.client.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.core.toDoubleOrNullLenient
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.MeasurementKeys
import ch.coachapp.data.model.Pose
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.CheckinSubmission
import ch.coachapp.data.repository.isReviewed
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

data class CheckinForm(
    val weight: String = "",
    val bodyFat: String = "",
    val measurements: Map<String, String> = emptyMap(),
    val answers: Map<String, String> = emptyMap(),
    /** Newly picked photos, uploaded on submit. */
    val newPhotos: Map<Pose, ByteArray> = emptyMap(),
)

data class CheckinUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val weekStart: LocalDate = Dates.weekStart(),
    val questions: List<CheckinQuestion> = emptyList(),
    /** This week's check-in if one exists. */
    val current: Checkin? = null,
    val previous: Checkin? = null,
    val photoUrls: Map<Pose, String> = emptyMap(),
    val previousPhotoUrls: Map<Pose, String> = emptyMap(),
    val form: CheckinForm = CheckinForm(),
    val submitting: Boolean = false,
    val submitError: String? = null,
    val justSubmitted: Boolean = false,
) {
    val isReviewed: Boolean get() = current?.isReviewed == true
    val canSubmit: Boolean get() = form.weight.toDoubleOrNullLenient() != null && !submitting
}

class CheckinViewModel(
    private val auth: AuthRepository,
    private val checkins: CheckinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckinUiState())
    val state: StateFlow<CheckinUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        val profile = auth.currentProfile ?: return
        val coachId = profile.coachId ?: return
        val weekStart = Dates.weekStart()
        _state.update { it.copy(loading = true, error = null, weekStart = weekStart) }
        viewModelScope.launch {
            val template = async { checkins.template(coachId) }
            val history = async { checkins.checkins(profile.id) }
            runCatching {
                val all = history.await().getOrThrow()
                val current = all.firstOrNull { it.weekStart == weekStart }
                val previous = all.firstOrNull { it.weekStart < weekStart }
                CheckinUiState(
                    loading = false,
                    weekStart = weekStart,
                    questions = template.await().getOrThrow()?.questions.orEmpty(),
                    current = current,
                    previous = previous,
                    photoUrls = signedUrls(current),
                    previousPhotoUrls = if (current?.isReviewed == true) signedUrls(previous) else emptyMap(),
                    form = current?.toForm() ?: CheckinForm(),
                )
            }.onSuccess { newState ->
                _state.value = newState.copy(justSubmitted = _state.value.justSubmitted)
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load check-in") }
            }
        }
    }

    private suspend fun signedUrls(checkin: Checkin?): Map<Pose, String> =
        checkin?.photos.orEmpty().mapNotNull { photo ->
            checkins.photoUrl(photo.storagePath).getOrNull()?.let { photo.pose to it }
        }.toMap()

    private fun Checkin.toForm() = CheckinForm(
        weight = weight?.fmt(2).orEmpty(),
        bodyFat = bodyFat?.fmt().orEmpty(),
        measurements = measurements.mapValues { it.value.fmt() },
        answers = answers,
    )

    private fun updateForm(transform: (CheckinForm) -> CheckinForm) =
        _state.update { it.copy(form = transform(it.form), justSubmitted = false, submitError = null) }

    fun setWeight(value: String) = updateForm { it.copy(weight = value) }
    fun setBodyFat(value: String) = updateForm { it.copy(bodyFat = value) }
    fun setMeasurement(key: String, value: String) = updateForm { it.copy(measurements = it.measurements + (key to value)) }
    fun setAnswer(questionId: String, value: String) = updateForm { it.copy(answers = it.answers + (questionId to value)) }
    fun setPhoto(pose: Pose, bytes: ByteArray) = updateForm { it.copy(newPhotos = it.newPhotos + (pose to bytes)) }
    fun removeNewPhoto(pose: Pose) = updateForm { it.copy(newPhotos = it.newPhotos - pose) }

    fun submit() {
        val s = _state.value
        val coachId = auth.currentProfile?.coachId ?: return
        if (!s.canSubmit) return
        val form = s.form
        val submission = CheckinSubmission(
            weekStart = s.weekStart,
            weight = form.weight.toDoubleOrNullLenient(),
            bodyFat = form.bodyFat.toDoubleOrNullLenient(),
            measurements = MeasurementKeys.mapNotNull { key -> form.measurements[key]?.toDoubleOrNullLenient()?.let { key to it } }.toMap(),
            answers = form.answers.filterValues { it.isNotBlank() },
            photos = form.newPhotos,
        )
        _state.update { it.copy(submitting = true, submitError = null) }
        viewModelScope.launch {
            checkins.submit(coachId, submission)
                .onSuccess { saved ->
                    val urls = signedUrls(saved)
                    _state.update {
                        it.copy(
                            submitting = false,
                            justSubmitted = true,
                            current = saved,
                            photoUrls = urls,
                            form = it.form.copy(newPhotos = emptyMap()),
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(submitting = false, submitError = e.message) } }
        }
    }
}
