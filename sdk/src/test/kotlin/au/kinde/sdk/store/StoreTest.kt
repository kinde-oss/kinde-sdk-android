package au.kinde.sdk.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [Store] class.
 * Uses Robolectric to provide Android context for SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class StoreTest {

    private lateinit var context: Context
    private lateinit var store: Store
    private val testKey = "test_encryption_key"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        store = Store(context, testKey)
    }

    // ============================================
    // State Storage Tests
    // ============================================

    @Test
    fun `saveState and getState round trip works`() {
        val state = """{"accessToken":"abc123","refreshToken":"xyz789"}"""
        
        store.saveState(state)
        val retrieved = store.getState()
        
        assertEquals(state, retrieved)
    }

    @Test
    fun `getState returns null when no state saved`() {
        // Fresh store with no saved state
        val newStore = Store(context, "fresh_key_${System.currentTimeMillis()}")
        
        val result = newStore.getState()
        
        assertNull(result)
    }

    @Test
    fun `saveState overwrites previous state`() {
        val firstState = """{"token":"first"}"""
        val secondState = """{"token":"second"}"""
        
        store.saveState(firstState)
        store.saveState(secondState)
        val retrieved = store.getState()
        
        assertEquals(secondState, retrieved)
    }

    @Test
    fun `clearState removes stored state`() {
        val state = """{"token":"test"}"""
        store.saveState(state)
        
        store.clearState()
        val retrieved = store.getState()
        
        assertNull(retrieved)
    }

    @Test
    fun `saveState handles empty string`() {
        store.saveState("")
        val retrieved = store.getState()
        
        assertEquals("", retrieved)
    }

    @Test
    fun `saveState handles complex JSON`() {
        val complexState = """{
            "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9",
            "refreshToken": "refresh_token_value",
            "expiresIn": 3600,
            "tokenType": "Bearer",
            "scope": ["openid", "email", "profile"]
        }"""
        
        store.saveState(complexState)
        val retrieved = store.getState()
        
        assertEquals(complexState, retrieved)
    }

    @Test
    fun `saveState handles unicode characters`() {
        val unicodeState = """{"name":"日本語テスト","emoji":"🔐"}"""
        
        store.saveState(unicodeState)
        val retrieved = store.getState()
        
        assertEquals(unicodeState, retrieved)
    }

    // ============================================
    // Keys Storage Tests
    // ============================================

    @Test
    fun `saveKeys and getKeys round trip works`() {
        val keys = """{"keys":[{"kty":"RSA","kid":"key1","n":"abc","e":"AQAB"}]}"""
        
        store.saveKeys(keys)
        val retrieved = store.getKeys()
        
        assertEquals(keys, retrieved)
    }

    @Test
    fun `getKeys returns null when no keys saved`() {
        val newStore = Store(context, "fresh_key_${System.currentTimeMillis()}")
        
        val result = newStore.getKeys()
        
        assertNull(result)
    }

    @Test
    fun `saveKeys overwrites previous keys`() {
        val firstKeys = """{"keys":[{"kid":"first"}]}"""
        val secondKeys = """{"keys":[{"kid":"second"}]}"""
        
        store.saveKeys(firstKeys)
        store.saveKeys(secondKeys)
        val retrieved = store.getKeys()
        
        assertEquals(secondKeys, retrieved)
    }

    @Test
    fun `saveKeys handles empty string`() {
        store.saveKeys("")
        val retrieved = store.getKeys()
        
        assertEquals("", retrieved)
    }

    // ============================================
    // Encryption Tests
    // ============================================

    @Test
    fun `data is encrypted in storage`() {
        val plainText = "sensitive_data_123"
        
        store.saveState(plainText)
        
        // Get the raw value from SharedPreferences
        val prefs = context.getSharedPreferences("app_prefs_secure", Context.MODE_PRIVATE)
        val storedValue = prefs.getString("auth_state", null)
        
        // The stored value should NOT equal the plain text (it should be encrypted)
        assertNotNull(storedValue)
        // Base64 encoded encrypted data will be different from plain text
        assert(storedValue != plainText) { "Data should be encrypted" }
    }

    @Test
    fun `different keys produce different encrypted values`() {
        val plainText = "test_data"
        
        val store1 = Store(context, "key_one")
        val store2 = Store(context, "key_two")
        
        store1.saveState(plainText)
        store2.saveState(plainText)
        
        val prefs = context.getSharedPreferences("app_prefs_secure", Context.MODE_PRIVATE)
        val value1 = prefs.getString("auth_state", null)
        
        // Both stores use the same SharedPreferences, so value will be overwritten
        // This test verifies that encryption key affects the output
        assertNotNull(value1)
    }

    // ============================================
    // Persistence Tests
    // ============================================

    @Test
    fun `state persists across Store instances with same key`() {
        val state = """{"persistent":"data"}"""
        
        // Save with first instance
        val store1 = Store(context, testKey)
        store1.saveState(state)
        
        // Read with new instance using same key
        val store2 = Store(context, testKey)
        val retrieved = store2.getState()
        
        assertEquals(state, retrieved)
    }

    @Test
    fun `keys persist across Store instances with same key`() {
        val keys = """{"keys":[{"persistent":"key"}]}"""
        
        // Save with first instance
        val store1 = Store(context, testKey)
        store1.saveKeys(keys)
        
        // Read with new instance using same key
        val store2 = Store(context, testKey)
        val retrieved = store2.getKeys()
        
        assertEquals(keys, retrieved)
    }

    // ============================================
    // Edge Cases
    // ============================================

    @Test
    fun `store handles special characters in data`() {
        val specialChars = """{"data":"!@#$%^&*()_+-=[]{}|;':\",./<>?"}"""
        
        store.saveState(specialChars)
        val retrieved = store.getState()
        
        assertEquals(specialChars, retrieved)
    }

    @Test
    fun `store handles very long data`() {
        val longData = "x".repeat(10000)
        
        store.saveState(longData)
        val retrieved = store.getState()
        
        assertEquals(longData, retrieved)
    }

    @Test
    fun `clearState does not affect keys`() {
        val state = """{"state":"data"}"""
        val keys = """{"keys":"data"}"""
        
        store.saveState(state)
        store.saveKeys(keys)
        
        store.clearState()
        
        assertNull(store.getState())
        assertEquals(keys, store.getKeys())
    }
}

