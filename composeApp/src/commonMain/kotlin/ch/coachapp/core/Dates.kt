package ch.coachapp.core

import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.todayIn

object Dates {
    fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** Monday of the week containing [date]; check-ins are keyed by it. */
    fun weekStart(date: LocalDate = today()): LocalDate =
        date.minus(DatePeriod(days = date.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))

    fun format(date: LocalDate): String =
        "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"

    /** Formats the date part of a Postgres timestamp ("2026-10-09T08:15:00+00:00"). */
    fun formatTimestamp(timestamp: String?): String =
        timestamp?.take(10)?.let { runCatching { format(LocalDate.parse(it)) }.getOrNull() } ?: "–"

    fun dayName(date: LocalDate): String =
        date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
}
