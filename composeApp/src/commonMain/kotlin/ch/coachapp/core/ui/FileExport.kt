package ch.coachapp.core.ui

import androidx.compose.runtime.Composable

/** Hands bytes to the user as a file: a save dialog on Android and iOS, a download on the web. */
fun interface FileExporter {
    fun export(fileName: String, extension: String, bytes: ByteArray)
}

/** [onResult] gets false when the user cancels the save dialog or the write fails. */
@Composable
expect fun rememberFileExporter(onResult: (saved: Boolean) -> Unit): FileExporter
