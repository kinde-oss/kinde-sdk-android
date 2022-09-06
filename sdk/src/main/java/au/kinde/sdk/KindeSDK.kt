package au.kinde.sdk

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import au.kinde.sdk.api.OAuthApi
import au.kinde.sdk.api.OrganizationsApi
import au.kinde.sdk.api.UsersApi
import au.kinde.sdk.infrastructure.ApiClient
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ClientAuthentication
import net.openid.appauth.ClientSecretBasic
import net.openid.appauth.CodeVerifierUtil
import net.openid.appauth.EndSessionRequest
import net.openid.appauth.EndSessionResponse
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.TokenRequest
import retrofit2.Call


/**
 * @author roman
 * @since 1.0
 */
class KindeSDK(
    activity: AppCompatActivity,
    private val sdkListener: SDKListener? = null
) {

    private val authPrefs = activity.getSharedPreferences(
        PREFS_NAME,
        AppCompatActivity.MODE_PRIVATE
    )

    private val serviceConfiguration: AuthorizationServiceConfiguration

    private lateinit var state: AuthState

    private val authService = AuthorizationService(activity)

    private val launcher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == AppCompatActivity.RESULT_OK && data != null) {
            val resp = AuthorizationResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            state.update(resp, ex)
            authPrefs.edit().putString(AUTH_STATE_PREF, state.jsonSerializeString()).apply()
            resp?.let { exchangeToken(resp.createTokenExchangeRequest()) }
        }
    }

    private val endTokenLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == AppCompatActivity.RESULT_OK && data != null) {
            val resp = EndSessionResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            authPrefs.edit().remove(AUTH_STATE_PREF).apply()
            sdkListener?.onLogout()
        }
    }

    private val domain: String
    private val clientId: String
    private val clientSecret: String
    private var grantType: GrantType? = null

    private val apiClient: ApiClient
    private val oAuthApi: OAuthApi
    private val organizationsApi: OrganizationsApi
    private val userApi: UsersApi

    init {
        val appInfo = activity.packageManager.getApplicationInfo(
            activity.packageName,
            PackageManager.GET_META_DATA
        )
        val metaData = appInfo.metaData
        domain = metaData.getString(DOMAIN_KEY)
            ?: throw IllegalStateException("$DOMAIN_KEY is not present at meta-data")
        clientId = metaData.getString(CLIENT_ID_KEY)
            ?: throw IllegalStateException("$CLIENT_ID_KEY is not present at meta-data")
        clientSecret = metaData.getString(CLIENT_SECRET_KEY)
            ?: throw IllegalStateException("$CLIENT_SECRET_KEY is not present at meta-data")

        serviceConfiguration = AuthorizationServiceConfiguration(
            Uri.parse(AUTH_URL.format(domain)),
            Uri.parse(TOKEN_URL.format(domain)),
            null,
            Uri.parse(LOGOUT_URL.format(domain))
        )

        val stateJson = authPrefs.getString(AUTH_STATE_PREF, null)
        state = if (!stateJson.isNullOrEmpty()) {
            AuthState.jsonDeserialize(stateJson)
        } else {
            AuthState(serviceConfiguration)
        }

        apiClient = ApiClient(HTTPS.format(domain), authNames = arrayOf(BEARER_AUTH))
        oAuthApi = apiClient.createService(OAuthApi::class.java)
        organizationsApi = apiClient.createService(OrganizationsApi::class.java)
        userApi = apiClient.createService(UsersApi::class.java)

        val clientAuth: ClientAuthentication = ClientSecretBasic(clientSecret)
        state.performActionWithFreshTokens(
            authService, clientAuth
        ) { accessToken, idToken, ex ->
            accessToken?.let {
                apiClient.setBearerToken(accessToken)
                sdkListener?.onNewToken(accessToken)
            } ?: sdkListener?.onLogout()
        }
    }

    fun login(type: GrantType? = null) {
        login(type, mapOf())
    }

    fun register(type: GrantType? = null) {
        login(type, mapOf(REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE))
    }

    fun logout() {
        state.idToken?.let {
            val endSessionRequest = EndSessionRequest.Builder(serviceConfiguration)
                .setIdTokenHint(it)
                .setPostLogoutRedirectUri(Uri.parse(REDIRECT_URI.format(domain)))
                .setAdditionalParameters(mapOf(REDIRECT_PARAM_NAME to REDIRECT_URI.format(domain)))
                .build()
            val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
            endTokenLauncher.launch(endSessionIntent)
            apiClient.setBearerToken("")
        }
    }

    fun getProfile() = callApi(oAuthApi.getUser())

    fun getUsers(pageSize: Int, sort: String) {
        callApi(userApi.getUsers(pageSize = pageSize, sort = sort))
    }

    fun createOrganization(name: String) {
        callApi(organizationsApi.createOrganization(name))
    }

    private fun login(
        type: GrantType? = null,
        additionalParams: Map<String, String>
    ) {
        grantType = type
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfiguration,  // the authorization service configuration
            clientId,  // the client ID, typically pre-registered and static
            ResponseTypeValues.CODE,  // the response_type value: we want a code
            Uri.parse(REDIRECT_URI.format(domain))
        )
            .setCodeVerifier(if (grantType == GrantType.PKCE) CodeVerifierUtil.generateRandomCodeVerifier() else null)
            .setAdditionalParameters(additionalParams)

        val authRequest = authRequestBuilder
            .setNonce(null)
            .setScopes(SCOPES)
            .setLoginHint(LOGIN_HINT)
            .build()
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        launcher.launch(authIntent)
    }

    private fun exchangeToken(tokenRequest: TokenRequest) {
        val clientAuth: ClientAuthentication = ClientSecretBasic(clientSecret)
        authService.performTokenRequest(tokenRequest, clientAuth) { resp, ex ->
            if (resp != null) {
                state.update(resp, ex)
                apiClient.setBearerToken(state.accessToken.orEmpty())
                authPrefs.edit().putString(AUTH_STATE_PREF, state.jsonSerializeString())
                    .apply()
                sdkListener?.onNewToken(state.accessToken.orEmpty())
            } else {
                logout()
            }
        }
    }

    private fun <T> callApi(call: Call<T>): T? {
        if (state.accessToken.isNullOrEmpty()) {
            throw NotAuthorizedException
        }
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            throw WrongThreadException
        }
        val response = call.execute()
        if (response.isSuccessful) {
            return response.body()
        } else {
            throw Exception("response is unsuccessful")
        }
    }

    interface SDKListener {
        fun onNewToken(token: String)

        fun onLogout()
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val AUTH_STATE_PREF = "auth_state"

        private const val DOMAIN_KEY = "au.kinde.domain"
        private const val CLIENT_ID_KEY = "au.kinde.clientId"
        private const val CLIENT_SECRET_KEY = "au.kinde.clientSecret"

        private const val AUTH_URL = "https://%s/oauth2/auth"
        private const val TOKEN_URL = "https://%s/oauth2/token"
        private const val LOGOUT_URL = "https://%s/logout"
        private const val REDIRECT_URI = "au.kinde://%s/kinde_callback"

        private const val REGISTRATION_PAGE_PARAM_NAME = "start_page"
        private const val REGISTRATION_PAGE_PARAM_VALUE = "registration"
        private const val REDIRECT_PARAM_NAME = "redirect"

        private const val HTTPS = "https://%s/"
        private const val BEARER_AUTH = "kindeBearerAuth"
        private const val LOGIN_HINT = "jdoe@user.example.com"
        private val SCOPES = listOf("openid", "offline")
    }
}