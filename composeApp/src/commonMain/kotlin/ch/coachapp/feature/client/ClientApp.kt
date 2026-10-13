package ch.coachapp.feature.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import ch.coachapp.feature.client.checkin.CheckinDetailRoute
import ch.coachapp.feature.client.checkin.CheckinDetailViewModel
import ch.coachapp.feature.client.checkin.CheckinFormRoute
import ch.coachapp.feature.client.checkin.CheckinListRoute
import ch.coachapp.feature.client.checkin.CheckinListViewModel
import ch.coachapp.feature.client.checkin.CheckinViewModel
import ch.coachapp.feature.client.history.HistoryRoute
import ch.coachapp.feature.client.history.HistoryViewModel
import ch.coachapp.feature.client.intake.IntakeViewModel
import ch.coachapp.feature.client.plan.PlanRoute
import ch.coachapp.feature.client.plan.PlanViewModel
import ch.coachapp.feature.client.settings.ClientSettingsScreen
import ch.coachapp.feature.client.today.TodayRoute
import ch.coachapp.feature.client.today.TodayViewModel
import ch.coachapp.feature.client.workout.WorkoutSessionRoute
import ch.coachapp.feature.client.workout.WorkoutSessionViewModel
import ch.coachapp.feature.exercise.ExerciseDetailRoute
import ch.coachapp.feature.exercise.ExerciseDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val clientFeatureModule = module {
    viewModelOf(::TodayViewModel)
    viewModelOf(::PlanViewModel)
    viewModelOf(::CheckinViewModel)
    viewModelOf(::CheckinListViewModel)
    viewModelOf(::HistoryViewModel)
    viewModelOf(::IntakeViewModel)
    viewModel { (dayId: String) -> WorkoutSessionViewModel(dayId, get(), get(), get()) }
    viewModel { (checkinId: String) -> CheckinDetailViewModel(checkinId, get(), get()) }
    viewModel { (workoutExerciseId: String) -> ExerciseDetailViewModel(workoutExerciseId, get(), get(), get()) }
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
                onStartWorkout = { dayId -> navController.navigate(WorkoutSessionDestination(dayId)) },
                onOpenCheckin = { id -> navController.navigate(CheckinDetailDestination(id)) },
                onOpenSettings = { navController.navigate(ClientSettingsDestination) },
                onOpenCheckinForm = { navController.navigate(CheckinFormDestination) },
                onOpenExercise = { navController.navigate(ExerciseDetailDestination(it)) },
            )
        }
        composable<CheckinFormDestination> {
            CheckinFormRoute(onBack = { navController.popBackStack() }, onDone = { navController.popBackStack() })
        }
        composable<WorkoutSessionDestination> { entry ->
            WorkoutSessionRoute(
                dayId = entry.toRoute<WorkoutSessionDestination>().dayId,
                onDone = { navController.popBackStack() },
                onOpenExercise = { navController.navigate(ExerciseDetailDestination(it)) },
            )
        }
        composable<ExerciseDetailDestination> { entry ->
            ExerciseDetailRoute(
                workoutExerciseId = entry.toRoute<ExerciseDetailDestination>().workoutExerciseId,
                onBack = { navController.popBackStack() },
            )
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
    onStartWorkout: (String) -> Unit,
    onOpenCheckin: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCheckinForm: () -> Unit,
    onOpenExercise: (String) -> Unit,
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
            )
            when (tab) {
                ClientTab.Today -> TodayRoute(
                    onStartWorkout = onStartWorkout,
                    onOpenCheckin = onOpenCheckinForm,
                )
                ClientTab.Plan -> PlanRoute(onOpenExercise = onOpenExercise)
                ClientTab.Checkin -> CheckinListRoute(onOpenForm = onOpenCheckinForm, onOpenCheckin = onOpenCheckin)
                ClientTab.History -> HistoryRoute()
            }
        }
    }
}

@Composable
private fun ClientTopBar(title: String, onOpenSettings: () -> Unit) {
    // The same large-title header the coach tabs get from ScreenTopBar. Sign out lives in Settings.
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
        CircleIconButton(Icons.Default.Settings, "Settings", onOpenSettings)
    }
}
