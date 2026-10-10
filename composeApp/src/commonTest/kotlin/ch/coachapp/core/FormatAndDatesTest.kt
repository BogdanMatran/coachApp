package ch.coachapp.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatAndDatesTest {

    @Test
    fun fmtTrimsTrailingZeros() {
        assertEquals("64.3", 64.3.fmt())
        assertEquals("65", 65.0.fmt())
        assertEquals("-0.5", (-0.5).fmt())
        assertEquals("1.25", 1.25.fmt(2))
    }

    @Test
    fun lenientParsingAcceptsComma() {
        assertEquals(64.5, "64,5".toDoubleOrNullLenient())
        assertEquals(null, "abc".toDoubleOrNullLenient())
    }

    @Test
    fun weekStartIsMonday() {
        // 2026-10-09 is a Friday
        assertEquals(LocalDate(2026, 10, 5), Dates.weekStart(LocalDate(2026, 10, 9)))
        assertEquals(LocalDate(2026, 10, 5), Dates.weekStart(LocalDate(2026, 10, 5)))
        assertEquals(LocalDate(2026, 10, 5), Dates.weekStart(LocalDate(2026, 10, 11)))
    }

    @Test
    fun formatsTimestampsAndDates() {
        assertEquals("09.10.2026", Dates.formatTimestamp("2026-10-09T08:15:00.123+00:00"))
        assertEquals("–", Dates.formatTimestamp(null))
    }
}
