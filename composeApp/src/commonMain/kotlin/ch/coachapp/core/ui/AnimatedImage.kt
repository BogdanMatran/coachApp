package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Plays an animated image (the exercise demo GIFs).
 *
 * Coil only ships an animated decoder for Android, so this is per platform: Android hands the URL
 * to Coil, which now has the GIF decoder registered; iOS and the web decode the frames with Skia
 * and step through them, since both render through Skia anyway.
 *
 * [onError] fires when the animation cannot be played at all, so the caller can fall back to the
 * still image.
 */
@Composable
expect fun AnimatedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onError: () -> Unit = {},
)
