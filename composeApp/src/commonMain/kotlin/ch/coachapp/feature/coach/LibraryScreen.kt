package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.Chip
import ch.coachapp.core.ui.ChipRow
import ch.coachapp.core.ui.ConfirmDialog
import ch.coachapp.core.ui.DangerButton
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.ListRow
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.SegmentedControl
import ch.coachapp.core.ui.Tag
import ch.coachapp.core.ui.TintedButton
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.core.ui.clearFocusOnTap
import ch.coachapp.core.ui.groupedRow
import ch.coachapp.core.ui.highlightClickable
import ch.coachapp.data.model.Exercise
import ch.coachapp.data.model.Food
import ch.coachapp.data.model.Supplement
import ch.coachapp.data.repository.ExerciseFacets
import ch.coachapp.data.repository.ExerciseFilter
import ch.coachapp.data.repository.LibraryRepository
import ch.coachapp.feature.exercise.ExerciseDemo
import ch.coachapp.feature.exercise.ExerciseThumbnail
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.extension
import io.github.vinceglb.filekit.readBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

enum class LibraryTab(val label: String) { Exercises("Exercises"), Foods("Foods"), Supplements("Supplements") }

/** Matches the exercise-videos bucket's file size limit. */
private const val MaxVideoBytes = 50 * 1024 * 1024

/** Rows per request. The imported library is far too large to hold in one list. */
private const val ExercisePageSize = 60

/** Item currently open in the edit dialog; a blank id means "new". */
sealed interface LibraryEdit {
    data class EditExercise(val exercise: Exercise) : LibraryEdit
    data class EditFood(val food: Food) : LibraryEdit
    data class EditSupplement(val supplement: Supplement) : LibraryEdit
}

data class LibraryUiState(
    val tab: LibraryTab = LibraryTab.Exercises,
    val query: String = "",
    val loading: Boolean = true,
    val exercises: List<Exercise> = emptyList(),
    val facets: ExerciseFacets = ExerciseFacets(),
    val bodyPart: String? = null,
    val equipment: String? = null,
    /** False once the server returns a short page, so the list stops asking for more. */
    val moreExercises: Boolean = true,
    val loadingMore: Boolean = false,
    val foods: List<Food> = emptyList(),
    val supplements: List<Supplement> = emptyList(),
    val editing: LibraryEdit? = null,
    /** An exercise opened to see its demo and instructions, and to set the coach's own video. */
    val viewing: Exercise? = null,
    val videoBusy: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val message: String? = null,
)

class LibraryViewModel(private val library: LibraryRepository) : ViewModel() {

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Reloading the exercise page cancels the in-flight one, so fast typing cannot interleave. */
    private var exerciseJob: Job? = null

    fun load() {
        viewModelScope.launch {
            val foods = async { library.foods() }
            val supplements = async { library.supplements() }
            val facets = async { library.exerciseFacets() }
            val results = listOf(foods.await(), supplements.await(), facets.await())
            val error = results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            _state.update {
                it.copy(
                    loading = false,
                    foods = foods.await().getOrDefault(it.foods),
                    supplements = supplements.await().getOrDefault(it.supplements),
                    facets = facets.await().getOrDefault(it.facets),
                    error = error,
                )
            }
        }
        loadExercises()
    }

    private fun filter() = _state.value.let { ExerciseFilter(it.query, it.bodyPart, it.equipment) }

    private fun loadExercises() {
        exerciseJob?.cancel()
        exerciseJob = viewModelScope.launch {
            library.exercises(filter(), limit = ExercisePageSize)
                .onSuccess { page ->
                    _state.update {
                        it.copy(exercises = page, moreExercises = page.size == ExercisePageSize, error = null)
                    }
                }
                .onFailure { e -> _state.update { it.copy(error = e.message) } }
        }
    }

