package ch.coachapp.domain

import ch.coachapp.data.model.Checkin
import ch.coachapp.data.model.MeasurementKeys
import ch.coachapp.data.model.StrengthPoint
import kotlinx.datetime.LocalDate

data class SeriesPoint(val date: LocalDate, val value: Double, val detail: String? = null)

/** One metric over time, oldest point first. */
data class Series(val key: String, val label: String, val unit: String, val points: List<SeriesPoint>) {
    val first: SeriesPoint? get() = points.firstOrNull()
    val last: SeriesPoint? get() = points.lastOrNull()
    val change: Double? get() = if (points.size < 2) null else points.last().value - points.first().value
}

object Progress {
    /** Weight, body fat and each measurement that was recorded at least once. */
    fun bodySeries(checkins: List<Checkin>): List<Series> {
        val sorted = checkins.sortedBy { it.weekStart }
        fun series(key: String, label: String, unit: String, value: (Checkin) -> Double?) =
            Series(key, label, unit, sorted.mapNotNull { c -> value(c)?.let { SeriesPoint(c.weekStart, it) } })
        return buildList {
            add(series("weight", "Weight", "kg") { it.weight })
            add(series("body_fat", "Body fat", "%") { it.bodyFat })
            MeasurementKeys.forEach { key ->
                add(series(key, key.replaceFirstChar { it.uppercase() }, "cm") { it.measurements[key] })
            }
        }.filter { it.points.isNotEmpty() }
    }

    /** One series per exercise, ordered by name. */
    fun strengthSeries(points: List<StrengthPoint>): List<Series> =
        points.groupBy { it.exerciseId }.values
            .map { list ->
                val sorted = list.sortedBy { it.day }
                Series(
                    key = sorted.first().exerciseId,
                    label = sorted.first().exerciseName,
                    unit = "kg",
                    points = sorted.map { SeriesPoint(it.day, it.e1rm, "${it.weight.trimmed()} kg × ${it.reps}") },
                )
            }
            .sortedBy { it.label }

    /** Epley's estimate, the formula `strength_progress` uses server-side. */
    fun estimatedOneRepMax(weight: Double, reps: Int): Double = weight * (1 + reps / 30.0)

    private fun Double.trimmed(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
}
