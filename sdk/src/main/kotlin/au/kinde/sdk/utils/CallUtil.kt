package au.kinde.sdk.utils

import android.os.Looper
import au.kinde.sdk.NotAuthorizedException
import au.kinde.sdk.TokenExpiredException
import au.kinde.sdk.WrongThreadException
import net.openid.appauth.AuthState
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Call
import java.net.HttpURLConnection

fun <T> Call<T>.callApi(
    state: AuthState,
    withoutAuthorization: Boolean = false,
    refreshed: Boolean = false
): Pair<T?, Exception?> {
    if (state.accessToken.isNullOrEmpty() && !withoutAuthorization) {
        return Pair(null, NotAuthorizedException)
    }
    if (Thread.currentThread() == Looper.getMainLooper().thread) {
        return Pair(null, WrongThreadException)
    }
    try {
        val response = this.execute()
        return if (response.isSuccessful) {
            Pair(response.body(), null)
        } else {
            val errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
            Pair(
                null, if (!refreshed && !withoutAuthorization &&
                    isRejectedToken(response.code(), errorBody, state.accessToken, System.currentTimeMillis())
                ) {
                    TokenExpiredException
                } else {
                    Exception(
                        "response is unsuccessful for ${
                            this.request().url
                        }:${response.code()} ${response.message()}"
                    )
                }
            )
        }
    } catch (e: Exception) {
        return Pair(null, e)
    }
}

/**
 * Whether a failed API response means the access token was rejected, so a refresh
 * and one retry can fix it. Kinde's endpoints answer an expired or invalid token
 * differently: the Account API with 400 `TOKEN_ERROR`, the OAuth profile endpoints
 * with 403, others with the standard 401. A 400/403 for any other reason (a missing
 * scope or permission, a bad parameter) is not a token problem, so it is only
 * treated as one when the token has actually expired.
 */
internal fun isRejectedToken(code: Int, errorBody: String?, accessToken: String?, nowMillis: Long): Boolean = when (code) {
    HttpURLConnection.HTTP_UNAUTHORIZED -> true
    HttpURLConnection.HTTP_BAD_REQUEST, HttpURLConnection.HTTP_FORBIDDEN ->
        hasTokenErrorCode(errorBody) || isExpired(accessToken, nowMillis)
    else -> false
}

/**
 * True when Kinde's error body carries the code TOKEN_ERROR, as in
 * `[{"Code":"TOKEN_ERROR","Message":"…"}]`. Only the code is compared: the same
 * words in a message don't count.
 */
private fun hasTokenErrorCode(errorBody: String?): Boolean {
    val text = errorBody?.trim().orEmpty()
    fun JSONObject.code() = optString("Code").ifEmpty { optString("code") }
    fun JSONArray.objects() = (0 until length()).mapNotNull { optJSONObject(it) }
    val errors = try {
        when {
            text.startsWith("[") -> JSONArray(text).objects()
            text.startsWith("{") -> JSONObject(text).let { listOf(it) + (it.optJSONArray("errors")?.objects() ?: emptyList()) }
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
    return errors.any { it.code() == TOKEN_ERROR_CODE }
}

private const val TOKEN_ERROR_CODE = "TOKEN_ERROR"

private fun isExpired(accessToken: String?, nowMillis: Long): Boolean {
    val payload = accessToken?.split('.')?.getOrNull(1) ?: return false
    return try {
        val claims = org.json.JSONObject(
            String(android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP), Charsets.UTF_8)
        )
        val exp = (claims.opt("exp") as? Number)?.toLong() ?: return false
        nowMillis / 1000 >= exp
    } catch (e: Exception) {
        false
    }
}
