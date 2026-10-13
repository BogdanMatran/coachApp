package ch.coachapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.coachapp.core.exerciseGifUrl
import ch.coachapp.core.exerciseImageUrl
import ch.coachapp.data.model.Exercise
import coil3.compose.AsyncImage

/**
 * Exercise pictograms, loaded from the public `exercise-media` bucket.
 *
 * The artwork is drawn on white, so the plate behind it is white in both themes rather than a
 * theme surface; a missing or failed image falls back to a neutral tile with a dumbbell glyph,
 * which is also what a coach's own exercise shows, since those have no media.
 */
private val MediaBackground = Color.White

/**
 * The placeholder glyph. Hand-drawn on the 24dp grid as a single-weight stroke, like the rest of
 * the iconography, because material-icons-core carries no dumbbell. `Icon` tints it.
 */
private val Dumbbell: ImageVector = ImageVector.Builder(
    name = "Dumbbell",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.8f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(3f, 9.5f); lineTo(3f, 14.5f)       // outer plate, left
        moveTo(6.5f, 7f); lineTo(6.5f, 17f)       // inner plate, left
        moveTo(6.5f, 12f); lineTo(17.5f, 12f)     // bar
        moveTo(17.5f, 7f); lineTo(17.5f, 17f)     // inner plate, right
        moveTo(21f, 9.5f); lineTo(21f, 14.5f)     // outer plate, right
    }
}.build()

@Composable
private fun MediaFallback(modifier: Modifier, glyphSize: Dp) {
    val colors = LocalAppColors.current
    Box(modifier.background(colors.surface2), contentAlignment = Alignment.Center) {
        Icon(
            Dumbbell,
            contentDescription = null,
            tint = colors.label3,
            modifier = Modifier.size(glyphSize),
        )
    }
}

/** The 50dp still that opens an exercise row in a list or picker. */
@Composable
fun ExerciseThumb(exercise: Exercise, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    val path = exercise.imagePath
    val shape = RoundedCornerShape(9.dp)
    var failed by remember(path) { mutableStateOf(false) }
    if (path == null || failed) {
        MediaFallback(modifier.size(size).clip(shape), size * 0.42f)
        return
    }
    AsyncImage(
        model = exerciseImageUrl(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onError = { failed = true },
        modifier = modifier.size(size).clip(shape).background(MediaBackground),
    )
}

/**
 * The large demo plate shown while an exercise is being performed: the animation when there is one,
 * then the still, then the placeholder — the same fallback chain openGym uses.
 */
@Composable
fun ExerciseAnimation(exercise: Exercise, modifier: Modifier = Modifier, animate: Boolean = true) {
    val shape = MaterialTheme.shapes.large
    var stage by remember(exercise.id) {
        mutableStateOf(if (animate && exercise.gifPath != null) Stage.Gif else Stage.Still)
    }
    Box(
        modifier.fillMaxWidth().aspectRatio(1.4f).clip(shape).background(MediaBackground),
        contentAlignment = Alignment.Center,
    ) {
        val gif = exercise.gifPath
        val still = exercise.imagePath
        when {
            stage == Stage.Gif && gif != null -> AnimatedImage(
                url = exerciseGifUrl(gif),
                contentDescription = exercise.name,
                contentScale = ContentScale.Fit,
                onError = { stage = Stage.Still },
                modifier = Modifier.fillMaxSize(),
            )
            stage != Stage.Failed && still != null -> AsyncImage(
                model = exerciseImageUrl(still),
                contentDescription = exercise.name,
                contentScale = ContentScale.Fit,
                onError = { stage = Stage.Failed },
                modifier = Modifier.fillMaxSize(),
            )
            else -> MediaFallback(Modifier.fillMaxSize(), 34.dp)
        }
    }
}

private enum class Stage { Gif, Still, Failed }
