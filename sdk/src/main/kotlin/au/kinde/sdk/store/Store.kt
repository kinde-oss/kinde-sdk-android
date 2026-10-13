package au.kinde.sdk.store

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import java.security.MessageDigest

/**
 * Persists the SDK's auth state and JWKS in a per-domain SharedPreferences file.
 *
 * Values are encrypted with AES-256-GCM under a key held in the Android
 * Keystore (see [StoreCipher]), with a fresh IV per value, so a copy of the
 * preferences file (from a backup or a rooted device) can't be read without
 * the device's keystore. Values written by SDK 2.2.0 and earlier used a key
 * derived from the domain name; they are decrypted once on read and re-saved
 * in the new format, so signed-in users stay signed in across the upgrade.
 *
 * A value whose key no longer exists or rejects it (for example a backup
 * restored onto another device) is treated as absent and removed, which reads
 * as "signed out" rather than crashing. A value whose key exists but can't be
 * reached this time (a transient keystore failure) is kept for a later read.
 *
 * @author roman
 * @since 1.0
 */
class Store(context: Context, private val key: String) {

    // Create domain-specific SharedPreferences file to isolate state across domains
    // Sanitize the domain to create a safe filename (replace special chars with underscores)
    private val prefsName = "${PREFS_NAME}_${sanitizeDomainForFilename(key)}"

    private val authPrefs: SharedPreferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val cipher = StoreCipher.get(context)

    fun saveState(state: String) {
        store(AUTH_STATE_PREF, state)
    }

    fun getState(): String? = retrieve(AUTH_STATE_PREF)

    fun clearState() {
        synchronized(LOCK) {
            authPrefs.edit().remove(AUTH_STATE_PREF).apply()
        }
    }

    fun saveKeys(keys: String) {
        store(KEYS_PREF, keys)
    }

    fun getKeys(): String? = retrieve(KEYS_PREF)

    // One process-wide lock: several Store instances can point at the same file,
    // and the SDK writes from the main thread and background threads at once.
    private fun store(dataKey: String, data: String) = synchronized(LOCK) {
        authPrefs.edit().putString(dataKey, cipher.encrypt(data)).apply()
    }

    private fun retrieve(dataKey: String): String? = synchronized(LOCK) {
        val stored = authPrefs.getString(dataKey, null) ?: return null
        if (StoreCipher.isCurrentFormat(stored)) {
            return when (val result = cipher.decrypt(stored)) {
                is StoreCipher.Decrypted.Value -> {
                    // Written with the fallback key while the keystore was unavailable:
                    // move it to the key new values use now.
                    if (!cipher.isWriteFormat(stored)) {
                        authPrefs.edit().putString(dataKey, cipher.encrypt(result.plain)).apply()
                    }
                    result.plain
                }
                StoreCipher.Decrypted.Unreadable -> {
                    Log.w(TAG, "Discarding stored $dataKey that can no longer be decrypted (restored from another device?)")
                    authPrefs.edit().remove(dataKey).apply()
                    null
                }
                // The key exists but couldn't be reached this time; keep the value for later.
                StoreCipher.Decrypted.Unavailable -> null
            }
        }
        migrateLegacy(dataKey, stored)
    }

    /** Re-encrypts a value written by SDK 2.2.0 or earlier. */
    private fun migrateLegacy(dataKey: String, stored: String): String? {
        val plain = LegacyCipher.decrypt(key, authPrefs.getString(LEGACY_IV_PREF, null), stored)
        val editor = authPrefs.edit()
        if (plain == null) {
            Log.w(TAG, "Discarding unreadable legacy $dataKey")
            editor.remove(dataKey)
        } else {
            editor.putString(dataKey, cipher.encrypt(plain))
        }
        val legacyLeft = listOf(AUTH_STATE_PREF, KEYS_PREF).any { other ->
            other != dataKey && authPrefs.getString(other, null)?.let { !StoreCipher.isCurrentFormat(it) } == true
        }
        if (!legacyLeft) editor.remove(LEGACY_IV_PREF)
        editor.apply()
        return plain
    }

    companion object {
        private const val TAG = "KindeSDK"
        private const val PREFS_NAME = "app_prefs_secure"
        private const val AUTH_STATE_PREF = "auth_state"
        private const val KEYS_PREF = "keys"
        private const val LEGACY_IV_PREF = "iv"

        private val LOCK = Any()

        /**
         * Sanitizes a domain string to create a safe and unique filename.
         * Uses SHA-256 hash to guarantee uniqueness and prevent collisions
         * between similar domains (e.g., a-b.com vs a_b.com).
         */
        private fun sanitizeDomainForFilename(domain: String): String {
            // Use SHA-256 hash to guarantee unique filename for each domain
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(domain.toByteArray(Charsets.UTF_8))
            // Convert to hex string (safe for filenames)
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/**
 * The scheme used up to SDK 2.2.0, kept only to read values written by older versions:
 * AES-CBC with the domain name padded to 16 bytes as the key and one IV
 * stored beside the values. Never used for writing.
 */
internal object LegacyCipher {
    private const val KEY_SIZE = 16

    fun decrypt(domain: String, ivBase64: String?, cipherText: String): String? {
        if (ivBase64 == null) return null
        return try {
            val keyBytes = ByteArray(KEY_SIZE) { i -> domain.getOrNull(i)?.code?.toByte() ?: Byte.MIN_VALUE }
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                javax.crypto.Cipher.DECRYPT_MODE,
                javax.crypto.spec.SecretKeySpec(keyBytes, "AES"),
                javax.crypto.spec.IvParameterSpec(Base64.decode(ivBase64, Base64.NO_WRAP))
            )
            String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
