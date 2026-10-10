package ch.coachapp.feature.client

import kotlinx.serialization.Serializable

@Serializable
data object ClientHomeDestination

@Serializable
data class WorkoutSessionDestination(val dayId: String)

@Serializable
data class CheckinDetailDestination(val checkinId: String)
