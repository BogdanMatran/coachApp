package ch.coachapp.feature.client.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.FormKind
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.feature.client.checkin.QuestionInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

data class IntakeUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val firstName: String = "",
    val questions: List<CheckinQuestion> = emptyList(),
    val answers: Map<String, String> = emptyMap(),
    val submitting: Boolean = false,
) {
    val missing: Int get() = questions.count { answers[it.id].isNullOrBlank() }
}

/** The coach's onboarding questionnaire, answered once before the client reaches the app. */
class IntakeViewModel(private val auth: AuthRepository, private val checkins: CheckinRepository) : ViewModel() {

    private val _state = MutableStateFlow(IntakeUiState())
    val state: StateFlow<IntakeUiState> = _state.asStateFlow()

    fun load() {
        val profile = auth.currentProfile ?: return
        val coachId = profile.coachId ?: return
        _state.update { it.copy(loading = true, error = null, firstName = profile.displayName.substringBefore(' ')) }
        viewModelScope.launch {
            checkins.template(coachId, FormKind.Intake)
                .onSuccess { template -> _state.update { it.copy(loading = false, questions = template?.questions.orEmpty()) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun answer(id: String, value: String) = _state.update { it.copy(answers = it.answers + (id to value), error = null) }

    fun submit() {
        val coachId = auth.currentProfile?.coachId ?: return
        val s = _state.value
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            checkins.submitIntake(coachId, s.answers.filterKeys { id -> s.questions.any { it.id == id } })
                // Reloading the profile clears the gate and swaps in the app.
                .onSuccess { auth.reloadProfile() }
                .onFailure { e -> _state.update { it.copy(submitting = false, error = e.message) } }
        }
    }
}

@Composable
fun IntakeRoute(viewModel: IntakeViewModel = koinViewModel(), auth: AuthRepository = koinInject()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val scope = rememberCoroutineScope()
    IntakeScreen(
        state = state,
        onRetry = viewModel::load,
        onAnswer = viewModel::answer,
        onSubmit = viewModel::submit,
        onSignOut = { scope.launch { auth.signOut() } },
    )
}

@Composable
fun IntakeScreen(
    state: IntakeUiState,
    onRetry: () -> Unit,
    onAnswer: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = LocalAppColors.current
    when {
        state.loading -> LoadingBox()
        state.error != null && state.questions.isEmpty() -> ErrorBox(state.error, onRetry)
        else -> Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (state.firstName.isBlank()) "Welcome" else "Welcome, ${state.firstName}",
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    "Your coach would like to know a bit about you before building your plan.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.label2,
                )
                state.questions.forEach { question ->
                    InfoCard {
                        QuestionInput(question, state.answers[question.id].orEmpty()) { onAnswer(question.id, it) }
                    }
                }
                state.error?.let { Text(it, color = colors.redText) }
                PrimaryButton(
                    "Send to my coach",
                    onClick = onSubmit,
                    enabled = state.missing == 0,
                    loading = state.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.missing > 0) {
                    Text(
                        "${state.missing} question${if (state.missing == 1) "" else "s"} left",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.label2,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
                GhostButton(
                    "Sign out",
                    onSignOut,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    size = ButtonSize.Sm,
                    dim = true,
                )
            }
        }
    }
}
