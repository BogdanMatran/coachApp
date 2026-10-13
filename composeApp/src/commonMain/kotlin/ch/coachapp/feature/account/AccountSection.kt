package ch.coachapp.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.ConfirmDialog
import ch.coachapp.core.ui.GroupedList
import ch.coachapp.core.ui.ListRow
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.rememberFileExporter
import ch.coachapp.data.model.Role
import ch.coachapp.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class AccountUiState(
    val isCoach: Boolean = false,
    val exporting: Boolean = false,
    val deleting: Boolean = false,
    /** JSON waiting to be handed to the platform's save dialog. */
    val pendingExport: String? = null,
    val message: String? = null,
)

/** Data export and account deletion, shared by the coach and client settings screens. */
class AccountViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState(isCoach = auth.currentProfile?.role == Role.Coach))
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    fun export() {
        _state.update { it.copy(exporting = true, message = null) }
        viewModelScope.launch {
            auth.exportData()
                .onSuccess { json -> _state.update { it.copy(exporting = false, pendingExport = json) } }
                .onFailure { e -> _state.update { it.copy(exporting = false, message = e.message) } }
        }
    }

    fun exportHandled(saved: Boolean) =
        _state.update { it.copy(pendingExport = null, message = if (saved) "Your data was saved" else it.message) }

    fun delete() {
        _state.update { it.copy(deleting = true, message = null) }
        viewModelScope.launch {
            // On success the session ends and the app returns to sign-in, so only failures land here.
            auth.deleteAccount().onFailure { e -> _state.update { it.copy(deleting = false, message = e.message) } }
        }
    }
}

@Composable
fun AccountSection(viewModel: AccountViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exporter = rememberFileExporter(onResult = viewModel::exportHandled)
    var confirmDelete by remember { mutableStateOf(false) }

    state.pendingExport?.let { json ->
        // Hand over once; exportHandled clears it when the dialog closes.
        LaunchedEffect(json) {
            exporter.export("coachapp-export-${Dates.today()}", "json", json.encodeToByteArray())
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Your data")
        GroupedList {
            ListRow(
                title = if (state.exporting) "Preparing export…" else "Export my data",
                subtitle = "Plans, logs and check-ins as a JSON file",
                onClick = if (state.exporting) null else viewModel::export,
            )
            ListRow(
                title = if (state.deleting) "Deleting…" else "Delete account",
                subtitle = "Permanently removes your account and data",
                danger = true,
                showDivider = true,
                onClick = if (state.deleting) null else ({ confirmDelete = true }),
            )
        }
        state.message?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.label2)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete your account?",
            text = if (state.isCoach) {
                "This permanently deletes your account, templates, library items and invites, and the plans " +
                    "you assigned to clients, including their meal and supplement logs for those plans. " +
                    "Your clients keep their accounts. This can't be undone."
            } else {
                "This permanently deletes your account, plans, logs, check-ins and progress photos. " +
                    "This can't be undone."
            },
            confirmLabel = "Delete account",
            onConfirm = viewModel::delete,
            onDismiss = { confirmDelete = false },
        )
    }
}
