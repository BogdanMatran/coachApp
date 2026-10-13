package ch.coachapp.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Overrides the style on every window, so the SwiftUI shell and the status bar follow the app's
 * choice. System clears the override, which also lets isSystemInDarkTheme see the device again.
 */
@Composable
internal actual fun PlatformChromeAppearance(mode: ThemeMode, dark: Boolean) {
    LaunchedEffect(mode) {
        val style = when (mode) {
            ThemeMode.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { it.windows.filterIsInstance<UIWindow>() }
            .forEach { it.overrideUserInterfaceStyle = style }
    }
}
