package ch.coachapp.feature.client.today

import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.Celebration
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ChartCard
import ch.coachapp.core.ui.CheckMark
import ch.coachapp.core.ui.Chip
import ch.coachapp.core.ui.ChipRow
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.GroupedList
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.PrimaryButton
import ch.coachapp.core.ui.RingRow
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.StatRing
import ch.coachapp.core.ui.Tag
import ch.coachapp.core.ui.TintedButton
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.core.ui.rememberPressInteraction
import ch.coachapp.data.model.Meal
import ch.coachapp.data.model.SupplementItem
import ch.coachapp.domain.CheckinDue
import ch.coachapp.domain.macros
import ch.coachapp.feature.client.checkin.CheckinDueCard
import ch.coachapp.feature.client.macroLine
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayRoute(
    onStartWorkout: (String) -> Unit,
    onOpenCheckin: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // Reload whenever the tab (re)appears, e.g. after finishing a workout.
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.messageShown() }
    }
    Box(Modifier.fillMaxSize()) {
        TodayScreen(
            state = state,
            onRetry = viewModel::load,
            onSelectDay = viewModel::selectDay,
            onStartWorkout = onStartWorkout,
            onOpenCheckin = onOpenCheckin,
            onToggleMeal = viewModel::toggleMeal,
            onToggleSupplement = viewModel::toggleSupplement,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        state.celebration?.let { message ->
            Celebration(message.title, message.subtitle, onFinished = viewModel::celebrationShown)
        }
    }
}

/** Height the pinned Start workout button takes from the bottom of the list. */
private val StickyActionSpace = 76.dp

