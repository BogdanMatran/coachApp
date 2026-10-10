package ch.coachapp.ios

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import ch.coachapp.core.ui.CoachTheme
import ch.coachapp.core.ui.InstallImageLoader
import ch.coachapp.core.ui.LocalNativeChrome
import ch.coachapp.data.model.PlanKind
import ch.coachapp.feature.auth.AuthRoute
import ch.coachapp.feature.auth.JoinCoachScreen
import ch.coachapp.feature.client.checkin.CheckinDetailRoute
import ch.coachapp.feature.client.checkin.CheckinRoute
import ch.coachapp.feature.client.history.HistoryRoute
import ch.coachapp.feature.client.plan.PlanRoute
import ch.coachapp.feature.client.today.TodayRoute
import ch.coachapp.feature.client.workout.WorkoutSessionRoute
import ch.coachapp.feature.coach.CheckinReviewDestination
import ch.coachapp.feature.coach.CheckinReviewRoute
import ch.coachapp.feature.coach.CheckinTemplateDestination
import ch.coachapp.feature.coach.ClientDetailDestination
import ch.coachapp.feature.coach.ClientDetailRoute
import ch.coachapp.feature.coach.ClientsDestination
import ch.coachapp.feature.coach.InvitesDestination
import ch.coachapp.feature.coach.LibraryDestination
import ch.coachapp.feature.coach.MealBuilderDestination
import ch.coachapp.feature.coach.MealBuilderRoute
import ch.coachapp.feature.coach.PlansDestination
import ch.coachapp.feature.coach.SettingsDestination
import ch.coachapp.feature.coach.SupplementBuilderDestination
import ch.coachapp.feature.coach.SupplementBuilderRoute
import ch.coachapp.feature.coach.WorkoutBuilderDestination
import ch.coachapp.feature.coach.WorkoutBuilderRoute
import platform.UIKit.UIViewController

/*
 * One UIViewController per screen for the SwiftUI shell (iosApp). SwiftUI owns tabs,
 * navigation and the bars; these only render screen content. Plan kinds cross the
 * bridge as their API names ("workout", "meal", "supplement").
 */

private fun screen(host: ScreenHost?, content: @Composable () -> Unit): UIViewController =
    ComposeUIViewController {
        InstallImageLoader()
        CoachTheme {
            CompositionLocalProvider(LocalNativeChrome provides host) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
    }

private fun planKind(apiName: String): PlanKind = PlanKind.entries.first { it.rpcName == apiName }

// Auth --------------------------------------------------------------------------

fun authScreen(): UIViewController = screen(null) { AuthRoute() }

fun joinCoachScreen(): UIViewController = screen(null) { JoinCoachScreen() }

// Coach -------------------------------------------------------------------------

fun coachClientsScreen(host: ScreenHost, onOpenClient: (String) -> Unit, onOpenInvites: () -> Unit): UIViewController =
    screen(host) { ClientsDestination(onOpenClient = onOpenClient, onOpenInvites = onOpenInvites) }

fun coachPlansScreen(host: ScreenHost, onOpenBuilder: (kind: String, planId: String?) -> Unit): UIViewController =
    screen(host) { PlansDestination(onOpenBuilder = { kind, planId -> onOpenBuilder(kind.rpcName, planId) }) }

fun coachLibraryScreen(host: ScreenHost): UIViewController = screen(host) { LibraryDestination() }

fun coachSettingsScreen(
    host: ScreenHost,
    onOpenCheckinTemplate: () -> Unit,
    onOpenInvites: () -> Unit,
    onSignOut: () -> Unit,
): UIViewController = screen(host) {
    SettingsDestination(onOpenCheckinTemplate = onOpenCheckinTemplate, onOpenInvites = onOpenInvites, onSignOut = onSignOut)
}

fun coachInvitesScreen(host: ScreenHost, onBack: () -> Unit): UIViewController =
    screen(host) { InvitesDestination(onBack = onBack) }

fun coachClientDetailScreen(
    host: ScreenHost,
    clientId: String,
    onBack: () -> Unit,
    onOpenBuilder: (kind: String, planId: String?, clientId: String) -> Unit,
    onOpenCheckin: (String) -> Unit,
): UIViewController = screen(host) {
    ClientDetailDestination(
        route = ClientDetailRoute(clientId),
        onBack = onBack,
        onOpenBuilder = { kind, planId, client -> onOpenBuilder(kind.rpcName, planId, client) },
        onOpenCheckin = onOpenCheckin,
    )
}

fun coachBuilderScreen(host: ScreenHost, kind: String, planId: String?, clientId: String?, onDone: () -> Unit): UIViewController =
    screen(host) {
        when (planKind(kind)) {
            PlanKind.Workout -> WorkoutBuilderDestination(WorkoutBuilderRoute(planId, clientId), onDone = onDone)
            PlanKind.Meal -> MealBuilderDestination(MealBuilderRoute(planId, clientId), onDone = onDone)
            PlanKind.Supplement -> SupplementBuilderDestination(SupplementBuilderRoute(planId, clientId), onDone = onDone)
        }
    }

fun coachCheckinTemplateScreen(host: ScreenHost, onBack: () -> Unit): UIViewController =
    screen(host) { CheckinTemplateDestination(onBack = onBack) }

fun coachCheckinReviewScreen(host: ScreenHost, checkinId: String, onBack: () -> Unit): UIViewController =
    screen(host) { CheckinReviewDestination(CheckinReviewRoute(checkinId), onBack = onBack) }

// Client ------------------------------------------------------------------------

fun clientTodayScreen(host: ScreenHost, onStartWorkout: (String) -> Unit): UIViewController =
    screen(host) { TodayRoute(onStartWorkout = onStartWorkout) }

fun clientPlanScreen(host: ScreenHost): UIViewController = screen(host) { PlanRoute() }

fun clientCheckinScreen(host: ScreenHost): UIViewController = screen(host) { CheckinRoute() }

fun clientHistoryScreen(host: ScreenHost, onOpenCheckin: (String) -> Unit): UIViewController =
    screen(host) { HistoryRoute(onOpenCheckin = onOpenCheckin) }

fun clientWorkoutSessionScreen(host: ScreenHost, dayId: String, onDone: () -> Unit): UIViewController =
    screen(host) { WorkoutSessionRoute(dayId = dayId, onDone = onDone) }

fun clientCheckinDetailScreen(host: ScreenHost, checkinId: String, onBack: () -> Unit): UIViewController =
    screen(host) { CheckinDetailRoute(checkinId = checkinId, onBack = onBack) }
