package au.kinde.sdk

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import au.kinde.sdk.api.ApiOptions
import au.kinde.sdk.api.model.entitlements.EntitlementResponse
import au.kinde.sdk.api.model.entitlements.EntitlementsResponse
import au.kinde.sdk.api.model.CreateUser200Response
import au.kinde.sdk.api.model.CreateUserRequest
import au.kinde.sdk.api.model.User
import au.kinde.sdk.api.model.UserProfile
import au.kinde.sdk.api.model.UserProfileV2
import au.kinde.sdk.model.ClaimData
import au.kinde.sdk.model.Flag
import au.kinde.sdk.model.TokenType
import au.kinde.sdk.utils.ClaimApi
import au.kinde.sdk.utils.ClaimDelegate
import au.kinde.sdk.utils.TokenProvider

/**
 * Activity-scoped facade over the application-scoped [KindeClient].
 *
 * Construct one per activity, in `onCreate` (required: the browser result
 * launchers must be registered before the activity is started). The facade owns
 * only the browser plumbing for login/registration/logout; all token state,
 * refresh scheduling and API access live in the shared [KindeClient], so they
 * survive activity recreation, rotation and navigation.
 *
 * Tokens can also be read without any activity via
 * `KindeClient.getInstance(context)` — for example from an OkHttp interceptor.
 */
