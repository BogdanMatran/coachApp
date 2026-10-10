package ch.coachapp.feature.client

import ch.coachapp.core.fmt
import ch.coachapp.data.model.WorkoutExercise
import ch.coachapp.domain.Macros

fun macroLine(m: Macros): String =
    "${m.kcal.toInt()} kcal · P ${m.protein.fmt(0)}g · C ${m.carbs.fmt(0)}g · F ${m.fat.fmt(0)}g"

/** "4 × 6-8 · rest 150s · RPE 8" */
fun prescription(we: WorkoutExercise): String = buildList {
    add("${we.sets} × ${we.reps}")
    add("rest ${we.restSec}s")
    we.tempo?.takeIf { it.isNotBlank() }?.let { add("tempo $it") }
    we.targetRpe?.let { add("RPE ${it.fmt()}") }
}.joinToString(" · ")
