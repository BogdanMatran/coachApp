package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.MealPlan
import ch.coachapp.data.model.PlanKind
import ch.coachapp.data.model.PlanSummary
import ch.coachapp.data.model.SupplementPlan
import ch.coachapp.data.model.WorkoutExercise
import ch.coachapp.data.model.WorkoutPlan
import ch.coachapp.domain.MealPlanDraft
import ch.coachapp.domain.SupplementPlanDraft
import ch.coachapp.domain.WorkoutDraft
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Workout, meal and supplement plans. Templates have no client; assigned plans are copies. */
interface PlanRepository {
    suspend fun templates(kind: PlanKind): Result<List<PlanSummary>>
    suspend fun clientPlans(kind: PlanKind, clientId: String): Result<List<PlanSummary>>

    suspend fun workoutPlan(id: String): Result<WorkoutPlan>

    /** One exercise of a plan, with the full exercise row (instructions, coach video). */
    suspend fun workoutExercise(id: String): Result<WorkoutExercise>
    suspend fun mealPlan(id: String): Result<MealPlan>
    suspend fun supplementPlan(id: String): Result<SupplementPlan>

    suspend fun activeWorkoutPlan(clientId: String): Result<WorkoutPlan?>
    suspend fun activeMealPlan(clientId: String): Result<MealPlan?>
    suspend fun activeSupplementPlan(clientId: String): Result<SupplementPlan?>

    /** Each save returns the plan id. */
    suspend fun saveWorkoutPlan(draft: WorkoutDraft): Result<String>
    suspend fun saveMealPlan(draft: MealPlanDraft): Result<String>
    suspend fun saveSupplementPlan(draft: SupplementPlanDraft): Result<String>

    /** Copies [templateId] to the client as their active plan and returns the new plan id. */
    suspend fun assign(kind: PlanKind, templateId: String, clientId: String): Result<String>
    suspend fun delete(kind: PlanKind, id: String): Result<Unit>
}

val PlanKind.table: String
    get() = when (this) {
        PlanKind.Workout -> "workout_plans"
        PlanKind.Meal -> "meal_plans"
        PlanKind.Supplement -> "supplement_plans"
    }

private val WorkoutColumns = Columns.raw("*, workout_days(*, workout_exercises(*, exercises(*, exercise_videos(*))))")
private val MealColumns = Columns.raw("*, meals(*, meal_items(*, foods(*)))")
private val SupplementColumns = Columns.raw("*, supplement_items(*, supplements(*))")
private val SummaryColumns = Columns.list("id", "name", "client_id", "active", "updated_at")

class SupabasePlanRepository(private val client: SupabaseClient) : PlanRepository {

    private fun me(): String = client.auth.currentUserOrNull()?.id ?: throw AppException("Not signed in")

    override suspend fun templates(kind: PlanKind) = apiCall {
        client.from(kind.table).select(SummaryColumns) {
            filter {
                eq("coach_id", me())
                exact("client_id", null)
            }
            order("name", Order.ASCENDING)
        }.decodeList<PlanSummary>()
    }

    override suspend fun clientPlans(kind: PlanKind, clientId: String) = apiCall {
        client.from(kind.table).select(SummaryColumns) {
            filter { eq("client_id", clientId) }
            order("updated_at", Order.DESCENDING)
        }.decodeList<PlanSummary>()
    }

    override suspend fun workoutExercise(id: String) = apiCall {
        client.from("workout_exercises").select(Columns.raw("*, exercises(*, exercise_videos(*))")) {
            filter { eq("id", id) }
        }.decodeSingle<WorkoutExercise>()
    }

    override suspend fun workoutPlan(id: String) = apiCall {
        client.from("workout_plans").select(WorkoutColumns) { filter { eq("id", id) } }.decodeSingle<WorkoutPlan>()
    }

    override suspend fun mealPlan(id: String) = apiCall {
        client.from("meal_plans").select(MealColumns) { filter { eq("id", id) } }.decodeSingle<MealPlan>()
    }

    override suspend fun supplementPlan(id: String) = apiCall {
        client.from("supplement_plans").select(SupplementColumns) { filter { eq("id", id) } }.decodeSingle<SupplementPlan>()
    }

    override suspend fun activeWorkoutPlan(clientId: String) = apiCall {
        client.from("workout_plans").select(WorkoutColumns) {
            filter { eq("client_id", clientId); eq("active", true) }
        }.decodeSingleOrNull<WorkoutPlan>()
    }

    override suspend fun activeMealPlan(clientId: String) = apiCall {
        client.from("meal_plans").select(MealColumns) {
            filter { eq("client_id", clientId); eq("active", true) }
        }.decodeSingleOrNull<MealPlan>()
    }

    override suspend fun activeSupplementPlan(clientId: String) = apiCall {
        client.from("supplement_plans").select(SupplementColumns) {
            filter { eq("client_id", clientId); eq("active", true) }
        }.decodeSingleOrNull<SupplementPlan>()
    }

    override suspend fun saveWorkoutPlan(draft: WorkoutDraft) = save("save_workout_plan", draft.toJson())
    override suspend fun saveMealPlan(draft: MealPlanDraft) = save("save_meal_plan", draft.toJson())
    override suspend fun saveSupplementPlan(draft: SupplementPlanDraft) = save("save_supplement_plan", draft.toJson())

    private suspend fun save(function: String, payload: JsonObject) = apiCall {
        client.postgrest.rpc(function, buildJsonObject { put("p", payload) }).decodeAs<String>()
    }

    override suspend fun assign(kind: PlanKind, templateId: String, clientId: String) = apiCall {
        client.postgrest.rpc("assign_plan", buildJsonObject {
            put("p_kind", kind.rpcName)
            put("p_template", templateId)
            put("p_client", clientId)
        }).decodeAs<String>()
    }

    override suspend fun delete(kind: PlanKind, id: String) = apiCall {
        client.from(kind.table).delete { filter { eq("id", id) } }
        Unit
    }
}
