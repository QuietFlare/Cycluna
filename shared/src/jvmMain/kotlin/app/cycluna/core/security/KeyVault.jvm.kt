package app.cycluna.core.security

/**
 * JVM actual — in-memory only, for unit tests. There is no secure OS vault in the
 * plain JVM test target; production secure storage is iOS Keychain / Android Keystore.
 */
actual fun defaultKeyVault(): KeyVault = InMemoryKeyVault

internal object InMemoryKeyVault : KeyVault {
    private val store = mutableMapOf<String, String>()
    override fun set(key: String, value: String) { store[key] = value }
    override fun get(key: String): String? = store[key]
    override fun remove(key: String) { store.remove(key) }
    override fun clear() { store.clear() }
}
