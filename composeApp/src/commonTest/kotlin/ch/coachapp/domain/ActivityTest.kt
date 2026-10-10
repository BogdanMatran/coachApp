package ch.coachapp.domain

import ch.coachapp.data.model.DayActivity
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActivityTest {

    private fun day(d: Int, workouts: Int = 0, meals: Int = 0, supps: Int = 0) =
        DayActivity(LocalDate(2026, 10, d), workouts, meals, supps, plannedMeals = 4, plannedSupplements = 4)

    @Test
    fun levelsScaleWithCompletionAndWorkoutsAddOne() {
        assertEquals(0, day(1).level())
        assertEquals(1, day(1, meals = 1).level())
        assertEquals(2, day(1, meals = 2, supps = 2).level())
        assertEquals(3, day(1, meals = 4, supps = 3).level())
        // A workout alone is at least level 2; on a complete day it tops out at 4.
        assertEquals(2, day(1, workouts = 1).level())
        assertEquals(4, day(1, workouts = 1, meals = 4, supps = 4).level())
    }

    @Test
    fun levelWithoutPlanCountsAnyLogAsComplete() {
        assertEquals(3, DayActivity(LocalDate(2026, 10, 1), meals = 1).level())
    }

    @Test
    fun completionIgnoresExtraLogsAndNeedsAPlan() {
        assertEquals(0.5, listOf(day(1, meals = 4), day(2, supps = 4)).completion())
        assertEquals(1.0, listOf(day(1, meals = 9, supps = 9)).completion())
        assertNull(listOf(DayActivity(LocalDate(2026, 10, 1))).completion())
    }

    @Test
    fun weekColumnsStartOnMondayAndEndWithCurrentWeek() {
        // 2026-10-09 is a Friday.
        val today = LocalDate(2026, 10, 9)
        val columns = weekColumns(listOf(day(9, workouts = 1)), today, weeks = 3)

        assertEquals(3, columns.size)
        assertEquals(LocalDate(2026, 9, 21), columns.first().first()!!.day)
        assertEquals(LocalDate(2026, 10, 5), columns.last()[0]!!.day)
        assertEquals(1, columns.last()[4]!!.workouts)
        // Saturday and Sunday of the current week are in the future.
        assertNull(columns.last()[5])
        assertNull(columns.last()[6])
    }

    @Test
    fun streakCountsBackFromTodayOrYesterday() {
        val today = LocalDate(2026, 10, 9)
        val history = listOf(day(5, meals = 1), day(6, meals = 1), day(7), day(8, meals = 2))

        // Active today and yesterday, gap on the 7th.
        assertEquals(2, (history + day(9, workouts = 1)).currentStreak(today))
        // Nothing logged yet today: the streak up to yesterday still counts.
        assertEquals(1, (history + day(9)).currentStreak(today))
        // Last activity two days ago: streak is broken.
        assertEquals(0, listOf(day(7, meals = 1)).currentStreak(today))
    }
}
