package au.kinde.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IdTokenValidatorTest {

    private val now = 1_800_000_000_000L
    private val issuer = "https://acme.kinde.com"
    private val client = "client_123"

    private fun token(vararg claims: Pair<String, Any?>): String {
        val base = mutableMapOf<String, Any?>(
            "iss" to issuer, "aud" to listOf(client), "exp" to now / 1000 + 3600,
            "iat" to now / 1000, "nonce" to "n-1", "sub" to "kp_1"
        )
        claims.forEach { (k, v) -> if (v == null) base.remove(k) else base[k] = v }
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        val payload = JSONObject(base.mapValues { (_, v) -> if (v is List<*>) org.json.JSONArray(v) else v }).toString()
        return enc.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." + enc.encodeToString(payload.toByteArray()) + ".sig"
    }

    private fun check(t: String, nonce: String? = "n-1", expectedIssuer: String = issuer) =
        IdTokenValidator.validate(t, expectedIssuer, client, nonce, now)

    @Test
    fun `a valid token passes`() {
        assertNull(check(token()))
    }

    @Test
    fun `a trailing slash on either issuer is ignored`() {
        assertNull(check(token("iss" to "$issuer/")))
        assertNull(check(token(), expectedIssuer = "$issuer/"))
    }

    @Test
    fun `audience may be a string or a list containing the client`() {
        assertNull(check(token("aud" to client)))
        assertNull(check(token("aud" to listOf(client, "other"), "azp" to client)))
    }

    @Test
    fun `azp does not replace the client in aud`() {
        assertTrue(check(token("aud" to listOf("other"), "azp" to client))!!.contains("audience"))
    }

    @Test
    fun `several audiences need azp naming the client`() {
        assertTrue(check(token("aud" to listOf(client, "other")))!!.contains("authorized party"))
    }

    @Test
    fun `wrong issuer is rejected`() {
        assertTrue(check(token("iss" to "https://evil.example.com"))!!.contains("issuer"))
    }

    @Test
    fun `a token for another client is rejected`() {
        assertTrue(check(token("aud" to listOf("someone_else")))!!.contains("audience"))
    }

    @Test
    fun `expired tokens are rejected, within clock skew they pass`() {
        assertTrue(check(token("exp" to now / 1000 - 300))!!.contains("expired"))
        assertNull(check(token("exp" to now / 1000 - 60)))
    }

    @Test
    fun `missing exp or iat is rejected`() {
        assertEquals("ID token has no numeric expiry", check(token("exp" to null)))
        assertEquals("ID token has no numeric issue time", check(token("iat" to null)))
    }

    @Test
    fun `non-numeric exp or iat is rejected without throwing`() {
        assertEquals("ID token has no numeric expiry", check(token("exp" to "abc")))
        assertEquals("ID token has no numeric issue time", check(token("iat" to "abc")))
    }

    @Test
    fun `an issue time far from the device clock is rejected`() {
        assertTrue(check(token("iat" to now / 1000 - 3600))!!.contains("issue time"))
    }

    @Test
    fun `nonce must match on sign-in`() {
        assertTrue(check(token("nonce" to "replayed"))!!.contains("nonce"))
        assertTrue(check(token("nonce" to null))!!.contains("nonce"))
    }

    @Test
    fun `nonce is not required for refreshes`() {
        assertNull(check(token("nonce" to null), nonce = null))
    }

    @Test
    fun `garbage is rejected`() {
        assertEquals("ID token is not a JWT", check("not-a-jwt"))
        assertEquals("ID token payload is not readable", check("a.%%%.c"))
    }
}
