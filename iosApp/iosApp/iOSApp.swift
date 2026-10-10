import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        SessionKt.setUpApp()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}
