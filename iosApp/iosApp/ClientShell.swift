import SwiftUI
import ComposeApp

enum ClientRoute: Hashable {
    case workout(dayId: String)
    case checkin(id: String)
    case checkinForm
    case exercise(workoutExerciseId: String)
    case settings
}

struct ClientShell: View {
    let session: SessionModel
    let firstName: String
    let displayName: String
    let email: String?

    var body: some View {
        TabView {
            ClientStack(session: session, displayName: displayName, email: email) { nav in
                ComposeScreen(title: firstName.isEmpty ? "Today" : "Hi, \(firstName)", displayMode: .large) { host in
                    IosScreensKt.clientTodayScreen(
                        host: host,
                        onStartWorkout: { nav.push(.workout(dayId: $0)) },
                        onOpenCheckin: { nav.push(.checkinForm) }
                    )
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Today", systemImage: "house") }

            ClientStack(session: session, displayName: displayName, email: email) { nav in
                ComposeScreen(title: "Plan", displayMode: .large) { host in
                    IosScreensKt.clientPlanScreen(host: host) { nav.push(.exercise(workoutExerciseId: $0)) }
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Plan", systemImage: "list.bullet.clipboard") }

            ClientStack(session: session, displayName: displayName, email: email) { nav in
                ComposeScreen(title: "Check-in", displayMode: .large) { host in
                    IosScreensKt.clientCheckinScreen(
                        host: host,
                        onOpenForm: { nav.push(.checkinForm) },
                        onOpenCheckin: { nav.push(.checkin(id: $0)) }
                    )
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Check-in", systemImage: "checkmark.circle") }

            ClientStack(session: session, displayName: displayName, email: email) { _ in
                ComposeScreen(title: "History", displayMode: .large) { host in
                    IosScreensKt.clientHistoryScreen(host: host)
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("History", systemImage: "calendar") }
        }
    }
}

/// One tab's navigation stack, with the account menu on its root screen.
private struct ClientStack<Root: View>: View {
    let session: SessionModel
    let displayName: String
    let email: String?
    @StateObject private var nav = Navigator<ClientRoute>()
    private let root: (Navigator<ClientRoute>) -> Root

    init(
        session: SessionModel,
        displayName: String,
        email: String?,
        @ViewBuilder root: @escaping (Navigator<ClientRoute>) -> Root
    ) {
        self.session = session
        self.displayName = displayName
        self.email = email
        self.root = root
    }

    var body: some View {
        NavigationStack(path: $nav.path) {
            root(nav)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Settings", systemImage: "gearshape") {
                            nav.push(.settings)
                        }
                    }
                }
                .navigationDestination(for: ClientRoute.self) { route in
                    destination(route)
                }
        }
    }

    @ViewBuilder
    private func destination(_ route: ClientRoute) -> some View {
        switch route {
        case .workout(let dayId):
            ComposeScreen(title: "Workout") { host in
                IosScreensKt.clientWorkoutSessionScreen(
                    host: host,
                    dayId: dayId,
                    onDone: { nav.pop() },
                    onOpenExercise: { nav.push(.exercise(workoutExerciseId: $0)) }
                )
            }
        case .checkin(let id):
            ComposeScreen(title: "Check-in") { host in
                IosScreensKt.clientCheckinDetailScreen(host: host, checkinId: id, onBack: { nav.pop() })
            }
            .contentUnderTabBar()
        case .exercise(let id):
            ComposeScreen(title: "Exercise") { host in
                IosScreensKt.clientExerciseDetailScreen(host: host, workoutExerciseId: id, onBack: { nav.pop() })
            }
            .contentUnderTabBar()
        case .checkinForm:
            ComposeScreen(title: "Check-in") { host in
                IosScreensKt.clientCheckinFormScreen(host: host, onBack: { nav.pop() }, onDone: { nav.pop() })
            }
        case .settings:
            ComposeScreen(title: "Settings") { host in
                IosScreensKt.clientSettingsScreen(
                    host: host,
                    displayName: displayName,
                    email: email,
                    onBack: { nav.pop() },
                    onSignOut: { session.signOut() }
                )
            }
        }
    }
}
