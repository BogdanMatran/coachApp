package ch.coachapp.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A top bar button described as data, so it can be drawn by Material (Android/web) or
 * handed to the native iOS navigation bar. [onClick] == null makes it a plain label.
 */
data class BarAction(
    val id: String,
    val label: String,
    val icon: ImageVector? = null,
    /** SF Symbol used by the iOS navigation bar; falls back to [label] text when null. */
    val sfSymbol: String? = null,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/**
 * Implemented by the iOS shell. When present, screens report their title and actions
 * instead of drawing a Material top bar, and the primary action moves from a FAB into the bar.
 */
interface NativeChrome {
    fun update(title: String, actions: List<BarAction>, interceptBack: Boolean, onBack: (() -> Unit)?)
}

val LocalNativeChrome = staticCompositionLocalOf<NativeChrome?> { null }

/**
 * Screen top bar. [primary] is the screen's main action (shown as a FAB by [PrimaryFab] on
 * Material, as a toolbar button on iOS). [interceptBack] lets the screen confirm before leaving.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: List<BarAction> = emptyList(),
    primary: BarAction? = null,
    interceptBack: Boolean = false,
) {
    val chrome = LocalNativeChrome.current
    if (chrome != null) {
        SideEffect { chrome.update(title, actions + listOfNotNull(primary), interceptBack, onBack) }
        return
    }
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            }
        },
        actions = { actions.forEach { MaterialBarAction(it) } },
    )
}

@Composable
private fun MaterialBarAction(action: BarAction) {
    val onClick = action.onClick
    when {
        onClick == null -> Text(
            action.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 16.dp),
        )
        action.icon != null -> IconButton(onClick = onClick, enabled = action.enabled) {
            Icon(action.icon, contentDescription = action.label)
        }
        else -> TextButton(onClick = onClick, enabled = action.enabled) { Text(action.label) }
    }
}

/** Material FAB for the screen's primary action; on iOS the action lives in the navigation bar instead. */
@Composable
fun PrimaryFab(action: BarAction?) {
    val onClick = action?.onClick ?: return
    if (LocalNativeChrome.current != null) return
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { action.icon?.let { Icon(it, contentDescription = null) } },
        text = { Text(action.label) },
    )
}
