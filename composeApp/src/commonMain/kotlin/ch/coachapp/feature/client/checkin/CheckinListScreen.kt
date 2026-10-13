package ch.coachapp.feature.client.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.ui.AppCard
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.core.ui.weekLabel
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.repository.AuthRepository
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.isReviewed
import ch.coachapp.domain.CheckinDue
import ch.coachapp.domain.CheckinSchedule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.compose.viewmodel.koinViewModel

data class CheckinListUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val checkins: List<Checkin> = emptyList(),
    val due: CheckinDue? = null,
    val weekStart: LocalDate = Dates.weekStart(),
)

/** The client's check-ins, newest first, and whether this week's is due. */
class CheckinListViewModel(private val auth: AuthRepository, private val checkins: CheckinRepository) : ViewModel() {

    private val _state = MutableStateFlow(CheckinListUiState())
    val state: StateFlow<CheckinListUiState> = _state.asStateFlow()

    fun load() {
        val clientId = auth.currentProfile?.id ?: return
        val today = Dates.today()
        val weekStart = Dates.weekStart(today)
        _state.update { it.copy(loading = it.checkins.isEmpty() && it.due == null, error = null, weekStart = weekStart) }
        viewModelScope.launch {
            val list = async { checkins.checkins(clientId) }
            val schedule = async { checkins.schedule(clientId, weekStart) }
            list.await()
                .onSuccess { all ->
                    _state.update {
                        it.copy(
                            loading = false,
                            checkins = all,
                            // Optional: without the schedule the list still works, just without the alert.
                            due = schedule.await().getOrNull()?.let { s ->
                                CheckinSchedule.status(today, s.checkinDay, s.submittedThisWeek, s.joinedOn)
                            },
                        )
                    }
                }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load check-ins") } }
        }
    }
}

@Composable
fun CheckinListRoute(
    onOpenForm: () -> Unit,
    onOpenCheckin: (String) -> Unit,
    viewModel: CheckinListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Reload whenever the tab (re)appears, e.g. after sending a check-in.
    LaunchedEffect(Unit) { viewModel.load() }
    CheckinListScreen(state, onRetry = viewModel::load, onOpenForm = onOpenForm, onOpenCheckin = onOpenCheckin)
}

@Composable
fun CheckinListScreen(
    state: CheckinListUiState,
    onRetry: () -> Unit,
    onOpenForm: () -> Unit,
    onOpenCheckin: (String) -> Unit,
) {
    when {
        state.loading -> LoadingBox()
        state.error != null -> ErrorBox(state.error, onRetry)
        else -> Centered {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp + bottomBarInset()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.due?.let { due ->
                    if (due != CheckinDue.Submitted) item(key = "due") { CheckinDueCard(due, onOpenForm) }
                }
                if (state.checkins.isEmpty()) {
                    item(key = "empty") {
                        EmptyState("No check-ins yet", "Your weekly check-ins and your coach's feedback will show up here.")
                    }
                } else {
                    item(key = "header") { SectionHeader("Your check-ins") }
                    items(state.checkins, key = { it.id }) { checkin ->
                        // This week's, until reviewed, can still be updated; everything else is read-only.
                        val editable = checkin.weekStart == state.weekStart && !checkin.isReviewed
                        CheckinRow(checkin, onClick = { if (editable) onOpenForm() else onOpenCheckin(checkin.id) })
                    }
                }
            }
        }
    }
}

/** This week's check-in, until it's sent. Overdue reads in red so it isn't missed. */
@Composable
fun CheckinDueCard(due: CheckinDue, onStart: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    val (title, body, urgent) = when (due) {
        CheckinDue.DueToday -> Triple("Check-in due today", "Send your coach this week's progress.", true)
        is CheckinDue.Overdue -> Triple(
            "Check-in overdue",
            if (due.days == 1) "It was due yesterday." else "It was due ${due.days} days ago.",
            true,
        )
        is CheckinDue.Upcoming -> Triple("Next check-in", "Due ${Dates.dayName(due.date)}, ${Dates.format(due.date)}.", false)
        CheckinDue.Submitted -> return
    }
    InfoCard(modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (due is CheckinDue.Overdue) colors.redText else colors.label,
        )
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.label2)
        if (urgent) PrimaryButton("Start check-in", onClick = onStart, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CheckinRow(checkin: Checkin, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(checkin.weekLabel(), style = MaterialTheme.typography.titleMedium)
                    checkin.weight?.let {
                        Text("${it.fmt()} kg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                StatusChip(checkin)
            }
            checkin.coachFeedback?.takeIf { it.isNotBlank() }?.let {
                Text("“$it”", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
