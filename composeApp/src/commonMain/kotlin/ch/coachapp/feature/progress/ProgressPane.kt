package ch.coachapp.feature.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.ChartCard
import ch.coachapp.core.ui.Chip
import ch.coachapp.core.ui.ChipRow
import ch.coachapp.core.ui.DropdownField
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.LineChart
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.PhotoTile
import ch.coachapp.core.ui.weekLabel
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.Pose
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.LogRepository
import ch.coachapp.domain.Progress
import ch.coachapp.domain.Series
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

val progressModule = module {
    viewModel { (clientId: String) -> ProgressViewModel(clientId, get(), get()) }
}

data class ProgressUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val body: List<Series> = emptyList(),
    /** Key of the measurement shown in the measurements chart. */
    val measurementKey: String? = null,
    val strength: List<Series> = emptyList(),
    val exerciseKey: String? = null,
    /** Check-ins with at least one photo, oldest first. */
    val photoCheckins: List<Checkin> = emptyList(),
    val pose: Pose = Pose.Front,
    val beforeId: String? = null,
    val afterId: String? = null,
    val beforeUrl: String? = null,
    val afterUrl: String? = null,
) {
    val weight: Series? get() = body.firstOrNull { it.key == "weight" }
    val bodyFat: Series? get() = body.firstOrNull { it.key == "body_fat" }
    val measurements: List<Series> get() = body.filter { it.unit == "cm" }
    // Waist is the measurement coaches look at first, so it's the default when recorded.
    val measurement: Series?
        get() = measurements.firstOrNull { it.key == measurementKey }
            ?: measurements.firstOrNull { it.key == "waist" }
            ?: measurements.firstOrNull()
    val exercise: Series? get() = strength.firstOrNull { it.key == exerciseKey } ?: strength.firstOrNull()
    val isEmpty: Boolean get() = body.isEmpty() && strength.isEmpty() && photoCheckins.isEmpty()
}

/** Trends for one client: body metrics from check-ins, strength from logged sets, photo comparison. */
class ProgressViewModel(
    private val clientId: String,
    private val checkins: CheckinRepository,
    private val logs: LogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProgressUiState())
    val state: StateFlow<ProgressUiState> = _state.asStateFlow()

    fun load() {
        _state.update { it.copy(loading = it.isEmpty, error = null) }
        viewModelScope.launch {
            val checkinList = async { checkins.checkins(clientId) }
            val strength = async { logs.strengthProgress(clientId) }
            val list = checkinList.await().getOrElse { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load progress") }
                return@launch
            }
            val withPhotos = list.filter { it.photos.isNotEmpty() }.sortedBy { it.weekStart }
            _state.update { s ->
                s.copy(
                    loading = false,
                    body = Progress.bodySeries(list),
                    // Optional: no strength chart is better than no progress screen.
                    strength = Progress.strengthSeries(strength.await().getOrDefault(emptyList())),
                    photoCheckins = withPhotos,
                    beforeId = s.beforeId?.takeIf { id -> withPhotos.any { it.id == id } } ?: withPhotos.firstOrNull()?.id,
                    afterId = s.afterId?.takeIf { id -> withPhotos.any { it.id == id } } ?: withPhotos.lastOrNull()?.id,
                )
            }
            loadPhotos()
        }
    }

    fun selectMeasurement(key: String) = _state.update { it.copy(measurementKey = key) }
    fun selectExercise(key: String) = _state.update { it.copy(exerciseKey = key) }

    fun selectPose(pose: Pose) {
        _state.update { it.copy(pose = pose) }
        loadPhotos()
    }

    fun selectBefore(id: String) {
        _state.update { it.copy(beforeId = id) }
        loadPhotos()
    }

    fun selectAfter(id: String) {
        _state.update { it.copy(afterId = id) }
        loadPhotos()
    }

    private fun loadPhotos() {
        val s = _state.value
        fun path(id: String?) = s.photoCheckins.firstOrNull { it.id == id }?.photos?.firstOrNull { it.pose == s.pose }?.storagePath
        viewModelScope.launch {
            val before = async { path(s.beforeId)?.let { checkins.photoUrl(it).getOrNull() } }
            val after = async { path(s.afterId)?.let { checkins.photoUrl(it).getOrNull() } }
            _state.update { it.copy(beforeUrl = before.await(), afterUrl = after.await()) }
        }
    }
}

