import SwiftUI
import LocalAuthentication

/// Optional on-device access gate. When the user enables "Require Face ID to open", the
/// app content is covered until they pass biometric (or device-passcode) auth — protecting
/// their data from anyone with a physical, already-unlocked phone. No accounts involved;
/// this is purely local access control, not authentication.
@Observable
final class AppLock {
    /// True once the user has passed auth for the current foreground session.
    var isUnlocked = false
    /// Guards against overlapping prompts.
    private var authenticating = false

    /// Present the system auth prompt. Falls open if the device has no biometrics/passcode
    /// configured, so enabling the setting can never lock a user out of their own data.
    func authenticate(reason: String = "Unlock Cycluna to view your data") {
        guard !authenticating else { return }
        authenticating = true

        let context = LAContext()
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            authenticating = false
            isUnlocked = true
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason) { success, _ in
            Task { @MainActor in
                self.isUnlocked = success
                self.authenticating = false
            }
        }
    }

    /// Re-lock (called when the app leaves the foreground).
    func lock() { isUnlocked = false }
}

/// Full-screen cover shown while the app is locked. Matches the night-sky brand and offers
/// a retry, in case the user cancels the system prompt.
struct LockScreen: View {
    var onUnlock: () -> Void

    var body: some View {
        ZStack {
            Theme.backgroundGradient.ignoresSafeArea()
            VStack(spacing: 18) {
                Image(systemName: "moon.stars.fill")
                    .font(.system(size: 44))
                    .foregroundStyle(Theme.primary)
                Text("Cycluna is locked")
                    .font(.cyclunaSerif(28))
                    .foregroundStyle(Theme.ink)
                Text("Your data stays private on this device.")
                    .font(.subheadline)
                    .foregroundStyle(Theme.inkSoft)
                Button(action: onUnlock) {
                    Label("Unlock", systemImage: "faceid")
                        .font(.headline)
                        .foregroundStyle(.white)
                        .padding(.vertical, 14).padding(.horizontal, 30)
                        .background(Theme.primary, in: Capsule())
                }
                .padding(.top, 8)
            }
        }
    }
}
