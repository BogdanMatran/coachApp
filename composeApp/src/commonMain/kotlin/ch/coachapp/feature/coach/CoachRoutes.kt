package ch.coachapp.feature.coach

import ch.coachapp.data.model.PlanKind
import kotlinx.serialization.Serializable

@Serializable
data object CoachHomeRoute

@Serializable
data class ClientDetailRoute(val clientId: String)

@Serializable
data object InvitesRoute

/** Builders edit [planId] when set, otherwise create a new plan (a template when [clientId] is null). */
@Serializable
data class WorkoutBuilderRoute(val planId: String? = null, val clientId: String? = null)

@Serializable
data class MealBuilderRoute(val planId: String? = null, val clientId: String? = null)

@Serializable
data class SupplementBuilderRoute(val planId: String? = null, val clientId: String? = null)

@Serializable
data object CheckinTemplateRoute

@Serializable
data object IntakeTemplateRoute

@Serializable
data class CheckinReviewRoute(val checkinId: String)

fun builderRoute(kind: PlanKind, planId: String? = null, clientId: String? = null): Any = when (kind) {
    PlanKind.Workout -> WorkoutBuilderRoute(planId, clientId)
    PlanKind.Meal -> MealBuilderRoute(planId, clientId)
    PlanKind.Supplement -> SupplementBuilderRoute(planId, clientId)
}
