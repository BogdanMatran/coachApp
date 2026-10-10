package ch.coachapp.feature.coach

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ch.coachapp.core.Dates
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.ActivityCard
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ChartCard
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.RingRow
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.StatChip
import ch.coachapp.core.ui.StatRing
import ch.coachapp.core.ui.TargetBar
import ch.coachapp.core.ui.weekLabel
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinStatus
import ch.coachapp.data.model.DayActivity
import ch.coachapp.data.model.MealLog
import ch.coachapp.data.model.MealPlan
import ch.coachapp.data.model.PlanKind
import ch.coachapp.data.model.PlanSummary
import ch.coachapp.data.model.Profile
import ch.coachapp.data.model.SupplementLog
import ch.coachapp.data.model.SupplementPlan
import ch.coachapp.data.model.WorkoutLog
import ch.coachapp.data.model.WorkoutPlan
import ch.coachapp.data.repository.CheckinRepository
import ch.coachapp.data.repository.CoachRepository
import ch.coachapp.data.repository.LogRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.domain.macros
import ch.coachapp.domain.macrosFor
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

enum class ClientTab(val label: String) {
    Overview("Overview"), Workout("Workout"), Meals("Meals"), Supplements("Supplements"), Checkins("Check-ins"), Logs("Logs"),
}

/** Meals and supplements completed on one day, for the adherence table. */
data class DayAdherence(val date: LocalDate, val mealsDone: Int, val mealsTotal: Int, val supplementsDone: Int, val supplementsTotal: Int)

data class ClientDetailUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val tab: ClientTab = ClientTab.Overview,
    val profile: Profile? = null,
    val workout: WorkoutPlan? = null,
    val meal: MealPlan? = null,
    val supplement: SupplementPlan? = null,
    val checkins: List<Checkin> = emptyList(),
    val history: List<WorkoutLog> = emptyList(),
    val mealLogs: List<MealLog> = emptyList(),
    val supplementLogs: List<SupplementLog> = emptyList(),
    val activity: List<DayActivity> = emptyList(),
    /** Open "assign template" dialog; templates are null while loading. */
    val assignKind: PlanKind? = null,
    val templates: List<PlanSummary>? = null,
    val assigning: Boolean = false,
    val message: String? = null,
) {
    val lastWeek: List<DayAdherence>
        get() {
            val meals = meal?.meals?.size ?: 0
            val supplements = supplement?.items?.size ?: 0
            val mealIds = meal?.meals?.map { it.id }?.toSet().orEmpty()
            val itemIds = supplement?.items?.map { it.id }?.toSet().orEmpty()
            return (0 until 7).map { offset ->
                val date = Dates.today().minus(DatePeriod(days = offset))
                DayAdherence(
                    date = date,
                    mealsDone = mealLogs.count { it.logDate == date && it.completed && it.mealId in mealIds },
                    mealsTotal = meals,
                    supplementsDone = supplementLogs.count { it.logDate == date && it.taken && it.supplementItemId in itemIds },
                    supplementsTotal = supplements,
                )
            }
        }

    val adherencePercent: Int?
        get() {
            val days = lastWeek
            val total = days.sumOf { it.mealsTotal + it.supplementsTotal }
            if (total == 0) return null
            return (100 * days.sumOf { it.mealsDone + it.supplementsDone } / total).coerceAtMost(100)
        }
}

