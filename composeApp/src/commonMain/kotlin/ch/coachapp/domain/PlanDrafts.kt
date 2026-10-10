package ch.coachapp.domain

import ch.coachapp.data.model.Exercise
import ch.coachapp.data.model.Food
import ch.coachapp.data.model.MealPlan
import ch.coachapp.data.model.Supplement
import ch.coachapp.data.model.SupplementPlan
import ch.coachapp.data.model.SupplementTiming
import ch.coachapp.data.model.WorkoutPlan
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Editable, in-memory plan trees used by the plan builders. They are saved in one
 * round trip through the save_*_plan RPCs, which keep existing row ids stable.
 */
object DraftKeys {
    private var counter = 0L
    fun next(): Long = ++counter
}

private fun <T> List<T>.move(index: Int, delta: Int): List<T> {
    val target = index + delta
    if (index !in indices || target !in indices) return this
    return toMutableList().apply { add(target, removeAt(index)) }
}

fun <T> List<T>.moveUp(index: Int) = move(index, -1)
fun <T> List<T>.moveDown(index: Int) = move(index, 1)

// ---------------------------------------------------------------------------
// Workout
// ---------------------------------------------------------------------------

data class WorkoutDraft(
    val id: String? = null,
    val clientId: String? = null,
    val name: String = "",
    val notes: String = "",
    val days: List<WorkoutDayDraft> = listOf(WorkoutDayDraft(title = "Day 1")),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("client_id", clientId)
        put("name", name.trim())
        put("notes", notes.trim().ifEmpty { null })
        putJsonArray("days") {
            days.forEach { day ->
                addJsonObject {
                    put("id", day.id)
                    put("title", day.title.trim().ifEmpty { "Day" })
                    put("notes", day.notes.trim().ifEmpty { null })
                    putJsonArray("exercises") {
                        day.exercises.forEach { ex ->
                            addJsonObject {
                                put("id", ex.id)
                                put("exercise_id", ex.exercise.id)
                                put("sets", ex.sets)
                                put("reps", ex.reps.trim().ifEmpty { "10" })
                                put("rest_sec", ex.restSec)
                                put("tempo", ex.tempo.trim().ifEmpty { null })
                                put("target_rpe", ex.targetRpe)
                                put("notes", ex.notes.trim().ifEmpty { null })
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun from(plan: WorkoutPlan) = WorkoutDraft(
            id = plan.id,
            clientId = plan.clientId,
            name = plan.name,
            notes = plan.notes.orEmpty(),
            days = plan.sortedDays.map { day ->
                WorkoutDayDraft(
                    id = day.id,
                    title = day.title,
                    notes = day.notes.orEmpty(),
                    exercises = day.sortedExercises.mapNotNull { we ->
                        val exercise = we.exercise ?: return@mapNotNull null
                        WorkoutExerciseDraft(
                            id = we.id,
                            exercise = exercise,
                            sets = we.sets,
                            reps = we.reps,
                            restSec = we.restSec,
                            tempo = we.tempo.orEmpty(),
                            targetRpe = we.targetRpe,
                            notes = we.notes.orEmpty(),
                        )
                    },
                )
            },
        )
    }
}

data class WorkoutDayDraft(
    val key: Long = DraftKeys.next(),
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val exercises: List<WorkoutExerciseDraft> = emptyList(),
)

data class WorkoutExerciseDraft(
    val key: Long = DraftKeys.next(),
    val id: String? = null,
    val exercise: Exercise,
    val sets: Int = 3,
    val reps: String = "10",
    val restSec: Int = 90,
    val tempo: String = "",
    val targetRpe: Double? = null,
    val notes: String = "",
)

// ---------------------------------------------------------------------------
// Meals
// ---------------------------------------------------------------------------

data class MealPlanDraft(
    val id: String? = null,
    val clientId: String? = null,
    val name: String = "",
    val notes: String = "",
    val targetKcal: Int? = null,
    val targetProtein: Int? = null,
    val targetCarbs: Int? = null,
    val targetFat: Int? = null,
    val meals: List<MealDraft> = listOf(MealDraft(name = "Breakfast")),
) {
    val macros: Macros get() = meals.map { it.macros }.sum()

    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("client_id", clientId)
        put("name", name.trim())
        put("notes", notes.trim().ifEmpty { null })
        put("target_kcal", targetKcal)
        put("target_protein", targetProtein)
        put("target_carbs", targetCarbs)
        put("target_fat", targetFat)
        putJsonArray("meals") {
            meals.forEach { meal ->
                addJsonObject {
                    put("id", meal.id)
                    put("name", meal.name.trim().ifEmpty { "Meal" })
                    put("time_hint", meal.timeHint.trim().ifEmpty { null })
                    putJsonArray("items") {
                        meal.items.forEach { item ->
                            addJsonObject {
                                put("id", item.id)
                                put("food_id", item.food.id)
                                put("grams", item.grams)
                                put("notes", item.notes.trim().ifEmpty { null })
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun from(plan: MealPlan) = MealPlanDraft(
            id = plan.id,
            clientId = plan.clientId,
            name = plan.name,
            notes = plan.notes.orEmpty(),
            targetKcal = plan.targetKcal,
            targetProtein = plan.targetProtein,
            targetCarbs = plan.targetCarbs,
            targetFat = plan.targetFat,
            meals = plan.sortedMeals.map { meal ->
                MealDraft(
                    id = meal.id,
                    name = meal.name,
                    timeHint = meal.timeHint.orEmpty(),
                    items = meal.sortedItems.mapNotNull { item ->
                        val food = item.food ?: return@mapNotNull null
                        MealItemDraft(id = item.id, food = food, grams = item.grams, notes = item.notes.orEmpty())
                    },
                )
            },
        )
    }
}

data class MealDraft(
    val key: Long = DraftKeys.next(),
    val id: String? = null,
    val name: String = "",
    val timeHint: String = "",
    val items: List<MealItemDraft> = emptyList(),
) {
    val macros: Macros get() = items.map { it.food.macrosFor(it.grams) }.sum()
}

data class MealItemDraft(
    val key: Long = DraftKeys.next(),
    val id: String? = null,
    val food: Food,
    val grams: Double = 100.0,
    val notes: String = "",
)

// ---------------------------------------------------------------------------
// Supplements
// ---------------------------------------------------------------------------

data class SupplementPlanDraft(
    val id: String? = null,
    val clientId: String? = null,
    val name: String = "",
    val notes: String = "",
    val items: List<SupplementItemDraft> = emptyList(),
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("id", id)
        put("client_id", clientId)
        put("name", name.trim())
        put("notes", notes.trim().ifEmpty { null })
        putJsonArray("items") {
            items.forEach { item ->
                addJsonObject {
                    put("id", item.id)
                    put("supplement_id", item.supplement.id)
                    put("dosage", item.dosage.trim().ifEmpty { null })
                    put("timing", item.timing.apiValue)
                    put("notes", item.notes.trim().ifEmpty { null })
                }
            }
        }
    }

    companion object {
        fun from(plan: SupplementPlan) = SupplementPlanDraft(
            id = plan.id,
            clientId = plan.clientId,
            name = plan.name,
            notes = plan.notes.orEmpty(),
            items = plan.sortedItems.mapNotNull { item ->
                val supplement = item.supplement ?: return@mapNotNull null
                SupplementItemDraft(
                    id = item.id,
                    supplement = supplement,
                    dosage = item.dosage.orEmpty(),
                    timing = item.timing,
                    notes = item.notes.orEmpty(),
                )
            },
        )
    }
}

data class SupplementItemDraft(
    val key: Long = DraftKeys.next(),
    val id: String? = null,
    val supplement: Supplement,
    val dosage: String = supplement.dosageLabel,
    val timing: SupplementTiming = SupplementTiming.Morning,
    val notes: String = "",
)
