package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory

/** Installs the app-wide Coil image loader (Ktor-based so it works on every target). */
@Composable
fun InstallImageLoader() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                addPlatformDecoders()
            }
            .build()
    }
}

/**
 * Hook for decoders Coil only ships per platform. Android gets its animated-GIF decoder here;
 * the Skia targets have none to add, and animate exercise demos through [AnimatedImage] instead.
 */
expect fun ComponentRegistry.Builder.addPlatformDecoders()
