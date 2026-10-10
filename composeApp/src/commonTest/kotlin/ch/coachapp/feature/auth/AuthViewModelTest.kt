package ch.coachapp.feature.auth

import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.AuthState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun clientSignUpNeedsAValidInvite() = runTest(dispatcher) {
        val repo = FakeAuthRepository(validCodes = mapOf("WELCOME1" to "Demo Coach"))
        val vm = AuthViewModel(repo)
        vm.setMode(AuthMode.SignUp)
        vm.setFullName("Anna")
        vm.setEmail("anna@demo.app")
        vm.setPassword("password123")

        vm.setInviteCode("nope99")
        advanceUntilIdle()
        assertTrue(vm.state.value.inviteChecked)
        assertNull(vm.state.value.inviteCoachName)
        assertFalse(vm.state.value.canSubmit)

        vm.setInviteCode("welcome1")
        advanceUntilIdle()
        assertEquals("WELCOME1", vm.state.value.inviteCode)
        assertEquals("Demo Coach", vm.state.value.inviteCoachName)
        assertTrue(vm.state.value.canSubmit)

        vm.submit()
        advanceUntilIdle()
        assertEquals(listOf("client:anna@demo.app:WELCOME1"), repo.calls)
    }

    @Test
    fun signInErrorIsShown() = runTest(dispatcher) {
        val repo = FakeAuthRepository(signInError = "Invalid login credentials")
        val vm = AuthViewModel(repo)
        vm.setEmail("coach@demo.app")
        vm.setPassword("wrongpass")
        vm.submit()
        advanceUntilIdle()
        assertEquals("Invalid login credentials", vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun confirmationRequiredSwitchesToSignIn() = runTest(dispatcher) {
        val vm = AuthViewModel(FakeAuthRepository(needsConfirmation = true))
        vm.setMode(AuthMode.SignUp)
        vm.setRole(SignUpRole.Coach)
        vm.setFullName("Coach")
        vm.setEmail("c@demo.app")
        vm.setPassword("password123")
        vm.submit()
        advanceUntilIdle()
        assertEquals(AuthMode.SignIn, vm.state.value.mode)
        assertTrue(vm.state.value.info != null)
    }
}

private class FakeAuthRepository(
    private val validCodes: Map<String, String> = emptyMap(),
    private val signInError: String? = null,
    private val needsConfirmation: Boolean = false,
) : AuthRepository {
    val calls = mutableListOf<String>()
    override val state: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)

    override suspend fun signIn(email: String, password: String): Result<Unit> =
        signInError?.let { Result.failure(Exception(it)) } ?: Result.success(Unit).also { calls += "signin:$email" }

    override suspend fun signUpCoach(fullName: String, email: String, password: String): Result<Boolean> {
        calls += "coach:$email"
        return Result.success(needsConfirmation)
    }

    override suspend fun signUpClient(fullName: String, email: String, password: String, inviteCode: String): Result<Boolean> {
        calls += "client:$email:$inviteCode"
        return Result.success(needsConfirmation)
    }

    override suspend fun checkInvite(code: String): Result<String?> = Result.success(validCodes[code])
    override suspend fun redeemInvite(code: String) = Result.success(Unit)
    override suspend fun updateName(fullName: String) = Result.success(Unit)
    override suspend fun reloadProfile() = Unit
    override suspend fun signOut() = Unit
}
