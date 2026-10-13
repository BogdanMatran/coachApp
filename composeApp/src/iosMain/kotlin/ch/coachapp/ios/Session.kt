package ch.coachapp.ios

import ch.coachapp.data.model.Role
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.AuthState
import ch.coachapp.di.appModules
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

/** Called once from the Swift app before any screen is created. */
fun setUpApp() {
    if (KoinPlatform.getKoinOrNull() == null) startKoin { modules(appModules) }
}

/** Which shell the Swift root should show. */
enum class SessionKind { Loading, SignedOut, Error, Coach, Client, JoinCoach, Intake }

data class SessionSnapshot(
    val kind: SessionKind,
    val profileId: String?,
    val firstName: String,
    val displayName: String,
    val email: String?,
    val message: String?,
)

class SessionObservation(private val onCancel: () -> Unit) {
    fun cancel() = onCancel()
}

/** Exposes the auth state to SwiftUI, delivering updates on the main thread. */
class SessionBridge : KoinComponent {
    private val auth: AuthRepository by inject()
    private val scope = MainScope()

    fun observe(onChange: (SessionSnapshot) -> Unit): SessionObservation {
        val job = scope.launch { auth.state.collect { onChange(it.toSnapshot()) } }
        return SessionObservation { job.cancel() }
    }

    fun signOut() {
        scope.launch { auth.signOut() }
    }

    fun retry() {
        scope.launch { auth.reloadProfile() }
    }
}

private fun AuthState.toSnapshot(): SessionSnapshot = when (this) {
    AuthState.Loading -> SessionSnapshot(SessionKind.Loading, null, "", "", null, null)
    AuthState.SignedOut -> SessionSnapshot(SessionKind.SignedOut, null, "", "", null, null)
    is AuthState.Error -> SessionSnapshot(SessionKind.Error, null, "", "", null, message)
    is AuthState.SignedIn -> SessionSnapshot(
        kind = when {
            profile.role == Role.Coach -> SessionKind.Coach
            profile.coachId == null -> SessionKind.JoinCoach
            intakePending -> SessionKind.Intake
            else -> SessionKind.Client
        },
        profileId = profile.id,
        firstName = profile.displayName.substringBefore(' '),
        displayName = profile.displayName,
        email = profile.email,
        message = null,
    )
}
