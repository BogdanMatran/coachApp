import SwiftUI
import ComposeApp

/// Picks the shell for the signed-in user's role, mirroring AppRoot on Android and the web.
struct RootView: View {
    @StateObject private var session = SessionModel()

    var body: some View {
        ZStack {
            Color(.appBackground).ignoresSafeArea()
            content
        }
    }

    @ViewBuilder
    private var content: some View {
        if let snapshot = session.snapshot {
            if snapshot.kind == .coach {
                CoachShell(session: session).id(snapshot.profileId)
            } else if snapshot.kind == .client {
                ClientShell(session: session, firstName: snapshot.firstName).id(snapshot.profileId)
            } else if snapshot.kind == .joincoach {
                ComposeFullScreen { IosScreensKt.joinCoachScreen() }
            } else if snapshot.kind == .signedout {
                ComposeFullScreen { IosScreensKt.authScreen() }
            } else if snapshot.kind == .error {
                SessionErrorView(message: snapshot.message ?? "Something went wrong", session: session)
            } else {
                ProgressView()
            }
        } else {
            ProgressView()
        }
    }
}

private struct SessionErrorView: View {
    let message: String
    let session: SessionModel

    var body: some View {
        ContentUnavailableView {
            Label("Couldn't load your profile", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Retry") { session.retry() }
                .buttonStyle(.borderedProminent)
            Button("Sign out", role: .destructive) { session.signOut() }
        }
    }
}