@Composable
fun ProgressPane(
    clientId: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    viewModel: ProgressViewModel = koinViewModel(key = "progress-$clientId") { parametersOf(clientId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(clientId) { viewModel.load() }
    ProgressContent(
        state = state,
        onRetry = viewModel::load,
        onSelectMeasurement = viewModel::selectMeasurement,
        onSelectExercise = viewModel::selectExercise,
        onSelectPose = viewModel::selectPose,
        onSelectBefore = viewModel::selectBefore,
        onSelectAfter = viewModel::selectAfter,
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
fun ProgressContent(
    state: ProgressUiState,
    onRetry: () -> Unit,
    onSelectMeasurement: (String) -> Unit,
    onSelectExercise: (String) -> Unit,
    onSelectPose: (Pose) -> Unit,
    onSelectBefore: (String) -> Unit,
    onSelectAfter: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val chart = LocalChartColors.current
    val colors = LocalAppColors.current
    when {
        state.loading -> LoadingBox(modifier)
        state.error != null -> ErrorBox(state.error, onRetry, modifier)
        state.isEmpty -> EmptyState(
            title = "No progress data yet",
            body = "Trends appear after a couple of check-ins and logged workouts.",
            modifier = modifier,
        )
        else -> LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.weight?.let { series ->
                item(key = "weight") { ChartCard("Weight") { LineChart(series, chart.workouts) } }
            }
            state.bodyFat?.let { series ->
                item(key = "body_fat") { ChartCard("Body fat") { LineChart(series, chart.fat) } }
            }
            state.measurement?.let { series ->
                item(key = "measurements") {
                    ChartCard("Measurements") {
                        if (state.measurements.size > 1) {
                            ChipRow {
                                state.measurements.forEach { m ->
                                    Chip(m.label, selected = m.key == series.key, onClick = { onSelectMeasurement(m.key) })
                                }
                            }
                        }
                        LineChart(series, chart.meals)
                    }
                }
            }
            state.exercise?.let { series ->
                item(key = "strength") {
                    ChartCard("Strength", subtitle = "Estimated one-rep max from the best set of each session") {
                        ChipRow {
                            state.strength.forEach { s ->
                                Chip(s.label, selected = s.key == series.key, onClick = { onSelectExercise(s.key) })
                            }
                        }
                        LineChart(series, chart.supplements)
                    }
                }
            }
            if (state.photoCheckins.isNotEmpty()) {
                item(key = "photos") {
                    ChartCard("Progress photos") {
                        ChipRow {
                            Pose.entries.forEach { pose ->
                                Chip(pose.label, selected = pose == state.pose, onClick = { onSelectPose(pose) })
                            }
                        }
                        if (state.photoCheckins.size > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CheckinPicker("Before", state.beforeId, state.photoCheckins, onSelectBefore, Modifier.weight(1f))
                                CheckinPicker("After", state.afterId, state.photoCheckins, onSelectAfter, Modifier.weight(1f))
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (state.photoCheckins.size > 1) {
                                PhotoTile(state.beforeUrl, label(state.photoCheckins, state.beforeId), Modifier.weight(1f))
                            }
                            PhotoTile(state.afterUrl, label(state.photoCheckins, state.afterId), Modifier.weight(1f))
                        }
                        Text(
                            "Photos are private to you and your coach.",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.label2,
                        )
                    }
                }
            }
        }
    }
}

private fun label(checkins: List<Checkin>, id: String?): String? = checkins.firstOrNull { it.id == id }?.weekLabel()

@Composable
private fun CheckinPicker(label: String, selectedId: String?, checkins: List<Checkin>, onSelect: (String) -> Unit, modifier: Modifier) {
    val selected = checkins.firstOrNull { it.id == selectedId } ?: return
    DropdownField(
        label = label,
        selected = selected,
        options = checkins,
        optionLabel = { Dates.format(it.weekStart) },
        onSelect = { onSelect(it.id) },
        modifier = modifier,
    )
}
