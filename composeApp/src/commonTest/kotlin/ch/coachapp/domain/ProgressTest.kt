package ch.coachapp.domain

import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.StrengthPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.datetime.LocalDate

class ProgressTest {
    private fun checkin(day: Int, weight: Double?, waist: Double? = null) = Checkin(
        id = "c$day",
        clientId = "client",
        coachId = "coach",
        weekStart = LocalDate(2026, 9, day),
        weight = weight,
        measurements = waist?.let { mapOf("waist" to it) }.orEmpty(),
    )

    @Test
    fun bodySeriesIsOldestFirstAndSkipsEmptyMetrics() {
        val series = Progress.bodySeries(listOf(checkin(14, 64.0, 71.0), checkin(7, 65.0), checkin(21, null, 70.0)))
        assertEquals(listOf("weight", "waist"), series.map { it.key })
        val weight = series.first()
        assertEquals(listOf(65.0, 64.0), weight.points.map { it.value })
        assertEquals(-1.0, weight.change)
    }

    @Test
    fun singlePointHasNoChange() {
        assertNull(Progress.bodySeries(listOf(checkin(7, 65.0))).first().change)
    }

    @Test
    fun strengthSeriesGroupsByExercise() {
        val points = listOf(
            StrengthPoint("b", "Squat", LocalDate(2026, 9, 8), 80.0, 60.0, 10),
            StrengthPoint("a", "Bench", LocalDate(2026, 9, 1), 50.0, 40.0, 8),
            StrengthPoint("b", "Squat", LocalDate(2026, 9, 1), 75.0, 60.0, 8),
        )
        val series = Progress.strengthSeries(points)
        assertEquals(listOf("Bench", "Squat"), series.map { it.label })
        assertEquals(listOf(75.0, 80.0), series[1].points.map { it.value })
        assertEquals("60 kg × 10", series[1].last?.detail)
    }

    @Test
    fun epley() {
        assertEquals(80.0, Progress.estimatedOneRepMax(60.0, 10), 0.001)
    }
}
