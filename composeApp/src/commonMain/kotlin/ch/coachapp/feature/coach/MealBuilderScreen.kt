package ch.coachapp.feature.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import ch.coachapp.core.ui.ButtonSize
import ch.coachapp.core.ui.NeutralButton
import ch.coachapp.core.ui.GhostButton
import ch.coachapp.core.ui.AppCard
import ch.coachapp.core.ui.AppTextField
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.fmt
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.BarAction
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.ErrorBox
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.LocalChartColors
import ch.coachapp.core.ui.MacroDonut
import ch.coachapp.core.ui.TargetBar
import ch.coachapp.data.model.Food
import ch.coachapp.data.repository.LibraryRepository
import ch.coachapp.data.repository.PlanRepository
import ch.coachapp.domain.Macros
import ch.coachapp.domain.MealDraft
import ch.coachapp.domain.MealItemDraft
import ch.coachapp.domain.MealPlanDraft
import ch.coachapp.domain.macrosFor
import ch.coachapp.domain.moveDown
import ch.coachapp.domain.moveUp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

data class MealBuilderUiState(
    val loading: Boolean = true,
    val loadError: String? = null,
    val draft: MealPlanDraft = MealPlanDraft(),
    val foods: List<Food> = emptyList(),
    val saving: Boolean = false,
    val saveError: String? = null,
    val saved: Boolean = false,
) {
    val title: String
        get() = when {
            draft.id != null -> "Edit meal plan"
            draft.clientId != null -> "New client meal plan"
            else -> "New meal template"
        }
}

class MealBuilderViewModel(
    private val route: MealBuilderRoute,
    private val plans: PlanRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MealBuilderUiState(draft = MealPlanDraft(clientId = route.clientId)))
    val state: StateFlow<MealBuilderUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            val foods = library.foods()
            val draft = route.planId?.let { id -> plans.mealPlan(id).map(MealPlanDraft::from) }
            val error = foods.exceptionOrNull() ?: draft?.exceptionOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    loadError = error?.message,
                    foods = foods.getOrDefault(emptyList()),
                    draft = draft?.getOrNull() ?: it.draft,
                )
            }
        }
    }

    private fun edit(transform: (MealPlanDraft) -> MealPlanDraft) = _state.update { it.copy(draft = transform(it.draft), saveError = null) }

    private fun editMeal(mealKey: Long, transform: (MealDraft) -> MealDraft) =
        edit { d -> d.copy(meals = d.meals.map { if (it.key == mealKey) transform(it) else it }) }

    fun setName(value: String) = edit { it.copy(name = value) }
    fun setNotes(value: String) = edit { it.copy(notes = value) }
    fun setTargets(kcal: Int?, protein: Int?, carbs: Int?, fat: Int?) =
        edit { it.copy(targetKcal = kcal, targetProtein = protein, targetCarbs = carbs, targetFat = fat) }

    fun addMeal() = edit { it.copy(meals = it.meals + MealDraft(name = "Meal ${it.meals.size + 1}")) }
    fun setMealName(mealKey: Long, name: String) = editMeal(mealKey) { it.copy(name = name) }
    fun setMealTime(mealKey: Long, time: String) = editMeal(mealKey) { it.copy(timeHint = time) }
    fun removeMeal(mealKey: Long) = edit { d -> d.copy(meals = d.meals.filterNot { it.key == mealKey }) }
    fun moveMealUp(index: Int) = edit { it.copy(meals = it.meals.moveUp(index)) }
    fun moveMealDown(index: Int) = edit { it.copy(meals = it.meals.moveDown(index)) }

    fun addItem(mealKey: Long, food: Food) = editMeal(mealKey) { it.copy(items = it.items + MealItemDraft(food = food)) }
    fun setItemGrams(mealKey: Long, itemKey: Long, grams: Double) =
        editMeal(mealKey) { meal -> meal.copy(items = meal.items.map { if (it.key == itemKey) it.copy(grams = grams) else it }) }
    fun removeItem(mealKey: Long, itemKey: Long) =
        editMeal(mealKey) { meal -> meal.copy(items = meal.items.filterNot { it.key == itemKey }) }

    fun save() {
        val draft = _state.value.draft
        if (draft.name.isBlank()) {
            _state.update { it.copy(saveError = "Give the plan a name") }
            return
        }
        _state.update { it.copy(saving = true, saveError = null) }
        viewModelScope.launch {
            plans.saveMealPlan(draft)
                .onSuccess { _state.update { it.copy(saving = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(saving = false, saveError = e.message) } }
        }
    }
}

