package ch.coachapp.feature.client.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.EmptyState
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.MacroDonut
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.core.ui.SegmentedControl
import ch.coachapp.core.ui.TargetBar
import ch.coachapp.core.ui.bottomBarInset
import ch.coachapp.core.ui.highlightClickable
import ch.coachapp.data.model.MealPlan
import ch.coachapp.data.model.SupplementPlan
import ch.coachapp.data.model.WorkoutPlan
import ch.coachapp.domain.macros
import ch.coachapp.domain.macrosFor
import ch.coachapp.feature.client.macroLine
import ch.coachapp.feature.client.prescription
import ch.coachapp.feature.exercise.ExerciseThumbnail
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlanRoute(onOpenExercise: (workoutExerciseId: String) -> Unit, viewModel: PlanViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlanScreen(state, onRetry = viewModel::load, onSelectSection = viewModel::selectSection, onOpenExercise = onOpenExercise)
}

@Composable
fun PlanScreen(
    state: PlanUiState,
    onRetry: () -> Unit,
    onSelectSection: (PlanSection) -> Unit,
    onOpenExercise: (String) -> Unit = {},
) {
    Centered {
        Column(Modifier.fillMaxSize()) {
            SegmentedControl(
                options = PlanSection.entries,
                selected = state.section,
                onSelect = { onSelectSection(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                label = { section -> section.label },
            )
            when {
                state.loading -> LoadingBox()
                state.error != null -> ErrorBox(state.error, onRetry)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 16.dp + bottomBarInset()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (state.section) {
                        PlanSection.Workout -> workoutSection(state.workout, onOpenExercise)
                        PlanSection.Meals -> mealSection(state.meal)
                        PlanSection.Supplements -> supplementSection(state.supplement)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.noPlan(kind: String) {
    item { EmptyState("No $kind plan yet", "Your coach hasn't assigned one. Check back soon.") }
}

private fun LazyListScope.planHeader(name: String, notes: String?) {
    item {
        Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        notes?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun LazyListScope.workoutSection(plan: WorkoutPlan?, onOpenExercise: (String) -> Unit) {
    if (plan == null) return noPlan("workout")
    planHeader(plan.name, plan.notes)
    items(plan.sortedDays, key = { it.id }) { day ->
        InfoCard {
            Text(day.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            day.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            day.sortedExercises.forEachIndexed { index, we ->
                if (index > 0) HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().highlightClickable { onOpenExercise(we.id) }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    we.exercise?.let { ExerciseThumbnail(it, size = 40.dp) }
                    Column(Modifier.weight(1f)) {
                        Text(we.exercise?.name ?: "Exercise", style = MaterialTheme.typography.bodyLarge)
                        Text(prescription(we), style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.accText)
                        we.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "How to do it", tint = LocalAppColors.current.label3)
                }
            }
            if (day.exercises.isEmpty()) Text("Rest day", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.mealSection(plan: MealPlan?) {
    if (plan == null) return noPlan("meal")
    planHeader(plan.name, plan.notes)
    item {
        val total = plan.macros()
        InfoCard {
            Text("Daily totals", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            MacroDonut(total)
            val chart = LocalChartColors.current
            TargetBar("Calories", total.kcal, plan.targetKcal?.toDouble(), "kcal")
            TargetBar("Protein", total.protein, plan.targetProtein?.toDouble(), "g", color = chart.protein)
            TargetBar("Carbs", total.carbs, plan.targetCarbs?.toDouble(), "g", color = chart.carbs)
            TargetBar("Fat", total.fat, plan.targetFat?.toDouble(), "g", color = chart.fat)
        }
    }
    items(plan.sortedMeals, key = { it.id }) { meal ->
        InfoCard {
            Row {
                Text(meal.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                meal.timeHint?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(macroLine(meal.macros()), style = MaterialTheme.typography.bodySmall, color = LocalAppColors.current.accText)
            meal.sortedItems.forEach { item ->
                val food = item.food ?: return@forEach
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(food.name, style = MaterialTheme.typography.bodyLarge)
                        item.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Column {
                        Text("${item.grams.toInt()} g", style = MaterialTheme.typography.bodyMedium)
                        Text("${food.macrosFor(item.grams).kcal.toInt()} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.supplementSection(plan: SupplementPlan?) {
    if (plan == null) return noPlan("supplement")
    planHeader(plan.name, plan.notes)
    plan.sortedItems.groupBy { it.timing }.entries.sortedBy { it.key.ordinal }.forEach { (timing, items) ->
        item(key = "timing-${timing.name}") { SectionHeader(timing.label) }
        items(items, key = { it.id }) { item ->
            InfoCard {
                Text(item.supplement?.name ?: "Supplement", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                item.dosage?.takeIf { it.isNotBlank() }?.let { Text(it, color = LocalAppColors.current.accText) }
                item.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
