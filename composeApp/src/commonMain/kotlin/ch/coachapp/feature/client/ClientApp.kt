package ch.coachapp.feature.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ch.coachapp.core.ui.AdaptiveScaffold
import ch.coachapp.core.ui.CircleIconButton
import ch.coachapp.core.ui.LocalAppColors
import ch.coachapp.core.ui.NavItem
import ch.coachapp.data.model.Profile
import ch.coachapp.feature.client.settings.ClientSettingsScreen
import ch.coachapp.feature.client.checkin.CheckinDetailRoute
import ch.coachapp.feature.client.checkin.CheckinDetailViewModel
import ch.coachapp.feature.client.checkin.CheckinRoute
import ch.coachapp.feature.client.checkin.CheckinViewModel
import ch.coachapp.feature.client.history.HistoryRoute
import ch.coachapp.feature.client.history.HistoryViewModel
import ch.coachapp.feature.client.plan.PlanRoute
import ch.coachapp.feature.client.plan.PlanViewModel
import ch.coachapp.feature.client.today.TodayRoute
import ch.coachapp.feature.client.today.TodayViewModel
import ch.coachapp.feature.client.workout.WorkoutSessionRoute
import ch.coachapp.feature.client.workout.WorkoutSessionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val clientFeatureModule = module {
    viewModelOf(::TodayViewModel)
    viewModelOf(::PlanViewModel)
    viewModelOf(::CheckinViewModel)
    viewModelOf(::HistoryViewModel)
    viewModel { (dayId: String) -> WorkoutSessionViewModel(dayId, get(), get(), get()) }
    viewModel { (checkinId: String) -> CheckinDetailViewModel(checkinId, get(), get()) }
}

private enum class ClientTab(val item: NavItem) {
    Today(NavItem("Today", Icons.Default.Home)),
    Plan(NavItem("Plan", Icons.AutoMirrored.Filled.List)),
    Checkin(NavItem("Check-in", Icons.Default.CheckCircle)),
    History(NavItem("History", Icons.Default.DateRange)),
}

@Composable
fun ClientApp(profile: Profile, onSignOut: () -> Unit) {
    val navController = rememberNavController()
    NavHost(navController, startDestination = ClientHomeDestination) {
        composable<ClientHomeDestination> {
            ClientHome(
                profile = profile,
                onSignOut = onSignOut,
                onStartWorkout = { dayId -> navController.navigate(WorkoutSessionDestination(dayId)) },
                onOpenCheckin = { id -> navController.navigate(CheckinDetailDestination(id)) },
                onOpenSettings = { navController.navigate(ClientSettingsDestination) },
            )
        }
        composable<WorkoutSessionDestination> { entry ->
            WorkoutSessionRoute(dayId = entry.toRoute<WorkoutSessionDestination>().dayId, onDone = { navController.popBackStack() })
        }
        composable<CheckinDetailDestination> { entry ->
            CheckinDetailRoute(checkinId = entry.toRoute<CheckinDetailDestination>().checkinId, onBack = { navController.popBackStack() })
        }
        composable<ClientSettingsDestination> {
            ClientSettingsScreen(
                displayName = profile.displayName,
                email = profile.email,
                onBack = { navController.popBackStack() },
                onSignOut = onSignOut,
            )
        }
    }
}

@Composable
private fun ClientHome(
    profile: Profile,
    onSignOut: () -> Unit,
    onStartWorkout: (String) -> Unit,
    onOpenCheckin: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var tabIndex by rememberSaveable { mutableStateOf(0) }
    val tab = ClientTab.entries[tabIndex]
    AdaptiveScaffold(
        items = ClientTab.entries.map { it.item },
        selectedIndex = tabIndex,
        onSelect = { tabIndex = it },
    ) { _ ->
        Column(Modifier.fillMaxSize()) {
            ClientTopBar(
                title = if (tab == ClientTab.Today) "Hi, ${profile.displayName.substringBefore(' ')}" else tab.item.label,
                onOpenSettings = onOpenSettings,
                onSignOut = onSignOut,
            )
            when (tab) {
                ClientTab.Today -> TodayRoute(onStartWorkout = onStartWorkout)
                ClientTab.Plan -> PlanRoute()
                ClientTab.Checkin -> CheckinRoute()
                ClientTab.History -> HistoryRoute(onOpenCheckin = onOpenCheckin)
            }
        }
    }
}

@Composable
private fun ClientTopBar(title: String, onOpenSettings: () -> Unit, onSignOut: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    // The same large-title header the coach tabs get from ScreenTopBar; the overflow menu has to
    // be anchored to its button, which is why this is not a ScreenTopBar call.
    Row(
        Modifier
            .fillMaxWidth()
            .background(LocalAppColors.current.bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 18.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Box {
            CircleIconButton(Icons.Default.MoreVert, "More", { menuOpen = true })
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Settings") },
                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    onClick = { menuOpen = false; onOpenSettings() },
                )
                DropdownMenuItem(
                    text = { Text("Sign out") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    onClick = { menuOpen = false; onSignOut() },
                )
            }
        }
    }
}
