package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.DropdownField
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.data.model.Supplement
import ch.coachapp.data.model.SupplementTiming
import ch.coachapp.data.repository.LibraryRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.domain.SupplementItemDraft
import ch.coachapp.domain.SupplementPlanDraft
import ch.coachapp.domain.moveDown
import ch.coachapp.domain.moveUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class SupplementBuilderUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val draft: SupplementPlanDraft = SupplementPlanDraft(),
    val supplements: List<Supplement> = emptyList(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
) {
    val title: String
        get() = when {
            draft.id != null -> "Edit supplement plan"
            draft.clientId != null -> "New client supplement plan"
            else -> "New supplement template"
        }
}

class SupplementBuilderViewModel(
    private val route: SupplementBuilderRoute,
    private val plans: PlanRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SupplementBuilderUiState(draft = SupplementPlanDraft(clientId = route.clientId)))
    val state: StateFlow<SupplementBuilderUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val supplements = library.supplements()
            val draft = route.planId?.let { id -> plans.supplementPlan(id).map(SupplementPlanDraft::from) }
            val error = supplements.exceptionOrNull() ?: draft?.exceptionOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    loadError = error?.message,
                    supplements = supplements.getOrDefault(emptyList()),
                    draft = draft?.getOrNull() ?: it.draft,
                )
            }
        }
    }

    private fun edit(transform: (SupplementPlanDraft) -> SupplementPlanDraft) = _state.update { it.copy(draft = transform(it.draft), saveError = null) }

    private fun editItem(itemKey: Long, transform: (SupplementItemDraft) -> SupplementItemDraft) =
        edit { d -> d.copy(items = d.items.map { if (it.key == itemKey) transform(it) else it }) }

    fun setName(value: String) = edit { it.copy(name = value) }
    fun setNotes(value: String) = edit { it.copy(notes = value) }
    fun addItem(supplement: Supplement) = edit { it.copy(items = it.items + SupplementItemDraft(supplement = supplement)) }
    fun setDosage(itemKey: Long, dosage: String) = editItem(itemKey) { it.copy(dosage = dosage) }
    fun setTiming(itemKey: Long, timing: SupplementTiming) = editItem(itemKey) { it.copy(timing = timing) }
    fun setItemNotes(itemKey: Long, notes: String) = editItem(itemKey) { it.copy(notes = notes) }
    fun removeItem(itemKey: Long) = edit { d -> d.copy(items = d.items.filterNot { it.key == itemKey }) }
    fun moveUp(index: Int) = edit { it.copy(items = it.items.moveUp(index)) }
    fun moveDown(index: Int) = edit { it.copy(items = it.items.moveDown(index)) }

    fun save() {
        val draft = _state.value.draft
        if (draft.name.isBlank()) {
            _state.update { it.copy(saveError = "Give the plan a name") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            plans.saveSupplementPlan(draft)
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}

@Composable
fun SupplementBuilderDestination(
    route: SupplementBuilderRoute,
    onDone: () -> Unit,
    viewModel: SupplementBuilderViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    SupplementBuilderScreen(state, viewModel, onBack = onDone)
}

@Composable
fun SupplementBuilderScreen(state: SupplementBuilderUiState, actions: SupplementBuilderViewModel, onBack: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val draft = state.draft

    Scaffold(
        topBar = {
            BackTopBar(
                state.title,
                onBack,
                actions = listOf(
                    BarAction("save", if (state.saving) "Saving…" else "Save", enabled = !state.saving && !state.loading, onClick = actions::save),
                ),
            )
        },
    ) { padding ->
        Centered(Modifier.padding(padding).imePadding()) {
            when {
                state.loading -> LoadingBox()
                state.loadError != null -> ErrorBox(state.loadError, onRetry = actions::load)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            OutlinedTextField(draft.name, actions::setName, label = { Text("Plan name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(draft.notes, actions::setNotes, label = { Text("Notes for the client") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (draft.items.isEmpty()) {
                        item { EmptyState("No supplements yet", "Add items from your supplement library.") }
                    }
                    itemsIndexed(draft.items, key = { _, item -> item.key }) { index, item ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.supplement.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { actions.moveUp(index) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                    IconButton(onClick = { actions.moveDown(index) }, enabled = index < draft.items.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                    IconButton(onClick = { actions.removeItem(item.key) }) { Icon(Icons.Default.Delete, "Remove") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(item.dosage, { actions.setDosage(item.key, it) }, label = { Text("Dosage") }, singleLine = true, modifier = Modifier.weight(1f))
                                    DropdownField("Timing", item.timing, SupplementTiming.entries, { it.label }, { actions.setTiming(item.key, it) }, Modifier.weight(1f))
                                }
                                OutlinedTextField(item.notes, { actions.setItemNotes(item.key, it) }, label = { Text("Notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("Add supplement")
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        LibraryPickerDialog(
            title = "Add supplement",
            items = state.supplements,
            itemLabel = { it.name },
            itemSubtitle = { it.dosageLabel.ifEmpty { null } },
            onPick = { actions.addItem(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}
