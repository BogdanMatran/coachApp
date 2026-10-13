package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.launch

@Composable
actual fun rememberFileExporter(onResult: (saved: Boolean) -> Unit): FileExporter {
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onResult)
    // The save dialog only returns a destination, so the bytes wait here until it closes.
    var pending by remember { mutableStateOf<ByteArray?>(null) }
    val launcher = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { file ->
        val bytes = pending
        pending = null
        if (file == null || bytes == null) {
            currentOnResult(false)
        } else {
            scope.launch { currentOnResult(runCatching { file.write(bytes) }.isSuccess) }
        }
    }
    return remember(launcher) {
        FileExporter { fileName, extension, bytes ->
            pending = bytes
            launcher.launch(suggestedName = fileName, extension = extension)
        }
    }
}
