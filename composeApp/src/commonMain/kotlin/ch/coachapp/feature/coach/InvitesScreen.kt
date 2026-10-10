package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ConfirmDialog
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.data.model.Invite
import ch.coachapp.data.repository.CoachRepository
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

enum class InviteStatus(val label: String) { Active("Active"), Used("Used"), Expired("Expired") }

fun Invite.status(now: Instant = Clock.System.now()): InviteStatus = when {
    usedBy != null || usedAt != null -> InviteStatus.Used
    runCatching { Instant.parse(expiresAt) < now }.getOrDefault(false) -> InviteStatus.Expired
    else -> InviteStatus.Active
}

data class InvitesUiState(
    val loading: Boolean = true,
    val invites: List<Invite> = emptyList(),
    val creating: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class InvitesViewModel(private val coach: CoachRepository) : ViewModel() {

    private val _state = MutableStateFlow(InvitesUiState())
    val state: StateFlow<InvitesUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            coach.invites()
                .onSuccess { invites -> _state.update { it.copy(loading = false, invites = invites, error = null) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun create(email: String?) {
        _state.update { it.copy(creating = true) }
        viewModelScope.launch {
            coach.createInvite(email)
                .onSuccess { invite ->
                    _state.update { it.copy(creating = false, invites = listOf(invite) + it.invites, message = "Invite ${invite.code} created") }
                }
                .onFailure { e -> _state.update { it.copy(creating = false, message = e.message) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            coach.deleteInvite(id)
                .onSuccess { _state.update { s -> s.copy(invites = s.invites.filterNot { it.id == id }) } }
                .onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@Composable
fun InvitesDestination(onBack: () -> Unit, viewModel: InvitesViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    InvitesScreen(state, onBack, viewModel::load, viewModel::create, viewModel::delete, viewModel::messageShown)
}

@Composable
fun InvitesScreen(
    state: InvitesUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCreate: (String?) -> Unit,
    onDelete: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Invite?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }

    Scaffold(
        topBar = { BackTopBar("Invites", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New invite") },
                expanded = !state.creating,
            )
        },
    ) { padding ->
        Centered(Modifier.padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error, onRetry = onRetry)
                state.invites.isEmpty() -> EmptyState(
                    "No invites yet",
                    "Clients sign up with an invite code and are linked to you automatically.",
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.invites, key = { it.id }) { invite ->
                        InviteCard(
                            invite = invite,
                            onCopy = {
                                clipboard.setText(AnnotatedString(invite.code))
                                scope.launch { snackbar.showSnackbar("Copied ${invite.code}") }
                            },
                            onDelete = { deleting = invite },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreateInviteDialog(onCreate = { onCreate(it); showCreate = false }, onDismiss = { showCreate = false })
    }
    deleting?.let { invite ->
        ConfirmDialog(
            title = "Delete invite?",
            text = "Code ${invite.code} will stop working. Clients who already joined stay linked.",
            onConfirm = { onDelete(invite.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun InviteCard(invite: Invite, onCopy: () -> Unit, onDelete: () -> Unit) {
    val status = invite.status()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(invite.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Text(
                    when (status) {
                        InviteStatus.Active -> "Active · expires ${Dates.formatTimestamp(invite.expiresAt)}"
                        InviteStatus.Used -> "Used ${Dates.formatTimestamp(invite.usedAt)}"
                        InviteStatus.Expired -> "Expired ${Dates.formatTimestamp(invite.expiresAt)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status == InviteStatus.Active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                invite.email?.let { Text("For $it", style = MaterialTheme.typography.bodySmall) }
            }
            if (status == InviteStatus.Active) {
                IconButton(onClick = onCopy) { Icon(Icons.Default.Share, contentDescription = "Copy code") }
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
        }
    }
}

@Composable
private fun CreateInviteDialog(onCreate: (String?) -> Unit, onDismiss: () -> Unit) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New invite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A code valid for 14 days. The email is only a note for you.")
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Client email (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(email.ifBlank { null }) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
