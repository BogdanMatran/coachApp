package ch.coachapp.data.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Role { @SerialName("coach") Coach, @SerialName("client") Client }

@Serializable
data class Profile(
    val id: String,
    val role: Role,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("coach_id") val coachId: String? = null,
    val units: String = "metric",
) {
    val displayName: String get() = fullName.ifBlank { email ?: "Unnamed" }
}

@Serializable
data class Invite(
    val id: String,
    val code: String,
    @SerialName("coach_id") val coachId: String,
    val email: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("used_by") val usedBy: String? = null,
    @SerialName("used_at") val usedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ClientOverview(
    @SerialName("client_id") val clientId: String,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    @SerialName("last_checkin") val lastCheckin: LocalDate? = null,
    @SerialName("pending_reviews") val pendingReviews: Int = 0,
    @SerialName("last_workout") val lastWorkout: String? = null,
    @SerialName("adherence_7d") val adherence7d: Int? = null,
) {
    val displayName: String get() = fullName.ifBlank { email ?: "Unnamed" }
}

// ---------------------------------------------------------------------------
// Libraries
// ---------------------------------------------------------------------------

@Serializable
data class Exercise(
    val id: String = "",
    @SerialName("coach_id") val coachId: String? = null,
    val name: String = "",
    @SerialName("muscle_group") val muscleGroup: String? = null,
    val equipment: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val instructions: String? = null,
) {
    val isGlobal: Boolean get() = coachId == null
}

@Serializable
data class Food(
    val id: String = "",
    @SerialName("coach_id") val coachId: String? = null,
    val name: String = "",
    /** Per 100 g. */
    val kcal: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    @SerialName("serving_unit") val servingUnit: String = "g",
) {
    val isGlobal: Boolean get() = coachId == null
}

@Serializable
data class Supplement(
    val id: String = "",
    @SerialName("coach_id") val coachId: String? = null,
    val name: String = "",
    @SerialName("default_dosage") val defaultDosage: String? = null,
    val unit: String? = null,
    val notes: String? = null,
) {
    val isGlobal: Boolean get() = coachId == null
    val dosageLabel: String get() = listOfNotNull(defaultDosage, unit).joinToString(" ")
}

// ---------------------------------------------------------------------------
// Plans. client_id == null means a reusable template.
// ---------------------------------------------------------------------------

enum class PlanKind(val rpcName: String, val label: String) {
    Workout("workout", "Workout"),
    Meal("meal", "Meal"),
    Supplement("supplement", "Supplements"),
}

@Serializable
data class WorkoutPlan(
    val id: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("client_id") val clientId: String? = null,
    val name: String,
    val notes: String? = null,
    val active: Boolean = true,
    @SerialName("start_date") val startDate: LocalDate? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("workout_days") val days: List<WorkoutDay> = emptyList(),
) {
    val sortedDays: List<WorkoutDay> get() = days.sortedBy { it.dayIndex }
}

@Serializable
data class WorkoutDay(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("day_index") val dayIndex: Int = 0,
    val title: String,
    val notes: String? = null,
    @SerialName("workout_exercises") val exercises: List<WorkoutExercise> = emptyList(),
) {
    val sortedExercises: List<WorkoutExercise> get() = exercises.sortedBy { it.position }
}

@Serializable
data class WorkoutExercise(
    val id: String,
    @SerialName("day_id") val dayId: String,
    @SerialName("exercise_id") val exerciseId: String,
    val position: Int = 0,
    val sets: Int = 3,
    val reps: String = "10",
    @SerialName("rest_sec") val restSec: Int = 90,
    val tempo: String? = null,
    @SerialName("target_rpe") val targetRpe: Double? = null,
    val notes: String? = null,
    @SerialName("exercises") val exercise: Exercise? = null,
)

@Serializable
data class MealPlan(
    val id: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("client_id") val clientId: String? = null,
    val name: String,
    val notes: String? = null,
    @SerialName("target_kcal") val targetKcal: Int? = null,
    @SerialName("target_protein") val targetProtein: Int? = null,
    @SerialName("target_carbs") val targetCarbs: Int? = null,
    @SerialName("target_fat") val targetFat: Int? = null,
    val active: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    val meals: List<Meal> = emptyList(),
) {
    val sortedMeals: List<Meal> get() = meals.sortedBy { it.position }
}

@Serializable
data class Meal(
    val id: String,
    @SerialName("plan_id") val planId: String,
    val name: String,
    val position: Int = 0,
    @SerialName("time_hint") val timeHint: String? = null,
    @SerialName("meal_items") val items: List<MealItem> = emptyList(),
) {
    val sortedItems: List<MealItem> get() = items.sortedBy { it.position }
}

@Serializable
data class MealItem(
    val id: String,
    @SerialName("meal_id") val mealId: String,
    @SerialName("food_id") val foodId: String,
    val grams: Double = 100.0,
    val notes: String? = null,
    val position: Int = 0,
    @SerialName("foods") val food: Food? = null,
)

@Serializable
enum class SupplementTiming(val label: String) {
    @SerialName("morning") Morning("Morning"),
    @SerialName("pre_workout") PreWorkout("Pre-workout"),
    @SerialName("post_workout") PostWorkout("Post-workout"),
    @SerialName("with_meals") WithMeals("With meals"),
    @SerialName("evening") Evening("Evening"),
    @SerialName("bedtime") Bedtime("Bedtime"),
    ;

    val apiValue: String get() = when (this) {
        Morning -> "morning"
        PreWorkout -> "pre_workout"
        PostWorkout -> "post_workout"
        WithMeals -> "with_meals"
        Evening -> "evening"
        Bedtime -> "bedtime"
    }
}

@Serializable
data class SupplementPlan(
    val id: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("client_id") val clientId: String? = null,
    val name: String,
    val notes: String? = null,
    val active: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("supplement_items") val items: List<SupplementItem> = emptyList(),
) {
    val sortedItems: List<SupplementItem> get() = items.sortedBy { it.position }
}

@Serializable
data class SupplementItem(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("supplement_id") val supplementId: String,
    val dosage: String? = null,
    val timing: SupplementTiming = SupplementTiming.Morning,
    val notes: String? = null,
    val position: Int = 0,
    @SerialName("supplements") val supplement: Supplement? = null,
)

/** A lightweight row for plan pickers and template lists. */
@Serializable
data class PlanSummary(
    val id: String,
    val name: String,
    @SerialName("client_id") val clientId: String? = null,
    val active: Boolean = true,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ---------------------------------------------------------------------------
// Client logs
// ---------------------------------------------------------------------------

@Serializable
data class WorkoutLog(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("workout_day_id") val workoutDayId: String? = null,
    @SerialName("day_title") val dayTitle: String? = null,
    @SerialName("performed_at") val performedAt: String,
    val notes: String? = null,
    @SerialName("set_logs") val sets: List<SetLog> = emptyList(),
)

@Serializable
data class SetLog(
    val id: String = "",
    @SerialName("log_id") val logId: String = "",
    @SerialName("workout_exercise_id") val workoutExerciseId: String? = null,
    @SerialName("exercise_id") val exerciseId: String,
    @SerialName("set_no") val setNo: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val rpe: Double? = null,
    @SerialName("exercises") val exercise: Exercise? = null,
)

@Serializable
data class MealLog(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("meal_id") val mealId: String,
    @SerialName("log_date") val logDate: LocalDate,
    val completed: Boolean = true,
    val notes: String? = null,
)

@Serializable
data class SupplementLog(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("supplement_item_id") val supplementItemId: String,
    @SerialName("log_date") val logDate: LocalDate,
    val taken: Boolean = true,
)

// ---------------------------------------------------------------------------
// Check-ins
// ---------------------------------------------------------------------------

@Serializable
enum class QuestionType(val label: String) {
    @SerialName("scale") Scale("Scale 1–5"),
    @SerialName("text") Text("Text"),
    @SerialName("number") Number("Number"),
    @SerialName("yesno") YesNo("Yes / No"),
}

@Serializable
data class CheckinQuestion(val id: String, val label: String, val type: QuestionType = QuestionType.Text)

@Serializable
data class CheckinTemplate(
    val id: String = "",
    @SerialName("coach_id") val coachId: String,
    val questions: List<CheckinQuestion> = emptyList(),
)

@Serializable
enum class CheckinStatus { @SerialName("submitted") Submitted, @SerialName("reviewed") Reviewed }

@Serializable
enum class Pose(val label: String) { @SerialName("front") Front("Front"), @SerialName("side") Side("Side"), @SerialName("back") Back("Back") }

@Serializable
data class Checkin(
    val id: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("coach_id") val coachId: String,
    @SerialName("week_start") val weekStart: LocalDate,
    val weight: Double? = null,
    @SerialName("body_fat") val bodyFat: Double? = null,
    val measurements: Map<String, Double> = emptyMap(),
    val answers: Map<String, String> = emptyMap(),
    val status: CheckinStatus = CheckinStatus.Submitted,
    @SerialName("coach_feedback") val coachFeedback: String? = null,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("checkin_photos") val photos: List<CheckinPhoto> = emptyList(),
)

@Serializable
data class CheckinPhoto(
    val id: String,
    @SerialName("checkin_id") val checkinId: String,
    @SerialName("storage_path") val storagePath: String,
    val pose: Pose,
)

/** Body measurements collected in a check-in, in cm. */
val MeasurementKeys = listOf("chest", "waist", "hips", "arm", "thigh")

// ---------------------------------------------------------------------------
// Activity (heatmap)
// ---------------------------------------------------------------------------

@Serializable
data class DayActivity(
    val day: LocalDate,
    val workouts: Int = 0,
    val meals: Int = 0,
    val supplements: Int = 0,
    @SerialName("planned_meals") val plannedMeals: Int = 0,
    @SerialName("planned_supplements") val plannedSupplements: Int = 0,
)
