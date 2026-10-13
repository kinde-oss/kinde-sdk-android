package au.kinde.sdk.store

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM encryption for [Store] values.
 *
 * On devices the key is generated inside the Android Keystore and never leaves
 * it, so the stored ciphertext is useless off the device. Every value gets its
 * own random 96-bit IV, and GCM authenticates it, so tampering is detected.
 *
 * Stored format: `<prefix><base64(iv || ciphertext+tag)>`, where the prefix
 * records which key encrypted it:
 * - `k1:` Android Keystore key (every device: API 23+ ships the keystore)
 * - `s1:` software key, used for new values only where the Android Keystore
 *   provider doesn't exist (JVM/Robolectric tests) or failed this run. It is
 *   random, not derived from anything public, and kept in its own preferences
 *   file (`app_prefs_secure_key`).
 *
 * Both kinds are always readable, whichever key new values are written with, so
 * a run where the keystore misbehaves doesn't strand values written by another.
 */
internal class StoreCipher private constructor(
    private val source: KeySource,
    private val readers: List<KeySource>,
) {

    internal interface KeySource {
        val prefix: String
        /** The keystore generates IVs itself and refuses caller-supplied ones. */
        val generatesIv: Boolean
        fun key(): SecretKey

        /**
         * The key for reading: null when it definitely doesn't exist (nothing
         * written with it can ever be decrypted). Throws when it can't be
         * reached right now, which must not cost the user their data.
         */
        fun existingKey(): SecretKey?
    }

    sealed class Decrypted {
        class Value(val plain: String) : Decrypted()
        /** The matching key exists and rejected the value, or no longer exists: safe to discard. */
        object Unreadable : Decrypted()
        /** The matching key couldn't be reached this time: keep the value and try again later. */
        object Unavailable : Decrypted()
    }

    /** True when [stored] was written with the key new values are written with. */
    fun isWriteFormat(stored: String) = stored.startsWith(source.prefix)

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        if (source.generatesIv) {
            cipher.init(Cipher.ENCRYPT_MODE, source.key())
        } else {
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
            cipher.init(Cipher.ENCRYPT_MODE, source.key(), GCMParameterSpec(TAG_BITS, iv))
        }
        val iv = cipher.iv
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return source.prefix + Base64.encodeToString(iv + sealed, Base64.NO_WRAP)
    }

    /**
     * Decrypts with the key that wrote [stored], whichever source that was, so
     * switching the write source between runs never strands existing values.
     */
    fun decrypt(stored: String): Decrypted {
        val reader = readers.firstOrNull { stored.startsWith(it.prefix) } ?: return Decrypted.Unreadable
        val key = try {
            reader.existingKey() ?: return Decrypted.Unreadable
        } catch (e: Exception) {
            Log.w("KindeSDK", "Key for stored value is temporarily unavailable; keeping the value", e)
            return Decrypted.Unavailable
        }
        return try {
            val bytes = Base64.decode(stored.removePrefix(reader.prefix), Base64.NO_WRAP)
            if (bytes.size <= IV_BYTES) return Decrypted.Unreadable
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            Decrypted.Value(String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8))
        } catch (e: javax.crypto.AEADBadTagException) {
            Decrypted.Unreadable
        } catch (e: IllegalArgumentException) {
            Decrypted.Unreadable
        } catch (e: Exception) {
            // Keystore operations can fail transiently (e.g. a busy keystore daemon).
            Log.w("KindeSDK", "Could not decrypt stored value right now; keeping it", e)
            Decrypted.Unavailable
        }
    }

    /** Keys held by the Android Keystore. One key for the app, shared by every domain's store. */
    private class KeystoreSource : KeySource {
        override val prefix = KEYSTORE_PREFIX
        override val generatesIv = true

        @Volatile
        private var cached: SecretKey? = null

        override fun existingKey(): SecretKey? = cached ?: synchronized(this) {
            if (Security.getProvider(ANDROID_KEYSTORE) == null) {
                // No keystore in this environment, so it can't be read *here*; that
                // says nothing about the key existing on the device that wrote it.
                throw IllegalStateException("Android Keystore provider not available")
            }
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.also { cached = it }
        }

        override fun key(): SecretKey = cached ?: synchronized(this) {
            cached ?: run {
                val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                    .apply {
                        init(
                            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setKeySize(256)
                                .build()
                        )
                    }
                    .generateKey()
            }.also { cached = it }
        }
    }

    /** A random key in app-private storage, for environments without the Android Keystore. */
    private class SoftwareSource(context: Context) : KeySource {
        override val prefix = SOFTWARE_PREFIX
        override val generatesIv = false
        private val prefs = context.getSharedPreferences(SOFTWARE_KEY_PREFS, Context.MODE_PRIVATE)

        @Volatile
        private var cached: SecretKey? = null

        override fun existingKey(): SecretKey? = cached ?: synchronized(this) {
            prefs.getString(SOFTWARE_KEY_PREF, null)
                ?.let { SecretKeySpec(Base64.decode(it, Base64.NO_WRAP), "AES") }
                ?.also { cached = it }
        }

        override fun key(): SecretKey = cached ?: synchronized(this) {
            cached ?: run {
                val existing = prefs.getString(SOFTWARE_KEY_PREF, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
                val bytes = existing ?: ByteArray(32).also {
                    random.nextBytes(it)
                    // commit, not apply: the key must be on disk before anything is encrypted with it.
                    prefs.edit().putString(SOFTWARE_KEY_PREF, Base64.encodeToString(it, Base64.NO_WRAP)).commit()
                }
                SecretKeySpec(bytes, "AES")
            }.also { cached = it }
        }
    }

    companion object {
        internal const val KEYSTORE_PREFIX = "k1:"
        internal const val SOFTWARE_PREFIX = "s1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "au.kinde.sdk.store.v1"
        private const val SOFTWARE_KEY_PREFS = "app_prefs_secure_key"
        private const val SOFTWARE_KEY_PREF = "k"

        private val random = SecureRandom()

        @Volatile
        private var instance: StoreCipher? = null

        /** True for values written by this cipher (any key source), false for the pre-keystore format. */
        fun isCurrentFormat(stored: String) = stored.startsWith(KEYSTORE_PREFIX) || stored.startsWith(SOFTWARE_PREFIX)

        fun get(context: Context): StoreCipher = instance ?: synchronized(this) {
            instance ?: create(context.applicationContext).also { instance = it }
        }

        /** Writes with the keystore when it works this run; always reads with both. */
        private fun create(context: Context): StoreCipher {
            val keystore = KeystoreSource()
            val software = SoftwareSource(context)
            val writer: KeySource = if (Security.getProvider(ANDROID_KEYSTORE) != null) {
                try {
                    keystore.key()
                    keystore
                } catch (e: Exception) {
                    Log.w("KindeSDK", "Android Keystore unavailable; using a software key for new values", e)
                    software
                }
            } else {
                software
            }
            return StoreCipher(writer, listOf(keystore, software))
        }

        /** Test hook: use these key sources instead of the real ones. */
        @androidx.annotation.VisibleForTesting
        internal fun installForTest(writer: KeySource, readers: List<KeySource>) {
            instance = StoreCipher(writer, readers)
        }

        /** Test hook: start over with a fresh choice of key source. */
        @androidx.annotation.VisibleForTesting
        internal fun resetForTest() {
            instance = null
        }
    }
}
