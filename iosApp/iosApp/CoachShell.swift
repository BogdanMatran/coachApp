import SwiftUI
import ComposeApp

enum CoachRoute: Hashable {
    case client(id: String)
    case invites
    /// kind is "workout", "meal" or "supplement"; no clientId means a template.
    case builder(kind: String, planId: String?, clientId: String?)
    case checkinTemplate
    case checkinReview(id: String)
}

struct CoachShell: View {
    let session: SessionModel

    var body: some View {
        TabView {
            CoachStack { nav in
                ComposeScreen(title: "Clients", displayMode: .large) { host in
                    IosScreensKt.coachClientsScreen(
                        host: host,
                        onOpenClient: { nav.push(.client(id: $0)) },
                        onOpenInvites: { nav.push(.invites) }
                    )
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Clients", systemImage: "person.2") }

            CoachStack { nav in
                ComposeScreen(title: "Plans", displayMode: .large) { host in
                    IosScreensKt.coachPlansScreen(host: host) { kind, planId in
                        nav.push(.builder(kind: kind, planId: planId, clientId: nil))
                    }
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Plans", systemImage: "list.bullet.clipboard") }

            CoachStack { _ in
                ComposeScreen(title: "Library", displayMode: .large) { host in
                    IosScreensKt.coachLibraryScreen(host: host)
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Library", systemImage: "books.vertical") }

            CoachStack { nav in
                ComposeScreen(title: "Settings", displayMode: .large) { host in
                    IosScreensKt.coachSettingsScreen(
                        host: host,
                        onOpenCheckinTemplate: { nav.push(.checkinTemplate) },
                        onOpenInvites: { nav.push(.invites) },
                        onSignOut: { session.signOut() }
                    )
                }
                .contentUnderTabBar()
            }
            .tabItem { Label("Settings", systemImage: "gearshape") }
        }
    }
}

/// One tab's navigation stack; every pushed coach screen is resolved here.
private struct CoachStack<Root: View>: View {
    @StateObject private var nav = Navigator<CoachRoute>()
    private let root: (Navigator<CoachRoute>) -> Root

    init(@ViewBuilder root: @escaping (Navigator<CoachRoute>) -> Root) {
        self.root = root
    }

    var body: some View {
        NavigationStack(path: $nav.path) {
            root(nav).navigationDestination(for: CoachRoute.self) { route in
                destination(route)
            }
        }
    }

    @ViewBuilder
    private func destination(_ route: CoachRoute) -> some View {
        switch route {
        case .client(let id):
            ComposeScreen(title: "Client") { host in
                IosScreensKt.coachClientDetailScreen(
                    host: host,
                    clientId: id,
                    onBack: { nav.pop() },
                    onOpenBuilder: { kind, planId, clientId in
                        nav.push(.builder(kind: kind, planId: planId, clientId: clientId))
                    },
                    onOpenCheckin: { nav.push(.checkinReview(id: $0)) }
                )
            }
        case .invites:
            ComposeScreen(title: "Invites") { host in
                IosScreensKt.coachInvitesScreen(host: host, onBack: { nav.pop() })
            }
        case .builder(let kind, let planId, let clientId):
            ComposeScreen(title: "Plan") { host in
                IosScreensKt.coachBuilderScreen(host: host, kind: kind, planId: planId, clientId: clientId, onDone: { nav.pop() })
            }
        case .checkinTemplate:
            ComposeScreen(title: "Check-in questions") { host in
                IosScreensKt.coachCheckinTemplateScreen(host: host, onBack: { nav.pop() })
            }
        case .checkinReview(let id):
            ComposeScreen(title: "Check-in") { host in
                IosScreensKt.coachCheckinReviewScreen(host: host, checkinId: id, onBack: { nav.pop() })
            }
        }
    }
}
