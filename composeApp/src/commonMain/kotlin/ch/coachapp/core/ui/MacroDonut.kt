package ch.coachapp.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import ch.coachapp.domain.Macros

/** Calorie split between protein, carbs and fat (4/4/9 kcal per gram), total kcal in the middle. */
@Composable
fun MacroDonut(macros: Macros, modifier: Modifier = Modifier) {
    val colors = LocalChartColors.current
    val proteinKcal = macros.protein * 4
    val carbsKcal = macros.carbs * 4
    val fatKcal = macros.fat * 9
    val total = proteinKcal + carbsKcal + fatKcal
    fun share(kcal: Double) = if (total > 0) " · ${(kcal / total * 100).toInt()}%" else ""

    SegmentedRing(
        segments = listOf(
            RingSegment("Protein", proteinKcal, colors.protein, "${macros.protein.toInt()} g${share(proteinKcal)}"),
            RingSegment("Carbs", carbsKcal, colors.carbs, "${macros.carbs.toInt()} g${share(carbsKcal)}"),
            RingSegment("Fat", fatKcal, colors.fat, "${macros.fat.toInt()} g${share(fatKcal)}"),
        ),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${macros.kcal.toInt()}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
