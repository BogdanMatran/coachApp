package ch.coachapp.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ch.coachapp.data.repository.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { SignIn, SignUp }
enum class SignUpRole { Coach, Client }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val role: SignUpRole = SignUpRole.Client,
    val fullName: String = "",
    val email: String = "",
    val password: String = "",
    val inviteCode: String = "",
    /** Coach behind a valid invite code; null while unknown or invalid. */
    val inviteCoachName: String? = null,
    val inviteChecked: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
) {
    val canSubmit: Boolean
        get() = email.isNotBlank() && password.length >= 6 && when (mode) {
            AuthMode.SignIn -> true
            AuthMode.SignUp -> fullName.isNotBlank() && (role == SignUpRole.Coach || inviteCoachName != null)
        }
}

class AuthViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    private var inviteJob: Job? = null

    fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null, info = null) }
    fun setRole(role: SignUpRole) = _state.update { it.copy(role = role, error = null) }
    fun setFullName(value: String) = _state.update { it.copy(fullName = value) }
    fun setEmail(value: String) = _state.update { it.copy(email = value) }
    fun setPassword(value: String) = _state.update { it.copy(password = value) }

    fun setInviteCode(value: String) {
        val code = value.uppercase().filter { it.isLetterOrDigit() }.take(12)
        _state.update { it.copy(inviteCode = code, inviteCoachName = null, inviteChecked = false) }
        inviteJob?.cancel()
        if (code.length < 6) return
        inviteJob = viewModelScope.launch {
            delay(400) // debounce typing
            val coach = auth.checkInvite(code).getOrNull()
            _state.update { it.copy(inviteCoachName = coach, inviteChecked = true) }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit || s.loading) return
        _state.update { it.copy(loading = true, error = null, info = null) }
        viewModelScope.launch {
            val result: Result<Boolean> = when (s.mode) {
                AuthMode.SignIn -> auth.signIn(s.email, s.password).map { false }
                AuthMode.SignUp -> when (s.role) {
                    SignUpRole.Coach -> auth.signUpCoach(s.fullName, s.email, s.password)
                    SignUpRole.Client -> auth.signUpClient(s.fullName, s.email, s.password, s.inviteCode)
                }
            }
            result
                .onSuccess { needsConfirmation ->
                    _state.update {
                        if (needsConfirmation) {
                            it.copy(loading = false, mode = AuthMode.SignIn, info = "Check your inbox to confirm your email, then sign in.")
                        } else {
                            it.copy(loading = false)
                        }
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }
}