@Composable
fun TodayScreen(
    state: TodayUiState,
    onRetry: () -> Unit,
    onSelectDay: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenCheckin: () -> Unit,
    onToggleMeal: (String) -> Unit,
    onToggleSupplement: (String) -> Unit,
) {
    when {
        state.loading -> LoadingBox()
        state.error != null -> ErrorBox(state.error, onRetry)
        else -> Box(Modifier.fillMaxSize()) {
            // Today's workout is the main action, so it sits pinned in the thumb zone until it's done.
            val stickyDay = state.selectedDay?.takeIf { it.exercises.isNotEmpty() && !state.workoutDoneToday }
            Centered {
                LazyColumn(
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp + bottomBarInset() + if (stickyDay != null) StickyActionSpace else 0.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "date") {
                        Text(
                            "${Dates.dayName(state.date)}, ${Dates.format(state.date)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = LocalAppColors.current.label2,
                        )
                    }
                    state.checkinDue?.let { due ->
                        if (due != CheckinDue.Submitted) item(key = "checkin") { CheckinDueCard(due, onOpenCheckin) }
                    }
                    if (state.hasAnyPlan) {
                        item(key = "rings") { TodayRings(state) }
                    } else {
                        item(key = "empty") { NoPlanYet(state.checkinDue, onOpenCheckin) }
                    }
                    state.workoutPlan?.let {
                        item(key = "workout-header") { SectionHeader("Workout") }
                        item(key = "workout") { WorkoutCard(state, onSelectDay) }
                    }
                    state.mealPlan?.takeIf { it.meals.isNotEmpty() }?.let { plan ->
                        item(key = "meals-header") {
                            SectionHeader("Meals", trailing = { Text("${state.mealsDoneCount}/${plan.meals.size}", color = LocalAppColors.current.label2) })
                        }
                        item(key = "meals") {
                            GroupedList {
                                plan.sortedMeals.forEachIndexed { index, meal ->
                                    MealRow(meal, done = meal.id in state.mealsDone, showDivider = index > 0, onToggle = { onToggleMeal(meal.id) })
                                }
                            }
                        }
                    }
                    state.supplementPlan?.takeIf { it.items.isNotEmpty() }?.let { plan ->
                        item(key = "supplements-header") {
                            SectionHeader("Supplements", trailing = { Text("${state.supplementsTakenCount}/${plan.items.size}", color = LocalAppColors.current.label2) })
                        }
                        plan.sortedItems.groupBy { it.timing }.entries.sortedBy { it.key.ordinal }.forEach { (timing, items) ->
                            item(key = "timing-${timing.name}") {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        timing.label,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = LocalAppColors.current.accText,
                                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                                    )
                                    GroupedList {
                                        items.forEachIndexed { index, item ->
                                            SupplementRow(item, taken = item.id in state.supplementsTaken, showDivider = index > 0) {
                                                onToggleSupplement(item.id)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stickyDay?.let { day ->
                PrimaryButton(
                    "Start ${day.title}",
                    onClick = { onStartWorkout(day.id) },
                    icon = Icons.Default.PlayArrow,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp + bottomBarInset()),
                )
            }
        }
    }
}

/** Before the coach assigns anything: say what's coming, and offer the one thing to do now. */
@Composable
private fun NoPlanYet(checkinDue: CheckinDue?, onOpenCheckin: () -> Unit) {
    val canCheckIn = checkinDue == CheckinDue.DueToday || checkinDue is CheckinDue.Overdue
    EmptyState(
        title = "Your coach is preparing your plan",
        body = if (canCheckIn) {
            "Workouts, meals and supplements appear here once they're assigned. Meanwhile, your first check-in helps your coach tailor it."
        } else {
            "Workouts, meals and supplements appear here once they're assigned. We'll let you know when it's ready."
        },
        action = if (canCheckIn) ({ TintedButton("Start check-in", onClick = onOpenCheckin) }) else null,
    )
}

/** Progress rings: meals and supplements for today, workouts for this week. */
@Composable
private fun TodayRings(state: TodayUiState) {
    val colors = LocalChartColors.current
    ChartCard(title = "Your progress") {
        RingRow {
            state.workoutPlan?.let { plan ->
                val target = plan.days.size.coerceAtLeast(1)
                StatRing(
                    label = "Workouts",
                    progress = state.workoutsThisWeek.toFloat() / target,
                    color = colors.workouts,
                    centerText = "${state.workoutsThisWeek}/$target",
                    caption = "this week",
                )
            }
            state.mealPlan?.takeIf { it.meals.isNotEmpty() }?.let { plan ->
                StatRing(
                    label = "Meals",
                    progress = state.mealsDoneCount.toFloat() / plan.meals.size,
                    color = colors.meals,
                    centerText = "${state.mealsDoneCount}/${plan.meals.size}",
                    caption = "today",
                )
            }
            state.supplementPlan?.takeIf { it.items.isNotEmpty() }?.let { plan ->
                StatRing(
                    label = "Supplements",
                    progress = state.supplementsTakenCount.toFloat() / plan.items.size,
                    color = colors.supplements,
                    centerText = "${state.supplementsTakenCount}/${plan.items.size}",
                    caption = "today",
                )
            }
        }
    }
}

/** What's on today; the button that starts it is pinned to the bottom of the screen. */
@Composable
private fun WorkoutCard(state: TodayUiState, onSelectDay: (String) -> Unit) {
    val plan = state.workoutPlan ?: return
    val day = state.selectedDay
    val colors = LocalAppColors.current
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(plan.name, style = MaterialTheme.typography.labelMedium, color = colors.label2, modifier = Modifier.weight(1f))
            if (state.workoutDoneToday) Tag("Done today", accent = true)
        }
        if (day == null) {
            Text("This plan has no workout days yet.")
            return@InfoCard
        }
        Text(
            if (day.id == state.nextDayId && !state.workoutDoneToday) "Next up: ${day.title}" else day.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            day.sortedExercises.joinToString(" · ") { it.exercise?.name ?: "Exercise" }.ifEmpty { "No exercises" },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.label2,
        )
        if (plan.days.size > 1) {
            ChipRow {
                plan.sortedDays.forEach { d ->
                    Chip(d.title, selected = d.id == day.id, onClick = { onSelectDay(d.id) }, onCard = true)
                }
            }
        }
    }
}

@Composable
private fun MealRow(meal: Meal, done: Boolean, showDivider: Boolean, onToggle: () -> Unit) {
    CheckRow(
        checked = done,
        onToggle = onToggle,
        showDivider = showDivider,
        title = listOfNotNull(meal.timeHint, meal.name).joinToString(" · "),
        subtitle = buildString {
            append(macroLine(meal.macros()))
            val foods = meal.sortedItems.mapNotNull { item -> item.food?.let { "${it.name} ${item.grams.toInt()}g" } }
            if (foods.isNotEmpty()) append("\n").append(foods.joinToString(", "))
        },
    )
}

@Composable
private fun SupplementRow(item: SupplementItem, taken: Boolean, showDivider: Boolean, onToggle: () -> Unit) {
    CheckRow(
        checked = taken,
        onToggle = onToggle,
        showDivider = showDivider,
        title = item.supplement?.name ?: "Supplement",
        subtitle = listOfNotNull(item.dosage, item.notes).joinToString(" · ").ifEmpty { null },
    )
}

/** A row of a [GroupedList] that ticks off. The whole row is the target; [CheckMark] only draws it. */
@Composable
private fun CheckRow(checked: Boolean, onToggle: () -> Unit, showDivider: Boolean, title: String, subtitle: String?) {
    val colors = LocalAppColors.current
    val interaction = rememberPressInteraction()
    val pressed by interaction.collectIsPressedAsState()
    Column(Modifier.fillMaxWidth()) {
        if (showDivider) {
            // Past the check column, so the block reads as one list.
            Box(Modifier.padding(start = 54.dp).fillMaxWidth().heightIn(min = 0.5.dp, max = 0.5.dp).background(colors.sep))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pressed) colors.surface2 else Color.Transparent)
                .toggleable(
                    value = checked,
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Checkbox,
                    onValueChange = { onToggle() },
                )
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckMark(checked)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (checked) colors.label2 else colors.label,
                    textDecoration = if (checked) TextDecoration.LineThrough else null,
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.labelMedium, color = colors.label2)
                }
            }
        }
    }
}