    fun loadMoreExercises() {
        val current = _state.value
        if (current.loadingMore || !current.moreExercises || current.tab != LibraryTab.Exercises) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            library.exercises(filter(), limit = ExercisePageSize, offset = current.exercises.size)
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            exercises = it.exercises + page,
                            moreExercises = page.size == ExercisePageSize,
                            loadingMore = false,
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loadingMore = false, message = e.message) } }
        }
    }

    fun selectTab(tab: LibraryTab) = _state.update { it.copy(tab = tab) }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
        if (_state.value.tab == LibraryTab.Exercises) loadExercises()
    }

    fun setBodyPart(value: String?) {
        _state.update { it.copy(bodyPart = value) }
        loadExercises()
    }

    fun setEquipment(value: String?) {
        _state.update { it.copy(equipment = value) }
        loadExercises()
    }

    fun startNew() = _state.update {
        it.copy(
            editing = when (it.tab) {
                LibraryTab.Exercises -> LibraryEdit.EditExercise(Exercise())
                LibraryTab.Foods -> LibraryEdit.EditFood(Food())
                LibraryTab.Supplements -> LibraryEdit.EditSupplement(Supplement())
            },
        )
    }

    fun edit(edit: LibraryEdit) = _state.update { it.copy(editing = edit) }
    fun dismissEdit() = _state.update { it.copy(editing = null) }

    /** Opening an exercise fetches the full row, for its instructions and the coach's video. */
    fun view(exercise: Exercise) {
        _state.update { it.copy(viewing = exercise) }
        viewModelScope.launch {
            library.exercise(exercise.id).onSuccess { full ->
                _state.update { if (it.viewing?.id == full.id) it.copy(viewing = full) else it }
            }
        }
    }

    fun dismissView() = _state.update { it.copy(viewing = null) }

    fun uploadVideo(bytes: ByteArray, extension: String) = changeVideo("Video uploaded") { id ->
        if (bytes.size > MaxVideoBytes) Result.failure(IllegalArgumentException("Videos can be up to 50 MB."))
        else library.uploadExerciseVideo(id, bytes, extension)
    }

    fun linkVideo(url: String) = changeVideo("Video linked") { id -> library.linkExerciseVideo(id, url) }

    fun removeVideo() = changeVideo("Video removed") { id -> library.removeExerciseVideo(id) }

    private fun changeVideo(done: String, change: suspend (exerciseId: String) -> Result<Unit>) {
        val exercise = _state.value.viewing ?: return
        _state.update { it.copy(videoBusy = true) }
        viewModelScope.launch {
            change(exercise.id)
                .onSuccess {
                    val fresh = library.exercise(exercise.id).getOrDefault(exercise)
                    _state.update { s ->
                        s.copy(
                            videoBusy = false,
                            message = done,
                            viewing = fresh.takeIf { s.viewing?.id == it.id } ?: s.viewing,
                            exercises = s.exercises.map { if (it.id == fresh.id) fresh else it },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(videoBusy = false, message = e.message) } }
        }
    }

    fun save(edit: LibraryEdit) {
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = when (edit) {
                is LibraryEdit.EditExercise -> library.saveExercise(edit.exercise)
                is LibraryEdit.EditFood -> library.saveFood(edit.food)
                is LibraryEdit.EditSupplement -> library.saveSupplement(edit.supplement)
            }
            result
                .onSuccess {
                    _state.update { it.copy(saving = false, editing = null, message = "Saved") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(saving = false, message = e.message) } }
        }
    }

    fun delete(tab: LibraryTab, id: String) {
        viewModelScope.launch {
            val result = when (tab) {
                LibraryTab.Exercises -> library.deleteExercise(id)
                LibraryTab.Foods -> library.deleteFood(id)
                LibraryTab.Supplements -> library.deleteSupplement(id)
            }
            result.onSuccess { load() }.onFailure { e -> _state.update { it.copy(message = e.message) } }
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

@Composable
fun LibraryDestination(viewModel: LibraryViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    LibraryScreen(
        state = state,
        onTabChange = viewModel::selectTab,
        onQueryChange = viewModel::setQuery,
        onRetry = viewModel::load,
        onNew = viewModel::startNew,
        onEdit = viewModel::edit,
        onDismissEdit = viewModel::dismissEdit,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onMessageShown = viewModel::messageShown,
        onBodyPartChange = viewModel::setBodyPart,
        onEquipmentChange = viewModel::setEquipment,
        onLoadMore = viewModel::loadMoreExercises,
        onView = viewModel::view,
        onDismissView = viewModel::dismissView,
        onUploadVideo = viewModel::uploadVideo,
        onLinkVideo = viewModel::linkVideo,
        onRemoveVideo = viewModel::removeVideo,
    )
}

/** A library row reduced to what the list needs. */
private data class LibraryRow(
    val id: String,
    val title: String,
    val subtitle: String,
    val global: Boolean,
    val edit: LibraryEdit,
    val exercise: Exercise? = null,
)

private fun LibraryUiState.rows(): List<LibraryRow> {
    val rows = when (tab) {
        LibraryTab.Exercises -> exercises.map {
            LibraryRow(
                it.id, it.name, it.attributes.joinToString(" · "), it.isGlobal,
                LibraryEdit.EditExercise(it), exercise = it,
            )
        }
        LibraryTab.Foods -> foods.map {
            LibraryRow(
                it.id, it.name,
                "${it.kcal.fmt(0)} kcal · P ${it.protein.fmt()} · C ${it.carbs.fmt()} · F ${it.fat.fmt()} per 100 g",
                it.isGlobal, LibraryEdit.EditFood(it),
            )
        }
        LibraryTab.Supplements -> supplements.map {
            LibraryRow(it.id, it.name, it.dosageLabel, it.isGlobal, LibraryEdit.EditSupplement(it))
        }
    }
    val q = query.trim()
    return if (q.isEmpty() || tab == LibraryTab.Exercises) {
        rows
    } else {
        rows.filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
    }
}

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onTabChange: (LibraryTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onNew: () -> Unit,
    onEdit: (LibraryEdit) -> Unit,
    onDismissEdit: () -> Unit,
    onSave: (LibraryEdit) -> Unit,
    onDelete: (LibraryTab, String) -> Unit,
    onMessageShown: () -> Unit,
    onBodyPartChange: (String?) -> Unit = {},
    onEquipmentChange: (String?) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onView: (Exercise) -> Unit = {},
    onDismissView: () -> Unit = {},
    onUploadVideo: (ByteArray, String) -> Unit = { _, _ -> },
    onLinkVideo: (String) -> Unit = {},
    onRemoveVideo: () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    var deleting by remember { mutableStateOf<LibraryRow?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }

    TabScaffold(
        title = "Library",
        snackbarHostState = snackbar,
        primary = BarAction("add", "Add ${state.tab.label.lowercase().removeSuffix("s")}", Icons.Default.Add, sfSymbol = "plus", onClick = onNew),
    ) { padding ->
        Centered(Modifier.padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                SegmentedControl(
                    options = LibraryTab.entries,
                    selected = state.tab,
                    onSelect = { onTabChange(it) },
                    modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                    label = { tab -> tab.label },
                )
                AppTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search ${state.tab.label.lowercase()}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                if (state.tab == LibraryTab.Exercises) {
                    FacetChips("Any body part", state.facets.bodyParts, state.bodyPart, onBodyPartChange)
                    FacetChips("Any equipment", state.facets.equipment, state.equipment, onEquipmentChange)
                }
                val rows = state.rows()
                val listState = rememberLazyListState()
                // Ask for the next page a few rows before the end, so scrolling does not stall.
                val nearEnd by remember(rows.size) {
                    derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= rows.size - 8 }
                }
                LaunchedEffect(nearEnd, rows.size) { if (nearEnd && rows.isNotEmpty()) onLoadMore() }
                when {
                    state.loading -> LoadingBox()
                    state.error != null && rows.isEmpty() -> ErrorBox(state.error, onRetry = onRetry)
                    rows.isEmpty() -> EmptyState(
                        "Nothing here yet",
                        if (state.tab == LibraryTab.Exercises && (state.query.isNotBlank() || state.bodyPart != null || state.equipment != null)) {
                            "No exercise matches those filters."
                        } else {
                            "Add your own items with the button below."
                        },
                    )
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp + bottomBarInset()),
                    ) {
                        itemsIndexed(rows, key = { _, it -> it.id }) { index, row ->
                            ListRow(
                                title = row.title,
                                subtitle = row.subtitle.takeIf { it.isNotBlank() },
                                leading = row.exercise?.let { { ExerciseThumbnail(it, size = 44.dp) } },
                                trailing = {
                                    if (row.global) {
                                        Tag("Library")
                                    } else {
                                        IconButton(onClick = { deleting = row }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalAppColors.current.label2)
                                        }
                                    }
                                },
                                showDivider = index > 0,
                                dividerInset = if (row.exercise != null) 72.dp else 14.dp,
                                onClick = if (!row.global || row.exercise != null) ({
                                    val exercise = row.exercise
                                    if (exercise != null) onView(exercise) else onEdit(row.edit)
                                }) else null,
                                modifier = Modifier.groupedRow(index, rows.size),
                            )
                        }
                        if (state.loadingMore) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        }
                    }
                }
            }
        }
    }

    when (val edit = state.editing) {
        is LibraryEdit.EditExercise -> ExerciseDialog(edit.exercise, state.saving, { onSave(LibraryEdit.EditExercise(it)) }, onDismissEdit)
        is LibraryEdit.EditFood -> FoodDialog(edit.food, state.saving, { onSave(LibraryEdit.EditFood(it)) }, onDismissEdit)
        is LibraryEdit.EditSupplement -> SupplementDialog(edit.supplement, state.saving, { onSave(LibraryEdit.EditSupplement(it)) }, onDismissEdit)
        null -> Unit
    }
    state.viewing?.let { exercise ->
        ExerciseDetailDialog(
            exercise = exercise,
            videoBusy = state.videoBusy,
            onDismiss = onDismissView,
            onEdit = { onDismissView(); onEdit(LibraryEdit.EditExercise(exercise)) },
            onUploadVideo = onUploadVideo,
            onLinkVideo = onLinkVideo,
            onRemoveVideo = onRemoveVideo,
        )
    }
    deleting?.let { row ->
        ConfirmDialog(
            title = "Delete ${row.title}?",
            text = "It is also removed from every plan that uses it.",
            onConfirm = { onDelete(state.tab, row.id) },
            onDismiss = { deleting = null },
        )
    }
}

