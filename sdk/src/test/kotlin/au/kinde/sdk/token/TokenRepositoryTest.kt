package au.kinde.sdk.token

import android.net.Uri
import au.kinde.sdk.infrastructure.ApiClient
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TokenRepositoryTest {

    private val config = AuthorizationServiceConfiguration(
        Uri.parse("https://acme.kinde.com/oauth2/auth"), Uri.parse("https://acme.kinde.com/oauth2/token")
    )
    private val refresh: TokenRequest = TokenRequest.Builder(config, "client_123")
        .setGrantType("refresh_token").setRefreshToken("rt_1").build()

    /** A repository whose HTTP layer answers with [respond], so no server is needed. */
    private fun repository(respond: (okhttp3.Request) -> Response): TokenRepository {
        val fake = Interceptor { chain -> respond(chain.request()) }
        val api = ApiClient("https://acme.kinde.com/", OkHttpClient.Builder().addInterceptor(fake)).createService(TokenApi::class.java)
        return TokenRepository(api, "test")
    }

    private fun reply(request: okhttp3.Request, code: Int, body: String, type: String = "application/json") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("status $code")
        .body(body.toResponseBody(type.toMediaType())).build()

    /** Robolectric's test thread is the main looper thread; token requests refuse to run there. */
    private fun call(repo: TokenRepository): Pair<TokenResponse?, AuthorizationException?> {
        var result: Pair<TokenResponse?, AuthorizationException?>? = null
        Thread { result = repo.getToken(AuthState(config), refresh) }.apply { start(); join() }
        return result!!
    }

    @Test
    fun `a rejected refresh token is reported as invalid_grant`() {
        val (resp, ex) = call(repository { reply(it, 400, """{"error":"invalid_grant","error_description":"The refresh token is invalid."}""") })

        assertNull(resp)
        assertEquals("invalid_grant", ex?.error)
        assertEquals("The refresh token is invalid.", ex?.errorDescription)
    }

    @Test
    fun `other OAuth errors in a 4xx body are reported by name`() {
        val (_, ex) = call(repository { reply(it, 401, """{"error":"invalid_client"}""") })

        assertEquals("invalid_client", ex?.error)
    }

    @Test
    fun `a server error without an OAuth body carries the HTTP status`() {
        val (resp, ex) = call(repository { reply(it, 503, "<html>down</html>", "text/html") })

        assertNull(resp)
        assertNull(ex?.error)
        assertEquals(503, ex?.code)
    }

    @Test
    fun `no response at all is a general error`() {
        val (resp, ex) = call(repository { throw IOException("offline") })

        assertNull(resp)
        assertNull(ex?.error)
        assertEquals(AuthorizationException.TYPE_GENERAL_ERROR, ex?.type)
    }

    @Test
    fun `a successful response is parsed and sends the SDK header and client ID`() {
        var sent: okhttp3.Request? = null
        val (resp, ex) = call(repository {
            sent = it
            reply(it, 200, """{"access_token":"at_2","refresh_token":"rt_2","token_type":"bearer","expires_in":3600}""")
        })

        assertNull(ex)
        assertEquals("at_2", resp?.accessToken)
        assertEquals("rt_2", resp?.refreshToken)
        assertEquals("Kotlin/test", sent?.header("Kinde-SDK"))
        val form = okio.Buffer().also { sent!!.body!!.writeTo(it) }.readUtf8()
        assertNotNull(Regex("client_id=client_123").find(form))
    }

    @Test
    fun `token requests on the main thread are refused`() {
        val (resp, ex) = repository { reply(it, 200, "{}") }.getToken(AuthState(config), refresh)

        assertNull(resp)
        assertNotNull(ex)
    }
}
