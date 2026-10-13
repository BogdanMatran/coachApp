package ch.coachapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState

@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier) {
    val player = rememberVideoPlayerState()
    LaunchedEffect(url) {
        player.loop = true
        player.volume = 0f
        player.openUri(url)
    }
    DisposableEffect(player) { onDispose { player.dispose() } }
    Box(
        modifier.clickable(onClickLabel = if (player.isPlaying) "Pause" else "Play") {
            if (player.isPlaying) player.pause() else player.play()
        },
        contentAlignment = Alignment.Center,
    ) {
        VideoPlayerSurface(playerState = player, modifier = Modifier.fillMaxSize())
        if (!player.isPlaying) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(32.dp))
            }
        }
    }
}
