package ch.coachapp.feature.client.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.Dates
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ChartCard
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.RingRow
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.StatRing
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.data.model.Meal
import ch.coachapp.data.model.SupplementItem
import ch.coachapp.domain.macros
import ch.coachapp.feature.client.macroLine
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayRoute(onStartWorkout: (String) -> Unit, viewModel: TodayViewModel = koinViewModel()) {
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
            onToggleMeal = viewModel::toggleMeal,
            onToggleSupplement = viewModel::toggleSupplement,
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun TodayScreen(
    state: TodayUiState,
    onRetry: () -> Unit,
    onSelectDay: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onToggleMeal: (String) -> Unit,
    onToggleSupplement: (String) -> Unit,
) {
    when {
        state.loading -> LoadingBox()
        state.error != null -> ErrorBox(state.error, onRetry)
        else -> Centered {
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp + bottomBarInset()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Text("${Dates.dayName(state.date)}, ${Dates.format(state.date)}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.hasAnyPlan) {
                    item { TodayRings(state) }
                }
                if (!state.hasAnyPlan) {
                    item {
                        EmptyState(
                            title = "Your coach is preparing your plan",
                            body = "Workouts, meals and supplements will show up here once they're assigned.",
                        )
                    }
                }
                state.workoutPlan?.let { plan ->
                    item { SectionHeader("Workout") }
                    item { WorkoutCard(state, onSelectDay, onStartWorkout) }
                }
                state.mealPlan?.let { plan ->
                    item { SectionHeader("Meals", trailing = { Text("${state.mealsDoneCount}/${plan.meals.size}") }) }
                    items(plan.sortedMeals, key = { it.id }) { meal ->
                        MealRow(meal, done = meal.id in state.mealsDone, onToggle = { onToggleMeal(meal.id) })
                    }
                }
                state.supplementPlan?.let { plan ->
                    item { SectionHeader("Supplements") }
                    plan.sortedItems.groupBy { it.timing }.entries.sortedBy { it.key.ordinal }.forEach { (timing, items) ->
                        item(key = "timing-${timing.name}") {
                            Text(timing.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                        }
                        items(items, key = { it.id }) { item ->
                            SupplementRow(item, taken = item.id in state.supplementsTaken, onToggle = { onToggleSupplement(item.id) })
                        }
                    }
                }
            }
        }
    }
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

@Composable
private fun WorkoutCard(state: TodayUiState, onSelectDay: (String) -> Unit, onStartWorkout: (String) -> Unit) {
    val plan = state.workoutPlan ?: return
    val day = state.selectedDay
    InfoCard {
        Text(plan.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (day == null) {
            Text("This plan has no workout days yet.")
            return@InfoCard
        }
        Text(
            if (day.id == state.nextDayId) "Next up: ${day.title}" else day.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            day.sortedExercises.joinToString(" · ") { it.exercise?.name ?: "Exercise" }.ifEmpty { "No exercises" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (plan.days.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plan.sortedDays, key = { it.id }) { d ->
                    FilterChip(selected = d.id == day.id, onClick = { onSelectDay(d.id) }, label = { Text(d.title) })
                }
            }
        }
        Button(onClick = { onStartWorkout(day.id) }, enabled = day.exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Text("Start workout", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun MealRow(meal: Meal, done: Boolean, onToggle: () -> Unit) {
    CheckRow(
        checked = done,
        onToggle = onToggle,
        title = listOfNotNull(meal.timeHint, meal.name).joinToString(" · "),
        subtitle = buildString {
            append(macroLine(meal.macros()))
            val foods = meal.sortedItems.mapNotNull { item -> item.food?.let { "${it.name} ${item.grams.toInt()}g" } }
            if (foods.isNotEmpty()) append("\n").append(foods.joinToString(", "))
        },
    )
}

@Composable
private fun SupplementRow(item: SupplementItem, taken: Boolean, onToggle: () -> Unit) {
    CheckRow(
        checked = taken,
        onToggle = onToggle,
        title = item.supplement?.name ?: "Supplement",
        subtitle = listOfNotNull(item.dosage, item.notes).joinToString(" · ").ifEmpty { null },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onToggle: () -> Unit, title: String, subtitle: String?) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (checked) TextDecoration.LineThrough else null,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
