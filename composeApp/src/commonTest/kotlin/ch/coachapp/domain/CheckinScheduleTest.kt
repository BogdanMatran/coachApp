package ch.coachapp.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class CheckinScheduleTest {
    // 2026-10-14 is a Wednesday.
    private val wednesday = LocalDate(2026, 10, 14)

    @Test
    fun dueDateIsTheChosenDayOfThisWeek() {
        assertEquals(LocalDate(2026, 10, 12), CheckinSchedule.dueDate(wednesday, 1))
        assertEquals(LocalDate(2026, 10, 18), CheckinSchedule.dueDate(wednesday, 7))
    }

    @Test
    fun submittedWinsOverEverything() {
        assertEquals(CheckinDue.Submitted, CheckinSchedule.status(wednesday, 1, submittedThisWeek = true))
    }

    @Test
    fun dueTodayOverdueAndUpcoming() {
        assertEquals(CheckinDue.DueToday, CheckinSchedule.status(wednesday, 3, false))
        assertEquals(CheckinDue.Overdue(2), CheckinSchedule.status(wednesday, 1, false))
        assertEquals(CheckinDue.Upcoming(LocalDate(2026, 10, 16)), CheckinSchedule.status(wednesday, 5, false))
    }

    @Test
    fun joiningAfterTheDueDayStartsNextWeek() {
        assertEquals(
            CheckinDue.Upcoming(LocalDate(2026, 10, 19)),
            CheckinSchedule.status(wednesday, 1, false, joinedOn = LocalDate(2026, 10, 13)),
        )
        assertEquals(CheckinDue.Overdue(2), CheckinSchedule.status(wednesday, 1, false, joinedOn = LocalDate(2026, 10, 12)))
    }

    @Test
    fun outOfRangeDaysAreClamped() {
        assertEquals("Monday", CheckinSchedule.dayName(0))
        assertEquals("Sunday", CheckinSchedule.dayName(9))
    }
}
