package app.cycluna.android.core

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK or
    BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * Optional on-device access gate. When "Require unlock to open" is on, the app's content is
 * covered until the user passes biometric or device-credential auth — protecting their data
 * from anyone holding an already-unlocked phone.
 *
 * No accounts are involved; this is local access control, not authentication.
 */
@Stable
class AppLock {

    /** True once the user has passed auth for the current foreground session. */
    var isUnlocked by mutableStateOf(false)

    /** Guards against overlapping prompts. */
    private var authenticating = false

    /**
     * Present the system prompt.
     *
     * Falls open when the device has nothing enrolled, so switching the setting on can never
     * lock someone out of their own data — the same rule the iOS twin follows.
     */
    fun authenticate(activity: FragmentActivity) {
        if (authenticating || isUnlocked) return

        if (BiometricManager.from(activity).canAuthenticate(ALLOWED) !=
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            isUnlocked = true
            return
        }

        authenticating = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    isUnlocked = true
                    authenticating = false
                }

                override fun onAuthenticationError(code: Int, message: CharSequence) {
                    // A cancelled prompt leaves the lock screen up with its own retry button
                    // rather than dumping the user into the app.
                    authenticating = false
                }

                override fun onAuthenticationFailed() {
                    // A wrong finger is not an error; the system prompt lets them try again.
                }
            },
        )

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Cycluna")
                .setSubtitle("Unlock to view your data")
                .setAllowedAuthenticators(ALLOWED)
                .build()
        )
    }

    /** Re-lock; called when the app leaves the foreground. */
    fun lock() {
        isUnlocked = false
    }
}
