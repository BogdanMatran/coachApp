package ch.coachapp.feature.coach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.ProgressRing
import ch.coachapp.core.ui.StatChip
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.data.model.ClientOverview
import ch.coachapp.data.repository.CoachRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class ClientsUiState(
    val loading: Boolean = true,
    val clients: List<ClientOverview> = emptyList(),
    val query: String = "",
    val error: String? = null,
) {
    val filtered: List<ClientOverview>
        get() = if (query.isBlank()) clients else clients.filter {
            it.displayName.contains(query.trim(), ignoreCase = true) || it.email?.contains(query.trim(), ignoreCase = true) == true
        }
    val pendingTotal: Int get() = clients.sumOf { it.pendingReviews }
}

class ClientsViewModel(private val coach: CoachRepository) : ViewModel() {

    private val _state = MutableStateFlow(ClientsUiState())
    val state: StateFlow<ClientsUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.clients.isEmpty(), error = null) }
        viewModelScope.launch {
            coach.clientOverview()
                .onSuccess { clients -> _state.update { it.copy(loading = false, clients = clients) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }
}

@Composable
fun ClientsDestination(
    onOpenClient: (String) -> Unit,
    onOpenInvites: () -> Unit,
    viewModel: ClientsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    ClientsScreen(state, viewModel::setQuery, viewModel::load, onOpenClient, onOpenInvites)
}

@Composable
fun ClientsScreen(
    state: ClientsUiState,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenClient: (String) -> Unit,
    onOpenInvites: () -> Unit,
) {
    TabScaffold(
        title = "Clients",
        actions = listOf(
            BarAction("refresh", "Refresh", Icons.Default.Refresh, sfSymbol = "arrow.clockwise", onClick = onRefresh),
            BarAction("invites", "Invites", Icons.Default.Email, sfSymbol = "envelope", onClick = onOpenInvites),
        ),
        primary = BarAction("invite", "Invite client", Icons.Default.Add, sfSymbol = "person.badge.plus", onClick = onOpenInvites),
    ) { padding ->
        Centered(Modifier.padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.error != null && state.clients.isEmpty() -> ErrorBox(state.error, onRetry = onRefresh)
                state.clients.isEmpty() -> EmptyState(
                    title = "No clients yet",
                    body = "Create an invite code and share it with your first client.",
                    action = { PrimaryButton("Create invite", onClick = onOpenInvites) },
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp + bottomBarInset()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatChip("Clients", state.clients.size.toString())
                            StatChip("Check-ins to review", state.pendingTotal.toString())
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("Search clients") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    items(state.filtered, key = { it.clientId }) { client ->
                        ClientCard(client, onClick = { onOpenClient(client.clientId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientCard(client: ClientOverview, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing(
                    progress = (client.adherence7d ?: 0) / 100f,
                    color = LocalChartColors.current.meals,
                    size = 56.dp,
                    strokeWidth = 7.dp,
                    modifier = Modifier.semantics { contentDescription = "Adherence ${client.adherence7d ?: 0} percent" },
                ) {
                    Text(client.adherence7d?.let { "$it%" } ?: "–", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f)) {
                    Text(client.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    client.email?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (client.pendingReviews > 0) {
                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                        Text("${client.pendingReviews} to review", modifier = Modifier.padding(horizontal = 4.dp))
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("Last check-in", client.lastCheckin?.let { Dates.format(it) } ?: "–")
                StatChip("Last workout", Dates.formatTimestamp(client.lastWorkout))
            }
        }
    }
}
