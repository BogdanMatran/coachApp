package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.AppCard
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ConfirmDialog
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.ListRow
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.SegmentedControl
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.data.model.PlanKind
import ch.coachapp.data.model.PlanSummary
import ch.coachapp.data.repository.PlanRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class PlansUiState(
    val kind: PlanKind = PlanKind.Workout,
    val loading: Boolean = true,
    val templates: Map<PlanKind, List<PlanSummary>> = emptyMap(),
    val error: String? = null,
) {
    val current: List<PlanSummary>? get() = templates[kind]
}

class PlansViewModel(private val plans: PlanRepository) : ViewModel() {

    private val _state = MutableStateFlow(PlansUiState())
    val state: StateFlow<PlansUiState> = _state.asStateFlow()

    fun load() {
        val kind = _state.value.kind
        _state.update { it.copy(loading = it.templates[kind] == null, error = null) }
        viewModelScope.launch {
            plans.templates(kind)
                .onSuccess { list -> _state.update { it.copy(loading = false, templates = it.templates + (kind to list)) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message) } }
        }
    }

    fun selectKind(kind: PlanKind) {
        _state.update { it.copy(kind = kind) }
        load()
    }

    fun delete(id: String) {
        val kind = _state.value.kind
        viewModelScope.launch {
            plans.delete(kind, id)
                .onSuccess { load() }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }
}

@Composable
fun PlansDestination(
    onOpenBuilder: (PlanKind, String?) -> Unit,
    viewModel: PlansViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    PlansScreen(state, viewModel::selectKind, viewModel::load, viewModel::delete, onOpenBuilder)
}

@Composable
fun PlansScreen(
    state: PlansUiState,
    onKindChange: (PlanKind) -> Unit,
    onRetry: () -> Unit,
    onDelete: (String) -> Unit,
    onOpenBuilder: (PlanKind, String?) -> Unit,
) {
    var deleting by remember { mutableStateOf<PlanSummary?>(null) }

    TabScaffold(
        title = "Plan templates",
        primary = BarAction(
            "new", "New ${state.kind.label.lowercase()} template", Icons.Default.Add, sfSymbol = "plus",
            onClick = { onOpenBuilder(state.kind, null) },
        ),
    ) { padding ->
        Centered(Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SegmentedControl(
                    options = PlanKind.entries,
                    selected = state.kind,
                    onSelect = { onKindChange(it) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    label = { kind -> kind.label },
                )
                val templates = state.current
                when {
                    state.loading -> LoadingBox()
                    state.error != null && templates == null -> ErrorBox(state.error, onRetry = onRetry)
                    templates.isNullOrEmpty() -> EmptyState(
                        "No ${state.kind.label.lowercase()} templates",
                        "Templates are reusable plans you can assign to any client. Each client gets their own copy.",
                    )
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp + bottomBarInset()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(templates, key = { it.id }) { plan ->
                            AppCard(Modifier.fillMaxWidth(), onClick = { onOpenBuilder(state.kind, plan.id) }) {
                                ListRow(
                                    title = plan.name,
                                    subtitle = "Updated ${Dates.formatTimestamp(plan.updatedAt)}",
                                    trailing = {
                                        IconButton(onClick = { deleting = plan }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalAppColors.current.label2)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { plan ->
        ConfirmDialog(
            title = "Delete template?",
            text = "\"${plan.name}\" will be deleted. Plans already assigned to clients are not affected.",
            onConfirm = { onDelete(plan.id) },
            onDismiss = { deleting = null },
        )
    }
}
