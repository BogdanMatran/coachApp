package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.AppearanceCard
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.DangerButton
import ch.coachapp.core.ui.GroupedList
import ch.coachapp.core.ui.IconTile
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.ListRow
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.feature.account.AccountSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class SettingsUiState(
    val fullName: String = "",
    val savedName: String = "",
    val email: String = "",
    val saving: Boolean = false,
    val message: String? = null,
) {
    val nameChanged: Boolean get() = fullName.trim() != savedName && fullName.isNotBlank()
}

class SettingsViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(
        auth.currentProfile.let { SettingsUiState(it?.fullName.orEmpty(), it?.fullName.orEmpty(), it?.email.orEmpty()) },
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setName(name: String) = _state.update { it.copy(fullName = name, message = null) }

    fun saveName() {
        val name = _state.value.fullName.trim()
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            auth.updateName(name)
                .onSuccess { _state.update { it.copy(saving = false, savedName = name, message = "Name updated") } }
                .onFailure { e -> _state.update { it.copy(saving = false, message = e.message) } }
        }
    }
}

@Composable
fun SettingsDestination(
    onOpenCheckinTemplate: () -> Unit,
    onOpenIntakeTemplate: () -> Unit,
    onOpenInvites: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, viewModel::setName, viewModel::saveName, onOpenCheckinTemplate, onOpenIntakeTemplate, onOpenInvites, onSignOut)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onOpenCheckinTemplate: () -> Unit,
    onOpenIntakeTemplate: () -> Unit,
    onOpenInvites: () -> Unit,
    onSignOut: () -> Unit,
) {
    TabScaffold(title = "Settings") { padding ->
        Centered(Modifier.padding(padding)) {
            Column(
                Modifier.verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + bottomBarInset()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader("Profile")
                InfoCard {
                    AppTextField(
                        value = state.fullName,
                        onValueChange = onNameChange,
                        label = { Text("Display name") },
                        supportingText = { Text("Clients see this name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(state.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.message?.let { Text(it, color = LocalAppColors.current.accText) }
                    PrimaryButton("Save", onClick = onSaveName, enabled = state.nameChanged, loading = state.saving)
                }

                SectionHeader("Appearance")
                AppearanceCard()

                SectionHeader("Coaching")
                GroupedList {
                    SettingsRow(
                        "Check-in questions",
                        "The weekly questionnaire your clients fill in",
                        Icons.AutoMirrored.Filled.List,
                        onOpenCheckinTemplate,
                    )
                    SettingsRow(
                        "Onboarding form",
                        "Questions new clients answer once after joining",
                        Icons.Default.Person,
                        onOpenIntakeTemplate,
                        showDivider = true,
                    )
                    SettingsRow(
                        "Invites",
                        "Codes for new clients to join you",
                        Icons.Default.Email,
                        onOpenInvites,
                        showDivider = true,
                    )
                }

                AccountSection()

                DangerButton(
                    "Sign out",
                    onSignOut,
                    modifier = Modifier.padding(top = 24.dp),
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    showDivider: Boolean = false,
) {
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { IconTile(icon) },
        // Past the icon rail, so the two rows read as one object.
        showDivider = showDivider,
        dividerInset = 55.dp,
        trailing = {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = LocalAppColors.current.label3,
            )
        },
        onClick = onClick,
    )
}
