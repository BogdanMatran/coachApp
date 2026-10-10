package ch.coachapp.domain

import ch.coachapp.data.model.Exercise
import ch.coachapp.data.model.Supplement
import ch.coachapp.data.model.SupplementTiming
import ch.coachapp.data.model.WorkoutDay
import ch.coachapp.data.model.WorkoutExercise
import ch.coachapp.data.model.WorkoutPlan
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanDraftsTest {

    private val squat = Exercise(id = "ex-squat", name = "Squat")
    private val bench = Exercise(id = "ex-bench", name = "Bench")

    @Test
    fun workoutDraftRoundTripsPlanInDisplayOrder() {
        val plan = WorkoutPlan(
            id = "plan", coachId = "coach", clientId = "client", name = "PPL",
            days = listOf(
                WorkoutDay(id = "d2", planId = "plan", dayIndex = 1, title = "Push", exercises = listOf(
                    WorkoutExercise(id = "we2", dayId = "d2", exerciseId = bench.id, position = 0, exercise = bench),
                )),
                WorkoutDay(id = "d1", planId = "plan", dayIndex = 0, title = "Legs", exercises = listOf(
                    WorkoutExercise(id = "we1b", dayId = "d1", exerciseId = squat.id, position = 1, sets = 5, reps = "5", exercise = squat),
                    WorkoutExercise(id = "we1a", dayId = "d1", exerciseId = bench.id, position = 0, exercise = bench),
                )),
            ),
        )

        val draft = WorkoutDraft.from(plan)

        assertEquals(listOf("Legs", "Push"), draft.days.map { it.title })
        assertEquals(listOf("we1a", "we1b"), draft.days[0].exercises.map { it.id })
        assertEquals(5, draft.days[0].exercises[1].sets)
    }

    @Test
    fun workoutJsonKeepsIdsAndBlanksBecomeNull() {
        val draft = WorkoutDraft(
            id = null,
            clientId = "client",
            name = "  Full body ",
            notes = "   ",
            days = listOf(
                WorkoutDayDraft(id = "d1", title = "A", exercises = listOf(
                    WorkoutExerciseDraft(id = "we1", exercise = squat, sets = 4, reps = "", targetRpe = 8.0),
                )),
            ),
        )

        val json = draft.toJson()
        assertEquals(JsonNull, json["id"])
        assertEquals("Full body", json["name"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, json["notes"])

        val ex = json["days"]!!.jsonArray[0].jsonObject["exercises"]!!.jsonArray[0].jsonObject
        assertEquals("we1", ex["id"]!!.jsonPrimitive.content)
        assertEquals("ex-squat", ex["exercise_id"]!!.jsonPrimitive.content)
        assertEquals("10", ex["reps"]!!.jsonPrimitive.content)
        assertEquals("8.0", ex["target_rpe"]!!.jsonPrimitive.content)
    }

    @Test
    fun supplementJsonUsesApiTimingValues() {
        val draft = SupplementPlanDraft(
            name = "Basics",
            items = listOf(SupplementItemDraft(supplement = Supplement(id = "s1", name = "Creatine"), timing = SupplementTiming.PreWorkout)),
        )
        val item = draft.toJson()["items"]!!.jsonArray[0].jsonObject
        assertEquals("pre_workout", item["timing"]!!.jsonPrimitive.content)
    }

    @Test
    fun moveUpAndDownSwapNeighboursAndIgnoreEdges() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), list.moveUp(1))
        assertEquals(listOf("a", "c", "b"), list.moveDown(1))
        assertEquals(list, list.moveUp(0))
        assertEquals(list, list.moveDown(2))
    }
}