class KindeSDK(
    private val activity: ComponentActivity,
    private val loginRedirect: String,
    private val logoutRedirect: String,
    private val scopes: List<String> = DEFAULT_SCOPES,
    private val sdkListener: SDKListener
) : TokenProvider, ClaimApi by ClaimDelegate, DefaultLifecycleObserver {

    private val client = KindeClient.getInstance(activity)

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        client.onAuthorizationResult(result.resultCode, result.data)
    }

    private val endTokenLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        client.onEndSessionResult(result.resultCode, result.data)
    }

    private val endSessionLauncherBridge = object : KindeClient.EndSessionLauncher {
        override fun launchEndSession(intent: Intent) {
            endTokenLauncher.launch(intent)
        }
    }

    init {
        activity.lifecycle.addObserver(this)
        client.attachEndSessionLauncher(endSessionLauncherBridge, logoutRedirect)
        client.attachListener(sdkListener)

        // Check for invitation_code in the launching intent
        val invitationCode =
            activity.intent?.data?.getQueryParameter(KindeClient.INVITATION_CODE_PARAM_NAME)
        if (!invitationCode.isNullOrEmpty() && !client.invitationState.isProcessed(invitationCode)) {
            // Check if already resumed; if so, handle immediately, otherwise use observer
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                handleInvitation(invitationCode)
            } else {
                // Use lifecycle observer to handle invitation after activity is resumed
                activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        owner.lifecycle.removeObserver(this)
                        handleInvitation(invitationCode)
                    }
                })
            }
        }

        // Skip normal auth callbacks if handling invitation
        if (!client.isHandlingInvitation()) {
            client.notifyInitialState(sdkListener)
        }
    }

    override fun getToken(tokenType: TokenType): String? = client.getToken(tokenType)

    fun getRefreshToken(): String? = client.getRefreshToken()

    /**
     * Initiate login flow
     *
     * @param type The grant type (PKCE or implicit)
     * @param orgCode Optional organization code
     * @param loginHint Optional login hint (email)
     * @param domain Optional domain to use for this login (overrides config)
     * @param clientId Optional client ID to use for this login (overrides config)
     * @param connectionId Optional connection ID
     */
    @JvmOverloads
    fun login(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        domain: String? = null,
        clientId: String? = null,
        invitationCode: String? = null,
        connectionId: String? = null
    ) {
        client.login(
            type, orgCode, loginHint, domain, clientId, invitationCode, connectionId,
            loginRedirect, scopes
        ) { launcher.launch(it) }
    }

    /**
     * Initiate registration flow
     *
     * @param type The grant type (PKCE or implicit)
     * @param orgCode Optional organization code
     * @param loginHint Optional login hint (email)
     * @param pricingTableKey Optional pricing table key
     * @param planInterest Optional plan interest
     * @param domain Optional domain to use for this registration (overrides config)
     * @param clientId Optional client ID to use for this registration (overrides config)
     * @param connectionId Optional connection ID
     */
    @JvmOverloads
    fun register(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        pricingTableKey: String? = null,
        planInterest: String? = null,
        domain: String? = null,
        clientId: String? = null,
        invitationCode: String? = null,
        connectionId: String? = null
    ) {
        client.register(
            type, orgCode, loginHint, pricingTableKey, planInterest, domain, clientId,
            invitationCode, connectionId, loginRedirect, scopes
        ) { launcher.launch(it) }
    }

    /**
     * Initiate organization creation flow
     *
     * @param type The grant type (PKCE or implicit)
     * @param orgName The name of the organization to create
     * @param pricingTableKey Optional pricing table key
     * @param planInterest Optional plan interest
     * @param domain Optional domain to use for this operation (overrides config)
     * @param clientId Optional client ID to use for this operation (overrides config)
     * @param connectionId Optional connection ID
     */
    @JvmOverloads
    fun createOrg(
        type: GrantType? = null,
        orgName: String,
        pricingTableKey: String? = null,
        planInterest: String? = null,
        domain: String? = null,
        clientId: String? = null,
        connectionId: String? = null
    ) {
        client.createOrg(
            type, orgName, pricingTableKey, planInterest, domain, clientId, connectionId,
            loginRedirect, scopes
        ) { launcher.launch(it) }
    }

    /**
     * Handle an invitation code by redirecting to registration with the code.
     * This is typically called when the app detects an invitation_code in the incoming intent/deep link.
     *
     * @param invitationCode The invitation code from the URL
     * @param type Optional grant type (defaults to null)
     * @param orgCode Optional organization code
     */
    fun handleInvitation(
        invitationCode: String,
        type: GrantType? = null,
        orgCode: String? = null
    ) {
        client.handleInvitation(invitationCode, type, orgCode, loginRedirect, scopes) {
            launcher.launch(it)
        }
    }

    fun logout() {
        client.logout(logoutRedirect)
    }

    /**
     * Refreshes the authentication state from persistent storage.
     * Call this method when you need to sync the in-memory state with storage,
     * especially when navigating between activities that may have modified the auth state.
     */
    fun refreshState() = client.refreshState()

    /**
     * Checks if the user is currently authenticated.
     * This method relies on shared preferences rather than in-memory state
     * to work correctly across multiple activities.
     */
    fun isAuthenticated(): Boolean = client.isAuthenticated()

    /**
     * Clears all cached API responses (permissions, roles, and feature flags).
     * Call this when you need to force fresh data on the next API call, or when switching contexts
     * (e.g., changing organizations).
     */
    fun clearCache() = client.clearCache()

    /**
     * Check if the SDK is currently handling an invitation code.
     * This can be used to show appropriate loading UI while the invitation flow is in progress.
     *
     * @return true if an invitation code was detected and is being processed
     */
    fun isHandlingInvitation() = client.isHandlingInvitation()

    fun getUser(): UserProfile? = client.getUser()

    fun getUserProfileV2(): UserProfileV2? = client.getUserProfileV2()

    fun getEntitlement(key: String): EntitlementResponse? = client.getEntitlement(key)

    fun getEntitlements(): EntitlementsResponse? = client.getEntitlements()

    fun createUser(createUserRequest: CreateUserRequest? = null): CreateUser200Response? =
        client.createUser(createUserRequest)

    fun getUsers(
        sort: kotlin.String? = null,
        pageSize: kotlin.Int? = null,
        userId: kotlin.Int? = null,
        nextToken: kotlin.String? = null
    ): kotlin.collections.List<User>? = client.getUsers(sort, pageSize, userId, nextToken)

    /**
     * Get all permissions for the authenticated user
     *
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API.
     *                Set useCache = false to bypass cache and force a fresh API call.
     * @return ClaimData.Permissions containing org code and list of permission keys
     */
    fun getPermissions(options: ApiOptions? = null): ClaimData.Permissions =
        client.getPermissions(options)

    /**
     * Check if user has a specific permission
     *
     * Note: When using forceApi=true, this fetches ALL permissions from the API, but results are
     * cached for 60 seconds by default. Subsequent calls within the cache window will use cached data.
     * To force a fresh API call, use ApiOptions(forceApi = true, useCache = false).
     *
     * @param permission The permission key to check
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API
     * @return ClaimData.Permission with orgCode and isGranted status
     */
    fun getPermission(permission: String, options: ApiOptions? = null): ClaimData.Permission =
        client.getPermission(permission, options)

    /**
     * Get all roles for the authenticated user
     *
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API.
     *                Set useCache = false to bypass cache and force a fresh API call.
     * @return ClaimData.Roles containing org code and list of role keys
     */
    fun getRoles(options: ApiOptions? = null): ClaimData.Roles = client.getRoles(options)

    /**
     * Check if user has a specific role
     *
     * Note: When using forceApi=true, this fetches ALL roles from the API, but results are
     * cached for 60 seconds by default. Subsequent calls within the cache window will use cached data.
     * To force a fresh API call, use ApiOptions(forceApi = true, useCache = false).
     *
     * @param role The role key to check
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API
     * @return ClaimData.Role with orgCode and isGranted status
     */
    fun getRole(role: String, options: ApiOptions? = null): ClaimData.Role =
        client.getRole(role, options)

    /**
     * Get a boolean feature flag value
     *
     * Note: When using forceApi=true, this fetches ALL feature flags from the API, but results are
     * cached for 60 seconds by default. Subsequent calls within the cache window will use cached data.
     * To force a fresh API call, use ApiOptions(forceApi = true, useCache = false).
     *
     * @param code The flag code/key
     * @param defaultValue Default value if flag doesn't exist
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API
     * @return The boolean flag value or defaultValue if not found
     */
    fun getBooleanFlag(code: String, defaultValue: Boolean? = null, options: ApiOptions? = null): Boolean? =
        client.getBooleanFlag(code, defaultValue, options)

    /**
     * Get a string feature flag value
     *
     * Note: When using forceApi=true, this fetches ALL feature flags from the API, but results are
     * cached for 60 seconds by default. Subsequent calls within the cache window will use cached data.
     * To force a fresh API call, use ApiOptions(forceApi = true, useCache = false).
     *
     * @param code The flag code/key
     * @param defaultValue Default value if flag doesn't exist
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API
     * @return The string flag value or defaultValue if not found
     */
    fun getStringFlag(code: String, defaultValue: String? = null, options: ApiOptions? = null): String? =
        client.getStringFlag(code, defaultValue, options)

    /**
     * Get an integer feature flag value
     *
     * Note: When using forceApi=true, this fetches ALL feature flags from the API, but results are
     * cached for 60 seconds by default. Subsequent calls within the cache window will use cached data.
     * To force a fresh API call, use ApiOptions(forceApi = true, useCache = false).
     *
     * @param code The flag code/key
     * @param defaultValue Default value if flag doesn't exist
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API
     * @return The integer flag value or defaultValue if not found
     */
    fun getIntegerFlag(code: String, defaultValue: Int? = null, options: ApiOptions? = null): Int? =
        client.getIntegerFlag(code, defaultValue, options)

    /**
     * Get all feature flags for the authenticated user
     *
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API.
     *                Set useCache = false to bypass cache and force a fresh API call.
     * @return Map of flag codes to Flag objects
     */
    fun getAllFlags(options: ApiOptions? = null): Map<String, Flag> = client.getAllFlags(options)

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Only this facade's plumbing dies with the activity; token state and the
        // refresh schedule live on in the shared KindeClient.
        client.detachListener(sdkListener)
        client.detachEndSessionLauncher(endSessionLauncherBridge)
    }

    companion object {
        private val DEFAULT_SCOPES = listOf("openid", "offline", "email", "profile")
    }
}
