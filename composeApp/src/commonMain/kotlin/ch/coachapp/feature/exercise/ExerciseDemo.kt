package ch.coachapp.feature.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ch.coachapp.core.ui.ExerciseAnimation
import ch.coachapp.core.ui.ExerciseThumb
import ch.coachapp.core.ui.LoadingBox
import ch.coachapp.core.ui.PlatformVideoPlayer
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.TintedButton
import ch.coachapp.data.model.Exercise
import ch.coachapp.data.repository.LibraryRepository
import org.koin.compose.koinInject

/**
 * How an exercise is demonstrated. The coach's own video wins; without one, the library's
 * animation (or still) is shown as before. Uploads and direct file links play inline and loop;
 * other links (YouTube, Vimeo) keep the library demo and offer a button that opens the link.
 */
@Composable
fun ExerciseDemo(exercise: Exercise, modifier: Modifier = Modifier) {
    val video = exercise.customVideo
    if (video == null) {
        ExerciseAnimation(exercise, modifier)
        return
    }
    val library = koinInject<LibraryRepository>()
    val url by produceState<String?>(null, video) { value = library.videoUrl(video).getOrNull() }
    val uriHandler = LocalUriHandler.current

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            video.playsInApp -> {
                val resolved = url
                if (resolved == null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1.4f).clip(MaterialTheme.shapes.large)) { LoadingBox() }
                } else {
                    InlineVideo(resolved)
                }
            }
            else -> {
                ExerciseAnimation(exercise)
                url?.let { link ->
                    TintedButton(
                        "Watch your coach's video",
                        onClick = { uriHandler.openUri(link) },
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** The coach's video on the same plate as the library demo it replaces. */
@Composable
private fun InlineVideo(url: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.4f)
            .clip(MaterialTheme.shapes.large)
            .background(Color.Black),
    ) {
        PlatformVideoPlayer(url, Modifier.fillMaxSize())
    }
}

/** A small badge for list thumbnails: this exercise has the coach's own video. */
@Composable
fun CoachVideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(18.dp).clip(CircleShape).background(LocalAppColors.current.acc).padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = "Coach video", tint = LocalAppColors.current.onAcc, modifier = Modifier.size(14.dp))
    }
}

/** The list thumbnail, badged when the coach has their own video for the exercise. */
@Composable
fun ExerciseThumbnail(exercise: Exercise, modifier: Modifier = Modifier, size: Dp = 50.dp) {
    Box(modifier) {
        ExerciseThumb(exercise, size = size)
        if (exercise.customVideo != null) CoachVideoBadge(Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp))
    }
}
