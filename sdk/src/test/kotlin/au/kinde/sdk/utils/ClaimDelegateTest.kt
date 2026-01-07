package au.kinde.sdk.utils

import android.util.Base64
import au.kinde.sdk.model.FlagType
import au.kinde.sdk.model.TokenType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [ClaimDelegate].
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ClaimDelegateTest {

    private val mockTokenProvider = mockk<TokenProvider>()

    @Before
    fun setup() {
        ClaimDelegate.tokenProvider = mockTokenProvider
    }

    @After
    fun tearDown() {
        ClaimDelegate.tokenProvider = null
    }

    // Helper function to create a test JWT
    private fun createTestJwt(payload: String): String {
        val header = """{"alg":"RS256","typ":"JWT"}"""
        val headerEncoded = Base64.encodeToString(header.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        val payloadEncoded = Base64.encodeToString(payload.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP)
        return "$headerEncoded.$payloadEncoded.signature"
    }

    // ============================================
    // getClaim Tests
    // ============================================

    @Test
    fun `getClaim returns claim value from access token`() {
        val payload = """{"custom_claim":"custom_value"}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getClaim("custom_claim", TokenType.ACCESS_TOKEN)

        assertEquals("custom_claim", result.name)
        assertEquals("custom_value", result.value)
    }

    @Test
    fun `getClaim returns null value when claim does not exist`() {
        val payload = """{"other_claim":"value"}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getClaim("missing_claim", TokenType.ACCESS_TOKEN)

        assertEquals("missing_claim", result.name)
        assertNull(result.value)
    }

    @Test
    fun `getClaim returns null value when token provider returns null`() {
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns null

        val result = ClaimDelegate.getClaim("any_claim", TokenType.ACCESS_TOKEN)

        assertEquals("any_claim", result.name)
        assertNull(result.value)
    }

    // ============================================
    // getUserDetails Tests
    // ============================================

    @Test
    fun `getUserDetails extracts all user fields from ID token`() {
        val payload = """{
            "sub": "user_123",
            "given_name": "John",
            "family_name": "Doe",
            "email": "john@example.com",
            "picture": "https://example.com/photo.jpg"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ID_TOKEN) } returns jwt

        val result = ClaimDelegate.getUserDetails()

        assertEquals("user_123", result.id)
        assertEquals("John", result.givenName)
        assertEquals("Doe", result.familyName)
        assertEquals("john@example.com", result.email)
        assertEquals("https://example.com/photo.jpg", result.picture)
    }

    @Test
    fun `getUserDetails returns empty strings when claims are missing`() {
        val payload = """{}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ID_TOKEN) } returns jwt

        val result = ClaimDelegate.getUserDetails()

        assertEquals("", result.id)
        assertEquals("", result.givenName)
        assertEquals("", result.familyName)
        assertEquals("", result.email)
        assertEquals("", result.picture)
    }

    @Test
    fun `getUserDetails returns empty strings when token is null`() {
        every { mockTokenProvider.getToken(TokenType.ID_TOKEN) } returns null

        val result = ClaimDelegate.getUserDetails()

        assertEquals("", result.id)
        assertEquals("", result.givenName)
        assertEquals("", result.familyName)
        assertEquals("", result.email)
        assertEquals("", result.picture)
    }

    // ============================================
    // getPermissions Tests
    // ============================================

    @Test
    fun `getPermissions returns permissions list from access token`() {
        val payload = """{
            "org_code": "org_123",
            "permissions": ["read:users", "write:users", "delete:users"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getPermissions()

        assertEquals("org_123", result.orgCode)
        assertEquals(3, result.permissions.size)
        assertTrue(result.permissions.contains("read:users"))
        assertTrue(result.permissions.contains("write:users"))
        assertTrue(result.permissions.contains("delete:users"))
    }

    @Test
    fun `getPermissions returns empty list when permissions claim is missing`() {
        val payload = """{"org_code": "org_123"}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getPermissions()

        assertEquals("org_123", result.orgCode)
        assertTrue(result.permissions.isEmpty())
    }

    // ============================================
    // getPermission Tests
    // ============================================

    @Test
    fun `getPermission returns true when permission is granted`() {
        val payload = """{
            "org_code": "org_123",
            "permissions": ["read:users", "write:users"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getPermission("read:users")

        assertEquals("org_123", result.orgCode)
        assertTrue(result.isGranted)
    }

    @Test
    fun `getPermission returns false when permission is not granted`() {
        val payload = """{
            "org_code": "org_123",
            "permissions": ["read:users"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getPermission("delete:users")

        assertEquals("org_123", result.orgCode)
        assertFalse(result.isGranted)
    }

    // ============================================
    // getRoles Tests
    // ============================================

    @Test
    fun `getRoles returns roles list from access token`() {
        val payload = """{
            "org_code": "org_123",
            "roles": ["admin", "editor"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getRoles()

        assertEquals("org_123", result.orgCode)
        assertEquals(2, result.roles.size)
        assertTrue(result.roles.contains("admin"))
        assertTrue(result.roles.contains("editor"))
    }

    @Test
    fun `getRoles returns empty list when roles claim is missing`() {
        val payload = """{"org_code": "org_123"}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getRoles()

        assertEquals("org_123", result.orgCode)
        assertTrue(result.roles.isEmpty())
    }

    // ============================================
    // getRole Tests
    // ============================================

    @Test
    fun `getRole returns true when role is granted`() {
        val payload = """{
            "org_code": "org_123",
            "roles": ["admin", "editor"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getRole("admin")

        assertEquals("org_123", result.orgCode)
        assertTrue(result.isGranted)
    }

    @Test
    fun `getRole returns false when role is not granted`() {
        val payload = """{
            "org_code": "org_123",
            "roles": ["editor"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getRole("admin")

        assertEquals("org_123", result.orgCode)
        assertFalse(result.isGranted)
    }

    // ============================================
    // getUserOrganizations Tests
    // ============================================

    @Test
    fun `getUserOrganizations returns org codes from ID token`() {
        val payload = """{
            "org_codes": ["org_1", "org_2", "org_3"]
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ID_TOKEN) } returns jwt

        val result = ClaimDelegate.getUserOrganizations()

        assertEquals(3, result.orgCodes.size)
        assertTrue(result.orgCodes.contains("org_1"))
        assertTrue(result.orgCodes.contains("org_2"))
        assertTrue(result.orgCodes.contains("org_3"))
    }

    @Test
    fun `getUserOrganizations returns empty list when claim is missing`() {
        val payload = """{}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ID_TOKEN) } returns jwt

        val result = ClaimDelegate.getUserOrganizations()

        assertTrue(result.orgCodes.isEmpty())
    }

    // ============================================
    // getOrganization Tests
    // ============================================

    @Test
    fun `getOrganization returns org code from access token`() {
        val payload = """{"org_code": "org_123"}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getOrganization()

        assertEquals("org_123", result.orgCode)
    }

    @Test
    fun `getOrganization returns empty string when claim is missing`() {
        val payload = """{}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getOrganization()

        assertEquals("", result.orgCode)
    }

    // ============================================
    // getFlag Tests
    // ============================================

    @Test
    fun `getFlag returns boolean flag from token`() {
        val payload = """{
            "feature_flags": "{\"dark_mode\":{\"t\":\"b\",\"v\":true}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getFlag("dark_mode")

        assertNotNull(result)
        assertEquals("dark_mode", result?.code)
        assertEquals(FlagType.Boolean, result?.type)
        assertEquals(true, result?.value)
        assertFalse(result?.isDefault ?: true)
    }

    @Test
    fun `getFlag returns string flag from token`() {
        val payload = """{
            "feature_flags": "{\"theme\":{\"t\":\"s\",\"v\":\"dark\"}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getFlag("theme")

        assertNotNull(result)
        assertEquals("theme", result?.code)
        assertEquals(FlagType.String, result?.type)
        assertEquals("dark", result?.value)
    }

    @Test
    fun `getFlag returns integer flag from token`() {
        val payload = """{
            "feature_flags": "{\"max_items\":{\"t\":\"i\",\"v\":100}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getFlag("max_items")

        assertNotNull(result)
        assertEquals("max_items", result?.code)
        assertEquals(FlagType.Integer, result?.type)
        assertEquals(100, result?.value)
    }

    @Test
    fun `getFlag returns default value when flag not found`() {
        val payload = """{
            "feature_flags": "{}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getFlag("missing_flag", "default_value")

        assertNotNull(result)
        assertEquals("missing_flag", result?.code)
        assertEquals("default_value", result?.value)
        assertTrue(result?.isDefault ?: false)
    }

    @Test
    fun `getFlag returns null when flag not found and no default`() {
        val payload = """{
            "feature_flags": "{}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getFlag("missing_flag")

        assertNull(result)
    }

    @Test
    fun `getFlag returns null when type mismatch`() {
        val payload = """{
            "feature_flags": "{\"theme\":{\"t\":\"s\",\"v\":\"dark\"}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getFlag("theme", null, FlagType.Boolean)

        assertNull(result)
    }

    // ============================================
    // getBooleanFlag Tests
    // ============================================

    @Test
    fun `getBooleanFlag returns boolean value`() {
        val payload = """{
            "feature_flags": "{\"enabled\":{\"t\":\"b\",\"v\":true}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getBooleanFlag("enabled")

        assertEquals(true, result)
    }

    @Test
    fun `getBooleanFlag returns default when not found`() {
        val payload = """{
            "feature_flags": "{}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getBooleanFlag("missing", false)

        assertEquals(false, result)
    }

    // ============================================
    // getStringFlag Tests
    // ============================================

    @Test
    fun `getStringFlag returns string value`() {
        val payload = """{
            "feature_flags": "{\"theme\":{\"t\":\"s\",\"v\":\"dark\"}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getStringFlag("theme")

        assertEquals("dark", result)
    }

    @Test
    fun `getStringFlag returns default when not found`() {
        val payload = """{
            "feature_flags": "{}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getStringFlag("missing", "light")

        assertEquals("light", result)
    }

    // ============================================
    // getIntegerFlag Tests
    // ============================================

    @Test
    fun `getIntegerFlag returns integer value`() {
        val payload = """{
            "feature_flags": "{\"limit\":{\"t\":\"i\",\"v\":50}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getIntegerFlag("limit")

        assertEquals(50, result)
    }

    @Test
    fun `getIntegerFlag returns default when not found`() {
        val payload = """{
            "feature_flags": "{}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getIntegerFlag("missing", 10)

        assertEquals(10, result)
    }

    // ============================================
    // getAllFlags Tests
    // ============================================

    @Test
    fun `getAllFlags returns all flags from token`() {
        val payload = """{
            "feature_flags": "{\"flag1\":{\"t\":\"b\",\"v\":true},\"flag2\":{\"t\":\"s\",\"v\":\"test\"}}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getAllFlags()

        assertEquals(2, result.size)
        assertTrue(result.containsKey("flag1"))
        assertTrue(result.containsKey("flag2"))
        assertEquals(true, result["flag1"]?.value)
        assertEquals("test", result["flag2"]?.value)
    }

    @Test
    fun `getAllFlags returns empty map when no flags`() {
        val payload = """{
            "feature_flags": "{}"
        }"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getAllFlags()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAllFlags returns empty map when feature_flags claim is missing`() {
        val payload = """{}"""
        val jwt = createTestJwt(payload)
        every { mockTokenProvider.getToken(TokenType.ACCESS_TOKEN) } returns jwt

        val result = ClaimDelegate.getAllFlags()

        assertTrue(result.isEmpty())
    }
}