@Composable
fun MealBuilderDestination(
    route: MealBuilderRoute,
    onDone: () -> Unit,
    viewModel: MealBuilderViewModel = koinViewModel { parametersOf(route) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    MealBuilderScreen(state, viewModel, onBack = onDone)
}

fun Macros.summary(): String = "${kcal.fmt(0)} kcal · P ${protein.fmt(0)} · C ${carbs.fmt(0)} · F ${fat.fmt(0)}"

@Composable
fun MealBuilderScreen(state: MealBuilderUiState, actions: MealBuilderViewModel, onBack: () -> Unit) {
    var pickingFor by remember { mutableStateOf<Long?>(null) }
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
                            AppTextField(draft.name, actions::setName, label = { Text("Plan name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            AppTextField(draft.notes, actions::setNotes, label = { Text("Notes for the client") }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item { TargetsCard(draft, actions) }
                    itemsIndexed(draft.meals, key = { _, meal -> meal.key }) { index, meal ->
                        MealCard(
                            meal = meal,
                            index = index,
                            isLast = index == draft.meals.lastIndex,
                            actions = actions,
                            onAddFood = { pickingFor = meal.key },
                        )
                    }
                    item {
                        NeutralButton("Add meal", { actions.addMeal() }, icon = Icons.Default.Add)
                    }
                }
            }
        }
    }

    pickingFor?.let { mealKey ->
        LibraryPickerDialog(
            title = "Add food",
            items = state.foods,
            itemLabel = { it.name },
            itemSubtitle = { "${it.kcal.fmt(0)} kcal / 100 g" },
            onPick = { actions.addItem(mealKey, it); pickingFor = null },
            onDismiss = { pickingFor = null },
        )
    }
}

@Composable
private fun TargetsCard(draft: MealPlanDraft, actions: MealBuilderViewModel) {
    val totals = draft.macros
    InfoCard {
        Text("Daily targets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntField(draft.targetKcal, { actions.setTargets(it, draft.targetProtein, draft.targetCarbs, draft.targetFat) }, "kcal", Modifier.weight(1f))
            IntField(draft.targetProtein, { actions.setTargets(draft.targetKcal, it, draft.targetCarbs, draft.targetFat) }, "Protein", Modifier.weight(1f), suffix = "g")
            IntField(draft.targetCarbs, { actions.setTargets(draft.targetKcal, draft.targetProtein, it, draft.targetFat) }, "Carbs", Modifier.weight(1f), suffix = "g")
            IntField(draft.targetFat, { actions.setTargets(draft.targetKcal, draft.targetProtein, draft.targetCarbs, it) }, "Fat", Modifier.weight(1f), suffix = "g")
        }
        MacroDonut(totals)
        val chart = LocalChartColors.current
        TargetBar("Calories", totals.kcal, draft.targetKcal?.toDouble(), "kcal")
        TargetBar("Protein", totals.protein, draft.targetProtein?.toDouble(), "g", color = chart.protein)
        TargetBar("Carbs", totals.carbs, draft.targetCarbs?.toDouble(), "g", color = chart.carbs)
        TargetBar("Fat", totals.fat, draft.targetFat?.toDouble(), "g", color = chart.fat)
    }
}

@Composable
private fun MealCard(meal: MealDraft, index: Int, isLast: Boolean, actions: MealBuilderViewModel, onAddFood: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(meal.name, { actions.setMealName(meal.key, it) }, label = { Text("Meal") }, singleLine = true, modifier = Modifier.weight(2f))
                AppTextField(meal.timeHint, { actions.setMealTime(meal.key, it.take(10)) }, label = { Text("Time") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.macros.summary(), style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.accText, modifier = Modifier.weight(1f))
                IconButton(onClick = { actions.moveMealUp(index) }, enabled = index > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move meal up") }
                IconButton(onClick = { actions.moveMealDown(index) }, enabled = !isLast) { Icon(Icons.Default.KeyboardArrowDown, "Move meal down") }
                IconButton(onClick = { actions.removeMeal(meal.key) }) { Icon(Icons.Default.Delete, "Remove meal") }
            }
            meal.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(item.food.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            item.food.macrosFor(item.grams).summary(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DecimalField(
                        value = item.grams,
                        onValueChange = { v -> v?.let { actions.setItemGrams(meal.key, item.key, it) } },
                        label = "Amount",
                        suffix = "g",
                        modifier = Modifier.width(120.dp),
                    )
                    IconButton(onClick = { actions.removeItem(meal.key, item.key) }) { Icon(Icons.Default.Close, "Remove food") }
                }
            }
            GhostButton("Add food", onAddFood, size = ButtonSize.Sm, icon = Icons.Default.Add)
        }
    }
}
