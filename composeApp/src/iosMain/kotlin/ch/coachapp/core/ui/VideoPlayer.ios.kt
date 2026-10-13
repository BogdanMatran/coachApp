package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitViewController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFoundation.AVLayerVideoGravityResizeAspect
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.AVKit.AVPlayerViewController
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

/**
 * The system player rather than ComposeMediaPlayer: that library configures the audio session and
 * reads track properties synchronously on the main thread, which Xcode flags as a hang risk and
 * which can stall the UI while a remote video starts loading. AVPlayer loads asynchronously.
 *
 * Starts muted, so a demo never cuts into the music the client trains to; the controls unmute.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformVideoPlayer(url: String, modifier: Modifier) {
    val player = remember(url) { NSURL.URLWithString(url)?.let { AVPlayer(uRL = it) } } ?: return
    val controller = remember(player) {
        AVPlayerViewController().apply {
            this.player = player
            showsPlaybackControls = true
            videoGravity = AVLayerVideoGravityResizeAspect
        }
    }
    DisposableEffect(player) {
        player.muted = true
        // Loop like the library animation it replaces.
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            player.seekToTime(CMTimeMake(value = 0, timescale = 1))
            player.play()
        }
        player.play()
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player.pause()
        }
    }
    UIKitViewController(factory = { controller }, modifier = modifier)
}
