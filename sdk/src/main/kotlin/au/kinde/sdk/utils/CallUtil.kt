package au.kinde.sdk

import android.os.Looper
import net.openid.appauth.AuthState
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
            Pair(
                null, if (response.code() == HttpURLConnection.HTTP_FORBIDDEN && !refreshed) {
                    TokenExpiredException
                } else {
                    Exception(
                        "response is unsuccessful for ${
                            this.request().url()
                        }:${response.code()} ${response.message()}"
                    )
                }
            )
        }
    } catch (e: Exception) {
        return Pair(null, e)
    }
}