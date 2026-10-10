import SwiftUI
import ComposeApp

enum ClientRoute: Hashable {
    case workout(dayId: String)
    case checkin(id: String)
}

struct ClientShell: View {
    let session: SessionModel
    let firstName: String

    var body: some View {
        TabView {
            ClientStack(session: session) { nav in
                ComposeScreen(title: firstName.isEmpty ? "Today" : "Hi, \(firstName)", displayMode: .large) { host in
                    IosScreensKt.clientTodayScreen(host: host) { nav.push(.workout(dayId: $0)) }
                }
            }
            .tabItem { Label("Today", systemImage: "house") }

            ClientStack(session: session) { _ in
                ComposeScreen(title: "Plan", displayMode: .large) { host in
                    IosScreensKt.clientPlanScreen(host: host)
                }
            }
            .tabItem { Label("Plan", systemImage: "list.bullet.clipboard") }

            ClientStack(session: session) { _ in
                ComposeScreen(title: "Check-in", displayMode: .large) { host in
                    IosScreensKt.clientCheckinScreen(host: host)
                }
            }
            .tabItem { Label("Check-in", systemImage: "checkmark.circle") }

            ClientStack(session: session) { nav in
                ComposeScreen(title: "History", displayMode: .large) { host in
                    IosScreensKt.clientHistoryScreen(host: host) { nav.push(.checkin(id: $0)) }
                }
            }
            .tabItem { Label("History", systemImage: "calendar") }
        }
    }
}

/// One tab's navigation stack, with the account menu on its root screen.
private struct ClientStack<Root: View>: View {
    let session: SessionModel
    @StateObject private var nav = Navigator<ClientRoute>()
    private let root: (Navigator<ClientRoute>) -> Root

    init(session: SessionModel, @ViewBuilder root: @escaping (Navigator<ClientRoute>) -> Root) {
        self.session = session
        self.root = root
    }

    var body: some View {
        NavigationStack(path: $nav.path) {
            root(nav)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Menu {
                            Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right", role: .destructive) {
                                session.signOut()
                            }
                        } label: {
                            Image(systemName: "person.crop.circle")
                        }
                        .accessibilityLabel("Account")
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
                IosScreensKt.clientWorkoutSessionScreen(host: host, dayId: dayId, onDone: { nav.pop() })
            }
        case .checkin(let id):
            ComposeScreen(title: "Check-in") { host in
                IosScreensKt.clientCheckinDetailScreen(host: host, checkinId: id, onBack: { nav.pop() })
            }
        }
    }
}
