package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.CheckinAnswers
import ch.coachapp.core.ui.CheckinMetrics
import ch.coachapp.core.ui.CheckinPhotos
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.weekLabel
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.Pose
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.CoachRepository
import ch.coachapp.data.repository.isReviewed
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class CheckinReviewUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val clientName: String = "",
    val checkin: Checkin? = null,
    val previous: Checkin? = null,
    val questions: List<CheckinQuestion> = emptyList(),
    val photoUrls: Map<Pose, String> = emptyMap(),
    val previousPhotoUrls: Map<Pose, String> = emptyMap(),
    val feedback: String = "",
    val saving: Boolean = false,
    val message: String? = null,
)

class CheckinReviewViewModel(
    private val route: CheckinReviewRoute,
    private val checkins: CheckinRepository,
    private val coach: CoachRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckinReviewUiState())
    val state: StateFlow<CheckinReviewUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val checkin = checkins.checkin(route.checkinId).getOrElse { e ->
                _state.update { it.copy(loading = false, error = e.message) }
                return@launch
            }
            val client = async { coach.client(checkin.clientId) }
            val history = async { checkins.checkins(checkin.clientId) }
            val template = async { checkins.template(checkin.coachId) }
            // History is sorted newest first, so the first older week is the previous check-in.
            val previous = history.await().getOrNull()?.firstOrNull { it.weekStart < checkin.weekStart }
            val urls = async { signedUrls(checkin) }
            val previousUrls = async { previous?.let { signedUrls(it) }.orEmpty() }

            _state.update {
                it.copy(
                    loading = false,
                    clientName = client.await().getOrNull()?.displayName.orEmpty(),
                    checkin = checkin,
                    previous = previous,
                    questions = template.await().getOrNull()?.questions.orEmpty(),
                    photoUrls = urls.await(),
                    previousPhotoUrls = previousUrls.await(),
                    feedback = checkin.coachFeedback.orEmpty(),
                )
            }
        }
    }

    private suspend fun signedUrls(checkin: Checkin): Map<Pose, String> = coroutineScope {
        checkin.photos
            .map { photo -> async { checkins.photoUrl(photo.storagePath).getOrNull()?.let { photo.pose to it } } }
            .awaitAll()
            .filterNotNull()
            .toMap()
    }

    fun setFeedback(value: String) = _state.update { it.copy(feedback = value, message = null) }

    fun submitReview() {
        val checkin = _state.value.checkin ?: return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            checkins.review(checkin.id, _state.value.feedback)
                .onSuccess {
                    val updated = checkins.checkin(checkin.id).getOrNull()
                    _state.update { it.copy(saving = false, checkin = updated ?: it.checkin, message = "Review saved") }
                }
                .onFailure { e -> _state.update { it.copy(saving = false, message = e.message) } }
        }
    }
}

@Composable
fun CheckinReviewDestination(
    route: CheckinReviewRoute,
    onBack: () -> Unit,
    viewModel: CheckinReviewViewModel = koinViewModel(key = route.checkinId) { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CheckinReviewScreen(state, onBack, viewModel::load, viewModel::setFeedback, viewModel::submitReview)
}

@Composable
fun CheckinReviewScreen(
    state: CheckinReviewUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onFeedbackChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val checkin = state.checkin
    Scaffold(
        topBar = { BackTopBar(listOf(state.clientName, checkin?.weekLabel()).filter { !it.isNullOrBlank() }.joinToString(" · ").ifEmpty { "Check-in" }, onBack) },
    ) { padding ->
        Centered(Modifier.padding(padding).imePadding()) {
            when {
                state.loading -> LoadingBox()
                checkin == null -> ErrorBox(state.error ?: "Check-in not found", onRetry = onRetry)
                else -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Submitted ${Dates.formatTimestamp(checkin.submittedAt)}" +
                            (if (checkin.isReviewed) " · reviewed ${Dates.formatTimestamp(checkin.reviewedAt)}" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SectionHeader(if (state.previous != null) "Metrics (vs. ${state.previous.weekLabel().lowercase()})" else "Metrics")
                    CheckinMetrics(checkin, state.previous)

                    SectionHeader("Answers")
                    InfoCard { CheckinAnswers(checkin, state.questions) }

                    SectionHeader("Progress photos")
                    CheckinPhotos(
                        urls = state.photoUrls,
                        previousUrls = state.previousPhotoUrls,
                        previousLabel = state.previous?.let { Dates.format(it.weekStart) },
                    )

                    SectionHeader("Your feedback")
                    AppTextField(
                        value = state.feedback,
                        onValueChange = onFeedbackChange,
                        placeholder = { Text("What went well, what to adjust next week…") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    state.message?.let { Text(it, color = LocalAppColors.current.accText, fontWeight = FontWeight.Medium) }
                    PrimaryButton(
                        text = if (checkin.isReviewed) "Update feedback" else "Mark reviewed",
                        onClick = onSubmit,
                        enabled = state.feedback.isNotBlank(),
                        loading = state.saving,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    )
                }
            }
        }
    }
}
