package au.kinde.sdk

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Base64.URL_SAFE
import android.util.Base64.decode
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import au.kinde.sdk.api.ApiOptions
import au.kinde.sdk.api.FeatureFlagsApi
import au.kinde.sdk.api.OAuthApi
import au.kinde.sdk.api.PermissionsApi
import au.kinde.sdk.api.RolesApi
import au.kinde.sdk.api.UsersApi
import au.kinde.sdk.api.model.CreateUser200Response
import au.kinde.sdk.api.model.CreateUserRequest
import au.kinde.sdk.api.model.User
import au.kinde.sdk.api.model.UserProfile
import au.kinde.sdk.api.model.UserProfileV2
import au.kinde.sdk.infrastructure.ApiClient
import au.kinde.sdk.keys.Keys
import au.kinde.sdk.keys.KeysApi
import au.kinde.sdk.model.TokenType
import au.kinde.sdk.store.Store
import au.kinde.sdk.token.TokenApi
import au.kinde.sdk.token.TokenRepository
import au.kinde.sdk.utils.ClaimApi
import au.kinde.sdk.utils.ClaimDelegate
import au.kinde.sdk.utils.TokenProvider
import com.google.gson.Gson
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import kotlin.concurrent.thread
import au.kinde.sdk.model.ClaimData
import au.kinde.sdk.model.Flag

