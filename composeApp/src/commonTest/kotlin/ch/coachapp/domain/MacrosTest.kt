package ch.coachapp.domain

import ch.coachapp.data.model.Food
import kotlin.test.Test
import kotlin.test.assertEquals

class MacrosTest {

    private val oats = Food(id = "oats", name = "Oats", kcal = 389.0, protein = 16.9, carbs = 66.3, fat = 6.9)
    private val chicken = Food(id = "chicken", name = "Chicken", kcal = 165.0, protein = 31.0, carbs = 0.0, fat = 3.6)

    @Test
    fun macrosScaleFromPer100g() {
        val m = oats.macrosFor(50.0)
        assertEquals(194.5, m.kcal, 1e-9)
        assertEquals(8.45, m.protein, 1e-9)
        assertEquals(33.15, m.carbs, 1e-9)
        assertEquals(3.45, m.fat, 1e-9)
    }

    @Test
    fun draftTotalsSumAllMealsAndItems() {
        val draft = MealPlanDraft(
            name = "Test",
            meals = listOf(
                MealDraft(name = "Breakfast", items = listOf(MealItemDraft(food = oats, grams = 100.0))),
                MealDraft(name = "Lunch", items = listOf(MealItemDraft(food = chicken, grams = 200.0))),
            ),
        )
        assertEquals(389.0 + 330.0, draft.macros.kcal, 1e-9)
        assertEquals(16.9 + 62.0, draft.macros.protein, 1e-9)
    }

    @Test
    fun emptyPlanHasZeroMacros() {
        assertEquals(Macros.Zero, MealPlanDraft(meals = emptyList()).macros)
    }
}
