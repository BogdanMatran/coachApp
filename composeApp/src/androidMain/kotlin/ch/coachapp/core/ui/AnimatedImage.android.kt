package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** Coil animates GIFs here: ImageLoading.android.kt registers the decoder. */
@Composable
actual fun AnimatedImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    onError: () -> Unit,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = contentScale,
        onError = { onError() },
        modifier = modifier,
    )
}