class KindeSDK(
    activity: ComponentActivity,
    private val loginRedirect: String,
    private val logoutRedirect: String,
    private val scopes: List<String> = DEFAULT_SCOPES,
    private val sdkListener: SDKListener
) : TokenProvider, ClaimApi by ClaimDelegate, DefaultLifecycleObserver {

    private val gson = Gson()

    private val serviceConfiguration: AuthorizationServiceConfiguration

    private lateinit var state: AuthState

    private val authService = AuthorizationService(activity)

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data

        if (result.resultCode == ComponentActivity.RESULT_CANCELED && data != null) {
            val ex = AuthorizationException.fromIntent(data)
            ex?.let { sdkListener.onException(LogoutException("${ex.errorDescription}")) }
        }

        if (result.resultCode == ComponentActivity.RESULT_OK && data != null) {
            val resp = AuthorizationResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            synchronized(stateLock) {
                state.update(resp, ex)
                store.saveState(state.jsonSerializeString())
            }
            resp?.let {
                thread {
                    getToken(resp.createTokenExchangeRequest())
                }
            }
            ex?.let { sdkListener.onException(AuthException("${ex.error} ${ex.errorDescription}")) }
        }
    }

    private val endTokenLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == ComponentActivity.RESULT_OK && data != null) {
            val resp = EndSessionResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            apiClient.setBearerToken("")
            sdkListener.onLogout()
            store.clearState()
            ex?.let { sdkListener.onException(LogoutException("${ex.error} ${ex.errorDescription}")) }
        }
    }

    private val domain: String
    private val clientId: String
    private val audience: String?

    // Runtime overrides for domain and clientId (cleared on logout)
    private var runtimeDomain: String? = null
    private var runtimeClientId: String? = null

    private val store: Store
    private val tokenRepository: TokenRepository
    private val apiClient: ApiClient
    private val keysApi: KeysApi
    private val oAuthApi: OAuthApi
    private val usersApi: UsersApi
    private val permissionsApi: PermissionsApi
    private val rolesApi: RolesApi
    private val featureFlagsApi: FeatureFlagsApi

    private val tokenRefreshHandler = Handler(Looper.getMainLooper())
    private var tokenRefreshRunnable: Runnable? = null

    @Volatile
    private var isPaused = false
    private var lastTokenUpdateTime = 0L
    private val refreshLock = Object()
    private val stateLock = Object()

    @Volatile
    private var isRefreshing = false

    // Cache infrastructure for API responses
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long
    )

    @Volatile
    private var permissionsCache: CacheEntry<ClaimData.Permissions>? = null
    @Volatile
    private var rolesCache: CacheEntry<ClaimData.Roles>? = null
    @Volatile
    private var flagsCache: CacheEntry<Map<String, Flag>>? = null

    init {
        activity.lifecycle.addObserver(this)
        val appInfo = activity.packageManager.getApplicationInfo(
            activity.packageName,
            PackageManager.GET_META_DATA
        )
        val metaData = appInfo.metaData
        domain = if (metaData.containsKey(DOMAIN_KEY)) {
            metaData.getString(DOMAIN_KEY).orEmpty()
        } else {
            sdkListener.onException(IllegalStateException("$DOMAIN_KEY is not present at meta-data"))
            ""
        }
        clientId = if (metaData.containsKey(CLIENT_ID_KEY)) {
            metaData.getString(CLIENT_ID_KEY).orEmpty()
        } else {
            sdkListener.onException(IllegalStateException("$CLIENT_ID_KEY is not present at meta-data"))
            ""
        }
        audience = if (metaData.containsKey(AUDIENCE_KEY)) {
            metaData.getString(AUDIENCE_KEY).orEmpty()
        } else {
            null
        }
        if (!loginRedirect.startsWith(REDIRECT_URI_SCHEME) ||
            !logoutRedirect.startsWith(REDIRECT_URI_SCHEME)
        ) {
            sdkListener.onException(IllegalStateException("Check your redirect urls"))
        }

        serviceConfiguration = AuthorizationServiceConfiguration(
            AUTH_URL.format(domain).toUri(),
            TOKEN_URL.format(domain).toUri(),
            null,
            LOGOUT_URL.format(domain).toUri()
        )

        store = Store(activity, domain)

        val stateJson = store.getState()
        state = if (!stateJson.isNullOrEmpty()) {
            AuthState.jsonDeserialize(stateJson)
        } else {
            AuthState(serviceConfiguration)
        }

        apiClient = ApiClient(HTTPS.format(domain), authNames = arrayOf(BEARER_AUTH))

        tokenRepository =
            TokenRepository(apiClient.createService(TokenApi::class.java), BuildConfig.SDK_VERSION)

        keysApi = apiClient.createService(KeysApi::class.java)
        oAuthApi = apiClient.createService(OAuthApi::class.java)
        usersApi = apiClient.createService(UsersApi::class.java)
        permissionsApi = apiClient.createService(PermissionsApi::class.java)
        rolesApi = apiClient.createService(RolesApi::class.java)
        featureFlagsApi = apiClient.createService(FeatureFlagsApi::class.java)

        if (store.getKeys().isNullOrEmpty()) {
            keysApi.getKeys().enqueue(object : Callback<Keys> {
                override fun onResponse(call: Call<Keys>, response: Response<Keys>) {
                    response.body()?.let { keys ->
                        store.saveKeys(gson.toJson(keys))
                    }
                }

                override fun onFailure(call: Call<Keys>, t: Throwable) {
                    sdkListener.onException(Exception(t))
                }
            })
        }

        if (!stateJson.isNullOrEmpty()) {
            refreshState()
            if (isAuthenticated()) {
                state.accessToken?.let { accessToken ->
                    apiClient.setBearerToken(accessToken)
                    sdkListener.onNewToken(accessToken)
                    scheduleTokenRefresh()
                }
            }
        } else {
            sdkListener.onLogout()
        }
        ClaimDelegate.tokenProvider = this
    }

    override fun getToken(tokenType: TokenType): String? =
        if (tokenType == TokenType.ACCESS_TOKEN) state.accessToken else state.idToken

    fun getRefreshToken(): String? = state.refreshToken

    /**
     * Initiate login flow
     * 
     * @param type The grant type (PKCE or implicit)
     * @param orgCode Optional organization code
     * @param loginHint Optional login hint (email)
     * @param domain Optional domain to use for this login (overrides config)
     * @param clientId Optional client ID to use for this login (overrides config)
     */
    fun login(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        domain: String? = null,
        clientId: String? = null
    ) {
        login(type, orgCode, loginHint, mapOf(), domain, clientId)
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
     */
    fun register(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        pricingTableKey: String? = null,
        planInterest: String? = null,
        domain: String? = null,
        clientId: String? = null
    ) {
        val params = mutableMapOf<String, String>(
            REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE
        )
        if (!pricingTableKey.isNullOrBlank()) {
            params[PRICING_TABLE_KEY_PARAM_NAME] = pricingTableKey
        }
        if (!planInterest.isNullOrBlank()) {
            params[PLAN_INTEREST_PARAM_NAME] = planInterest
        }
        login(type, orgCode, loginHint, params, domain, clientId)
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
     */
    fun createOrg(
        type: GrantType? = null,
        orgName: String,
        pricingTableKey: String? = null,
        planInterest: String? = null,
        domain: String? = null,
        clientId: String? = null
    ) {
        val params = mutableMapOf<String, String>(
            REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE,
            CREATE_ORG_PARAM_NAME to true.toString(),
            ORG_NAME_PARAM_NAME to orgName
        )
        if (!pricingTableKey.isNullOrBlank()) {
            params[PRICING_TABLE_KEY_PARAM_NAME] = pricingTableKey
        }
        if (!planInterest.isNullOrBlank()) {
            params[PLAN_INTEREST_PARAM_NAME] = planInterest
        }
        login(
            type,
            null,
            null,
            params,
            domain,
            clientId
        )
    }

    fun logout() {
        clearCache()
        cancelTokenRefresh()
        
        // Use the effective domain for logout
        val effectiveDomain = runtimeDomain ?: domain
        val logoutServiceConfig = AuthorizationServiceConfiguration(
            AUTH_URL.format(effectiveDomain).toUri(),
            TOKEN_URL.format(effectiveDomain).toUri(),
            null,
            LOGOUT_URL.format(effectiveDomain).toUri()
        )
        
        val endSessionRequest = EndSessionRequest.Builder(logoutServiceConfig)
            .setPostLogoutRedirectUri(logoutRedirect.toUri())
            .setAdditionalParameters(mapOf(REDIRECT_PARAM_NAME to logoutRedirect))
            .setState(null)
            .build()
        val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
        endTokenLauncher.launch(endSessionIntent)
        
        // Clear runtime overrides after logout
        runtimeDomain = null
        runtimeClientId = null
    }

    /**
     * Refreshes the authentication state from persistent storage.
     * Call this method when you need to sync the in-memory state with storage,
     * especially when navigating between activities that may have modified the auth state.
     */
    fun refreshState() {
        synchronized(stateLock) {
            val stateJson = store.getState()
            if (!stateJson.isNullOrBlank()) {
                state = AuthState.jsonDeserialize(stateJson)
            }
        }
    }

    /**
     * Checks if the user is currently authenticated.
     * This method relies on shared preferences rather than in-memory state
     * to work correctly across multiple activities.
     */
    fun isAuthenticated(): Boolean {
        synchronized(stateLock) {
            val stateJson = store.getState()
            if (stateJson.isNullOrBlank()) return false
            // Temporarily deserialize to check auth status without mutating instance state
            val currentState = AuthState.jsonDeserialize(stateJson)
            return currentState.isAuthorized && checkTokenWithState(currentState)
        }
    }
    
    /**
     * Clears all cached API responses (permissions, roles, and feature flags).
     * Call this when you need to force fresh data on the next API call, or when switching contexts
     * (e.g., changing organizations).
     */
    fun clearCache() {
        permissionsCache = null
        rolesCache = null
        flagsCache = null
    }

    fun getUser(): UserProfile? = callApi(oAuthApi.getUser())

    fun getUserProfileV2(): UserProfileV2? = callApi(oAuthApi.getUserProfileV2())

    fun createUser(createUserRequest: CreateUserRequest? = null): CreateUser200Response? =
        callApi(usersApi.createUser(createUserRequest))

    fun getUsers(
        sort: kotlin.String? = null,
        pageSize: kotlin.Int? = null,
        userId: kotlin.Int? = null,
        nextToken: kotlin.String? = null
    ): kotlin.collections.List<User>? =
        callApi(usersApi.getUsers(sort, pageSize, userId, nextToken))

    /**
     * Get all permissions for the authenticated user
     * 
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API.
     *                Set useCache = false to bypass cache and force a fresh API call.
     * @return ClaimData.Permissions containing org code and list of permission keys
     */
    // Permissions with forceApi support
    fun getPermissions(options: ApiOptions? = null): ClaimData.Permissions {
        return if (options?.forceApi == true) {
            // Check cache first if caching is enabled
            if (options.useCache) {
                val cached = permissionsCache
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    return cached.data
                }
            }

            // Fetch from API
            val response = callApi(permissionsApi.getPermissions())
                ?: throw Exception("Failed to fetch permissions from API - check network and authentication")
            if (!response.success) {
                throw Exception("Permissions API returned success: false")
            }

            val permissions = ClaimData.Permissions(
                orgCode = response.data?.orgCode ?: "",
                permissions = response.getPermissionKeys()
            )

            // Cache the result if caching is enabled
            if (options.useCache) {
                permissionsCache = CacheEntry(permissions, System.currentTimeMillis())
            }

            permissions
        } else {
            ClaimDelegate.getPermissions()
        }
    }

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
    fun getPermission(permission: String, options: ApiOptions? = null): ClaimData.Permission {
        return if (options?.forceApi == true) {
            val perms = getPermissions(options)
            ClaimData.Permission(
                orgCode = perms.orgCode,
                isGranted = perms.permissions.contains(permission)
            )
        } else {
            ClaimDelegate.getPermission(permission)
        }
    }

    /**
     * Get all roles for the authenticated user
     * 
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API.
     *                Set useCache = false to bypass cache and force a fresh API call.
     * @return ClaimData.Roles containing org code and list of role keys
     */
    // Roles with forceApi support
    fun getRoles(options: ApiOptions? = null): ClaimData.Roles {
        return if (options?.forceApi == true) {
            // Check cache first if caching is enabled
            if (options.useCache) {
                val cached = rolesCache
                if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                    return cached.data
                }
            }

            // Fetch from API
            val response = callApi(rolesApi.getRoles())
                ?: throw Exception("Failed to fetch roles from API - check network and authentication")
            if (!response.success) {
                throw Exception("Roles API returned success: false")
            }

            val roles = ClaimData.Roles(
                orgCode = response.data?.orgCode ?: "",
                roles = response.getRoleKeys()
            )

            // Cache the result if caching is enabled
            if (options.useCache) {
                rolesCache = CacheEntry(roles, System.currentTimeMillis())
            }

            roles
        } else {
            ClaimDelegate.getRoles()
        }
    }

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
    fun getRole(role: String, options: ApiOptions? = null): ClaimData.Role {
        return if (options?.forceApi == true) {
            val roles = getRoles(options)
            ClaimData.Role(
                orgCode = roles.orgCode,
                isGranted = roles.roles.contains(role)
            )
        } else {
            ClaimDelegate.getRole(role)
        }
    }

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
    // Feature flags with forceApi support
    fun getBooleanFlag(code: String, defaultValue: Boolean? = null, options: ApiOptions? = null): Boolean? {
        return if (options?.forceApi == true) {
            val flags = fetchFlagsWithCache(options)
            val flag = flags[code]
            when {
                flag == null -> defaultValue
                flag.value is Boolean -> flag.value as Boolean
                else -> {
                    android.util.Log.w("KindeSDK", "Flag '$code' type mismatch: expected Boolean, got ${flag.type}")
                    defaultValue
                }
            }
        } else {
            ClaimDelegate.getBooleanFlag(code, defaultValue)
        }
    }

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
    fun getStringFlag(code: String, defaultValue: String? = null, options: ApiOptions? = null): String? {
        return if (options?.forceApi == true) {
            val flags = fetchFlagsWithCache(options)
            val flag = flags[code]
            when {
                flag == null -> defaultValue
                flag.value is String -> flag.value as String
                else -> {
                    android.util.Log.w("KindeSDK", "Flag '$code' type mismatch: expected String, got ${flag.type}")
                    defaultValue
                }
            }
        } else {
            ClaimDelegate.getStringFlag(code, defaultValue)
        }
    }

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
    fun getIntegerFlag(code: String, defaultValue: Int? = null, options: ApiOptions? = null): Int? {
        return if (options?.forceApi == true) {
            val flags = fetchFlagsWithCache(options)
            val flag = flags[code]
            when {
                flag == null -> defaultValue
                flag.value is Number -> (flag.value as Number).toInt()
                else -> {
                    android.util.Log.w("KindeSDK", "Flag '$code' type mismatch: expected Integer, got ${flag.type}")
                    defaultValue
                }
            }
        } else {
            ClaimDelegate.getIntegerFlag(code, defaultValue)
        }
    }

    /**
     * Get all feature flags for the authenticated user
     * 
     * @param options Optional API options. Use ApiOptions(forceApi = true) to fetch fresh data from API.
     *                Set useCache = false to bypass cache and force a fresh API call.
     * @return Map of flag codes to Flag objects
     */
    fun getAllFlags(options: ApiOptions? = null): Map<String, Flag> {
        return if (options?.forceApi == true) {
            fetchFlagsWithCache(options)
        } else {
            ClaimDelegate.getAllFlags()
        }
    }

    /**
     * Helper method to fetch feature flags from API with caching support
     */
    private fun fetchFlagsWithCache(options: ApiOptions): Map<String, Flag> {
        // Check cache first if caching is enabled
        if (options.useCache) {
            val cached = flagsCache
            if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
                return cached.data
            }
        }

        // Fetch from API
        val response = callApi(featureFlagsApi.getFeatureFlags())
            ?: throw Exception("Failed to fetch feature flags from API - check network and authentication")
        if (!response.success) {
            throw Exception("Feature flags API returned success: false")
        }

        val flags = response.toFlagMap()

        // Cache the result if caching is enabled
        if (options.useCache) {
            flagsCache = CacheEntry(flags, System.currentTimeMillis())
        }
        
        return flags
    }

    private fun login(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        additionalParams: Map<String, String>,
        customDomain: String? = null,
        customClientId: String? = null
    ) {
        // Store runtime overrides if provided
        customDomain?.let { runtimeDomain = it }
        customClientId?.let { runtimeClientId = it }
        
        // Use runtime values if set, otherwise fall back to config
        val effectiveDomain = runtimeDomain ?: domain
        val effectiveClientId = runtimeClientId ?: clientId
        
        // Reconfigure API client if domain changed
        reconfigureApiClientIfNeeded(effectiveDomain)
        
        // Create service configuration with effective domain
        val loginServiceConfig = AuthorizationServiceConfiguration(
            AUTH_URL.format(effectiveDomain).toUri(),
            TOKEN_URL.format(effectiveDomain).toUri(),
            null,
            LOGOUT_URL.format(effectiveDomain).toUri()
        )
        
        val verifier =
            if (type == GrantType.PKCE) CodeVerifierUtil.generateRandomCodeVerifier() else null
        val authRequestBuilder = AuthorizationRequest.Builder(
            loginServiceConfig, // the authorization service configuration
            effectiveClientId, // the client ID (config or runtime override)
            ResponseTypeValues.CODE, // the response_type value: we want a code
            loginRedirect.toUri()
        )
            .setCodeVerifier(verifier)
            .setAdditionalParameters(
                buildMap {
                    putAll(additionalParams)
                    audience?.let {
                        put(AUDIENCE_PARAM_NAME, audience)
                    }
                    orgCode?.let {
                        put(ORG_CODE_PARAM_NAME, orgCode)
                    }
                }
            )

        // Extract and set login_hint if it's provided in additionalParams and is not empty.
        loginHint?.takeIf { it.isNotEmpty() }?.let {
            authRequestBuilder.setLoginHint(it)
        }

        val authRequest = authRequestBuilder
            .setNonce(null)
            .setScopes(scopes)
            .build()
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        launcher.launch(authIntent)
    }

    private fun getToken(tokenRequest: TokenRequest, notifyListener: Boolean = true): Boolean {
        val grantType = tokenRequest.grantType

        // For refresh token requests, use synchronized block to prevent concurrent refreshes
        if (grantType == "refresh_token") {
            synchronized(refreshLock) {
                while (isRefreshing) {
                    try {
                        refreshLock.wait()
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return false
                    }
                }
                isRefreshing = true
            }
        }

        val (resp, ex) = tokenRepository.getToken(
            authState = state,
            tokenRequest = tokenRequest
        )

        if (resp != null) {
            val tokenNotExists = state.accessToken.isNullOrEmpty()
            synchronized(stateLock) {
                state.update(resp, ex)
                store.saveState(state.jsonSerializeString())
            }
            apiClient.setBearerToken(state.accessToken.orEmpty())
            lastTokenUpdateTime = System.currentTimeMillis()

            if (notifyListener && (tokenNotExists || !state.accessToken.isNullOrEmpty())) {
                sdkListener.onNewToken(state.accessToken.orEmpty())
            }

            // Always schedule the next refresh after successful token operation
            scheduleTokenRefresh()
            if (grantType == "refresh_token") {
                synchronized(refreshLock) {
                    isRefreshing = false
                    refreshLock.notifyAll()
                }
            } else {
                isRefreshing = false
            }
        } else {
            // Check if this is a 401/invalid_grant error (invalid refresh token)
            val isInvalidRefreshToken = ex?.let { exception ->
                val is401 = exception.message?.contains("401") == true
                val isInvalidGrant = exception.error == "invalid_grant"
                is401 || isInvalidGrant
            } ?: false

            ex?.let { sdkListener.onException(TokenException("${ex.error} ${ex.errorDescription}")) }

            // Always logout on invalid refresh token (401/invalid_grant)
            // For other errors, only logout if notifyListener is true (manual refresh)
            try {
                if (isInvalidRefreshToken || notifyListener) {
                    if (Looper.myLooper() == Looper.getMainLooper()) {
                        logout()
                    } else {
                        tokenRefreshHandler.post { logout() }
                    }
                }
            } finally {
                if (grantType == "refresh_token") {
                    synchronized(refreshLock) {
                        isRefreshing = false
                        refreshLock.notifyAll()
                    }
                } else {
                    isRefreshing = false
                }
            }
        }
        return resp != null
    }

    /*
    * checkToken should only verify token signature, not trigger refresh
    * Token refresh is handled automatically by scheduleTokenRefresh()
    */
    private fun checkToken() = checkTokenWithState(state)

    private fun checkTokenWithState(authState: AuthState): Boolean {
        if (authState.isAuthorized) {
            store.getKeys()?.let { keysString ->
                try {
                    gson.fromJson(keysString, Keys::class.java)?.let { keys ->
                        keys.keys.firstOrNull()?.let { key ->
                            val jwt = authState.accessToken.orEmpty()

                            val exponentB: ByteArray = decode(key.exponent, URL_SAFE)
                            val modulusB: ByteArray = decode(key.modulus, URL_SAFE)
                            val bigExponent = BigInteger(1, exponentB)
                            val bigModulus = BigInteger(1, modulusB)
                            val publicKey = KeyFactory.getInstance(key.keyType)
                                .generatePublic(RSAPublicKeySpec(bigModulus, bigExponent))
                            val signedData: String = jwt.substring(0, jwt.lastIndexOf("."))
                            val signatureB64u: String =
                                jwt.substring(jwt.lastIndexOf(".") + 1, jwt.length)
                            val signature: ByteArray = decode(signatureB64u, URL_SAFE)
                            val sig: Signature = Signature.getInstance("SHA256withRSA")
                            sig.initVerify(publicKey)
                            sig.update(signedData.toByteArray())
                            return sig.verify(signature)
                        }
                    }
                } catch (ex: Exception) {
                    sdkListener.onException(ex)
                }
            }
        }
        return false
    }

    private fun <T> callApi(call: Call<T>, refreshed: Boolean = false): T? {
        val (data, exception) = call.callApi(state, refreshed = refreshed)
        if (data != null) {
            return data
        }
        if (exception != null) {
            if (exception is TokenExpiredException) {
                // Use notifyListener=false to prevent logout on API call token refresh
                if (getToken(state.createTokenRefreshRequest(), notifyListener = false)) {
                    return callApi(call.clone(), refreshed = true)
                }
            } else {
                sdkListener.onException(exception)
            }
        }
        return null
    }

    private fun getExpireEpochSeconds(tokenType: TokenType): Long? {
        val expClaim = getClaim("exp", tokenType)
        return when (val value = expClaim.value) {
            is Long -> value
            is String -> value.toLongOrNull()
            is Number -> value.toLong()
            else -> null
        }
    }

    private fun scheduleTokenRefresh() {
        cancelTokenRefresh()

        if (isPaused) {
            return
        }

        val expireEpochSeconds = getExpireEpochSeconds(TokenType.ACCESS_TOKEN) ?: return

        val expireEpochMillis = expireEpochSeconds * 1000
        val refreshTimeMillis = expireEpochMillis - TOKEN_REFRESH_BUFFER_MS
        val currentTimeMillis = System.currentTimeMillis()
        val delayMillis = refreshTimeMillis - currentTimeMillis

        val retryDelayMs = 10_000L

        if (delayMillis > 0) {
            postTokenRefresh(delayMillis, retryDelayMs)
        } else {
            // Already inside the buffer window — refresh right away
            postTokenRefresh(0L, retryDelayMs)
        }
    }

    private fun postTokenRefresh(delayMillis: Long, retryDelayMs: Long) {
        tokenRefreshRunnable = createTokenRefreshRunnable(retryDelayMs)
        tokenRefreshHandler.postDelayed(tokenRefreshRunnable!!, delayMillis)
    }

    private fun createTokenRefreshRunnable(retryDelayMs: Long = 10_000L): Runnable {
        return Runnable {
            thread {
                val success = getToken(state.createTokenRefreshRequest(), notifyListener = false)
                if (!success) {
                    postTokenRefresh(retryDelayMs, retryDelayMs)
                }
            }
        }
    }

    private fun cancelTokenRefresh() {
        tokenRefreshRunnable?.let { tokenRefreshHandler.removeCallbacks(it) }
        tokenRefreshRunnable = null
    }
    
    /**
     * Reconfigures the API client if the domain has changed from the originally configured domain.
     * This ensures API calls go to the correct endpoint when using runtime domain overrides.
     */
    private fun reconfigureApiClientIfNeeded(effectiveDomain: String) {
        val currentBaseUrl = apiClient.getBaseUrl()
        val expectedBaseUrl = HTTPS.format(effectiveDomain)
        
        if (currentBaseUrl != expectedBaseUrl) {
            apiClient.setBaseUrl(expectedBaseUrl)
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        isPaused = true
        // Cancel scheduled refresh when app goes to background to save battery
        cancelTokenRefresh()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isPaused = false
        // Refresh state from storage and check if token needs refresh when app comes to foreground
        refreshState()
        if (isAuthenticated()) {
            scheduleTokenRefresh()
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        // Clean up resources
        cancelTokenRefresh()
        authService.dispose()
    }

    companion object {
        private const val DOMAIN_KEY = "au.kinde.domain"
        private const val CLIENT_ID_KEY = "au.kinde.clientId"
        private const val AUDIENCE_KEY = "audience"

        private const val AUTH_URL = "https://%s/oauth2/auth"
        private const val TOKEN_URL = "https://%s/oauth2/token"
        private const val LOGOUT_URL = "https://%s/logout"
        private const val REDIRECT_URI_SCHEME = "kinde.sdk://"

        private const val REGISTRATION_PAGE_PARAM_NAME = "start_page"
        private const val REGISTRATION_PAGE_PARAM_VALUE = "registration"
        private const val AUDIENCE_PARAM_NAME = "audience"
        private const val CREATE_ORG_PARAM_NAME = "is_create_org"
        private const val ORG_NAME_PARAM_NAME = "org_name"
        private const val ORG_CODE_PARAM_NAME = "org_code"
        private const val PRICING_TABLE_KEY_PARAM_NAME = "pricing_table_key"
        private const val PLAN_INTEREST_PARAM_NAME = "plan_interest"
        private const val REDIRECT_PARAM_NAME = "redirect"

        private const val HTTPS = "https://%s/"
        private const val BEARER_AUTH = "kindeBearerAuth"
        private const val LOGIN_HINT = "jdoe@user.example.com"
        private val DEFAULT_SCOPES = listOf("openid", "offline", "email", "profile")

        // Cache configuration
        private const val CACHE_TTL_MS = 60_000L // 60 seconds
        private const val TOKEN_REFRESH_BUFFER_MS = 10_000L // 10 seconds
    }
}
