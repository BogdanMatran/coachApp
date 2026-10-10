package ch.coachapp.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Width from which the app switches from a bottom bar to a navigation rail. */
val WideLayoutBreakpoint = 840.dp

/**
 * Top-level scaffold: bottom navigation bar on phones, navigation rail on tablets and the web.
 * [content] receives whether the layout is wide, so screens can show list-detail panes.
 */
@Composable
fun AdaptiveScaffold(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    railHeader: (@Composable () -> Unit)? = null,
    content: @Composable (wide: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutBreakpoint
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(header = railHeader?.let { { it() } }) {
                    items.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(item.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content(true) }
            }
        } else {
            // Screens own their top bars (which apply the status bar inset), so only the bottom bar adds padding here.
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    NavigationBar {
                        items.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = index == selectedIndex,
                                onClick = { onSelect(index) },
                                icon = { Icon(item.icon, contentDescription = null) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) { content(false) }
            }
        }
    }
}
