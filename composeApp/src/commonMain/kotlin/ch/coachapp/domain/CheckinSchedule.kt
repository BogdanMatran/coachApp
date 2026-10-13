package ch.coachapp.domain

import ch.coachapp.core.Dates
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus

/** Where a client stands with this week's check-in. Check-ins are keyed by the week's Monday. */
sealed interface CheckinDue {
    data object Submitted : CheckinDue
    data object DueToday : CheckinDue
    data class Upcoming(val date: LocalDate) : CheckinDue
    data class Overdue(val days: Int) : CheckinDue
}

object CheckinSchedule {
    /** The coach's chosen day in the week of [date]; [checkinDay] is ISO (1 = Monday … 7 = Sunday). */
    fun dueDate(date: LocalDate, checkinDay: Int): LocalDate =
        Dates.weekStart(date).plus(DatePeriod(days = checkinDay.coerceIn(1, 7) - 1))

    /** [joinedOn]: a client who joined after this week's due day starts checking in next week. */
    fun status(today: LocalDate, checkinDay: Int, submittedThisWeek: Boolean, joinedOn: LocalDate? = null): CheckinDue {
        if (submittedThisWeek) return CheckinDue.Submitted
        val due = dueDate(today, checkinDay)
        if (joinedOn != null && joinedOn > due) return CheckinDue.Upcoming(due.plus(DatePeriod(days = 7)))
        val late = due.daysUntil(today)
        return when {
            late == 0 -> CheckinDue.DueToday
            late > 0 -> CheckinDue.Overdue(late)
            else -> CheckinDue.Upcoming(due)
        }
    }

    fun dayName(checkinDay: Int): String =
        DayOfWeek(checkinDay.coerceIn(1, 7)).name.lowercase().replaceFirstChar { it.uppercase() }

    val days: List<Int> = DayOfWeek.entries.map { it.isoDayNumber }
}
