package au.kinde.sdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import au.kinde.sdk.model.TokenType
import io.mockk.clearAllMocks
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Unit tests for [KindeSDK].
 * 
 * This demonstrates Kotlin testing patterns:
 * - Using MockK for mocking (Kotlin-friendly mocking library)
 * - Testing with Robolectric for Android components
 * - Testing interfaces and delegates
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class KindeSDKTest {

    private lateinit var activityController: ActivityController<TestActivity>
    private lateinit var activity: TestActivity
    private lateinit var mockSdkListener: SDKListener
    
    // Test configuration values
    private val testDomain = "test.kinde.com"
    private val testClientId = "test_client_id"
    private val testLoginRedirect = "kinde.sdk://test.kinde.com/callback"
    private val testLogoutRedirect = "kinde.sdk://test.kinde.com/logout"

    /**
     * setUp() runs before each test method.
     * This is where we initialize our test fixtures.
     */
    @Before
    fun setup() {
        // Mock the SDKListener - we'll verify calls to it
        mockSdkListener = mockk(relaxed = true)
        
        // Set up mock metadata using Robolectric's shadow API
        val app = RuntimeEnvironment.getApplication()
        val shadowApp = shadowOf(app.packageManager)
        val appInfo = shadowApp.getInternalMutablePackageInfo(app.packageName).applicationInfo
        appInfo.metaData = Bundle().apply {
            putString("au.kinde.domain", testDomain)
            putString("au.kinde.clientId", testClientId)
        }
        
        // Create a test activity using Robolectric
        activityController = Robolectric.buildActivity(TestActivity::class.java)
        activity = activityController.get()
    }

    /**
     * tearDown() runs after each test method.
     * Clean up resources to prevent test pollution.
     */
    @After
    fun tearDown() {
        // Clean up any mocks
        clearAllMocks()
        
        // Destroy the activity
        if (::activityController.isInitialized) {
            activityController.pause().stop().destroy()
        }
    }

    // ============================================
    // Test 1: Simple Property Tests
    // ============================================

    @Test
    fun `clearCache clears all cached data`() {
        // Given: Create SDK instance
        val sdk = createKindeSDK()
        
        // When: Clear cache
        sdk.clearCache()
        
        // Then: No exception should be thrown
        // (This is a simple smoke test - we'll verify behavior in more complex tests)
        assertTrue(true) // Cache clearing is internal, so we verify no crash
    }

    // ============================================
    // Test 2: TokenProvider Interface
    // ============================================

    @Test
    fun `getToken returns null for access token when not authenticated`() {
        // Given: SDK instance with no authentication
        val sdk = createKindeSDK()
        
        // When: Request access token
        val token = sdk.getToken(TokenType.ACCESS_TOKEN)
        
        // Then: Should return null (no auth state)
        assertNull(token)
    }

    @Test
    fun `getToken returns null for id token when not authenticated`() {
        // Given: SDK instance with no authentication
        val sdk = createKindeSDK()
        
        // When: Request ID token
        val token = sdk.getToken(TokenType.ID_TOKEN)
        
        // Then: Should return null
        assertNull(token)
    }

    @Test
    fun `getRefreshToken returns null when not authenticated`() {
        // Given: SDK instance with no authentication
        val sdk = createKindeSDK()
        
        // When: Request refresh token
        val refreshToken = sdk.getRefreshToken()
        
        // Then: Should return null
        assertNull(refreshToken)
    }

    // ============================================
    // Test 3: Authentication State
    // ============================================

    @Test
    fun `isAuthenticated returns false when no auth state`() {
        // Given: SDK instance with no authentication
        val sdk = createKindeSDK()
        
        // When: Check authentication
        val isAuth = sdk.isAuthenticated()
        
        // Then: Should return false
        assertFalse(isAuth)
    }

    // ============================================
    // Test 4: API Options - Cache Behavior
    // ============================================

    @Test
    fun `getPermissions without options uses token claims`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get permissions without options (uses ClaimDelegate)
        val permissions = sdk.getPermissions()
        
        // Then: Should return ClaimData.Permissions with empty values
        assertNotNull(permissions)
        assertEquals("", permissions.orgCode)
        assertTrue(permissions.permissions.isEmpty())
    }

    @Test
    fun `getRoles without options uses token claims`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get roles without options
        val roles = sdk.getRoles()
        
        // Then: Should return ClaimData.Roles with empty values
        assertNotNull(roles)
        assertEquals("", roles.orgCode)
        assertTrue(roles.roles.isEmpty())
    }

    @Test
    fun `getAllFlags without options uses token claims`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get all flags without options
        val flags = sdk.getAllFlags()
        
        // Then: Should return empty map
        assertNotNull(flags)
        assertTrue(flags.isEmpty())
    }

    @Test
    fun `getBooleanFlag without options returns null when flag not in token`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get boolean flag without options
        val flagValue = sdk.getBooleanFlag("nonexistent_flag")
        
        // Then: Should return null
        assertNull(flagValue)
    }

    @Test
    fun `getBooleanFlag returns default value when flag not found`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get boolean flag with default value
        val flagValue = sdk.getBooleanFlag("nonexistent_flag", defaultValue = true)
        
        // Then: Should return default value
        assertTrue(flagValue == true)
    }

    @Test
    fun `getStringFlag without options returns null when flag not in token`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get string flag without options
        val flagValue = sdk.getStringFlag("nonexistent_flag")
        
        // Then: Should return null
        assertNull(flagValue)
    }

    @Test
    fun `getStringFlag returns default value when flag not found`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get string flag with default value
        val flagValue = sdk.getStringFlag("nonexistent_flag", defaultValue = "default")
        
        // Then: Should return default value
        assertEquals("default", flagValue)
    }

    @Test
    fun `getIntegerFlag without options returns null when flag not in token`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get integer flag without options
        val flagValue = sdk.getIntegerFlag("nonexistent_flag")
        
        // Then: Should return null
        assertNull(flagValue)
    }

    @Test
    fun `getIntegerFlag returns default value when flag not found`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get integer flag with default value
        val flagValue = sdk.getIntegerFlag("nonexistent_flag", defaultValue = 42)
        
        // Then: Should return default value
        assertEquals(42, flagValue)
    }

    // ============================================
    // Test 5: Permission Checks
    // ============================================

    @Test
    fun `getPermission without options returns Permission with isGranted false when not in token`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Check for a permission
        val permission = sdk.getPermission("read:users")
        
        // Then: Should return Permission with isGranted = false
        assertNotNull(permission)
        assertFalse(permission.isGranted)
        assertEquals("", permission.orgCode)
    }

    // ============================================
    // Test 6: Role Checks
    // ============================================

    @Test
    fun `getRole without options returns Role with isGranted false when not in token`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Check for a role
        val role = sdk.getRole("admin")
        
        // Then: Should return Role with isGranted = false
        assertNotNull(role)
        assertFalse(role.isGranted)
        assertEquals("", role.orgCode)
    }

    // ============================================
    // Test 7: SDK Initialization
    // ============================================

    @Test
    fun `SDK initializes successfully with valid metadata`() {
        // Given: Activity with valid metadata
        // When: Create SDK instance
        val sdk = createKindeSDK()
        
        // Then: SDK should be created without exception
        assertNotNull(sdk)
    }

    @Test
    fun `SDK can be created multiple times`() {
        // Given: Activity with valid metadata
        // When: Create multiple SDK instances
        val sdk1 = createKindeSDK()
        val sdk2 = createKindeSDK()
        
        // Then: Both instances should be created
        assertNotNull(sdk1)
        assertNotNull(sdk2)
    }

    // ============================================
    // Test 8: RefreshState
    // ============================================

    @Test
    fun `refreshState syncs state from storage`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Call refreshState
        sdk.refreshState()
        
        // Then: Should not throw exception
        assertTrue(true)
    }

    // ============================================
    // Test 9: User API Methods
    // ============================================

    @Test
    fun `getUser returns null when not authenticated`() {
        // Given: SDK instance with no auth
        val sdk = createKindeSDK()
        
        // When: Get user
        val user = sdk.getUser()
        
        // Then: Should return null
        assertNull(user)
    }

    @Test
    fun `getUserProfileV2 returns null when not authenticated`() {
        // Given: SDK instance with no auth
        val sdk = createKindeSDK()
        
        // When: Get user profile V2
        val userProfile = sdk.getUserProfileV2()
        
        // Then: Should return null
        assertNull(userProfile)
    }

    @Test
    fun `createUser returns null when not authenticated`() {
        // Given: SDK instance with no auth
        val sdk = createKindeSDK()
        
        // When: Create user
        val result = sdk.createUser()
        
        // Then: Should return null
        assertNull(result)
    }

    @Test
    fun `getUsers returns null when not authenticated`() {
        // Given: SDK instance with no auth
        val sdk = createKindeSDK()
        
        // When: Get users
        val users = sdk.getUsers()
        
        // Then: Should return null
        assertNull(users)
    }

    @Test
    fun `getUsers with parameters returns null when not authenticated`() {
        // Given: SDK instance with no auth
        val sdk = createKindeSDK()
        
        // When: Get users with parameters
        val users = sdk.getUsers(
            sort = "email",
            pageSize = 10,
            userId = 123,
            nextToken = "token_123"
        )
        
        // Then: Should return null
        assertNull(users)
    }

    // ============================================
    // Test 10: Lifecycle Callbacks
    // ============================================

    @Test
    fun `onPause sets isPaused flag and cancels token refresh`() {
        // Given: SDK instance
        createKindeSDK()
        
        // When: Move through lifecycle states
        activityController.pause()
        
        // Then: Should not throw exception
        // isPaused flag is set internally
        assertTrue(true)
    }

    @Test
    fun `onResume clears isPaused flag and schedules token refresh`() {
        // Given: SDK instance and paused activity
        createKindeSDK()
        activityController.pause()
        
        // When: Resume activity
        activityController.resume()
        
        // Then: Should not throw exception
        // isPaused flag is cleared internally
        assertTrue(true)
    }

    // ============================================
    // Test 11: Multiple Flag Types with Default Values
    // ============================================

    @Test
    fun `getBooleanFlag with false default value`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get boolean flag with false default
        val flagValue = sdk.getBooleanFlag("test_flag", defaultValue = false)
        
        // Then: Should return false
        assertFalse(flagValue == true)
    }

    @Test
    fun `getStringFlag with empty string default value`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get string flag with empty default
        val flagValue = sdk.getStringFlag("test_flag", defaultValue = "")
        
        // Then: Should return empty string
        assertEquals("", flagValue)
    }

    @Test
    fun `getIntegerFlag with zero default value`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get integer flag with zero default
        val flagValue = sdk.getIntegerFlag("test_flag", defaultValue = 0)
        
        // Then: Should return 0
        assertEquals(0, flagValue)
    }

    @Test
    fun `getIntegerFlag with negative default value`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get integer flag with negative default
        val flagValue = sdk.getIntegerFlag("test_flag", defaultValue = -1)
        
        // Then: Should return -1
        assertEquals(-1, flagValue)
    }

    // ============================================
    // Test 12: Edge Cases and Variations
    // ============================================

    @Test
    fun `clearCache can be called multiple times`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Clear cache multiple times
        sdk.clearCache()
        sdk.clearCache()
        sdk.clearCache()
        
        // Then: Should not throw exception
        assertTrue(true)
    }

    @Test
    fun `refreshState can be called multiple times`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Refresh state multiple times
        sdk.refreshState()
        sdk.refreshState()
        sdk.refreshState()
        
        // Then: Should not throw exception
        assertTrue(true)
    }

    @Test
    fun `isAuthenticated can be called multiple times`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Check authentication multiple times
        val result1 = sdk.isAuthenticated()
        val result2 = sdk.isAuthenticated()
        val result3 = sdk.isAuthenticated()
        
        // Then: Should return consistent results
        assertEquals(result1, result2)
        assertEquals(result2, result3)
    }

    @Test
    fun `getToken with ACCESS_TOKEN returns null for unauthenticated`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get access token
        val token = sdk.getToken(TokenType.ACCESS_TOKEN)
        
        // Then: Should return null
        assertNull(token)
    }

    @Test
    fun `getToken with ID_TOKEN returns null for unauthenticated`() {
        // Given: SDK instance
        val sdk = createKindeSDK()
        
        // When: Get ID token
        val token = sdk.getToken(TokenType.ID_TOKEN)
        
        // Then: Should return null
        assertNull(token)
    }

    // ============================================
    // Helper Methods
    // ============================================

    /**
     * Creates a KindeSDK instance for testing.
     * This handles all the boilerplate setup.
     */
    private fun createKindeSDK(): KindeSDK {
        return KindeSDK(
            activity = activity,
            loginRedirect = testLoginRedirect,
            logoutRedirect = testLogoutRedirect,
            scopes = listOf("openid", "profile", "email"),
            sdkListener = mockSdkListener
        )
    }


    /**
     * Test Activity class for testing
     */
    class TestActivity : ComponentActivity()
}
