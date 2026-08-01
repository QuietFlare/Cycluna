package app.cycluna.core.security

/**
 * Secure per-key string storage for sensitive values (auth JWT, journal
 * `letter_key`, etc.). Platform-backed: iOS → Keychain, Android → Keystore /
 * EncryptedSharedPreferences (later), JVM → in-memory (tests only).
 *
 * Never store secrets in plain preferences/UserDefaults — always via a KeyVault.
 */
interface KeyVault {
    fun set(key: String, value: String)
    fun get(key: String): String?
    fun remove(key: String)
    fun clear()
}

/** The platform's secure vault. */
expect fun defaultKeyVault(): KeyVault
