package ch.coachapp.core.ui

import android.os.Build
import coil3.ComponentRegistry
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder

/** ImageDecoder handles animated GIFs from API 28; below that Coil's own decoder does. */
actual fun ComponentRegistry.Builder.addPlatformDecoders() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        add(AnimatedImageDecoder.Factory())
    } else {
        add(GifDecoder.Factory())
    }
}
