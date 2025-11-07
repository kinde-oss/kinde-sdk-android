package au.kinde.sdk

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64.URL_SAFE
import android.util.Base64.decode
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import au.kinde.sdk.api.OAuthApi
import au.kinde.sdk.api.UsersApi
import au.kinde.sdk.api.model.*
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
import net.openid.appauth.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import kotlin.compareTo
import kotlin.concurrent.thread

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
            state.update(resp, ex)
            store.saveState(state.jsonSerializeString())
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

    private val store: Store
    private val tokenRepository: TokenRepository
    private val apiClient: ApiClient
    private val keysApi: KeysApi
    private val oAuthApi: OAuthApi
    private val usersApi: UsersApi

    private val tokenRefreshHandler = Handler(Looper.getMainLooper())
    private var tokenRefreshRunnable: Runnable? = null

    @Volatile
    private var isPaused = false
    private var lastTokenUpdateTime = 0L
    private val refreshLock = Object()

    @Volatile
    private var isRefreshing = false

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
            Uri.parse(AUTH_URL.format(domain)),
            Uri.parse(TOKEN_URL.format(domain)),
            null,
            Uri.parse(LOGOUT_URL.format(domain))
        )

        store = Store(activity, domain)

        val stateJson = store.getState()
        state = if (!stateJson.isNullOrEmpty()) {
            AuthState.jsonDeserialize(stateJson)
        } else {
            AuthState(serviceConfiguration)
        }

        apiClient = ApiClient(HTTPS.format(domain), authNames = arrayOf(BEARER_AUTH))

        tokenRepository = TokenRepository(apiClient.createService(TokenApi::class.java), BuildConfig.SDK_VERSION)

        keysApi = apiClient.createService(KeysApi::class.java)
        oAuthApi = apiClient.createService(OAuthApi::class.java)
        usersApi = apiClient.createService(UsersApi::class.java)

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

    fun login(type: GrantType? = null, orgCode: String? = null, loginHint: String? = null) {
        login(type, orgCode, loginHint, mapOf())
    }

    fun register(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        pricingTableKey: String? = null,
        planInterest: String? = null
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
        login(type, orgCode, loginHint, params)
    }

    fun createOrg(
        type: GrantType? = null,
        orgName: String,
        pricingTableKey: String? = null,
        planInterest: String? = null
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
            params
        )
    }

    fun logout() {
        cancelTokenRefresh()
        val endSessionRequest = EndSessionRequest.Builder(serviceConfiguration)
            .setPostLogoutRedirectUri(logoutRedirect.toUri())
            .setAdditionalParameters(mapOf(REDIRECT_PARAM_NAME to logoutRedirect))
            .setState(null)
            .build()
        val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
        endTokenLauncher.launch(endSessionIntent)
    }

    fun isAuthenticated() = state.isAuthorized && checkToken()

    fun getUser(): UserProfile? = callApi(oAuthApi.getUser())

    fun getUserProfileV2(): UserProfileV2? = callApi(oAuthApi.getUserProfileV2())

    fun createUser(createUserRequest: CreateUserRequest? = null): CreateUser200Response? =
        callApi(usersApi.createUser(createUserRequest))

    fun getUsers(
        sort: kotlin.String? = null,
        pageSize: kotlin.Int? = null,
        userId: kotlin.Int? = null,
        nextToken: kotlin.String? = null
    ): kotlin.collections.List<User>? = callApi(usersApi.getUsers(sort, pageSize, userId, nextToken))

    private fun login(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        additionalParams: Map<String, String>
    ) {
        val verifier =
            if (type == GrantType.PKCE) CodeVerifierUtil.generateRandomCodeVerifier() else null
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfiguration, // the authorization service configuration
            clientId, // the client ID, typically pre-registered and static
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
            state.update(resp, ex)
            apiClient.setBearerToken(state.accessToken.orEmpty())
            store.saveState(state.jsonSerializeString())
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

    private fun checkToken(): Boolean {
        // checkToken should only verify token signature, not trigger refresh
        // Token refresh is handled automatically by scheduleTokenRefresh()
        if (state.isAuthorized) {
            store.getKeys()?.let { keysString ->
                try {
                    gson.fromJson(keysString, Keys::class.java)?.let { keys ->
                        keys.keys.firstOrNull()?.let { key ->
                            val jwt = state.accessToken.orEmpty()

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

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        isPaused = true
        // Cancel scheduled refresh when app goes to background to save battery
        cancelTokenRefresh()
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        isPaused = false
        // Check if token needs refresh and reschedule when app comes to foreground
        if (state.isAuthorized) {
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
        private const val TOKEN_REFRESH_BUFFER_MS = 10_000L // 10 seconds
    }
}
