package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.Exercise
import ch.coachapp.data.model.ExerciseVideo
import ch.coachapp.data.model.Food
import ch.coachapp.data.model.Supplement
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Filters for the exercise library; null means "any". */
data class ExerciseFilter(
    val query: String = "",
    val bodyPart: String? = null,
    val equipment: String? = null,
)

/** Exercise, food and supplement libraries: global items plus the coach's own. */
interface LibraryRepository {
    /**
     * A page of the exercise library. The imported library is ~1,300 rows with a paragraph of
     * instructions each, so the list query filters and pages on the server and leaves
     * `instructions` behind; [exercise] fetches the full row when one is opened.
     */
    suspend fun exercises(filter: ExerciseFilter = ExerciseFilter(), limit: Int = 60, offset: Int = 0): Result<List<Exercise>>

    suspend fun exercise(id: String): Result<Exercise>

    /** The distinct body parts and equipment present in the library, for the filter chips. */
    suspend fun exerciseFacets(): Result<ExerciseFacets>
    suspend fun foods(): Result<List<Food>>
    suspend fun supplements(): Result<List<Supplement>>
    suspend fun saveExercise(exercise: Exercise): Result<Exercise>
    suspend fun saveFood(food: Food): Result<Food>
    suspend fun saveSupplement(supplement: Supplement): Result<Supplement>
    suspend fun deleteExercise(id: String): Result<Unit>
    suspend fun deleteFood(id: String): Result<Unit>
    suspend fun deleteSupplement(id: String): Result<Unit>

    /** Coach: uploads a demo video for [exerciseId], replacing any previous one. */
    suspend fun uploadExerciseVideo(exerciseId: String, bytes: ByteArray, extension: String): Result<Unit>

    /** Coach: uses a link (YouTube, Vimeo, a file URL) as the demo video for [exerciseId]. */
    suspend fun linkExerciseVideo(exerciseId: String, url: String): Result<Unit>
    suspend fun removeExerciseVideo(exerciseId: String): Result<Unit>

    /** A URL the player or browser can open: signed for uploads, as entered for links. */
    suspend fun videoUrl(video: ExerciseVideo): Result<String>
}

const val ExerciseVideoBucket = "exercise-videos"

/** Distinct values offered as filter chips above the exercise list. */
@Serializable
data class ExerciseFacets(
    @SerialName("body_parts") val bodyParts: List<String> = emptyList(),
    val equipment: List<String> = emptyList(),
)

/** Everything a list row needs; `instructions` is deliberately absent. */
private val ExerciseListColumns = Columns.list(
    "id", "coach_id", "name", "muscle_group", "equipment", "video_url",
    "ext_id", "body_part", "target", "secondary_muscles", "image_path", "gif_path", "exercise_videos(*)",
)

class SupabaseLibraryRepository(private val client: SupabaseClient) : LibraryRepository {

    private fun me(): String = client.auth.currentUserOrNull()?.id ?: throw AppException("Not signed in")

    override suspend fun exercises(filter: ExerciseFilter, limit: Int, offset: Int) = apiCall {
        client.from("exercises").select(ExerciseListColumns) {
            filter {
                filter.bodyPart?.let { eq("body_part", it) }
                filter.equipment?.let { eq("equipment", it) }
                filter.query.trim().takeIf { it.isNotEmpty() }?.let { ilike("name", "%$it%") }
            }
            order("name", Order.ASCENDING)
            range(offset.toLong(), (offset + limit - 1).toLong())
        }.decodeList<Exercise>()
    }

    override suspend fun exercise(id: String) = apiCall {
        client.from("exercises").select(Columns.raw("*, exercise_videos(*)")) { filter { eq("id", id) } }.decodeSingle<Exercise>()
    }

    override suspend fun exerciseFacets() = apiCall {
        // Aggregated in the database: selecting the two columns and reducing here would be capped
        // at PostgREST's db-max-rows (1,000) and silently lose any facet in the tail.
        client.postgrest.rpc("exercise_facets").decodeAs<ExerciseFacets>()
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
            put("body_part", exercise.bodyPart?.trim()?.ifEmpty { null })
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

    override suspend fun uploadExerciseVideo(exerciseId: String, bytes: ByteArray, extension: String) = apiCall {
        val coachId = me()
        val previous = currentVideo(coachId, exerciseId)
        val path = "$coachId/$exerciseId.${extension.lowercase()}"
        client.storage.from(ExerciseVideoBucket).upload(path, bytes) { upsert = true }
        upsertVideo(coachId, exerciseId, storagePath = path, externalUrl = null)
        // A new file type leaves the old upload under another name.
        previous?.storagePath?.takeIf { it != path }?.let { client.storage.from(ExerciseVideoBucket).delete(it) }
        Unit
    }

    override suspend fun linkExerciseVideo(exerciseId: String, url: String) = apiCall {
        val coachId = me()
        val previous = currentVideo(coachId, exerciseId)
        upsertVideo(coachId, exerciseId, storagePath = null, externalUrl = url.trim())
        previous?.storagePath?.let { client.storage.from(ExerciseVideoBucket).delete(it) }
        Unit
    }

    override suspend fun removeExerciseVideo(exerciseId: String) = apiCall {
        val coachId = me()
        val previous = currentVideo(coachId, exerciseId)
        client.from("exercise_videos").delete {
            filter {
                eq("coach_id", coachId)
                eq("exercise_id", exerciseId)
            }
        }
        previous?.storagePath?.let { client.storage.from(ExerciseVideoBucket).delete(it) }
        Unit
    }

    override suspend fun videoUrl(video: ExerciseVideo) = apiCall {
        video.storagePath?.let { client.storage.from(ExerciseVideoBucket).createSignedUrl(it, 2.hours) }
            ?: video.externalUrl
            ?: throw AppException("This video has no source")
    }

    private suspend fun currentVideo(coachId: String, exerciseId: String): ExerciseVideo? =
        client.from("exercise_videos").select {
            filter {
                eq("coach_id", coachId)
                eq("exercise_id", exerciseId)
            }
        }.decodeSingleOrNull<ExerciseVideo>()

    private suspend fun upsertVideo(coachId: String, exerciseId: String, storagePath: String?, externalUrl: String?) {
        client.from("exercise_videos").upsert(buildJsonObject {
            put("coach_id", coachId)
            put("exercise_id", exerciseId)
            put("storage_path", storagePath)
            put("external_url", externalUrl)
            put("updated_at", Clock.System.now().toString())
        }) { onConflict = "coach_id,exercise_id" }
    }

    private suspend fun delete(table: String, id: String) = apiCall {
        client.from(table).delete { filter { eq("id", id) } }
        Unit
    }
}
