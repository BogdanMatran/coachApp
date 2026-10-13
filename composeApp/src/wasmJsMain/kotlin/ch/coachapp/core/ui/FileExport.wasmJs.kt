package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.download
import kotlinx.coroutines.launch

@Composable
actual fun rememberFileExporter(onResult: (saved: Boolean) -> Unit): FileExporter {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    return remember {
        FileExporter { fileName, extension, bytes ->
            scope.launch { currentOnResult(runCatching { FileKit.download(bytes, "$fileName.$extension") }.isSuccess) }
        }
    }
}
