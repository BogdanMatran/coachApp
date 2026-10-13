package ch.coachapp.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width from which the app switches from a bottom bar to a navigation rail. */
val WideLayoutBreakpoint = 840.dp

/**
 * Height of the bottom navigation bar, or zero where there is none (the rail layout, and every
 * screen pushed on top of a tab). Content runs the full height of the screen and the bar floats
 * over it, so a scrollable adds this to the bottom of its content padding to let the last item
 * clear the bar. Read it with [bottomBarInset].
 *
 * On iOS the bar is the native UITabBar, and the iOS entry points provide the screen's bottom
 * safe-area inset here instead, which covers the tab bar once a screen extends under it.
 */
val LocalBottomBarHeight = staticCompositionLocalOf { 0.dp }

/** The space a scrollable should leave at its bottom for the navigation bar. */
@Composable
fun bottomBarInset(): Dp = LocalBottomBarHeight.current

/**
 * Bottom padding for a pushed screen whose content runs to the bottom edge: over the iOS tab bar
 * (Swift's contentUnderTabBar), or over the Android gesture bar, whichever is there.
 */
@Composable
fun underBarsInset(): Dp =
    maxOf(bottomBarInset(), WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())

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
    val colors = LocalAppColors.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WideLayoutBreakpoint
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = colors.bgEl,
                    header = railHeader?.let { { it() } },
                ) {
                    items.forEachIndexed { index, item ->
                        NavigationRailItem(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                            icon = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(25.dp)) },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = colors.accText,
                                selectedTextColor = colors.accText,
                                // label2, not label3: an unselected tab is still a control and its
                                // label is still text, so it needs 4.5:1.
                                unselectedIconColor = colors.label2,
                                unselectedTextColor = colors.label2,
                                // openGym tints the icon and label; there is no pill behind them.
                                indicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }
                Box(Modifier.weight(1f)) { content(true) }
            }
        } else {
            val density = LocalDensity.current
            var barHeight by remember { mutableStateOf(0.dp) }
            // Screens own their top bars (which apply the status bar inset), so only the bottom bar
            // adds padding here.
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    BottomBar(
                        items,
                        selectedIndex,
                        onSelect,
                        Modifier.onSizeChanged { barHeight = with(density) { it.height.toDp() } },
                    )
                },
            ) { padding ->
                // The bottom padding is deliberately not consumed: content runs under the bar, the
                // way it does in a native app. Scrollables reserve the height with bottomBarInset()
                // instead, so the last item can still be scrolled clear of it.
                CompositionLocalProvider(LocalBottomBarHeight provides barHeight) {
                    Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                        content(false)
                    }
                }
            }
        }
    }
}

/**
 * The bottom bar is opaque rather than glass: openGym blurs it in the browser, but Compose has no
 * cross-platform backdrop filter, and openGym's own documented fallback for that case is a solid
 * elevated backdrop. A hairline keeps it separated from the page.
 */
@Composable
private fun BottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.bgEl)
            .drawBehind {
                drawLine(colors.sep, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = HairlineWidth.toPx())
            }
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 6.dp, end = 6.dp, top = 7.dp, bottom = 6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom,
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val tint = if (selected) colors.accText else colors.label2
            val interaction = rememberPressInteraction()
            val pressed by interaction.collectIsPressedAsState()
            Column(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(index) },
                    )
                    .pressScale(interaction, IconPressScale)
                    // Dims while held, so a tap on the tab that is already selected still answers.
                    .alpha(if (pressed) 0.6f else 1f)
                    .padding(top = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = tint,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
