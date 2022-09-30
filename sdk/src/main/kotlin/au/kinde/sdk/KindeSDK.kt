package au.kinde.sdk

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import au.kinde.sdk.api.* // ktlint-disable no-wildcard-imports
import au.kinde.sdk.api.model.* // ktlint-disable no-wildcard-imports
import au.kinde.sdk.infrastructure.ApiClient
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
import retrofit2.Call

/**
 * @author roman
 * @since 1.0
 */
class KindeSDK(
    activity: AppCompatActivity,
    private val sdkListener: SDKListener
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
            resp?.let {
                apiClient.setBearerToken(state.accessToken.orEmpty())
                sdkListener.onNewToken(state.accessToken.orEmpty())
            }
            ex?.let { sdkListener.onException(AuthException("${ex.error} ${ex.errorDescription}")) }
        }
    }

    private val endTokenLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == AppCompatActivity.RESULT_OK && data != null) {
            val resp = EndSessionResponse.fromIntent(data)
            val ex = AuthorizationException.fromIntent(data)
            apiClient.setBearerToken("")
            sdkListener.onLogout()
            authPrefs.edit().remove(AUTH_STATE_PREF).apply()
            ex?.let { sdkListener.onException(LogoutException("${ex.error} ${ex.errorDescription}")) }
        }
    }

    private val domain: String
    private val clientId: String
    private var grantType: GrantType? = null

    private val apiClient: ApiClient
    private val oAuthApi: OAuthApi
    private val organizationsApi: OrganizationsApi
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
        usersApi = apiClient.createService(UsersApi::class.java)

        if (!stateJson.isNullOrEmpty()) {
            state.performActionWithFreshTokens(
                authService
            ) { accessToken, idToken, ex ->
                accessToken?.let {
                    apiClient.setBearerToken(accessToken)
                    sdkListener.onNewToken(accessToken)
                } ?: run {
                    sdkListener.onLogout()
                    ex?.let { sdkListener.onException(TokenException("${ex.error} ${ex.errorDescription}")) }
                }
            }
        } else {
            sdkListener.onLogout()
        }
    }

    fun login(type: GrantType? = null) {
        login(type, mapOf())
    }

    fun register(type: GrantType? = null) {
        login(type, mapOf(REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE))
    }

    fun logout() {
        val endSessionRequest = EndSessionRequest.Builder(serviceConfiguration)
            .setPostLogoutRedirectUri(Uri.parse(REDIRECT_URI.format(domain)))
            .setAdditionalParameters(mapOf(REDIRECT_PARAM_NAME to REDIRECT_URI.format(domain)))
            .build()
        val endSessionIntent = authService.getEndSessionRequestIntent(endSessionRequest)
        endTokenLauncher.launch(endSessionIntent)
    }

    fun getUser(): UserProfile? = callApi(oAuthApi.getUser())

    fun createOrganization(name: kotlin.String? = null): OrganizationCode? = callApi(organizationsApi.createOrganization(name))

    fun getUsers(sort: kotlin.String? = null, pageSize: kotlin.Int? = null, userId: kotlin.Int? = null, nextToken: kotlin.String? = null): kotlin.collections.List<User>? = callApi(usersApi.getUsers(sort, pageSize, userId, nextToken))

    private fun login(
        type: GrantType? = null,
        additionalParams: Map<String, String>
    ) {
        grantType = type
        val authRequestBuilder = AuthorizationRequest.Builder(
            serviceConfiguration, // the authorization service configuration
            clientId, // the client ID, typically pre-registered and static
            ResponseTypeValues.TOKEN, // the response_type value: we want a code
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

    private fun <T> callApi(call: Call<T>): T? {
        if (state.accessToken.isNullOrEmpty()) {
            sdkListener.onException(NotAuthorizedException)
            return null
        }
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            sdkListener.onException(WrongThreadException)
            return null
        }
        val response = call.execute()
        if (response.isSuccessful) {
            return response.body()
        } else {
            sdkListener.onException(Exception("response is unsuccessful:${response.code()} ${response.message()}"))
        }
        return null
    }

    interface SDKListener {
        fun onNewToken(token: String)

        fun onLogout()

        fun onException(exception: Exception)
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val AUTH_STATE_PREF = "auth_state"

        private const val DOMAIN_KEY = "au.kinde.domain"
        private const val CLIENT_ID_KEY = "au.kinde.clientId"

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
