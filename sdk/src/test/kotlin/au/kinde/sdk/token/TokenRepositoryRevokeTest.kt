package au.kinde.sdk.token

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Retrofit
import java.io.IOException

class TokenRepositoryRevokeTest {

    private val requests = mutableListOf<Request>()

    private fun repository(answer: (Request) -> Response): TokenRepository {
        val http = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain -> chain.request().also { requests += it }.let(answer) })
            .build()
        val retrofit = Retrofit.Builder().baseUrl("https://example.kinde.com/").client(http).build()
        return TokenRepository(retrofit.create(TokenApi::class.java), "test")
    }

    private fun reply(request: Request, code: Int, body: String = "") = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("")
        .body(body.toResponseBody("application/json".toMediaType()))
        .build()

    private fun Request.form(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()

    @Test
    fun `revoke posts the refresh token to Kinde's revocation endpoint`() {
        val result = repository { reply(it, 200) }.revokeRefreshToken("refresh-123", "client-abc")

        assertEquals(TokenRepository.Revocation.REVOKED, result)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://example.kinde.com/oauth2/revoke", request.url.toString())
        assertEquals("Kotlin/test", request.header("Kinde-SDK"))
        assertEquals(
            "token=refresh-123&token_type_hint=refresh_token&client_id=client-abc",
            request.form()
        )
    }

    @Test
    fun `a refused revocation is not worth retrying`() {
        val result = repository { reply(it, 401, """{"error":"invalid_client"}""") }
            .revokeRefreshToken("refresh-123", "client-abc")

        assertEquals(TokenRepository.Revocation.REJECTED, result)
    }

    @Test
    fun `a server error is worth retrying`() {
        val result = repository { reply(it, 503) }.revokeRefreshToken("refresh-123", "client-abc")

        assertEquals(TokenRepository.Revocation.FAILED, result)
    }

    @Test
    fun `a network failure is worth retrying, and doesn't throw`() {
        val result = repository { throw IOException("offline") }
            .revokeRefreshToken("refresh-123", "client-abc")

        assertEquals(TokenRepository.Revocation.FAILED, result)
    }
}
