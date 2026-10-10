package ch.coachapp

import androidx.compose.runtime.Composable
import ch.coachapp.core.ui.CoachTheme
import ch.coachapp.core.ui.InstallImageLoader
import ch.coachapp.di.appModules
import ch.coachapp.navigation.AppRoot
import org.koin.compose.KoinApplication
import org.koin.dsl.koinConfiguration

/** Entry point for Android and the web. iOS uses the native shell in iosApp instead. */
@Composable
fun App() {
    InstallImageLoader()
    KoinApplication(configuration = koinConfiguration { modules(appModules) }) {
        CoachTheme { AppRoot() }
    }
}
