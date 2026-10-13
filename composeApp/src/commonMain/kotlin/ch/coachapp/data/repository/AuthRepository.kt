package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.core.userMessage
import ch.coachapp.data.model.Profile
import ch.coachapp.data.model.Role
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    /** [intakePending]: a client who still has to fill in their coach's onboarding form. */
    data class SignedIn(val profile: Profile, val intakePending: Boolean = false) : AuthState
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

    /** Everything the user owns, as pretty-printed JSON. */
    suspend fun exportData(): Result<String>

    /** Deletes the user's photos, then the account and every row it owns, and signs out. */
    suspend fun deleteAccount(): Result<Unit>
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
            val profile = syncTimezone(fetchProfile(userId))
            AuthState.SignedIn(profile, intakePending = profile.role == Role.Client && profile.coachId != null && intakePending())
        } catch (e: Exception) {
            AuthState.Error(e.userMessage())
        }
    }

    private suspend fun intakePending(): Boolean =
        client.postgrest.rpc("intake_pending").decodeAs<Boolean>()

    /** Keeps the server's idea of "today" in step with the device, e.g. after travelling. */
    private suspend fun syncTimezone(profile: Profile): Profile {
        val zone = TimeZone.currentSystemDefault().id
        if (profile.timezone == zone) return profile
        return runCatching {
            client.from("profiles").update(buildJsonObject { put("timezone", zone) }) { filter { eq("id", profile.id) } }
            profile.copy(timezone = zone)
        }.getOrDefault(profile) // An unrecognised zone name is not worth blocking sign-in for.
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

    override suspend fun exportData() = apiCall {
        val data = client.postgrest.rpc("export_my_data").decodeAs<JsonElement>()
        PrettyJson.encodeToString(JsonElement.serializer(), data)
    }

    override suspend fun deleteAccount() = apiCall {
        val id = currentProfile?.id ?: throw AppException("Not signed in")
        // Storage rows don't cascade with the account. The prefix keeps a coach, who can also read
        // their clients' photo rows, to their own folder.
        val paths = client.from("checkin_photos").select(Columns.list("storage_path")) {
            filter { like("storage_path", "$id/%") }
        }.decodeList<StoragePath>().map { it.storagePath }
        if (paths.isNotEmpty()) client.storage.from(CheckinPhotoBucket).delete(paths)
        client.postgrest.rpc("delete_my_account")
        signOut()
    }
}

@Serializable
private data class StoragePath(@SerialName("storage_path") val storagePath: String)

private val PrettyJson = Json { prettyPrint = true }
