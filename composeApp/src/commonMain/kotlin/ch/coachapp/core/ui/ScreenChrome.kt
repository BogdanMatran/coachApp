package ch.coachapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
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
 * Screen top bar. A top-level screen gets a large title with its [subtitle] underneath and
 * circular actions on the trailing side; a pushed screen ([onBack] set) gets a compact bar
 * instead, so the back arrow is not dwarfed by a 34sp heading.
 *
 * [primary] is the screen's main action (shown as a FAB by [PrimaryFab] on Material, as a toolbar
 * button on iOS). [interceptBack] lets the screen confirm before leaving.
 */
@Composable
fun ScreenTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: List<BarAction> = emptyList(),
    primary: BarAction? = null,
    interceptBack: Boolean = false,
    subtitle: String? = null,
) {
    val chrome = LocalNativeChrome.current
    if (chrome != null) {
        SideEffect { chrome.update(title, actions + listOfNotNull(primary), interceptBack, onBack) }
        return
    }
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(
                start = if (onBack != null) 4.dp else PagePadding,
                end = 4.dp,
                top = 8.dp,
                bottom = if (onBack != null) 4.dp else 18.dp,
            ),
        verticalAlignment = if (onBack != null) Alignment.CenterVertically else Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) {
                    MaterialTheme.typography.titleMedium
                } else {
                    MaterialTheme.typography.displaySmall
                },
                color = colors.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.label2,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        actions.forEach { AppBarAction(it) }
    }
}

@Composable
private fun AppBarAction(action: BarAction) {
    val onClick = action.onClick
    when {
        onClick == null -> Text(
            action.label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalAppColors.current.label2,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        action.icon != null -> CircleIconButton(action.icon, action.label, onClick, enabled = action.enabled)
        else -> GhostButton(action.label, onClick, enabled = action.enabled, size = ButtonSize.Sm)
    }
}

/** Material FAB for the screen's primary action; on iOS the action lives in the navigation bar instead. */
@Composable
fun PrimaryFab(action: BarAction?) {
    val onClick = action?.onClick ?: return
    if (LocalNativeChrome.current != null) return
    val colors = LocalAppColors.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = colors.acc,
        contentColor = colors.onAcc,
        icon = { action.icon?.let { Icon(it, contentDescription = null) } },
        text = { Text(action.label, style = MaterialTheme.typography.titleMedium) },
    )
}
