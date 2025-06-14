package au.kinde.sdk

import android.content.pm.PackageManager
import android.net.Uri
import android.util.Base64.URL_SAFE
import android.util.Base64.decode
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import au.kinde.sdk.api.* // ktlint-disable no-wildcard-imports
import au.kinde.sdk.api.model.* // ktlint-disable no-wildcard-imports
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

/**
 * @author roman
 * @since 1.0
 */
class KindeSDK(
    activity: ComponentActivity,
    private val loginRedirect: String,
    private val logoutRedirect: String,
    private val scopes: List<String> = DEFAULT_SCOPES,
    private val sdkListener: SDKListener
) : TokenProvider, ClaimApi by ClaimDelegate {

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

    init {
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
                }
            }
        } else {
            sdkListener.onLogout()
        }
        ClaimDelegate.tokenProvider = this
    }

    override fun getToken(tokenType: TokenType): String? =
        if (tokenType == TokenType.ACCESS_TOKEN) state.accessToken else state.idToken

    fun login(type: GrantType? = null, orgCode: String? = null, loginHint: String? = null) {
        login(type, orgCode, loginHint, mapOf())
    }

    fun register(
        type: GrantType? = null,
        orgCode: String? = null,
        loginHint: String? = null,
        pricing_table_key: String? = null,
        planInterest: String? = null
    ) {
        val params = mutableMapOf<String, String>(
            REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE
        )
        if (!pricing_table_key.isNullOrBlank()) {
            params[PRICING_TABLE_KEY_PARAM_NAME] = pricing_table_key
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
        val endSessionRequest = EndSessionRequest.Builder(serviceConfiguration)
            .setPostLogoutRedirectUri(Uri.parse(logoutRedirect))
            .setAdditionalParameters(mapOf(REDIRECT_PARAM_NAME to logoutRedirect))
            .setState(null)
            .build()
        val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
        endTokenLauncher.launch(endSessionIntent)
    }

    fun isAuthenticated() = state.isAuthorized && checkToken()

    fun getUser(): UserProfile? = callApi(oAuthApi.getUser())

    fun getUserProfileV2(): UserProfileV2? = callApi(oAuthApi.getUserProfileV2())

    fun createUser(createUserRequest: CreateUserRequest? = null): CreateUser200Response? = callApi(usersApi.createUser(createUserRequest))

    fun getUsers(sort: kotlin.String? = null, pageSize: kotlin.Int? = null, userId: kotlin.Int? = null, nextToken: kotlin.String? = null): kotlin.collections.List<User>? = callApi(usersApi.getUsers(sort, pageSize, userId, nextToken))

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
            Uri.parse(loginRedirect)
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

    private fun getToken(tokenRequest: TokenRequest): Boolean {
        val (resp, ex) = tokenRepository.getToken(
            authState = state,
            tokenRequest = tokenRequest
        )
        if (resp != null) {
            val tokenNotExists = state.accessToken.isNullOrEmpty()
            state.update(resp, ex)
            apiClient.setBearerToken(state.accessToken.orEmpty())
            store.saveState(state.jsonSerializeString())
            if (tokenNotExists) {
                sdkListener.onNewToken(state.accessToken.orEmpty())
            }
        } else {
            ex?.let { sdkListener.onException(TokenException("${ex.error} ${ex.errorDescription}")) }
            logout()
        }
        return resp != null
    }

    private fun checkToken(): Boolean {
        if (isTokenExpired(TokenType.ACCESS_TOKEN)) {
            getToken(state.createTokenRefreshRequest())
        }
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
                if (getToken(state.createTokenRefreshRequest())) {
                    return callApi(call.clone(), refreshed = true)
                }
            } else {
                sdkListener.onException(exception)
            }
        }
        return null
    }

    private fun isTokenExpired(tokenType: TokenType): Boolean {
        val expClaim = getClaim("exp", tokenType)
        if (expClaim.value != null) {
            val expireEpochMillis = (expClaim.value as Long) * 1000
            val currentTimeMillis = System.currentTimeMillis()

            if (currentTimeMillis > expireEpochMillis) {
                return true
            }
        }
        return false
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
        private const val ORG_NAME_PARAM_NAME = "org_name "
        private const val ORG_CODE_PARAM_NAME = "org_code"
        private const val PRICING_TABLE_KEY_PARAM_NAME = "pricing_table_key"
        private const val PLAN_INTEREST_PARAM_NAME = "plan_interest"
        private const val REDIRECT_PARAM_NAME = "redirect"

        private const val HTTPS = "https://%s/"
        private const val BEARER_AUTH = "kindeBearerAuth"
        private const val LOGIN_HINT = "jdoe@user.example.com"
        private val DEFAULT_SCOPES = listOf("openid", "offline", "email", "profile")
    }
}
