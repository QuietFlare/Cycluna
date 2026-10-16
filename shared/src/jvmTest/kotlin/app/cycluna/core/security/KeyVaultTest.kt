package app.cycluna.core.security

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the JVM in-memory actual only, hence jvmTest. The Keychain actual is covered by
 * iosAppTests and the Keystore actual needs an instrumented test; neither runs here.
 */
class KeyVaultTest {
    private val vault = defaultKeyVault()

    @BeforeTest
    fun reset() = vault.clear()

    @Test
    fun setGetRemove() {
        vault.set("token", "abc123")
        assertEquals("abc123", vault.get("token"))
        vault.remove("token")
        assertNull(vault.get("token"))
    }

    @Test
    fun missingKeyIsNull() {
        assertNull(vault.get("nope"))
    }
}