class ClientDetailViewModel(
    private val route: ClientDetailRoute,
    private val coach: CoachRepository,
    private val plans: PlanRepository,
    private val checkins: CheckinRepository,
    private val logs: LogRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClientDetailUiState())
    val state: StateFlow<ClientDetailUiState> = _state.asStateFlow()

    fun load() {
        val id = route.clientId
        _state.update { it.copy(loading = it.profile == null, error = null) }
        viewModelScope.launch {
            val today = Dates.today()
            val from = today.minus(DatePeriod(days = 6))
            val profile = async { coach.client(id) }
            val workout = async { plans.activeWorkoutPlan(id) }
            val meal = async { plans.activeMealPlan(id) }
            val supplement = async { plans.activeSupplementPlan(id) }
            val checkinList = async { checkins.checkins(id) }
            val history = async { logs.workoutHistory(id) }
            val mealLogs = async { logs.mealLogs(id, from, today) }
            val supplementLogs = async { logs.supplementLogs(id, from, today) }
            val activity = async { logs.dailyActivity(id, days = 371) }

            val profileResult = profile.await()
            val results = listOf(workout, meal, supplement, checkinList, history, mealLogs, supplementLogs).map { it.await() }
            val error = profileResult.exceptionOrNull()?.message ?: results.firstNotNullOfOrNull { it.exceptionOrNull()?.message }
            _state.update {
                it.copy(
                    loading = false,
                    error = error,
                    profile = profileResult.getOrNull() ?: it.profile,
                    workout = workout.await().getOrDefault(it.workout),
                    meal = meal.await().getOrDefault(it.meal),
                    supplement = supplement.await().getOrDefault(it.supplement),
                    checkins = checkinList.await().getOrDefault(it.checkins),
                    history = history.await().getOrDefault(it.history),
                    mealLogs = mealLogs.await().getOrDefault(it.mealLogs),
                    supplementLogs = supplementLogs.await().getOrDefault(it.supplementLogs),
                    // Optional: a failed heatmap load shouldn't flag the whole screen.
                    activity = activity.await().getOrDefault(it.activity),
                )
            }
        }
    }

    fun selectTab(tab: ClientTab) = _state.update { it.copy(tab = tab) }

    fun openAssign(kind: PlanKind) {
        _state.update { it.copy(assignKind = kind, templates = null) }
        viewModelScope.launch {
            plans.templates(kind)
                .onSuccess { list -> _state.update { it.copy(templates = list) } }
                .onFailure { e -> _state.update { it.copy(assignKind = null, message = e.message) } }
        }
    }

    fun dismissAssign() = _state.update { it.copy(assignKind = null, templates = null) }

    fun assign(templateId: String) {
        val kind = _state.value.assignKind ?: return
        _state.update { it.copy(assigning = true) }
        viewModelScope.launch {
            plans.assign(kind, templateId, route.clientId)
                .onSuccess {
                    _state.update { it.copy(assigning = false, assignKind = null, templates = null, message = "${kind.label} plan assigned") }
                    load()
                }
                .onFailure { e -> _state.update { it.copy(assigning = false, message = e.message) } }
        }
    }
}

