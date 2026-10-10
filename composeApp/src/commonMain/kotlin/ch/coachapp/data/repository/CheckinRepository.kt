package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.AppJson
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.CheckinStatus
import ch.coachapp.data.model.CheckinTemplate
import ch.coachapp.data.model.Pose
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.hours
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

data class CheckinSubmission(
    val weekStart: LocalDate,
    val weight: Double?,
    val bodyFat: Double?,
    val measurements: Map<String, Double>,
    val answers: Map<String, String>,
    /** Newly picked photos only; existing ones are kept. */
    val photos: Map<Pose, ByteArray>,
)

interface CheckinRepository {
    suspend fun template(coachId: String): Result<CheckinTemplate?>
    suspend fun saveTemplate(questions: List<CheckinQuestion>): Result<Unit>

    suspend fun checkins(clientId: String): Result<List<Checkin>>
    suspend fun checkin(id: String): Result<Checkin>

    /** Client: creates or updates the check-in for the week and uploads photos. */
    suspend fun submit(coachId: String, submission: CheckinSubmission): Result<Checkin>

    /** Coach: stores feedback and marks the check-in reviewed. */
    suspend fun review(id: String, feedback: String): Result<Unit>

    suspend fun photoUrl(storagePath: String): Result<String>
}

const val CheckinPhotoBucket = "checkin-photos"
private val CheckinColumns = Columns.raw("*, checkin_photos(*)")

class SupabaseCheckinRepository(private val client: SupabaseClient) : CheckinRepository {

    private fun me(): String = client.auth.currentUserOrNull()?.id ?: throw AppException("Not signed in")

    override suspend fun template(coachId: String) = apiCall {
        client.from("checkin_templates").select { filter { eq("coach_id", coachId) } }.decodeSingleOrNull<CheckinTemplate>()
    }

    override suspend fun saveTemplate(questions: List<CheckinQuestion>) = apiCall {
        client.from("checkin_templates").upsert(buildJsonObject {
            put("coach_id", me())
            put("questions", AppJson.encodeToJsonElement(questions))
        }) { onConflict = "coach_id" }
        Unit
    }

    override suspend fun checkins(clientId: String) = apiCall {
        client.from("checkins").select(CheckinColumns) {
            filter { eq("client_id", clientId) }
            order("week_start", Order.DESCENDING)
        }.decodeList<Checkin>()
    }

    override suspend fun checkin(id: String) = apiCall {
        client.from("checkins").select(CheckinColumns) { filter { eq("id", id) } }.decodeSingle<Checkin>()
    }

    override suspend fun submit(coachId: String, submission: CheckinSubmission) = apiCall {
        val clientId = me()
        val saved = client.from("checkins").upsert(buildJsonObject {
            put("client_id", clientId)
            put("coach_id", coachId)
            put("week_start", submission.weekStart.toString())
            put("weight", submission.weight)
            put("body_fat", submission.bodyFat)
            putJsonObject("measurements") { submission.measurements.forEach { (k, v) -> put(k, v) } }
            putJsonObject("answers") { submission.answers.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        }) {
            onConflict = "client_id,week_start"
            select()
        }.decodeSingle<Checkin>()

        val bucket = client.storage.from(CheckinPhotoBucket)
        submission.photos.forEach { (pose, bytes) ->
            val path = "$clientId/${saved.id}/${pose.name.lowercase()}.jpg"
            bucket.upload(path, bytes) { upsert = true }
            client.from("checkin_photos").upsert(buildJsonObject {
                put("checkin_id", saved.id)
                put("storage_path", path)
                put("pose", pose.name.lowercase())
            }) { onConflict = "checkin_id,pose" }
        }
        checkin(saved.id).getOrThrow()
    }

    override suspend fun review(id: String, feedback: String) = apiCall {
        client.from("checkins").update(buildJsonObject {
            put("status", "reviewed")
            put("coach_feedback", feedback.trim())
        }) { filter { eq("id", id) } }
        Unit
    }

    override suspend fun photoUrl(storagePath: String) = apiCall {
        client.storage.from(CheckinPhotoBucket).createSignedUrl(storagePath, 1.hours)
    }
}

val Checkin.isReviewed: Boolean get() = status == CheckinStatus.Reviewed
