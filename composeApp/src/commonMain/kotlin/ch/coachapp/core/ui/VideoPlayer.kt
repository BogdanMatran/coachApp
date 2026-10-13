package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A looping inline video that starts muted, filling [modifier]. iOS uses the system player
 * (AVPlayerViewController), which loads asynchronously and brings its own controls; Android and
 * the web use ComposeMediaPlayer with a tap to play or pause.
 */
@Composable
expect fun PlatformVideoPlayer(url: String, modifier: Modifier = Modifier)
