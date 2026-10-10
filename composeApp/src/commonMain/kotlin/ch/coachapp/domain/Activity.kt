package ch.coachapp.domain

import ch.coachapp.data.model.DayActivity
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Heat level 0..4 for one day. Meal and supplement completion sets the base level;
 * a logged workout adds one level on top, so training days stand out.
 */
fun DayActivity.level(): Int {
    val planned = plannedMeals + plannedSupplements
    val done = meals + supplements
    val ratio = if (planned > 0) (done.toDouble() / planned).coerceAtMost(1.0) else if (done > 0) 1.0 else 0.0
    val base = when {
        ratio >= 0.85 -> 3
        ratio >= 0.5 -> 2
        ratio > 0.0 -> 1
        else -> 0
    }
    return if (workouts > 0) (base + 1).coerceIn(2, 4) else base
}

/** Share of planned meals and supplements completed over [days], 0..1, or null without a plan. */
fun List<DayActivity>.completion(): Double? {
    val planned = sumOf { it.plannedMeals + it.plannedSupplements }
    if (planned == 0) return null
    return (sumOf { minOf(it.meals, it.plannedMeals) + minOf(it.supplements, it.plannedSupplements) }.toDouble() / planned)
        .coerceIn(0.0, 1.0)
}

/**
 * Lays days out as GitHub does: one column per week (Monday first), the last column
 * holding the current week. Days outside [activity] are null.
 */
fun weekColumns(activity: List<DayActivity>, today: LocalDate, weeks: Int): List<List<DayActivity?>> {
    val byDay = activity.associateBy { it.day }
    val lastMonday = today.plus(DatePeriod(days = -today.dayOfWeek.ordinal))
    val firstMonday = lastMonday.plus(DatePeriod(days = -7 * (weeks - 1)))
    return List(weeks) { w ->
        List(7) { d ->
            val date = firstMonday.plus(DatePeriod(days = w * 7 + d))
            if (date > today) null else byDay[date] ?: DayActivity(date)
        }
    }
}

/** Consecutive active days (level > 0) ending today, or yesterday if today has nothing yet. */
fun List<DayActivity>.currentStreak(today: LocalDate): Int {
    val active = filter { it.level() > 0 }.map { it.day }.toSet()
    var day = if (today in active) today else today.plus(DatePeriod(days = -1))
    var streak = 0
    while (day in active) {
        streak++
        day = day.plus(DatePeriod(days = -1))
    }
    return streak
}
