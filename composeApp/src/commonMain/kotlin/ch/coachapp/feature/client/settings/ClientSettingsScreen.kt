package ch.coachapp.feature.client.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.coachapp.core.ui.AppearanceCard
import ch.coachapp.core.ui.BackTopBar
import ch.coachapp.core.ui.Centered
import ch.coachapp.core.ui.DangerButton
import ch.coachapp.core.ui.InfoCard
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.SectionHeader
import ch.coachapp.feature.account.AccountSection

/**
 * A client's own settings. Coaches have their own screen, with the coaching sections on top.
 *
 * Takes the name and email rather than a Profile so the iOS shell can hand them straight across
 * the bridge from its session snapshot, without exposing the model to Swift.
 */
@Composable
fun ClientSettingsScreen(
    displayName: String,
    email: String?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    Scaffold(topBar = { BackTopBar(title = "Settings", onBack = onBack) }) { padding ->
        Centered(Modifier.padding(padding)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionHeader("Appearance")
                AppearanceCard()

                SectionHeader("Account")
                InfoCard {
                    Text(displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        email.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalAppColors.current.label2,
                    )
                }

                AccountSection()

                DangerButton(
                    "Sign out",
                    onSignOut,
                    modifier = Modifier.padding(top = 16.dp),
                    icon = Icons.AutoMirrored.Filled.ExitToApp,
                )
            }
        }
    }
}
