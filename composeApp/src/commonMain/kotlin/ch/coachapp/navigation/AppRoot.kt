package ch.coachapp.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.data.model.Role
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.AuthState
import ch.coachapp.feature.auth.AuthRoute
import ch.coachapp.feature.auth.JoinCoachScreen
import ch.coachapp.feature.client.ClientApp
import ch.coachapp.feature.client.intake.IntakeRoute
import ch.coachapp.feature.coach.CoachApp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Picks the navigation graph for the signed-in user's role. */
@Composable
fun AppRoot(auth: AuthRepository = koinInject()) {
    val state by auth.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (val s = state) {
            AuthState.Loading -> LoadingBox()
            AuthState.SignedOut -> AuthRoute()
            is AuthState.Error -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                ErrorBox(s.message, onRetry = { scope.launch { auth.reloadProfile() } }, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { auth.signOut() } }) { Text("Sign out") }
            }
            is AuthState.SignedIn -> key(s.profile.id) {
                val onSignOut: () -> Unit = { scope.launch { auth.signOut() } }
                when {
                    s.profile.role == Role.Coach -> CoachApp(profile = s.profile, onSignOut = onSignOut)
                    s.profile.coachId == null -> JoinCoachScreen()
                    s.intakePending -> IntakeRoute()
                    else -> ClientApp(profile = s.profile, onSignOut = onSignOut)
                }
            }
        }
    }
}
