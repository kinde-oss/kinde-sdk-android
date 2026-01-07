package au.kinde.sdk.auth

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Unit tests for [HttpBearerAuth].
 * 
 * Tests the OkHttp interceptor that adds Bearer authentication headers to requests.
 */
class HttpBearerAuthTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    // ============================================
    // Test 1: Basic Bearer Token Addition
    // ============================================

    @Test
    fun `intercept adds Authorization header with Bearer token when not present`() {
        // Given: Auth interceptor with Bearer schema and token
        val auth = HttpBearerAuth("bearer", "test_token_123")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request without Authorization header
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Request should have Bearer Authorization header
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer test_token_123", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept adds Authorization header without schema when schema is empty`() {
        // Given: Auth interceptor with empty schema
        val auth = HttpBearerAuth("", "test_token_456")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Request should have token without schema prefix
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("test_token_456", recordedRequest.getHeader("Authorization"))
    }

    // ============================================
    // Test 2: Existing Authorization Header
    // ============================================

    @Test
    fun `intercept does not modify request when Authorization header already exists`() {
        // Given: Auth interceptor with token
        val auth = HttpBearerAuth("bearer", "test_token_789")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request with existing Authorization header
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .addHeader("Authorization", "Basic existing_auth")
            .build()
        client.newCall(request).execute()

        // Then: Request should keep original Authorization header
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Basic existing_auth", recordedRequest.getHeader("Authorization"))
    }

    // ============================================
    // Test 3: Blank Bearer Token
    // ============================================

    @Test
    fun `intercept does not add header when bearer token is empty`() {
        // Given: Auth interceptor with empty token
        val auth = HttpBearerAuth("bearer", "")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Request should not have Authorization header
        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept does not add header when bearer token is blank`() {
        // Given: Auth interceptor with blank token (whitespace)
        val auth = HttpBearerAuth("bearer", "   ")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Request should not have Authorization header
        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }

    // ============================================
    // Test 4: Schema Case Handling
    // ============================================

    @Test
    fun `intercept uses Bearer with capital B for lowercase bearer schema`() {
        // Given: Auth interceptor with lowercase "bearer" schema
        val auth = HttpBearerAuth("bearer", "token_abc")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Should use "Bearer" with capital B
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer token_abc", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept uses Bearer with capital B for uppercase BEARER schema`() {
        // Given: Auth interceptor with uppercase "BEARER" schema
        val auth = HttpBearerAuth("BEARER", "token_def")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Should use "Bearer" with capital B
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer token_def", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept uses Bearer with capital B for mixed case BeArEr schema`() {
        // Given: Auth interceptor with mixed case schema
        val auth = HttpBearerAuth("BeArEr", "token_ghi")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Should use "Bearer" with capital B
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer token_ghi", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept preserves custom schema when not bearer`() {
        // Given: Auth interceptor with custom schema
        val auth = HttpBearerAuth("CustomAuth", "token_jkl")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Should use custom schema as-is
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("CustomAuth token_jkl", recordedRequest.getHeader("Authorization"))
    }

    // ============================================
    // Test 5: Token Updates
    // ============================================

    @Test
    fun `bearerToken property can be updated and new value is used`() {
        // Given: Auth interceptor with initial token
        val auth = HttpBearerAuth("bearer", "old_token")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make first request
        val request1 = Request.Builder()
            .url(mockWebServer.url("/test1"))
            .build()
        client.newCall(request1).execute()

        // Then: First request has old token
        val recordedRequest1 = mockWebServer.takeRequest()
        assertEquals("Bearer old_token", recordedRequest1.getHeader("Authorization"))

        // When: Update token and make second request
        auth.bearerToken = "new_token"
        val request2 = Request.Builder()
            .url(mockWebServer.url("/test2"))
            .build()
        client.newCall(request2).execute()

        // Then: Second request has new token
        val recordedRequest2 = mockWebServer.takeRequest()
        assertEquals("Bearer new_token", recordedRequest2.getHeader("Authorization"))
    }

    @Test
    fun `bearerToken can be cleared by setting to empty string`() {
        // Given: Auth interceptor with token
        val auth = HttpBearerAuth("bearer", "active_token")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make first request with token
        val request1 = Request.Builder()
            .url(mockWebServer.url("/test1"))
            .build()
        client.newCall(request1).execute()

        val recordedRequest1 = mockWebServer.takeRequest()
        assertEquals("Bearer active_token", recordedRequest1.getHeader("Authorization"))

        // When: Clear token and make second request
        auth.bearerToken = ""
        val request2 = Request.Builder()
            .url(mockWebServer.url("/test2"))
            .build()
        client.newCall(request2).execute()

        // Then: Second request has no Authorization header
        val recordedRequest2 = mockWebServer.takeRequest()
        assertNull(recordedRequest2.getHeader("Authorization"))
    }

    // ============================================
    // Test 6: Constructor Defaults
    // ============================================

    @Test
    fun `default constructor creates instance with empty schema and token`() {
        // When: Create with default constructor
        val auth = HttpBearerAuth()

        // Then: Schema and token should be empty
        // This is tested indirectly through behavior
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        val recordedRequest = mockWebServer.takeRequest()
        assertNull(recordedRequest.getHeader("Authorization"))
    }

    // ============================================
    // Test 7: Edge Cases
    // ============================================

    @Test
    fun `intercept handles token with special characters`() {
        // Given: Token with special characters
        val auth = HttpBearerAuth("bearer", "token-with_special.chars123")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Token is added correctly
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer token-with_special.chars123", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept works with long token strings`() {
        // Given: Very long token
        val longToken = "a".repeat(1000)
        val auth = HttpBearerAuth("bearer", longToken)
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Long token is handled correctly
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("Bearer $longToken", recordedRequest.getHeader("Authorization"))
    }

    @Test
    fun `intercept with blank schema and valid token uses token only`() {
        // Given: Blank schema (whitespace) with valid token
        val auth = HttpBearerAuth("  ", "token_xyz")
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .build()

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        // When: Make request
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()
        client.newCall(request).execute()

        // Then: Should use token only (blank schema is treated as no schema)
        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("token_xyz", recordedRequest.getHeader("Authorization"))
    }
}
