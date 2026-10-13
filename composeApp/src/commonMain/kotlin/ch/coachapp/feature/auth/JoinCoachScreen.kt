package ch.coachapp.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.data.repository.AuthRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shown to a client account that isn't linked to a coach (e.g. the coach removed them). */
@Composable
fun JoinCoachScreen(auth: AuthRepository = koinInject()) {
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Join your coach", style = MaterialTheme.typography.headlineSmall)
            Text("Enter the invite code your coach shared with you.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppTextField(
                value = code,
                onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(12) },
                label = { Text("Invite code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            PrimaryButton(
                text = "Join",
                enabled = code.length >= 6,
                loading = loading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    loading = true
                    error = null
                    scope.launch {
                        auth.redeemInvite(code).onFailure { error = it.message }
                        loading = false
                    }
                },
            )
            GhostButton(
                "Sign out",
                onClick = { scope.launch { auth.signOut() } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = ButtonSize.Sm,
            )
        }
    }
}
