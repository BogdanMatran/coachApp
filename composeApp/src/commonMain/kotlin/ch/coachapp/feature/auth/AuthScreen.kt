package ch.coachapp.feature.auth

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.coachapp.core.ui.PrimaryButton
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthRoute(viewModel: AuthViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthScreen(
        state = state,
        onModeChange = viewModel::setMode,
        onRoleChange = viewModel::setRole,
        onFullNameChange = viewModel::setFullName,
        onEmailChange = viewModel::setEmail,
        onPasswordChange = viewModel::setPassword,
        onInviteCodeChange = viewModel::setInviteCode,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun AuthScreen(
    state: AuthUiState,
    onModeChange: (AuthMode) -> Unit,
    onRoleChange: (SignUpRole) -> Unit,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    // Back from the sign-up form returns to sign-in instead of leaving the app.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.mode == AuthMode.SignUp,
        onBackCompleted = { onModeChange(AuthMode.SignIn) },
    )
    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("CoachApp", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                if (state.mode == AuthMode.SignIn) "Welcome back" else "Create your account",
                style = MaterialTheme.typography.titleMedium,
            )

            if (state.mode == AuthMode.SignUp) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SignUpRole.entries.forEachIndexed { index, role ->
                        SegmentedButton(
                            selected = state.role == role,
                            onClick = { onRoleChange(role) },
                            shape = SegmentedButtonDefaults.itemShape(index, SignUpRole.entries.size),
                        ) { Text(if (role == SignUpRole.Coach) "I'm a coach" else "I have an invite") }
                    }
                }
                OutlinedTextField(
                    value = state.fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("Full name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.role == SignUpRole.Client) {
                    OutlinedTextField(
                        value = state.inviteCode,
                        onValueChange = onInviteCodeChange,
                        label = { Text("Invite code") },
                        singleLine = true,
                        isError = state.inviteChecked && state.inviteCoachName == null,
                        supportingText = {
                            when {
                                state.inviteCoachName != null -> Text("Joining ${state.inviteCoachName}")
                                state.inviteChecked -> Text("Invalid or expired code")
                                else -> Text("Ask your coach for a code")
                            }
                        },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (state.mode == AuthMode.SignUp) ({ Text("At least 6 characters") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

            PrimaryButton(
                text = if (state.mode == AuthMode.SignIn) "Sign in" else "Create account",
                onClick = onSubmit,
                enabled = state.canSubmit,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = { onModeChange(if (state.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (state.mode == AuthMode.SignIn) "New here? Create an account" else "Already have an account? Sign in")
            }
        }
    }
}
