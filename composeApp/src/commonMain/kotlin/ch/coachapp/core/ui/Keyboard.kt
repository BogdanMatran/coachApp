package ch.coachapp.core.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Dismisses the keyboard when a tap lands outside every text field.
 *
 * Compose does not do this on any platform, and on iOS it is the more jarring omission because
 * UIKit apps are expected to implement it themselves — without it the keyboard sits there until
 * the field is submitted. Applied once at each app root.
 *
 * A parent only receives the taps its children did not consume, so this never steals a tap meant
 * for another text field: that one focuses as usual. Drags are untouched, so scrolling still works.
 */
@Composable
fun Modifier.clearFocusOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return pointerInput(Unit) {
        detectTapGestures(
            onTap = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
        )
    }
}
