package au.kinde.sdk.token

import au.kinde.sdk.utils.callApi
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
        val (tokenResponseString, exception) =
            tokenApi.retrieveToken(
                LANGUAGE_HEADER.format(version),
                tokenRequest.requestParameters.apply {
                    put(TokenRequest.PARAM_CLIENT_ID, tokenRequest.clientId)
                }).callApi(authState, true)
        if (exception != null) return Pair(
            null,
            AuthorizationException(
                TYPE_GENERAL_ERROR,
                SERVER_ERROR.code,
                null,
                exception.message,
                null,
                exception
            )
        )

        val json = JSONObject(tokenResponseString.orEmpty())
        if (json.has(AuthorizationException.PARAM_ERROR)) {
            val ex: AuthorizationException? = try {
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
        val response: TokenResponse = try {
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
        return Pair(response, null)
    }

    /**
     * Revokes [token] at Kinde's revocation endpoint (RFC 7009). Returns true when
     * Kinde accepted the request; Kinde answers 200 for tokens that are already
     * invalid too.
     */
    fun revokeRefreshToken(token: String, clientId: String): Boolean = try {
        tokenApi.revokeToken(
            LANGUAGE_HEADER.format(version),
            mapOf(
                PARAM_TOKEN to token,
                PARAM_TOKEN_TYPE_HINT to TOKEN_TYPE_REFRESH,
                TokenRequest.PARAM_CLIENT_ID to clientId
            )
        ).execute().let { response ->
            response.body()?.close()
            response.errorBody()?.close()
            response.isSuccessful
        }
    } catch (e: Exception) {
        false
    }

    private companion object {
        private const val PARAM_TOKEN = "token"
        private const val PARAM_TOKEN_TYPE_HINT = "token_type_hint"
        private const val TOKEN_TYPE_REFRESH = "refresh_token"
        private const val LANGUAGE_HEADER = "Kotlin/%s"
    }
}