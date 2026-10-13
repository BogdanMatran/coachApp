package ch.coachapp.feature.coach

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ch.coachapp.core.ui.AdaptiveScaffold
import ch.coachapp.core.ui.NavItem
import ch.coachapp.data.model.FormKind
import ch.coachapp.data.model.Profile
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val coachFeatureModule = module {
    viewModelOf(::ClientsViewModel)
    viewModelOf(::InvitesViewModel)
    viewModelOf(::PlansViewModel)
    viewModelOf(::LibraryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { params -> CheckinTemplateViewModel(params.get(), get(), get()) }
    viewModel { params -> ClientDetailViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { params -> WorkoutBuilderViewModel(params.get(), get(), get()) }
    viewModel { params -> MealBuilderViewModel(params.get(), get(), get()) }
    viewModel { params -> SupplementBuilderViewModel(params.get(), get(), get()) }
    viewModel { params -> CheckinReviewViewModel(params.get(), get(), get()) }
}

private enum class CoachTab(val item: NavItem) {
    Clients(NavItem("Clients", Icons.Default.Person)),
    Plans(NavItem("Plans", Icons.AutoMirrored.Filled.List)),
    Library(NavItem("Library", Icons.Default.Search)),
    Settings(NavItem("Settings", Icons.Default.Settings)),
}

/** Coach side of the app: roster, plan templates, libraries and check-in reviews. */
@Composable
fun CoachApp(profile: Profile, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    NavHost(nav, startDestination = CoachHomeRoute) {
        composable<CoachHomeRoute> { CoachHome(nav, onSignOut) }
        composable<InvitesRoute> { InvitesDestination(onBack = back) }
        composable<ClientDetailRoute> { entry ->
            ClientDetailDestination(
                route = entry.toRoute(),
                onBack = back,
                onOpenBuilder = { kind, planId, clientId -> nav.navigate(builderRoute(kind, planId, clientId)) },
                onOpenCheckin = { nav.navigate(CheckinReviewRoute(it)) },
            )
        }
        composable<WorkoutBuilderRoute> { entry -> WorkoutBuilderDestination(entry.toRoute(), onDone = back) }
        composable<MealBuilderRoute> { entry -> MealBuilderDestination(entry.toRoute(), onDone = back) }
        composable<SupplementBuilderRoute> { entry -> SupplementBuilderDestination(entry.toRoute(), onDone = back) }
        composable<CheckinTemplateRoute> { CheckinTemplateDestination(onBack = back) }
        composable<IntakeTemplateRoute> { CheckinTemplateDestination(onBack = back, kind = FormKind.Intake) }
        composable<CheckinReviewRoute> { entry -> CheckinReviewDestination(entry.toRoute(), onBack = back) }
    }
}

@Composable
private fun CoachHome(nav: NavHostController, onSignOut: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    AdaptiveScaffold(
        items = CoachTab.entries.map { it.item },
        selectedIndex = selected,
        onSelect = { selected = it },
    ) {
        when (CoachTab.entries[selected]) {
            CoachTab.Clients -> ClientsDestination(
                onOpenClient = { nav.navigate(ClientDetailRoute(it)) },
                onOpenInvites = { nav.navigate(InvitesRoute) },
            )
            CoachTab.Plans -> PlansDestination(onOpenBuilder = { kind, planId -> nav.navigate(builderRoute(kind, planId)) })
            CoachTab.Library -> LibraryDestination()
            CoachTab.Settings -> SettingsDestination(
                onOpenCheckinTemplate = { nav.navigate(CheckinTemplateRoute) },
                onOpenIntakeTemplate = { nav.navigate(IntakeTemplateRoute) },
                onOpenInvites = { nav.navigate(InvitesRoute) },
                onSignOut = onSignOut,
            )
        }
    }
}
