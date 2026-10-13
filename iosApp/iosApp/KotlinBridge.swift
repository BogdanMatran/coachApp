import Combine
import SwiftUI
import UIKit
import ComposeApp

/// Kotlin auth state as an ObservableObject. Kotlin delivers updates on the main thread.
final class SessionModel: ObservableObject {
    @Published private(set) var snapshot: SessionSnapshot?
    private let bridge = SessionBridge()
    private var observation: SessionObservation?

    init() {
        observation = bridge.observe { [weak self] snapshot in
            self?.snapshot = snapshot
        }
    }

    deinit {
        observation?.cancel()
    }

    func signOut() { bridge.signOut() }
    func retry() { bridge.retry() }
}

/// Title and bar buttons reported by one Compose screen.
final class ScreenChromeModel: ObservableObject {
    let host = ScreenHost()
    @Published var title: String?
    @Published var items: [ChromeItem] = []
    @Published var interceptBack = false

    init() {
        host.onChange = { [weak self] state in
            self?.title = state.title
            self?.items = state.items
            self?.interceptBack = state.interceptBack
        }
    }
}

/// A Compose screen inside a NavigationStack: SwiftUI draws the navigation bar from what the screen reports.
struct ComposeScreen: View {
    let title: String
    var displayMode: NavigationBarItem.TitleDisplayMode = .inline
    let make: (ScreenHost) -> UIViewController
    @StateObject private var chrome = ScreenChromeModel()

    var body: some View {
        ComposeController(make: { make(chrome.host) })
            .background(Color(.appBackground))
            .navigationTitle(chrome.title ?? title)
            .navigationBarTitleDisplayMode(displayMode)
            // Screens with unsaved input (e.g. a workout session) confirm before leaving.
            .navigationBarBackButtonHidden(chrome.interceptBack)
            .toolbar {
                if chrome.interceptBack {
                    ToolbarItem(placement: .topBarLeading) {
                        Button { chrome.host.back() } label: { Image(systemName: "chevron.backward") }
                            .accessibilityLabel("Back")
                    }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    ForEach(chrome.items, id: \.id) { item in
                        ChromeButton(item: item) { chrome.host.trigger(id: item.id) }
                    }
                }
            }
    }
}

extension View {
    /// Lets a tab's root Compose screen run under the tab bar, the way a native scroll view does.
    ///
    /// The screen still learns the bar's height: it arrives as the bottom safe-area inset, which
    /// Compose exposes through `bottomBarInset()`, so lists pad their end and the last row can be
    /// scrolled clear. Only use it on screens that reserve that inset (the tab roots do).
    ///
    /// Before iOS 26 the bar would otherwise stay in its transparent scroll-edge appearance, since
    /// UIKit cannot see a UIScrollView inside Compose, so its blurred background is forced on.
    /// From iOS 26 the Liquid Glass bar floats over content by itself.
    @ViewBuilder
    func contentUnderTabBar() -> some View {
        let extended = ignoresSafeArea(.container, edges: .bottom)
        if #available(iOS 26, *) {
            extended
        } else {
            extended.toolbarBackground(.visible, for: .tabBar)
        }
    }
}

/// A full-screen Compose view without navigation chrome (sign in, join coach).
struct ComposeFullScreen: View {
    let make: () -> UIViewController

    var body: some View {
        ComposeController(make: make).ignoresSafeArea()
    }
}

private struct ChromeButton: View {
    let item: ChromeItem
    let action: () -> Void

    var body: some View {
        if !item.tappable {
            Text(item.label)
                .font(.subheadline.monospacedDigit())
                .foregroundStyle(.secondary)
        } else if let symbol = item.sfSymbol {
            Button(action: action) { Image(systemName: symbol) }
                .accessibilityLabel(item.label)
                .disabled(!item.enabled)
        } else {
            Button(item.label, action: action)
                .fontWeight(.semibold)
                .disabled(!item.enabled)
        }
    }
}

private struct ComposeController: UIViewControllerRepresentable {
    let make: () -> UIViewController

    func makeUIViewController(context: Context) -> UIViewController { make() }
    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Navigation path for one tab's NavigationStack.
final class Navigator<Route: Hashable>: ObservableObject {
    @Published var path: [Route] = []

    func push(_ route: Route) { path.append(route) }
    func pop() { if !path.isEmpty { path.removeLast() } }
}
