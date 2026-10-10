package ch.coachapp.domain

import ch.coachapp.data.model.Food
import ch.coachapp.data.model.Meal
import ch.coachapp.data.model.MealPlan

data class Macros(val kcal: Double = 0.0, val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    operator fun plus(other: Macros) = Macros(kcal + other.kcal, protein + other.protein, carbs + other.carbs, fat + other.fat)

    companion object {
        val Zero = Macros()
    }
}

/** Food values are stored per 100 g. */
fun Food.macrosFor(grams: Double): Macros {
    val f = grams / 100.0
    return Macros(kcal * f, protein * f, carbs * f, fat * f)
}

fun Iterable<Macros>.sum(): Macros = fold(Macros.Zero) { acc, m -> acc + m }

fun Meal.macros(): Macros = items.mapNotNull { item -> item.food?.macrosFor(item.grams) }.sum()

fun MealPlan.macros(): Macros = meals.map { it.macros() }.sum()
