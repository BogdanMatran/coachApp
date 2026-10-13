package ch.coachapp.core.ui

import androidx.compose.runtime.Composable

/** The canvas is the whole page, so there is no chrome outside Compose to update. */
@Composable
internal actual fun PlatformChromeAppearance(mode: ThemeMode, dark: Boolean) = Unit
