package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.Exercise
import ch.coachapp.data.model.Food
import ch.coachapp.data.model.Supplement
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Exercise, food and supplement libraries: global items plus the coach's own. */
interface LibraryRepository {
    suspend fun exercises(): Result<List<Exercise>>
    suspend fun foods(): Result<List<Food>>
    suspend fun supplements(): Result<List<Supplement>>
    suspend fun saveExercise(exercise: Exercise): Result<Exercise>
    suspend fun saveFood(food: Food): Result<Food>
    suspend fun saveSupplement(supplement: Supplement): Result<Supplement>
    suspend fun deleteExercise(id: String): Result<Unit>
    suspend fun deleteFood(id: String): Result<Unit>
    suspend fun deleteSupplement(id: String): Result<Unit>
}

class SupabaseLibraryRepository(private val client: SupabaseClient) : LibraryRepository {

    private fun me(): String = client.auth.currentUserOrNull()?.id ?: throw AppException("Not signed in")

    override suspend fun exercises() = apiCall {
        client.from("exercises").select { order("name", Order.ASCENDING) }.decodeList<Exercise>()
    }

    override suspend fun foods() = apiCall {
        client.from("foods").select { order("name", Order.ASCENDING) }.decodeList<Food>()
    }

    override suspend fun supplements() = apiCall {
        client.from("supplements").select { order("name", Order.ASCENDING) }.decodeList<Supplement>()
    }

    override suspend fun saveExercise(exercise: Exercise) = apiCall {
        save("exercises", exercise.id) {
            put("name", exercise.name.trim())
            put("muscle_group", exercise.muscleGroup?.trim()?.ifEmpty { null })
            put("equipment", exercise.equipment?.trim()?.ifEmpty { null })
            put("video_url", exercise.videoUrl?.trim()?.ifEmpty { null })
            put("instructions", exercise.instructions?.trim()?.ifEmpty { null })
        }.decodeSingle<Exercise>()
    }

    override suspend fun saveFood(food: Food) = apiCall {
        save("foods", food.id) {
            put("name", food.name.trim())
            put("kcal", food.kcal)
            put("protein", food.protein)
            put("carbs", food.carbs)
            put("fat", food.fat)
            put("serving_unit", food.servingUnit.ifBlank { "g" })
        }.decodeSingle<Food>()
    }

    override suspend fun saveSupplement(supplement: Supplement) = apiCall {
        save("supplements", supplement.id) {
            put("name", supplement.name.trim())
            put("default_dosage", supplement.defaultDosage?.trim()?.ifEmpty { null })
            put("unit", supplement.unit?.trim()?.ifEmpty { null })
            put("notes", supplement.notes?.trim()?.ifEmpty { null })
        }.decodeSingle<Supplement>()
    }

    /** Inserts when [id] is blank, otherwise updates. Both return the stored row. */
    private suspend fun save(table: String, id: String, fields: JsonObjectBuilder.() -> Unit) =
        if (id.isBlank()) {
            val body: JsonObject = buildJsonObject { put("coach_id", me()); fields() }
            client.from(table).insert(body) { select() }
        } else {
            client.from(table).update(buildJsonObject(fields)) {
                select()
                filter { eq("id", id) }
            }
        }

    override suspend fun deleteExercise(id: String) = delete("exercises", id)
    override suspend fun deleteFood(id: String) = delete("foods", id)
    override suspend fun deleteSupplement(id: String) = delete("supplements", id)

    private suspend fun delete(table: String, id: String) = apiCall {
        client.from(table).delete { filter { eq("id", id) } }
        Unit
    }
}
