package au.kinde.sdk

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.util.Base64.URL_SAFE
import android.util.Base64.decode
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import au.kinde.sdk.api.* // ktlint-disable no-wildcard-imports
import au.kinde.sdk.api.model.* // ktlint-disable no-wildcard-imports
import au.kinde.sdk.infrastructure.ApiClient
import au.kinde.sdk.keys.Keys
import au.kinde.sdk.keys.KeysApi
import au.kinde.sdk.model.ClaimData
import au.kinde.sdk.model.TokenType
import au.kinde.sdk.model.UserDetails
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
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.math.BigInteger
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.RSAPublicKeySpec

/**
 * @author roman
 * @since 1.0
 */
class KindeSDK(
    activity: AppCompatActivity,
    private val loginRedirect: String,
    private val logoutRedirect: String,
    private val scopes: List<String> = DEFAULT_SCOPES,
    private val sdkListener: SDKListener
) {

    private val authPrefs = activity.getSharedPreferences(
        PREFS_NAME,
        AppCompatActivity.MODE_PRIVATE
    )
    private val gson = Gson()

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
                exchangeToken(resp.createTokenExchangeRequest())
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
    private val audience: String?
    private var grantType: GrantType? = null

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

        val stateJson = authPrefs.getString(AUTH_STATE_PREF, null)
        state = if (!stateJson.isNullOrEmpty()) {
            AuthState.jsonDeserialize(stateJson)
        } else {
            AuthState(serviceConfiguration)
        }

        apiClient = ApiClient(HTTPS.format(domain), authNames = arrayOf(BEARER_AUTH))
        keysApi = apiClient.createService(KeysApi::class.java)
        oAuthApi = apiClient.createService(OAuthApi::class.java)
        usersApi = apiClient.createService(UsersApi::class.java)

        keysApi.getKeys().enqueue(object : Callback<Keys> {
            override fun onResponse(call: Call<Keys>, response: Response<Keys>) {
                response.body()?.let { keys ->
                    authPrefs.edit().putString(KEYS_PREF, gson.toJson(keys)).apply()
                }
            }

            override fun onFailure(call: Call<Keys>, t: Throwable) {
                sdkListener.onException(Exception(t))
            }
        })

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
    }

    fun login(type: GrantType? = null, orgCode: String? = null) {
        login(type, orgCode, mapOf())
    }

    fun register(type: GrantType? = null, orgCode: String? = null) {
        login(type, orgCode, mapOf(REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE))
    }

    fun createOrg(type: GrantType? = null, orgName: String) {
        login(
            type,
            null,
            mapOf(
                REGISTRATION_PAGE_PARAM_NAME to REGISTRATION_PAGE_PARAM_VALUE,
                CREATE_ORG_PARAM_NAME to true.toString(),
                ORG_NAME_PARAM_NAME to orgName
            )
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

        apiClient.setBearerToken("")
        sdkListener.onLogout()
        authPrefs.edit().remove(AUTH_STATE_PREF).apply()
    }

    fun isAuthenticated() = state.isAuthorized && checkToken()

    fun getUserDetails(): UserDetails = UserDetails(
        getClaim(SUB_CLAIM, TokenType.ID_TOKEN).orEmpty(),
        getClaim(GIVEN_NAME_CLAIM, TokenType.ID_TOKEN).orEmpty(),
        getClaim(FAMILY_NAME_CLAIM, TokenType.ID_TOKEN).orEmpty(),
        getClaim(EMAIL_CLAIM, TokenType.ID_TOKEN).orEmpty()
    )

    fun getClaim(claim: String, tokenType: TokenType = TokenType.ACCESS_TOKEN): String? {
        return getClaim(claim, tokenType, false) as String?
    }

    fun getPermissions(): ClaimData.Permissions {
        return ClaimData.Permissions(
            getClaim(ORG_CODE_CLAIM).orEmpty(),
            getClaim(PERMISSIONS_CLAIM, isList = true) as? List<String> ?: emptyList()
        )
    }

    fun getPermission(permission: String): ClaimData.Permission {
        return ClaimData.Permission(
            getClaim(ORG_CODE_CLAIM).orEmpty(),
            (getClaim(PERMISSIONS_CLAIM, isList = true) as? List<String> ?: emptyList())
                .contains(permission)
        )
    }

    fun getUserOrganizations(): ClaimData.Organizations {
        return ClaimData.Organizations(
            getClaim(ORG_CODES_CLAIM, TokenType.ID_TOKEN, isList = true) as? List<String>
                ?: emptyList()
        )
    }

    fun getOrganization(): ClaimData.Organization {
        return ClaimData.Organization(
            getClaim(ORG_CODE_CLAIM).orEmpty()
        )
    }

    fun getUser(): UserProfile? = callApi(oAuthApi.getUser())

    fun getUserProfileV2(): UserProfileV2? = callApi(oAuthApi.getUserProfileV2())

    fun createUser(createUserRequest: CreateUserRequest? = null): CreateUser200Response? = callApi(usersApi.createUser(createUserRequest))

    fun getUsers(sort: kotlin.String? = null, pageSize: kotlin.Int? = null, userId: kotlin.Int? = null, nextToken: kotlin.String? = null): kotlin.collections.List<User>? = callApi(usersApi.getUsers(sort, pageSize, userId, nextToken))

    private fun login(
        type: GrantType? = null,
        orgCode: String? = null,
        additionalParams: Map<String, String>
    ) {
        grantType = type
        val verifier =
            if (grantType == GrantType.PKCE) CodeVerifierUtil.generateRandomCodeVerifier() else null
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

        val authRequest = authRequestBuilder
            .setNonce(null)
            .setScopes(scopes)
            .setLoginHint(LOGIN_HINT)
            .build()
        val authIntent = authService.getAuthorizationRequestIntent(authRequest)
        launcher.launch(authIntent)
    }

    private fun exchangeToken(tokenRequest: TokenRequest) {
        authService.performTokenRequest(tokenRequest) { resp, ex ->
            if (resp != null) {
                state.update(resp, ex)
                apiClient.setBearerToken(state.accessToken.orEmpty())
                authPrefs.edit().putString(AUTH_STATE_PREF, state.jsonSerializeString())
                    .apply()
                sdkListener.onNewToken(state.accessToken.orEmpty())
            } else {
                ex?.let { sdkListener.onException(TokenException("${ex.error} ${ex.errorDescription}")) }
                logout()
            }
        }
    }

    private fun decodeToken(jwt: String): String {
        val parts = jwt.split(".")
        return try {
            val bytes = decode(parts[1], URL_SAFE)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Error parsing JWT: $e"
        }
    }

    private fun getClaim(
        claim: String,
        tokenType: TokenType = TokenType.ACCESS_TOKEN,
        isList: Boolean
    ): Any? {
        return (if (tokenType == TokenType.ACCESS_TOKEN) state.accessToken else state.idToken)?.let { token ->
            val decoded = decodeToken(token)
            val data = JSONObject(decoded)
            if (data.has(claim)) {
                if (isList) {
                    val list = data.getJSONArray(claim)
                    val claims = mutableListOf<String>()
                    for (i in 0 until list.length()) {
                        claims.add(list.getString(i))
                    }
                    claims
                } else {
                    data.getString(claim)
                }
            } else {
                null
            }
        }
    }

    private fun checkToken(): Boolean {
        if (state.isAuthorized) {
            authPrefs.getString(KEYS_PREF, null)?.let { keysString ->
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

    private fun <T> callApi(call: Call<T>): T? {
        if (state.accessToken.isNullOrEmpty()) {
            sdkListener.onException(NotAuthorizedException)
            return null
        }
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            sdkListener.onException(WrongThreadException)
            return null
        }
        try {
            val response = call.execute()
            if (response.isSuccessful) {
                return response.body()
            } else {
                sdkListener.onException(Exception("response is unsuccessful:${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            sdkListener.onException(e)
        }
        return null
    }

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val AUTH_STATE_PREF = "auth_state"
        private const val KEYS_PREF = "keys"

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
        private const val REDIRECT_PARAM_NAME = "redirect"

        private const val PERMISSIONS_CLAIM = "permissions"
        private const val ORG_CODE_CLAIM = "org_code"
        private const val ORG_CODES_CLAIM = "org_codes"
        private const val SUB_CLAIM = "sub"
        private const val GIVEN_NAME_CLAIM = "given_name"
        private const val FAMILY_NAME_CLAIM = "family_name"
        private const val EMAIL_CLAIM = "email"

        private const val HTTPS = "https://%s/"
        private const val BEARER_AUTH = "kindeBearerAuth"
        private const val LOGIN_HINT = "jdoe@user.example.com"
        private val DEFAULT_SCOPES = listOf("openid", "offline", "email", "profile")
    }
}
