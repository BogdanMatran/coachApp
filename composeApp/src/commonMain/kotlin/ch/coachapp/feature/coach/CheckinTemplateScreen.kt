package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ch.coachapp.core.ui.PrimaryFab
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.QuestionType
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.domain.moveDown
import ch.coachapp.domain.moveUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

data class CheckinTemplateUiState(
    val loading: Boolean = true,
    val questions: List<CheckinQuestion> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val loadError: String? = null,
    val message: String? = null,
)

/** Answers are stored by question id, so ids stay fixed when a label is edited later. */
fun questionId(label: String, existing: Collection<String>): String {
    val base = label.lowercase().map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        .split('_').filter { it.isNotEmpty() }.joinToString("_").take(32).ifEmpty { "question" }
    var id = base
    var n = 2
    while (id in existing) id = "${base}_${n++}"
    return id
}

class CheckinTemplateViewModel(
    private val auth: AuthRepository,
    private val checkins: CheckinRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckinTemplateUiState())
    val state: StateFlow<CheckinTemplateUiState> = _state.asStateFlow()

    fun load() {
        val coachId = auth.currentProfile?.id ?: return
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            checkins.template(coachId)
                .onSuccess { template -> _state.update { it.copy(loading = false, questions = template?.questions.orEmpty(), dirty = false) } }
                .onFailure { e -> _state.update { it.copy(loading = false, loadError = e.message) } }
        }
    }

    private fun edit(transform: (List<CheckinQuestion>) -> List<CheckinQuestion>) =
        _state.update { it.copy(questions = transform(it.questions), dirty = true, message = null) }

    fun add(label: String, type: QuestionType) = edit { list ->
        list + CheckinQuestion(questionId(label, list.map { it.id }), label.trim(), type)
    }

    fun update(id: String, label: String, type: QuestionType) = edit { list ->
        list.map { if (it.id == id) it.copy(label = label.trim(), type = type) else it }
    }

    fun remove(id: String) = edit { list -> list.filterNot { it.id == id } }
    fun moveUp(index: Int) = edit { it.moveUp(index) }
    fun moveDown(index: Int) = edit { it.moveDown(index) }

    fun save() {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            checkins.saveTemplate(_state.value.questions)
                .onSuccess { _state.update { it.copy(saving = false, dirty = false, message = "Saved") } }
                .onFailure { e -> _state.update { it.copy(saving = false, message = e.message) } }
        }
    }
}

@Composable
fun CheckinTemplateDestination(onBack: () -> Unit, viewModel: CheckinTemplateViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    CheckinTemplateScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onAdd = viewModel::add,
        onUpdate = viewModel::update,
        onRemove = viewModel::remove,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onSave = viewModel::save,
    )
}

@Composable
fun CheckinTemplateScreen(
    state: CheckinTemplateUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (String, QuestionType) -> Unit,
    onUpdate: (String, String, QuestionType) -> Unit,
    onRemove: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onSave: () -> Unit,
) {
    // null = closed, a question with blank id = adding a new one
    var editing by remember { mutableStateOf<CheckinQuestion?>(null) }
    val addQuestion = BarAction(
        "add", "Add question", Icons.Default.Add, sfSymbol = "plus",
        onClick = { editing = CheckinQuestion(id = "", label = "", type = QuestionType.Scale) },
    )

    Scaffold(
        topBar = {
            BackTopBar(
                "Check-in questions",
                onBack,
                actions = listOf(
                    BarAction("save", if (state.saving) "Saving…" else "Save", enabled = state.dirty && !state.saving, onClick = onSave),
                ),
                primary = addQuestion,
            )
        },
        floatingActionButton = { PrimaryFab(addQuestion) },
    ) { padding ->
        Centered(Modifier.padding(padding)) {
            when {
                state.loading -> LoadingBox()
                state.loadError != null -> ErrorBox(state.loadError, onRetry = onRetry)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Text(
                            "Clients answer these every week, next to weight, measurements and photos.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp)) }
                    }
                    if (state.questions.isEmpty()) {
                        item { EmptyState("No questions yet", "Add questions like energy, sleep or adherence.") }
                    }
                    itemsIndexed(state.questions, key = { _, q -> q.id }) { index, question ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(question.label, style = MaterialTheme.typography.bodyLarge)
                                    Text(question.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onMoveUp(index) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                                IconButton(onClick = { onMoveDown(index) }, enabled = index < state.questions.lastIndex) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                                IconButton(onClick = { editing = question }) { Icon(Icons.Default.Edit, "Edit") }
                                IconButton(onClick = { onRemove(question.id) }) { Icon(Icons.Default.Delete, "Delete") }
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { question ->
        QuestionDialog(
            initial = question,
            onSave = { label, type ->
                if (question.id.isBlank()) onAdd(label, type) else onUpdate(question.id, label, type)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun QuestionDialog(initial: CheckinQuestion, onSave: (String, QuestionType) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf(initial.label) }
    var type by remember { mutableStateOf(initial.type) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id.isBlank()) "New question" else "Edit question") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Question") }, modifier = Modifier.fillMaxWidth())
                DropdownField("Answer type", type, QuestionType.entries, { it.label }, { type = it }, Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { onSave(label, type) }, enabled = label.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
