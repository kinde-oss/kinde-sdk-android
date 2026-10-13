package au.kinde.sdk.store

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.CyclicBarrier
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // The cipher is process-wide and caches its key, but Robolectric clears
    // SharedPreferences between tests: start each test with a fresh one.
    @Before
    fun freshCipher() = StoreCipher.resetForTest()

    @Test
    fun `state round-trips through encryption`() {
        val store = Store(context, "domain-a.kinde.com")

        store.saveState("""{"auth":"state"}""")

        assertEquals("""{"auth":"state"}""", store.getState())
    }

    @Test
    fun `clearState removes stored state`() {
        val store = Store(context, "domain-a.kinde.com")
        store.saveState("some-state")

        store.clearState()

        assertNull(store.getState())
    }

    @Test
    fun `keys round-trip through encryption`() {
        val store = Store(context, "domain-a.kinde.com")

        store.saveKeys("""{"keys":[]}""")

        assertEquals("""{"keys":[]}""", store.getKeys())
    }

    @Test
    fun `stores for different domains are isolated`() {
        val storeA = Store(context, "domain-a.kinde.com")
        val storeB = Store(context, "domain-b.kinde.com")

        storeA.saveState("state-for-a")

        assertNull(storeB.getState())
        assertEquals("state-for-a", storeA.getState())
    }

    @Test
    fun `same domain shares one store`() {
        val first = Store(context, "domain-a.kinde.com")
        val second = Store(context, "domain-a.kinde.com")

        first.saveState("shared-state")

        assertEquals("shared-state", second.getState())
    }

    // -- Encryption at rest ---------------------------------------------------------------

    private val domain = "domain-a.kinde.com"

    private fun rawPrefs(forDomain: String = domain) = context.getSharedPreferences(
        "app_prefs_secure_" + MessageDigest.getInstance("SHA-256").digest(forDomain.toByteArray()).joinToString("") { "%02x".format(it) },
        Context.MODE_PRIVATE
    )

    /** How SDK 2.2.0 and earlier encrypted values: AES-CBC, key = domain padded to 16 bytes, one stored IV. */
    private fun writeLegacy(dataKey: String, plain: String, iv: ByteArray = ByteArray(16) { it.toByte() }) {
        val key = ByteArray(16) { i -> domain.getOrNull(i)?.code?.toByte() ?: Byte.MIN_VALUE }
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        rawPrefs().edit()
            .putString("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .putString(dataKey, Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP))
            .commit()
    }

    @Test
    fun `values are encrypted in the current format, never in plain text`() {
        Store(context, domain).saveState("""{"refreshToken":"rt_secret_value"}""")

        val raw = rawPrefs().getString("auth_state", null)!!
        assertTrue(raw, StoreCipher.isCurrentFormat(raw))
        assertFalse(raw.contains("rt_secret_value"))
        assertNull("no shared IV is stored any more", rawPrefs().getString("iv", null))
    }

    @Test
    fun `the domain name alone no longer decrypts the session`() {
        Store(context, domain).saveState("""{"refreshToken":"rt_secret_value"}""")
        val raw = rawPrefs().getString("auth_state", null)!!

        // The attack that worked against 2.2.0: rebuild the key from the public domain name.
        val decrypted = LegacyCipher.decrypt(domain, Base64.encodeToString(ByteArray(16), Base64.NO_WRAP), raw)

        assertNull(decrypted)
    }

    @Test
    fun `the same value encrypts differently every time`() {
        val store = Store(context, domain)
        store.saveState("same")
        val first = rawPrefs().getString("auth_state", null)
        store.saveState("same")

        assertNotEquals(first, rawPrefs().getString("auth_state", null))
        assertEquals("same", store.getState())
    }

    @Test
    fun `a session saved by 2_2_0 is still readable and is re-encrypted on first read`() {
        writeLegacy("auth_state", """{"state":"legacy"}""")

        assertEquals("""{"state":"legacy"}""", Store(context, domain).getState())

        val raw = rawPrefs().getString("auth_state", null)!!
        assertTrue("migrated: $raw", StoreCipher.isCurrentFormat(raw))
        assertNull("legacy IV removed once nothing uses it", rawPrefs().getString("iv", null))
        assertEquals("""{"state":"legacy"}""", Store(context, domain).getState())
    }

    @Test
    fun `the legacy IV is kept until every legacy value is migrated`() {
        writeLegacy("auth_state", "legacy-state")
        writeLegacy("keys", "legacy-keys")
        val store = Store(context, domain)

        assertEquals("legacy-state", store.getState())
        assertNotNull("keys still need it", rawPrefs().getString("iv", null))
        assertEquals("legacy-keys", store.getKeys())
        assertNull(rawPrefs().getString("iv", null))
    }

    @Test
    fun `a tampered value reads as absent instead of crashing`() {
        val store = Store(context, domain)
        store.saveState("original")
        val raw = rawPrefs().getString("auth_state", null)!!
        val flipped = raw.dropLast(2) + (if (raw[raw.length - 2] == 'A') "B" else "A") + raw.last()
        rawPrefs().edit().putString("auth_state", flipped).commit()

        assertNull(store.getState())
        assertNull("discarded", rawPrefs().getString("auth_state", null))
    }

    @Test
    fun `a value encrypted with another device's key reads as absent`() {
        // Simulates a backup restored onto a new device: same format, different key.
        val otherKey = SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, "AES")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, otherKey, GCMParameterSpec(128, iv)) }
        val foreign = StoreCipher.SOFTWARE_PREFIX + Base64.encodeToString(iv + cipher.doFinal("someone else".toByteArray()), Base64.NO_WRAP)
        rawPrefs().edit().putString("auth_state", foreign).commit()

        assertNull(Store(context, domain).getState())
    }

    @Test
    fun `an unreadable legacy value reads as absent`() {
        // Valid Base64, but not something the legacy cipher wrote.
        val junk = Base64.encodeToString("not really encrypted".toByteArray(), Base64.NO_WRAP)
        rawPrefs().edit().putString("auth_state", junk).commit()

        assertNull(Store(context, domain).getState())
        assertNull(rawPrefs().getString("auth_state", null))
    }

    @Test
    fun `concurrent first writes from two threads both stay readable`() {
        val corrupted = (1..40).count { i ->
            val d = "race$i.kinde.invalid"
            val barrier = CyclicBarrier(2)
            val a = Thread { barrier.await(); Store(context, d).saveKeys("keys-$i") }
            val b = Thread { barrier.await(); Store(context, d).saveState("state-$i") }
            a.start(); b.start(); a.join(); b.join()
            Store(context, d).getKeys() != "keys-$i" || Store(context, d).getState() != "state-$i"
        }

        assertEquals(0, corrupted)
    }

    @Test
    fun `a value whose key can't be reached right now is kept, not deleted`() {
        // Robolectric has no Android Keystore, which looks exactly like a transient
        // keystore failure to a k1: value: its key may well exist on the device.
        val keystoreValue = StoreCipher.KEYSTORE_PREFIX + Base64.encodeToString(ByteArray(40) { it.toByte() }, Base64.NO_WRAP)
        rawPrefs().edit().putString("auth_state", keystoreValue).commit()

        assertNull(Store(context, domain).getState())
        assertEquals("kept for a later read", keystoreValue, rawPrefs().getString("auth_state", null))
    }

    @Test
    fun `values written with the fallback key stay readable when the write key changes`() {
        Store(context, domain).saveState("written-by-fallback")
        val raw = rawPrefs().getString("auth_state", null)!!
        assertTrue(raw.startsWith(StoreCipher.SOFTWARE_PREFIX))

        // A fresh cipher instance (as in a new process) still reads it.
        StoreCipher.resetForTest()

        assertEquals("written-by-fallback", Store(context, domain).getState())
    }

    @Test
    fun `a fallback-key value whose key is gone is discarded`() {
        Store(context, domain).saveState("orphaned")
        context.getSharedPreferences("app_prefs_secure_key", Context.MODE_PRIVATE).edit().clear().commit()
        StoreCipher.resetForTest()

        assertNull(Store(context, domain).getState())
        assertNull(rawPrefs().getString("auth_state", null))
    }
}
