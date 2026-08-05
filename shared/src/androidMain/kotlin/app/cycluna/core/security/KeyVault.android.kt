package app.cycluna.core.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The application context the vault runs against.
 *
 * `defaultKeyVault()` takes no arguments — the iOS Keychain needs no handle, so the expect
 * declaration has none — but every Android storage API does. The app installs the context
 * once from `Application.onCreate()`, before anything can ask for a vault.
 */
object KeyVaultContext {
    @Volatile
    internal var appContext: Context? = null
        private set

    fun install(context: Context) {
        appContext = context.applicationContext
    }
}

/** Android actual — AES-GCM under a hardware-backed AndroidKeyStore key. */
actual fun defaultKeyVault(): KeyVault = AndroidKeyVault(
    requireNotNull(KeyVaultContext.appContext) {
        "KeyVaultContext.install() must be called from Application.onCreate()"
    }
)

/**
 * Values are encrypted with a 256-bit AES key that never leaves the AndroidKeyStore (the
 * Keychain's counterpart), then parked in ordinary preferences as `base64(iv):base64(ct)`.
 * Preferences alone would be plaintext on a rooted device, and Jetpack's
 * `EncryptedSharedPreferences` is deprecated, so the vault owns the crypto directly.
 *
 * The key deliberately carries no user-authentication requirement: the vault has to work
 * with the app lock switched off, and on a device with no biometrics enrolled.
 */
private class AndroidKeyVault(context: Context) : KeyVault {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun set(key: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(key, "${encode(cipher.iv)}$SEPARATOR${encode(ciphertext)}").commit()
    }

    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        val parts = stored.split(SEPARATOR)
        if (parts.size != 2) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, decode(parts[0])))
            String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8)
        } catch (_: Exception) {
            // The Keystore key is gone or the blob is corrupt — treat it as absent rather
            // than crashing. A vault miss is recoverable; the caller re-derives or re-auths.
            null
        }
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).commit()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private fun secretKey(): SecretKey = synchronized(AndroidKeyVault) {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "cycluna.keyvault"
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cycluna.keyvault.master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        const val SEPARATOR = ":"
    }
}