@Composable
fun ClientDetailDestination(
    route: ClientDetailRoute,
    onBack: () -> Unit,
    onOpenBuilder: (PlanKind, planId: String?, clientId: String) -> Unit,
    onOpenCheckin: (String) -> Unit,
    viewModel: ClientDetailViewModel = koinViewModel(key = route.clientId) { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    ClientDetailScreen(
        state = state,
        onBack = onBack,
        onRefresh = viewModel::load,
        onTabChange = viewModel::selectTab,
        onEditPlan = { kind, planId -> onOpenBuilder(kind, planId, route.clientId) },
        onNewPlan = { kind -> onOpenBuilder(kind, null, route.clientId) },
        onOpenAssign = viewModel::openAssign,
        onDismissAssign = viewModel::dismissAssign,
        onAssign = viewModel::assign,
        onOpenCheckin = onOpenCheckin,
    )
}

@Composable
fun ClientDetailScreen(
    state: ClientDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onTabChange: (ClientTab) -> Unit,
    onEditPlan: (PlanKind, String) -> Unit,
    onNewPlan: (PlanKind) -> Unit,
    onOpenAssign: (PlanKind) -> Unit,
    onDismissAssign: () -> Unit,
    onAssign: (String) -> Unit,
    onOpenCheckin: (String) -> Unit,
) {
    Scaffold(
        topBar = {
            BackTopBar(
                state.profile?.displayName ?: "Client",
                onBack,
                actions = listOf(BarAction("refresh", "Refresh", Icons.Default.Refresh, sfSymbol = "arrow.clockwise", onClick = onRefresh)),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 16.dp) {
                ClientTab.entries.forEach { tab ->
                    Tab(
                        selected = state.tab == tab,
                        onClick = { onTabChange(tab) },
                        text = { Text(tab.label) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Centered {
                when {
                    state.loading -> LoadingBox()
                    state.profile == null -> ErrorBox(state.error ?: "Client not found", onRetry = onRefresh)
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        state.message?.let { msg -> item { Text(msg, color = MaterialTheme.colorScheme.primary) } }
                        state.error?.let { err -> item { Text(err, color = MaterialTheme.colorScheme.error) } }
                        when (state.tab) {
                            ClientTab.Overview -> overviewTab(state, onTabChange)
                            ClientTab.Workout -> workoutTab(state.workout, onEditPlan, onNewPlan, onOpenAssign)
                            ClientTab.Meals -> mealTab(state.meal, onEditPlan, onNewPlan, onOpenAssign)
                            ClientTab.Supplements -> supplementTab(state.supplement, onEditPlan, onNewPlan, onOpenAssign)
                            ClientTab.Checkins -> checkinsTab(state.checkins, onOpenCheckin)
                            ClientTab.Logs -> logsTab(state)
                        }
                    }
                }
            }
        }
    }

    state.assignKind?.let { kind ->
        AssignTemplateDialog(kind, state.templates, state.assigning, onAssign, onDismissAssign)
    }
}

/** 7-day plan adherence and this week's workouts against the plan's training days. */
@Composable
private fun ThisWeekRings(state: ClientDetailUiState) {
    val colors = LocalChartColors.current
    val weekStart = Dates.weekStart().toString()
    val workoutsThisWeek = state.history.count { it.performedAt.take(10) >= weekStart }
    val plannedDays = state.workout?.days?.size ?: 0
    ChartCard(title = "This week") {
        RingRow {
            StatRing(
                label = "Adherence",
                progress = (state.adherencePercent ?: 0) / 100f,
                color = colors.meals,
                centerText = state.adherencePercent?.let { "$it%" } ?: "–",
                caption = "meals & supps, 7 days",
            )
            StatRing(
                label = "Workouts",
                progress = if (plannedDays > 0) workoutsThisWeek.toFloat() / plannedDays else 0f,
                color = colors.workouts,
                centerText = if (plannedDays > 0) "$workoutsThisWeek/$plannedDays" else "$workoutsThisWeek",
                caption = "since Monday",
            )
        }
    }
}

private fun LazyListScope.overviewTab(state: ClientDetailUiState, onTabChange: (ClientTab) -> Unit) {
    val profile = state.profile ?: return
    val latest = state.checkins.firstOrNull()
    item {
        InfoCard {
            Text(profile.displayName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            profile.email?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    item { ThisWeekRings(state) }
    item {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatChip("Pending reviews", state.checkins.count { it.status == CheckinStatus.Submitted }.toString())
            StatChip("Latest weight", latest?.weight?.let { "${it.fmt()} kg" } ?: "–")
            StatChip("Workouts logged", state.history.size.toString())
        }
    }
    if (state.activity.isNotEmpty()) item { ActivityCard(state.activity) }
    item { SectionHeader("Active plans") }
    item {
        Card {
            PlanRow("Workout", state.workout?.name) { onTabChange(ClientTab.Workout) }
            PlanRow("Meals", state.meal?.name) { onTabChange(ClientTab.Meals) }
            PlanRow("Supplements", state.supplement?.name) { onTabChange(ClientTab.Supplements) }
        }
    }
    latest?.let { checkin ->
        item { SectionHeader("Latest check-in") }
        item {
            InfoCard {
                Text(checkin.weekLabel(), fontWeight = FontWeight.SemiBold)
                Text(if (checkin.status == CheckinStatus.Reviewed) "Reviewed" else "Waiting for your review")
                checkin.coachFeedback?.let { Text("Your feedback: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun PlanRow(label: String, name: String?, onClick: () -> Unit) {
    ListItem(
        overlineContent = { Text(label) },
        headlineContent = { Text(name ?: "Not assigned", color = if (name == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Header with name and edit/assign/new actions, or an empty state when no plan is active. */
private fun LazyListScope.planHeader(
    kind: PlanKind,
    planId: String?,
    planName: String?,
    notes: String?,
    onEditPlan: (PlanKind, String) -> Unit,
    onNewPlan: (PlanKind) -> Unit,
    onOpenAssign: (PlanKind) -> Unit,
) {
    item {
        if (planId == null) {
            EmptyState(
                title = "No active ${kind.label.lowercase()} plan",
                body = "Assign one of your templates or build a plan just for this client.",
                action = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onOpenAssign(kind) }) { Text("Assign template") }
                        OutlinedButton(onClick = { onNewPlan(kind) }) { Text("Create new") }
                    }
                },
            )
        } else {
            InfoCard {
                Text(planName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { onEditPlan(kind, planId) }, label = { Text("Edit") }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                    AssistChip(onClick = { onOpenAssign(kind) }, label = { Text("Assign template") })
                    AssistChip(onClick = { onNewPlan(kind) }, label = { Text("Create new") }, leadingIcon = { Icon(Icons.Default.Add, null) })
                }
            }
        }
    }
}

private fun LazyListScope.workoutTab(
    plan: WorkoutPlan?,
    onEditPlan: (PlanKind, String) -> Unit,
    onNewPlan: (PlanKind) -> Unit,
    onOpenAssign: (PlanKind) -> Unit,
) {
    planHeader(PlanKind.Workout, plan?.id, plan?.name, plan?.notes, onEditPlan, onNewPlan, onOpenAssign)
    plan ?: return
    items(plan.sortedDays, key = { it.id }) { day ->
        InfoCard {
            Text(day.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (day.exercises.isEmpty()) Text("No exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
            day.sortedExercises.forEach { ex ->
                Column {
                    Text(ex.exercise?.name ?: "Exercise")
                    Text(
                        listOfNotNull(
                            "${ex.sets} × ${ex.reps}",
                            "${ex.restSec}s rest",
                            ex.targetRpe?.let { "RPE ${it.fmt()}" },
                            ex.tempo?.let { "Tempo $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ex.notes?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}

private fun LazyListScope.mealTab(
    plan: MealPlan?,
    onEditPlan: (PlanKind, String) -> Unit,
    onNewPlan: (PlanKind) -> Unit,
    onOpenAssign: (PlanKind) -> Unit,
) {
    planHeader(PlanKind.Meal, plan?.id, plan?.name, plan?.notes, onEditPlan, onNewPlan, onOpenAssign)
    plan ?: return
    item {
        val totals = plan.macros()
        InfoCard {
            TargetBar("Calories", totals.kcal, plan.targetKcal?.toDouble(), "kcal")
            TargetBar("Protein", totals.protein, plan.targetProtein?.toDouble(), "g")
            TargetBar("Carbs", totals.carbs, plan.targetCarbs?.toDouble(), "g")
            TargetBar("Fat", totals.fat, plan.targetFat?.toDouble(), "g")
        }
    }
    items(plan.sortedMeals, key = { it.id }) { meal ->
        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                meal.timeHint?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
            Text(meal.macros().summary(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            meal.sortedItems.forEach { item ->
                val food = item.food
                Text("${food?.name ?: "Food"} · ${item.grams.fmt()} g" + (food?.let { " · ${it.macrosFor(item.grams).kcal.fmt(0)} kcal" } ?: ""))
            }
        }
    }
}

private fun LazyListScope.supplementTab(
    plan: SupplementPlan?,
    onEditPlan: (PlanKind, String) -> Unit,
    onNewPlan: (PlanKind) -> Unit,
    onOpenAssign: (PlanKind) -> Unit,
) {
    planHeader(PlanKind.Supplement, plan?.id, plan?.name, plan?.notes, onEditPlan, onNewPlan, onOpenAssign)
    plan ?: return
    val byTiming = plan.sortedItems.groupBy { it.timing }.entries.sortedBy { it.key.ordinal }
    byTiming.forEach { (timing, items) ->
        item(key = timing.name) {
            InfoCard {
                Text(timing.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                items.forEach { item ->
                    Text(listOfNotNull(item.supplement?.name ?: "Supplement", item.dosage, item.notes).joinToString(" · "))
                }
            }
        }
    }
}

private fun LazyListScope.checkinsTab(checkins: List<Checkin>, onOpenCheckin: (String) -> Unit) {
    if (checkins.isEmpty()) {
        item { EmptyState("No check-ins yet", "Your client's weekly check-ins will show up here.") }
        return
    }
    items(checkins, key = { it.id }) { checkin ->
        Card(Modifier.fillMaxWidth().clickable { onOpenCheckin(checkin.id) }) {
            ListItem(
                headlineContent = { Text(checkin.weekLabel()) },
                supportingContent = {
                    Text(listOfNotNull(checkin.weight?.let { "${it.fmt()} kg" }, "${checkin.photos.size} photos").joinToString(" · "))
                },
                trailingContent = {
                    val reviewed = checkin.status == CheckinStatus.Reviewed
                    Text(
                        if (reviewed) "Reviewed" else "To review",
                        color = if (reviewed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

private fun LazyListScope.logsTab(state: ClientDetailUiState) {
    item { SectionHeader("Last 7 days") }
    item {
        InfoCard {
            state.lastWeek.forEach { day ->
                Row(Modifier.fillMaxWidth()) {
                    Text("${Dates.dayName(day.date).take(3)} ${Dates.format(day.date)}", modifier = Modifier.weight(1f))
                    Text("Meals ${day.mealsDone}/${day.mealsTotal}", modifier = Modifier.weight(1f))
                    Text("Supps ${day.supplementsDone}/${day.supplementsTotal}", modifier = Modifier.weight(1f))
                }
            }
        }
    }
    item { SectionHeader("Workouts") }
    if (state.history.isEmpty()) {
        item { EmptyState("No workouts logged yet") }
    }
    items(state.history, key = { it.id }) { log ->
        InfoCard {
            Text(
                "${log.dayTitle ?: "Workout"} · ${Dates.formatTimestamp(log.performedAt)}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            log.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            log.sets.groupBy { it.exerciseId }.forEach { (_, sets) ->
                val name = sets.first().exercise?.name ?: "Exercise"
                val summary = sets.sortedBy { it.setNo }.joinToString(", ") { set ->
                    buildString {
                        append(set.weight?.fmt() ?: "BW")
                        append(" × ")
                        append(set.reps ?: "–")
                        set.rpe?.let { append(" @${it.fmt()}") }
                    }
                }
                Text("$name: $summary", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AssignTemplateDialog(
    kind: PlanKind,
    templates: List<PlanSummary>?,
    assigning: Boolean,
    onAssign: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${kind.label.lowercase()} template") },
        text = {
            when {
                templates == null || assigning -> LoadingBox(Modifier.heightIn(max = 120.dp))
                templates.isEmpty() -> Text("You have no ${kind.label.lowercase()} templates yet. Create one in the Plans tab.")
                else -> LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    item {
                        Text(
                            "The client gets a copy that replaces their current plan.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    items(templates, key = { it.id }) { template ->
                        ListItem(
                            headlineContent = { Text(template.name) },
                            modifier = Modifier.clickable { onAssign(template.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
