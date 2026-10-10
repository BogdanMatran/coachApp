package ch.coachapp.feature.coach

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.data.repository.AuthRepository
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
    onOpenInvites: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state, viewModel::setName, viewModel::saveName, onOpenCheckinTemplate, onOpenInvites, onSignOut)
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onNameChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onOpenCheckinTemplate: () -> Unit,
    onOpenInvites: () -> Unit,
    onSignOut: () -> Unit,
) {
    TabScaffold(title = "Settings") { padding ->
        Centered(Modifier.padding(padding)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader("Profile")
                InfoCard {
                    OutlinedTextField(
                        value = state.fullName,
                        onValueChange = onNameChange,
                        label = { Text("Display name") },
                        supportingText = { Text("Clients see this name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(state.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    PrimaryButton("Save", onClick = onSaveName, enabled = state.nameChanged, loading = state.saving)
                }

                SectionHeader("Coaching")
                Card {
                    SettingsRow("Check-in questions", "The weekly questionnaire your clients fill in", onOpenCheckinTemplate) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
                    }
                    SettingsRow("Invites", "Codes for new clients to join you", onOpenInvites) {
                        Icon(Icons.Default.Email, contentDescription = null)
                    }
                }

                OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Sign out")
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon,
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
