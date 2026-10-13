package ch.coachapp.data.repository

import ch.coachapp.core.AppException
import ch.coachapp.core.apiCall
import ch.coachapp.data.model.ClientOverview
import ch.coachapp.data.model.Invite
import ch.coachapp.data.model.Profile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Coach-side client management: roster, dashboard aggregates and invites. */
interface CoachRepository {
    suspend fun clientOverview(): Result<List<ClientOverview>>
    suspend fun client(clientId: String): Result<Profile>
    suspend fun invites(): Result<List<Invite>>
    suspend fun createInvite(email: String?): Result<Invite>
    suspend fun deleteInvite(id: String): Result<Unit>

    /** [day] is ISO, 1 = Monday. */
    suspend fun setCheckinDay(clientId: String, day: Int): Result<Unit>
}

class SupabaseCoachRepository(private val client: SupabaseClient) : CoachRepository {

    private fun me(): String = client.auth.currentUserOrNull()?.id ?: throw AppException("Not signed in")

    override suspend fun clientOverview() = apiCall {
        client.from("coach_client_overview").select {
            filter { eq("coach_id", me()) }
            order("full_name", Order.ASCENDING)
        }.decodeList<ClientOverview>()
    }

    override suspend fun client(clientId: String) = apiCall {
        client.from("profiles").select { filter { eq("id", clientId) } }.decodeSingle<Profile>()
    }

    override suspend fun invites() = apiCall {
        client.from("invites").select {
            filter { eq("coach_id", me()) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Invite>()
    }

    override suspend fun createInvite(email: String?) = apiCall {
        client.from("invites").insert(buildJsonObject {
            put("coach_id", me())
            put("email", email?.trim()?.ifEmpty { null })
        }) { select() }.decodeSingle<Invite>()
    }

    override suspend fun deleteInvite(id: String) = apiCall {
        client.from("invites").delete { filter { eq("id", id) } }
        Unit
    }

    override suspend fun setCheckinDay(clientId: String, day: Int) = apiCall {
        client.postgrest.rpc("set_checkin_day", buildJsonObject {
            put("p_client", clientId)
            put("p_day", day)
        })
        Unit
    }
}
