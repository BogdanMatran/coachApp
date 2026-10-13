package ch.coachapp.core.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data

/**
 * Skia-backed GIF playback for iOS and the web.
 *
 * Coil has no animated decoder on either (its `coil-gif` artifact is an Android `.aar`), but both
 * render through Skia, whose `Codec` reads every frame of a GIF. Frames are decoded once up front —
 * an exercise demo is a handful of small frames — and then stepped through on the Compose frame
 * clock, so playback costs an array index per frame rather than a decode.
 */
@Composable
actual fun AnimatedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onError: () -> Unit,
) {
    val animation by produceState<GifAnimation?>(null, url) {
        value = runCatching { loadGif(url) }.getOrElse {
            onError()
            null
        }
    }
    val frames = animation ?: return

    var index by remember(frames) { mutableStateOf(0) }
    LaunchedEffect(frames) {
        if (frames.images.size <= 1) return@LaunchedEffect
        var shownAt = 0L
        while (true) {
            withFrameMillis { now ->
                if (shownAt == 0L) shownAt = now
                val due = frames.durationsMs[index]
                if (now - shownAt >= due) {
                    index = (index + 1) % frames.images.size
                    shownAt = now
                }
            }
        }
    }

    Image(
        bitmap = frames.images[index],
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
    )
}

private class GifAnimation(val images: List<ImageBitmap>, val durationsMs: List<Long>)

private val gifClient by lazy { HttpClient() }

private suspend fun loadGif(url: String): GifAnimation = withContext(Dispatchers.Default) {
    val bytes = gifClient.get(url).readRawBytes()
    val codec = Codec.makeFromData(Data.makeFromBytes(bytes))
    val count = codec.frameCount.coerceAtLeast(1)
    val images = ArrayList<ImageBitmap>(count)
    val durations = ArrayList<Long>(count)
    repeat(count) { frame ->
        val bitmap = Bitmap().apply {
            allocPixels(codec.imageInfo)
            codec.readPixels(this, frame)
        }
        images += bitmap.asComposeImageBitmap()
        // A GIF may declare 0ms, which means "as fast as sensible"; browsers settle on ~100ms.
        durations += codec.getFrameInfo(frame).duration.toLong().takeIf { it > 0 } ?: 100L
    }
    GifAnimation(images, durations)
}
