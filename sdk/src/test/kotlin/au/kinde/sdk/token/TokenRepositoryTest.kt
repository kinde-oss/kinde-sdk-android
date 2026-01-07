package au.kinde.sdk.token

import au.kinde.sdk.callApi
import io.mockk.*
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Call
import android.net.Uri

/**
 * Unit tests for [TokenRepository].
 * 
 * Tests cover:
 * - Successful token retrieval
 * - Network error handling
 * - OAuth error responses
 * - JSON parsing errors
 * - Edge cases
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class TokenRepositoryTest {

    private lateinit var mockTokenApi: TokenApi
    private lateinit var tokenRepository: TokenRepository
    private lateinit var mockAuthState: AuthState
    private lateinit var mockTokenRequest: TokenRequest
    private lateinit var mockCall: Call<String>
    private lateinit var requestParams: MutableMap<String, String>
    
    private val testVersion = "1.0.0"
    private val testClientId = "test_client_id"
    private val testAuthorizationEndpoint = Uri.parse("https://test.kinde.com/oauth2/auth")
    private val testTokenEndpoint = Uri.parse("https://test.kinde.com/oauth2/token")
    private val testRedirectUri = Uri.parse("kinde.sdk://test.kinde.com/callback")

    @Before
    fun setup() {
        mockTokenApi = mockk(relaxed = true)
        tokenRepository = TokenRepository(mockTokenApi, testVersion)
        
        // Create mock AuthState
        val serviceConfig = AuthorizationServiceConfiguration(
            testAuthorizationEndpoint,
            testTokenEndpoint
        )
        mockAuthState = AuthState(serviceConfig)
        
        // Create actual mutable map for request parameters
        requestParams = mutableMapOf(
            "grant_type" to "authorization_code",
            "code" to "test_code",
            "redirect_uri" to testRedirectUri.toString()
        )
        
        // Create real TokenRequest using builder
        mockTokenRequest = TokenRequest.Builder(
            serviceConfig,
            testClientId
        )
            .setGrantType("authorization_code")
            .setAuthorizationCode("test_code")
            .setRedirectUri(testRedirectUri)
            .build()
        
        mockCall = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    // ============================================
    // Test 1: Successful Token Response
    // ============================================

    @Test
    fun `getToken returns successful token response`() {
        // Given: Valid token response JSON
        val tokenJson = """
            {
                "access_token": "test_access_token",
                "token_type": "Bearer",
                "expires_in": 3600,
                "refresh_token": "test_refresh_token",
                "id_token": "test_id_token"
            }
        """.trimIndent()
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        // Mock the callApi extension function to return success
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(tokenJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should return successful response
        assertNotNull(response)
        assertNull(error)
        assertEquals("test_access_token", response?.accessToken)
        assertEquals("Bearer", response?.tokenType)
        assertEquals("test_refresh_token", response?.refreshToken)
        assertEquals("test_id_token", response?.idToken)
        
        // Verify header was set correctly
        verify { mockTokenApi.retrieveToken("Kotlin/$testVersion", any()) }
    }

    @Test
    fun `getToken includes client_id in request parameters`() {
        // Given: Token request and valid response
        val tokenJson = """{"access_token": "token", "token_type": "Bearer"}"""
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(tokenJson, null)
        
        // When: Get token
        tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Client ID should be added to parameters
        verify { 
            mockTokenApi.retrieveToken(
                any(), 
                match { params -> params["client_id"] == testClientId }
            ) 
        }
    }

    // ============================================
    // Test 2: Network/API Errors
    // ============================================

    @Test
    fun `getToken returns error when API call fails with network exception`() {
        // Given: API call throws exception
        val networkException = Exception("Network error")
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(null, networkException)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should return error
        assertNull(response)
        assertNotNull(error)
        assertEquals(AuthorizationException.TYPE_GENERAL_ERROR, error?.type)
        assertEquals(AuthorizationException.AuthorizationRequestErrors.SERVER_ERROR.code, error?.code)
        assertEquals("Network error", error?.message)
        assertNotNull(error?.cause)
    }

    @Test
    fun `getToken handles null response string with empty JSON`() {
        // Given: API returns null string
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(null as String?, null)
        
        // When: Get token (will throw JSONException due to null)
        val result = try {
            tokenRepository.getToken(mockAuthState, mockTokenRequest)
        } catch (e: Exception) {
            // JSONException or similar is expected
            Pair(null, null)
        }
        
        // Then: Should fail (either throw or return error)
        assertNull(result.first)
    }

    // ============================================
    // Test 3: OAuth Error Responses
    // ============================================

    @Test
    fun `getToken returns OAuth error when error parameter present`() {
        // Given: OAuth error response
        val errorJson = """
            {
                "error": "invalid_grant",
                "error_description": "The authorization code is invalid or expired"
            }
        """.trimIndent()
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(errorJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should return OAuth error
        assertNull(response)
        assertNotNull(error)
        assertEquals("invalid_grant", error?.error)
        assertEquals("The authorization code is invalid or expired", error?.errorDescription)
    }

    @Test
    fun `getToken handles OAuth error with error_uri`() {
        // Given: OAuth error with URI
        val errorJson = """
            {
                "error": "access_denied",
                "error_description": "User denied access",
                "error_uri": "https://test.kinde.com/errors/access_denied"
            }
        """.trimIndent()
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(errorJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should parse error URI
        assertNull(response)
        assertNotNull(error)
        assertEquals("access_denied", error?.error)
        assertNotNull(error?.errorUri)
        assertEquals("https://test.kinde.com/errors/access_denied", error?.errorUri.toString())
    }

    @Test
    fun `getToken handles OAuth error without optional fields`() {
        // Given: Minimal OAuth error
        val errorJson = """{"error": "server_error"}"""
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(errorJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should handle minimal error
        assertNull(response)
        assertNotNull(error)
        assertEquals("server_error", error?.error)
    }

    @Test
    fun `getToken handles malformed OAuth error JSON`() {
        // Given: Malformed error JSON (error field exists but is malformed)
        val errorJson = """{"error": 123}"""  // error should be string
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(errorJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should return error (either deserialization error or from exception handler)
        assertNull(response)
        assertNotNull(error)
        // The code may not parse "error":123 as string, so it returns deserialization error
        assertTrue(error?.code == AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR.code ||
                  error?.error == "123")
    }

    // ============================================
    // Test 4: JSON Parsing Errors
    // ============================================

    @Test
    fun `getToken handles invalid JSON response`() {
        // Given: Invalid JSON
        val invalidJson = "not valid json {{"
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(invalidJson, null)
        
        // When/Then: Invalid JSON should throw exception
        try {
            val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
            // If we get here, we should at least have an error
            assertNull(response)
            assertNotNull(error)
        } catch (e: Exception) {
            // JSONException is expected for invalid JSON
            assertTrue(e.message?.contains("JSON") == true || e is org.json.JSONException)
        }
    }

    @Test
    fun `getToken handles JSON missing required token fields`() {
        // Given: JSON missing required fields
        val incompleteJson = """{"some_field": "value"}"""
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(incompleteJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should return JSON deserialization error
        assertNull(response)
        assertNotNull(error)
        assertEquals(AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR.code, error?.code)
        assertTrue(error?.cause is JSONException)
    }

    @Test
    fun `getToken handles empty JSON object`() {
        // Given: Empty JSON
        val emptyJson = "{}"
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(emptyJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should fail due to missing required fields
        assertNull(response)
        assertNotNull(error)
        assertEquals(AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR.code, error?.code)
    }

    // ============================================
    // Test 5: Edge Cases
    // ============================================

    @Test
    fun `getToken handles response with only required fields`() {
        // Given: Minimal valid token response
        val minimalJson = """
            {
                "access_token": "minimal_token",
                "token_type": "Bearer"
            }
        """.trimIndent()
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(minimalJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should succeed with minimal fields
        assertNotNull(response)
        assertNull(error)
        assertEquals("minimal_token", response?.accessToken)
        assertEquals("Bearer", response?.tokenType)
    }

    @Test
    fun `getToken handles extra unexpected fields in response`() {
        // Given: Response with extra fields
        val extraFieldsJson = """
            {
                "access_token": "test_token",
                "token_type": "Bearer",
                "expires_in": 3600,
                "custom_field": "custom_value",
                "another_field": 123
            }
        """.trimIndent()
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(extraFieldsJson, null)
        
        // When: Get token
        val (response, error) = tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: Should ignore extra fields and succeed
        assertNotNull(response)
        assertNull(error)
        assertEquals("test_token", response?.accessToken)
    }

    @Test
    fun `getToken uses withoutAuthorization flag correctly`() {
        // Given: Valid response
        val tokenJson = """{"access_token": "token", "token_type": "Bearer"}"""
        
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(tokenJson, null)
        
        // When: Get token
        tokenRepository.getToken(mockAuthState, mockTokenRequest)
        
        // Then: callApi should be called with withoutAuthorization=true
        verify { mockCall.callApi(mockAuthState, true) }
    }

    @Test
    fun `getToken preserves existing request parameters`() {
        // Given: Request with refresh token grant
        val serviceConfig = AuthorizationServiceConfiguration(
            testAuthorizationEndpoint,
            testTokenEndpoint
        )
        val refreshTokenRequest = TokenRequest.Builder(
            serviceConfig,
            testClientId
        )
            .setGrantType("refresh_token")
            .setRefreshToken("custom_refresh")
            .setScope("openid profile")
            .build()
        
        val tokenJson = """{"access_token": "token", "token_type": "Bearer"}"""
        every { mockTokenApi.retrieveToken(any(), any()) } returns mockCall
        
        mockkStatic("au.kinde.sdk.CallUtilKt")
        every { mockCall.callApi(mockAuthState, true) } returns Pair(tokenJson, null)
        
        // When: Get token
        tokenRepository.getToken(mockAuthState, refreshTokenRequest)
        
        // Then: Original parameters should be preserved and client_id added
        verify { 
            mockTokenApi.retrieveToken(
                any(), 
                match { params -> 
                    params["grant_type"] == "refresh_token" &&
                    params["refresh_token"] == "custom_refresh" &&
                    params["scope"] == "openid profile" &&
                    params["client_id"] == testClientId
                }
            ) 
        }
    }
}
