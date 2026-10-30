package au.kinde.sdk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallUtilTest {

    private val now = 1_800_000_000_000L
    private val kindeTokenError = """[{"Code":"TOKEN_ERROR","Message":"Missing or invalid token"}]"""

    private fun token(expSeconds: Long): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." +
            enc.encodeToString("""{"exp":$expSeconds}""".toByteArray()) + ".sig"
    }

    private val valid = token(now / 1000 + 3600)
    private val expired = token(now / 1000 - 60)

    @Test
    fun `401 always means the token was rejected`() {
        assertTrue(isRejectedToken(401, null, valid, now))
    }

    @Test
    fun `the Account API's 400 TOKEN_ERROR is a rejected token`() {
        assertTrue(isRejectedToken(400, kindeTokenError, valid, now))
    }

    @Test
    fun `TOKEN_ERROR counts only as the error code`() {
        assertTrue(isRejectedToken(400, """{"code":"TOKEN_ERROR","message":"Invalid token"}""", valid, now))
        assertTrue(isRejectedToken(400, """{"errors":[{"code":"TOKEN_ERROR"}]}""", valid, now))
        assertFalse(isRejectedToken(400, """[{"Code":"INVALID_KEY","Message":"TOKEN_ERROR is not a valid key"}]""", valid, now))
        assertFalse(isRejectedToken(400, "TOKEN_ERROR", valid, now))
    }

    @Test
    fun `a 403 from the profile endpoint with an expired token is a rejected token`() {
        assertTrue(isRejectedToken(403, "", expired, now))
    }

    @Test
    fun `a 403 for a missing scope with a valid token is not`() {
        assertFalse(isRejectedToken(403, """{"errors":[{"code":"FORBIDDEN"}]}""", valid, now))
    }

    @Test
    fun `a 400 for a bad request with a valid token is not`() {
        assertFalse(isRejectedToken(400, """{"errors":[{"code":"INVALID_KEY"}]}""", valid, now))
    }

    @Test
    fun `other statuses are never token problems`() {
        assertFalse(isRejectedToken(404, kindeTokenError, expired, now))
        assertFalse(isRejectedToken(500, kindeTokenError, expired, now))
    }

    @Test
    fun `an unreadable token doesn't count as expired`() {
        assertFalse(isRejectedToken(403, null, "not-a-jwt", now))
        assertFalse(isRejectedToken(403, null, null, now))
    }

    @Test
    fun `the token sent is read from the Authorization header`() {
        assertEquals("abc", sentBearerToken("Bearer abc"))
        assertEquals(null, sentBearerToken(null))
        assertEquals(null, sentBearerToken("Basic xyz"))
        assertEquals(null, sentBearerToken("Bearer "))
    }

    @Test
    fun `a request sent with a token since replaced is retried with the new one`() {
        assertTrue(tokenReplaced("old", "new"))
        assertFalse(tokenReplaced("same", "same"))
        assertFalse(tokenReplaced(null, "new"))
        assertFalse(tokenReplaced("old", null))
    }
}
