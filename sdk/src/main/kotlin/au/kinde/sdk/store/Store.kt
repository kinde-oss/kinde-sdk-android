package au.kinde.sdk.store

import android.content.Context
import android.util.Base64
import androidx.appcompat.app.AppCompatActivity
import java.io.UnsupportedEncodingException
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.spec.InvalidKeySpecException
import java.security.spec.InvalidParameterSpecException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random.Default.nextBytes


/**
 * @author roman
 * @since 1.0
 */
class Store(context: Context, private val key: String) {

    // Create domain-specific SharedPreferences file to isolate state across domains
    // Sanitize the domain to create a safe filename (replace special chars with underscores)
    private val prefsName = "${PREFS_NAME}_${sanitizeDomainForFilename(key)}"
    
    private val authPrefs = context.getSharedPreferences(
        prefsName,
        AppCompatActivity.MODE_PRIVATE
    )

    fun saveState(state: String) {
        store(AUTH_STATE_PREF, state)
    }

    fun getState(): String? = retrieve(AUTH_STATE_PREF)

    fun clearState() {
        authPrefs.edit().remove(AUTH_STATE_PREF).apply()
    }

    fun saveKeys(keys: String) {
        store(KEYS_PREF, keys)
    }

    fun getKeys(): String? = retrieve(KEYS_PREF)

    private fun store(dataKey: String, data: String) {
        authPrefs.edit().putString(dataKey, encryptMsg(data)).apply()
    }

    private fun retrieve(dataKey: String): String? {
        return authPrefs.getString(dataKey, null)?.let { decryptMsg(it) }
    }

    @Throws(
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        InvalidKeyException::class,
        InvalidParameterSpecException::class,
        IllegalBlockSizeException::class,
        BadPaddingException::class,
        UnsupportedEncodingException::class
    )
    private fun encryptMsg(message: String): String? {
        val cipherText =
            getCipher(Cipher.ENCRYPT_MODE)?.doFinal(message.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class,
        InvalidParameterSpecException::class,
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        BadPaddingException::class,
        IllegalBlockSizeException::class,
        UnsupportedEncodingException::class
    )
    private fun decryptMsg(cipherText: String?): String? {
        val decode: ByteArray = Base64.decode(cipherText, Base64.NO_WRAP)
        return getCipher(Cipher.DECRYPT_MODE)?.doFinal(decode)?.let { String(it, Charsets.UTF_8) }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    private fun generateKey(key: String): SecretKey {
        return SecretKeySpec(ByteArray(KEY_SIZE) { i ->
            key.getOrNull(i)?.code?.toByte() ?: Byte.MIN_VALUE
        }, ALGORITHM)
    }

    private fun getCipher(mode: Int): Cipher? {
        val cipher: Cipher? = Cipher.getInstance(TRANSFORMATION)
        val ivSpec =
            authPrefs.getString(IV_PREF, null)
                ?.let { IvParameterSpec(Base64.decode(it, Base64.NO_WRAP)) } ?: run {
                val iv = nextBytes(IV_SIZE)
                authPrefs.edit().putString(IV_PREF, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()
                IvParameterSpec(iv)
            }
        cipher?.init(mode, generateKey(key), ivSpec)
        return cipher
    }

    companion object {
        private const val PREFS_NAME = "app_prefs_secure"
        private const val AUTH_STATE_PREF = "auth_state"
        private const val KEYS_PREF = "keys"
        private const val IV_PREF = "iv"

        private const val IV_SIZE = 16
        private const val KEY_SIZE = 16
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "$ALGORITHM/CBC/PKCS5Padding"
        
        /**
         * Sanitizes a domain string to create a safe and unique filename.
         * This prevents hash collisions that could occur with hashCode().
         */
        private fun sanitizeDomainForFilename(domain: String): String {
            // Replace special characters with underscores to create a safe filename
            // This ensures each domain gets a unique prefs file without collisions
            return domain.replace("[^a-zA-Z0-9]".toRegex(), "_")
        }
    }
}