/**
 * What an exercise looks like, what it works and how to do it, plus the coach's own video, which
 * replaces the library demo for their clients. Only the coach's own exercises can be edited.
 */
@Composable
private fun ExerciseDetailDialog(
    exercise: Exercise,
    videoBusy: Boolean,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onUploadVideo: (ByteArray, String) -> Unit,
    onLinkVideo: (String) -> Unit,
    onRemoveVideo: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val picker = rememberFilePickerLauncher(type = FileKitType.Video) { file ->
        if (file != null) scope.launch { onUploadVideo(file.readBytes(), file.extension.ifBlank { "mp4" }) }
    }
    var linking by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(exercise.name) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()).clearFocusOnTap(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ExerciseDemo(exercise)
                Text("Your video", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (exercise.customVideo != null) {
                        "Your clients see this video instead of the library demo."
                    } else {
                        "Upload or link your own demo. Your clients see it instead of the library animation."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (linking) {
                    AppTextField(
                        link,
                        { link = it },
                        label = { Text("Video link (YouTube, Vimeo, .mp4)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TintedButton(
                            "Save link",
                            onClick = { onLinkVideo(link); linking = false; link = "" },
                            enabled = link.trim().startsWith("http") && !videoBusy,
                            size = ButtonSize.Sm,
                        )
                        GhostButton("Cancel", onClick = { linking = false }, size = ButtonSize.Sm)
                    }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TintedButton(
                            if (exercise.customVideo?.storagePath != null) "Replace video" else "Upload video",
                            onClick = { picker.launch() },
                            loading = videoBusy,
                            size = ButtonSize.Sm,
                        )
                        TintedButton("Add link", onClick = { linking = true }, enabled = !videoBusy, size = ButtonSize.Sm)
                        // A link stored on the exercise row itself is edited in the exercise form.
                        if (exercise.customVideos.isNotEmpty()) {
                            DangerButton("Remove", onClick = onRemoveVideo, enabled = !videoBusy, size = ButtonSize.Sm)
                        }
                    }
                }
                val tags = exercise.attributes + listOfNotNull(exercise.bodyPart, exercise.target)
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        tags.distinct().forEach { Tag(it) }
                    }
                }
                exercise.instructions?.takeIf { it.isNotBlank() }?.let { steps ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        steps.lineSequence().filter { it.isNotBlank() }.forEachIndexed { index, step ->
                            Text("${index + 1}. $step", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { if (!exercise.isGlobal) GhostButton("Edit", onEdit, size = ButtonSize.Sm) },
        dismissButton = { GhostButton("Close", onDismiss, size = ButtonSize.Sm, dim = true) },
    )
}

/** A chip strip over one facet; the leading chip clears it. */
@Composable
private fun FacetChips(anyLabel: String, values: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    if (values.isEmpty()) return
    ChipRow(Modifier.padding(horizontal = 12.dp)) {
        Chip(anyLabel, selected == null, { onSelect(null) })
        values.forEach { value -> Chip(value, selected == value, { onSelect(if (selected == value) null else value) }) }
    }
}

@Composable
private fun EditDialog(
    title: String,
    canSave: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // A dialog composes in its own window, so it needs its own tap-to-dismiss handler.
            Column(
                Modifier.verticalScroll(rememberScrollState()).clearFocusOnTap(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        },
        confirmButton = { GhostButton("Save", onSave, enabled = canSave, loading = saving, size = ButtonSize.Sm) },
        dismissButton = { GhostButton("Cancel", onDismiss, size = ButtonSize.Sm, dim = true) },
    )
}

@Composable
private fun TextInput(value: String, onValueChange: (String) -> Unit, label: String, singleLine: Boolean = true) {
    AppTextField(value, onValueChange, label = { Text(label) }, singleLine = singleLine, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ExerciseDialog(initial: Exercise, saving: Boolean, onSave: (Exercise) -> Unit, onDismiss: () -> Unit) {
    var e by remember(initial) { mutableStateOf(initial) }
    EditDialog(if (initial.id.isBlank()) "New exercise" else "Edit exercise", e.name.isNotBlank(), saving, { onSave(e) }, onDismiss) {
        TextInput(e.name, { e = e.copy(name = it) }, "Name")
        TextInput(e.muscleGroup.orEmpty(), { e = e.copy(muscleGroup = it) }, "Muscle group")
        TextInput(e.equipment.orEmpty(), { e = e.copy(equipment = it) }, "Equipment")
        TextInput(e.instructions.orEmpty(), { e = e.copy(instructions = it) }, "Instructions", singleLine = false)
    }
}

@Composable
private fun FoodDialog(initial: Food, saving: Boolean, onSave: (Food) -> Unit, onDismiss: () -> Unit) {
    var f by remember(initial) { mutableStateOf(initial) }
    EditDialog(if (initial.id.isBlank()) "New food" else "Edit food", f.name.isNotBlank(), saving, { onSave(f) }, onDismiss) {
        TextInput(f.name, { f = f.copy(name = it) }, "Name")
        Text("Values per 100 g", style = MaterialTheme.typography.labelMedium)
        DecimalField(f.kcal, { f = f.copy(kcal = it ?: 0.0) }, "Calories", Modifier.fillMaxWidth(), suffix = "kcal")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField(f.protein, { f = f.copy(protein = it ?: 0.0) }, "Protein", Modifier.weight(1f), suffix = "g")
            DecimalField(f.carbs, { f = f.copy(carbs = it ?: 0.0) }, "Carbs", Modifier.weight(1f), suffix = "g")
            DecimalField(f.fat, { f = f.copy(fat = it ?: 0.0) }, "Fat", Modifier.weight(1f), suffix = "g")
        }
    }
}

@Composable
private fun SupplementDialog(initial: Supplement, saving: Boolean, onSave: (Supplement) -> Unit, onDismiss: () -> Unit) {
    var s by remember(initial) { mutableStateOf(initial) }
    EditDialog(if (initial.id.isBlank()) "New supplement" else "Edit supplement", s.name.isNotBlank(), saving, { onSave(s) }, onDismiss) {
        TextInput(s.name, { s = s.copy(name = it) }, "Name")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppTextField(s.defaultDosage.orEmpty(), { s = s.copy(defaultDosage = it) }, label = { Text("Default dosage") }, singleLine = true, modifier = Modifier.weight(1f))
            AppTextField(s.unit.orEmpty(), { s = s.copy(unit = it) }, label = { Text("Unit") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        TextInput(s.notes.orEmpty(), { s = s.copy(notes = it) }, "Notes", singleLine = false)
    }
}
