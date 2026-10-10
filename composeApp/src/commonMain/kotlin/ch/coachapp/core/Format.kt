package ch.coachapp.core

import kotlin.math.abs
import kotlin.math.roundToLong

/** Formats a number with at most [decimals] places and no trailing zeros (multiplatform-safe). */
fun Double.fmt(decimals: Int = 1): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val rounded = (this * factor).roundToLong()
    val whole = abs(rounded) / factor
    val frac = (abs(rounded) % factor).toString().padStart(decimals, '0').trimEnd('0')
    val sign = if (rounded < 0) "-" else ""
    return if (frac.isEmpty()) "$sign$whole" else "$sign$whole.$frac"
}

fun String.toDoubleOrNullLenient(): Double? = trim().replace(',', '.').toDoubleOrNull()
