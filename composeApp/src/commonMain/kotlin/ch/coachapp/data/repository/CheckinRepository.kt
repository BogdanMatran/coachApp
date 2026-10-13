package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.AppJson
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.CheckinQuestion
import ch.coachapp.data.model.CheckinStatus
import ch.coachapp.data.model.CheckinTemplate
import ch.coachapp.data.model.FormKind
import ch.coachapp.data.model.IntakeResponse
import ch.coachapp.data.model.Pose
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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
    suspend fun template(coachId: String, kind: FormKind = FormKind.Checkin): Result<CheckinTemplate?>
    suspend fun saveTemplate(questions: List<CheckinQuestion>, kind: FormKind = FormKind.Checkin): Result<Unit>

    suspend fun intakeResponse(clientId: String): Result<IntakeResponse?>

    /** Client: answers their coach's onboarding form. */
    suspend fun submitIntake(coachId: String, answers: Map<String, String>): Result<Unit>

    suspend fun checkins(clientId: String): Result<List<Checkin>>

    suspend fun schedule(clientId: String, weekStart: LocalDate): Result<CheckinScheduleInfo>
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

    override suspend fun template(coachId: String, kind: FormKind) = apiCall {
        client.from("checkin_templates").select {
            filter {
                eq("coach_id", coachId)
                eq("kind", kind.apiName)
            }
        }.decodeSingleOrNull<CheckinTemplate>()
    }

    override suspend fun saveTemplate(questions: List<CheckinQuestion>, kind: FormKind) = apiCall {
        client.from("checkin_templates").upsert(buildJsonObject {
            put("coach_id", me())
            put("kind", kind.apiName)
            put("questions", AppJson.encodeToJsonElement(questions))
        }) { onConflict = "coach_id,kind" }
        Unit
    }

    override suspend fun intakeResponse(clientId: String) = apiCall {
        client.from("intake_responses").select { filter { eq("client_id", clientId) } }.decodeSingleOrNull<IntakeResponse>()
    }

    override suspend fun submitIntake(coachId: String, answers: Map<String, String>) = apiCall {
        client.from("intake_responses").upsert(buildJsonObject {
            put("client_id", me())
            put("coach_id", coachId)
            putJsonObject("answers") { answers.forEach { (k, v) -> put(k, JsonPrimitive(v)) } }
        }) { onConflict = "client_id" }
        Unit
    }

    override suspend fun checkins(clientId: String) = apiCall {
        client.from("checkins").select(CheckinColumns) {
            filter { eq("client_id", clientId) }
            order("week_start", Order.DESCENDING)
        }.decodeList<Checkin>()
    }

    override suspend fun schedule(clientId: String, weekStart: LocalDate) = apiCall {
        val profile = client.from("profiles").select(Columns.list("checkin_day", "created_at")) { filter { eq("id", clientId) } }
            .decodeSingle<CheckinDayRow>()
        val submitted = client.from("checkins").select(Columns.list("id")) {
            filter {
                eq("client_id", clientId)
                eq("week_start", weekStart.toString())
            }
        }.decodeList<IdRow>().isNotEmpty()
        CheckinScheduleInfo(
            checkinDay = profile.checkinDay,
            submittedThisWeek = submitted,
            joinedOn = Instant.parse(profile.createdAt).toLocalDateTime(TimeZone.currentSystemDefault()).date,
        )
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

@Serializable
private data class CheckinDayRow(
    @SerialName("checkin_day") val checkinDay: Int,
    @SerialName("created_at") val createdAt: String,
)

/** What decides whether a client's check-in is due: their weekday, this week's submission, their join date. */
data class CheckinScheduleInfo(val checkinDay: Int, val submittedThisWeek: Boolean, val joinedOn: LocalDate)

@Serializable
private data class IdRow(val id: String)

private val FormKind.apiName: String get() = name.lowercase()

val Checkin.isReviewed: Boolean get() = status == CheckinStatus.Reviewed
