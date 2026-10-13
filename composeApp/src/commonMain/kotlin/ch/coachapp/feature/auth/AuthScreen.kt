package ch.coachapp.feature.auth

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.SegmentedControl
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
    // Which field has focus decides what the lifter does: watch the email, cover their eyes for
    // the password, press the bar overhead while signing in.
    var focused by remember { mutableStateOf<AuthField?>(null) }
    val pose = when {
        state.loading -> LifterPose.Lifting
        focused == AuthField.Password -> LifterPose.Covering
        focused != null -> LifterPose.Watching
        else -> LifterPose.Idle
    }
    val watchedText = when (focused) {
        AuthField.Name -> state.fullName
        AuthField.Invite -> state.inviteCode
        else -> state.email
    }
    // Smaller while typing, so it stays in view above the keyboard.
    val mascotSize by animateDpAsState(if (focused != null) 150.dp else 200.dp, label = "mascotSize")
    fun Modifier.tracks(field: AuthField) = onFocusChanged { focus ->
        if (focus.isFocused) focused = field else if (focused == field) focused = null
    }

    Box(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                LifterMascot(
                    pose = pose,
                    look = (watchedText.length / 28f).coerceIn(0f, 1f),
                    shakeKey = state.error,
                    modifier = Modifier.size(mascotSize),
                )
                Text(
                    "CoachApp",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = LocalAppColors.current.accText,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Your plan, your progress and your coach, in one place.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalAppColors.current.label2,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                if (state.mode == AuthMode.SignIn) "Welcome back" else "Create your account",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (state.mode == AuthMode.SignUp) {
                SegmentedControl(
                    options = SignUpRole.entries,
                    selected = state.role,
                    onSelect = { onRoleChange(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { role -> if (role == SignUpRole.Coach) "I'm a coach" else "I have an invite" },
                )
                AppTextField(
                    value = state.fullName,
                    onValueChange = onFullNameChange,
                    label = { Text("Full name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().tracks(AuthField.Name),
                )
                if (state.role == SignUpRole.Client) {
                    AppTextField(
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
                        modifier = Modifier.fillMaxWidth().tracks(AuthField.Invite),
                    )
                }
            }

            AppTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().tracks(AuthField.Email),
            )
            AppTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (state.mode == AuthMode.SignUp) ({ Text("At least 6 characters") }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().tracks(AuthField.Password),
            )

            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.info?.let { Text(it, color = LocalAppColors.current.accText) }

            PrimaryButton(
                text = if (state.mode == AuthMode.SignIn) "Sign in" else "Create account",
                onClick = onSubmit,
                enabled = state.canSubmit,
                loading = state.loading,
                modifier = Modifier.fillMaxWidth(),
            )
            GhostButton(
                if (state.mode == AuthMode.SignIn) "New here? Create an account" else "Already have an account? Sign in",
                onClick = { onModeChange(if (state.mode == AuthMode.SignIn) AuthMode.SignUp else AuthMode.SignIn) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = ButtonSize.Sm,
            )
        }
    }
}

private enum class AuthField { Name, Invite, Email, Password }
