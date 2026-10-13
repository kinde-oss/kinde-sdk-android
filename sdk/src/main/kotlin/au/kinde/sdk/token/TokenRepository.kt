package au.kinde.sdk.token

import android.os.Looper
import au.kinde.sdk.WrongThreadException
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationException.AuthorizationRequestErrors.SERVER_ERROR
import net.openid.appauth.AuthorizationException.TYPE_GENERAL_ERROR
import net.openid.appauth.AuthorizationException.TokenRequestErrors
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.openid.appauth.internal.Logger
import net.openid.appauth.internal.UriUtil
import org.json.JSONException
import org.json.JSONObject

/**
 * @author roman
 * @since 1.0
 */
class TokenRepository(private val tokenApi: TokenApi, private val version: String) {

    fun getToken(
        authState: AuthState,
        tokenRequest: TokenRequest
    ): Pair<TokenResponse?, AuthorizationException?> {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return Pair(null, generalError("Token requests must not run on the main thread", WrongThreadException))
        }
        val call = tokenApi.retrieveToken(
            LANGUAGE_HEADER.format(version),
            tokenRequest.requestParameters.apply {
                put(TokenRequest.PARAM_CLIENT_ID, tokenRequest.clientId)
            })
        val response = try {
            call.execute()
        } catch (e: Exception) {
            // No response at all (offline, DNS, TLS): transient, never a reason to sign out.
            return Pair(null, generalError(e.message, e))
        }

        // Token endpoints report OAuth errors (invalid_grant for a revoked or expired
        // refresh token, invalid_client, ...) in the body of a 4xx response, so read
        // the body whatever the status.
        val body = if (response.isSuccessful) response.body().orEmpty() else response.errorBody()?.string().orEmpty()
        val json = try {
            JSONObject(body)
        } catch (e: JSONException) {
            null
        }

        if (json != null && json.has(AuthorizationException.PARAM_ERROR)) {
            val ex: AuthorizationException = try {
                val error: String = json.getString(AuthorizationException.PARAM_ERROR)
                AuthorizationException.fromOAuthTemplate(
                    TokenRequestErrors.byString(error),
                    error,
                    json.optString(AuthorizationException.PARAM_ERROR_DESCRIPTION),
                    UriUtil.parseUriIfAvailable(
                        json.optString(AuthorizationException.PARAM_ERROR_URI)
                    )
                )
            } catch (jsonEx: JSONException) {
                AuthorizationException.fromTemplate(
                    AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR,
                    jsonEx
                )
            }
            return Pair(null, ex)
        }

        if (!response.isSuccessful || json == null) {
            return Pair(
                null,
                generalError("Token request failed: HTTP ${response.code()} ${response.message()}", null, response.code())
            )
        }

        val tokenResponse: TokenResponse = try {
            TokenResponse.Builder(tokenRequest).fromResponseJson(json).build()
        } catch (jsonEx: JSONException) {
            return Pair(
                null,
                AuthorizationException.fromTemplate(
                    AuthorizationException.GeneralErrors.JSON_DESERIALIZATION_ERROR,
                    jsonEx
                )
            )
        }

        Logger.debug("Token exchange with %s completed")
        return Pair(tokenResponse, null)
    }

    /**
     * A failure with no OAuth error from the server: network problems, 5xx, or an
     * unexpected body. [AuthorizationException.code] carries the HTTP status when
     * there was one, so callers can tell a server refusal from a transient failure.
     */
    private fun generalError(message: String?, cause: Throwable?, httpStatus: Int? = null) = AuthorizationException(
        TYPE_GENERAL_ERROR,
        httpStatus ?: SERVER_ERROR.code,
        null,
        message,
        null,
        cause
    )

    private companion object {
        private const val LANGUAGE_HEADER = "Kotlin/%s"
    }
}