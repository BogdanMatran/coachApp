package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.DayActivity
import ch.coachapp.data.model.MealLog
import ch.coachapp.data.model.SetLog
import ch.coachapp.data.model.SupplementLog
import ch.coachapp.data.model.WorkoutLog
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One performed set, as entered in the workout session. */
data class SetEntry(
    val workoutExerciseId: String?,
    val exerciseId: String,
    val setNo: Int,
    val reps: Int?,
    val weight: Double?,
    val rpe: Double?,
)

/** Client logs. Clients write their own; coaches read their clients' logs. */
interface LogRepository {
    suspend fun logWorkout(workoutDayId: String?, dayTitle: String, notes: String?, sets: List<SetEntry>): Result<String>
    suspend fun workoutHistory(clientId: String, limit: Long = 30): Result<List<WorkoutLog>>

    /** Most recent logged sets per exercise id, used to pre-fill a session. */
    suspend fun lastSets(clientId: String, exerciseIds: Set<String>): Result<Map<String, List<SetLog>>>

    suspend fun mealLogs(clientId: String, from: LocalDate, to: LocalDate): Result<List<MealLog>>
    suspend fun setMealDone(mealId: String, date: LocalDate, done: Boolean): Result<Unit>

    suspend fun supplementLogs(clientId: String, from: LocalDate, to: LocalDate): Result<List<SupplementLog>>
    suspend fun setSupplementTaken(itemId: String, date: LocalDate, taken: Boolean): Result<Unit>

    /** One row per day for the last [days] days, oldest first. */
    suspend fun dailyActivity(clientId: String, days: Int = 182): Result<List<DayActivity>>
}

private val HistoryColumns = Columns.raw("*, set_logs(*, exercises(id, name))")

class SupabaseLogRepository(private val client: SupabaseClient) : LogRepository {

    private fun me(): String = client.auth.currentUserOrNull()?.id ?: throw AppException("Not signed in")

    override suspend fun logWorkout(workoutDayId: String?, dayTitle: String, notes: String?, sets: List<SetEntry>) = apiCall {
        val log = client.from("workout_logs").insert(buildJsonObject {
            put("client_id", me())
            put("workout_day_id", workoutDayId)
            put("day_title", dayTitle)
            put("notes", notes?.trim()?.ifEmpty { null })
        }) { select() }.decodeSingle<WorkoutLog>()

        if (sets.isNotEmpty()) {
            client.from("set_logs").insert(buildJsonArray {
                sets.forEach { set ->
                    addJsonObject {
                        put("log_id", log.id)
                        put("workout_exercise_id", set.workoutExerciseId)
                        put("exercise_id", set.exerciseId)
                        put("set_no", set.setNo)
                        put("reps", set.reps)
                        put("weight", set.weight)
                        put("rpe", set.rpe)
                    }
                }
            })
        }
        log.id
    }

    override suspend fun workoutHistory(clientId: String, limit: Long) = apiCall {
        client.from("workout_logs").select(HistoryColumns) {
            filter { eq("client_id", clientId) }
            order("performed_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<WorkoutLog>()
    }

    override suspend fun lastSets(clientId: String, exerciseIds: Set<String>) = apiCall {
        if (exerciseIds.isEmpty()) return@apiCall emptyMap()
        val logs = workoutHistory(clientId, limit = 20).getOrThrow()
        buildMap {
            for (id in exerciseIds) {
                val sets = logs.firstNotNullOfOrNull { log ->
                    log.sets.filter { it.exerciseId == id }.sortedBy { it.setNo }.takeIf { it.isNotEmpty() }
                }
                if (sets != null) put(id, sets)
            }
        }
    }

    override suspend fun mealLogs(clientId: String, from: LocalDate, to: LocalDate) = apiCall {
        client.from("meal_logs").select {
            filter {
                eq("client_id", clientId)
                gte("log_date", from.toString())
                lte("log_date", to.toString())
            }
        }.decodeList<MealLog>()
    }

    override suspend fun setMealDone(mealId: String, date: LocalDate, done: Boolean) = apiCall {
        client.from("meal_logs").upsert(buildJsonObject {
            put("client_id", me())
            put("meal_id", mealId)
            put("log_date", date.toString())
            put("completed", done)
        }) { onConflict = "client_id,meal_id,log_date" }
        Unit
    }

    override suspend fun supplementLogs(clientId: String, from: LocalDate, to: LocalDate) = apiCall {
        client.from("supplement_logs").select {
            filter {
                eq("client_id", clientId)
                gte("log_date", from.toString())
                lte("log_date", to.toString())
            }
        }.decodeList<SupplementLog>()
    }

    override suspend fun setSupplementTaken(itemId: String, date: LocalDate, taken: Boolean) = apiCall {
        client.from("supplement_logs").upsert(buildJsonObject {
            put("client_id", me())
            put("supplement_item_id", itemId)
            put("log_date", date.toString())
            put("taken", taken)
        }) { onConflict = "client_id,supplement_item_id,log_date" }
        Unit
    }

    override suspend fun dailyActivity(clientId: String, days: Int) = apiCall {
        client.postgrest.rpc("daily_activity", buildJsonObject {
            put("p_client", clientId)
            put("p_days", days)
        }).decodeList<DayActivity>()
    }
}
