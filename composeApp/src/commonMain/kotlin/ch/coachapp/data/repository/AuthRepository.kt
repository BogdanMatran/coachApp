package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.core.userMessage
import ch.coachapp.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val profile: Profile) : AuthState
    data class Error(val message: String) : AuthState
}

interface AuthRepository {
    val state: StateFlow<AuthState>
    val currentProfile: Profile? get() = (state.value as? AuthState.SignedIn)?.profile

    suspend fun signIn(email: String, password: String): Result<Unit>

    /** Returns true when the account still needs email confirmation before sign-in. */
    suspend fun signUpCoach(fullName: String, email: String, password: String): Result<Boolean>
    suspend fun signUpClient(fullName: String, email: String, password: String, inviteCode: String): Result<Boolean>

    /** Coach name for a valid, unused invite code, or null. */
    suspend fun checkInvite(code: String): Result<String?>
    suspend fun redeemInvite(code: String): Result<Unit>
    suspend fun updateName(fullName: String): Result<Unit>
    suspend fun reloadProfile()
    suspend fun signOut()
}

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    scope: CoroutineScope,
) : AuthRepository {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    override val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        scope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        val userId = status.session.user?.id ?: return@collect
                        // Token refreshes re-emit Authenticated; only reload when the user changes.
                        if ((_state.value as? AuthState.SignedIn)?.profile?.id != userId) loadProfile(userId)
                    }
                    is SessionStatus.NotAuthenticated -> _state.value = AuthState.SignedOut
                    SessionStatus.Initializing -> _state.value = AuthState.Loading
                    is SessionStatus.RefreshFailure -> if (_state.value !is AuthState.SignedIn) _state.value = AuthState.SignedOut
                }
            }
        }
    }

    private suspend fun loadProfile(userId: String) {
        _state.value = try {
            AuthState.SignedIn(fetchProfile(userId))
        } catch (e: Exception) {
            AuthState.Error(e.userMessage())
        }
    }

    private suspend fun fetchProfile(userId: String): Profile =
        client.from("profiles").select { filter { eq("id", userId) } }.decodeSingle<Profile>()

    override suspend fun signIn(email: String, password: String) = apiCall {
        client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    override suspend fun signUpCoach(fullName: String, email: String, password: String) =
        signUp(fullName, email, password, role = "coach", inviteCode = null)

    override suspend fun signUpClient(fullName: String, email: String, password: String, inviteCode: String) = apiCall {
        if (checkInvite(inviteCode).getOrThrow() == null) throw AppException("Invite code is invalid or expired")
        signUp(fullName, email, password, role = "client", inviteCode = inviteCode).getOrThrow()
    }

    private suspend fun signUp(fullName: String, email: String, password: String, role: String, inviteCode: String?) = apiCall {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("full_name", fullName.trim())
                put("role", role)
                if (inviteCode != null) put("invite_code", inviteCode.trim().uppercase())
            }
        }
        client.auth.currentSessionOrNull() == null
    }

    override suspend fun checkInvite(code: String) = apiCall {
        client.postgrest.rpc("check_invite", buildJsonObject { put("p_code", code.trim()) })
            .decodeAs<String?>()
    }

    override suspend fun redeemInvite(code: String) = apiCall {
        client.postgrest.rpc("redeem_invite", buildJsonObject { put("p_code", code.trim()) })
        reloadProfile()
    }

    override suspend fun updateName(fullName: String) = apiCall {
        val id = currentProfile?.id ?: throw AppException("Not signed in")
        client.from("profiles").update(buildJsonObject { put("full_name", fullName.trim()) }) { filter { eq("id", id) } }
        reloadProfile()
    }

    override suspend fun reloadProfile() {
        client.auth.currentUserOrNull()?.id?.let { loadProfile(it) }
    }

    override suspend fun signOut() {
        runCatching { client.auth.signOut() }
        _state.value = AuthState.SignedOut
    }
}
