package au.kinde.sdk

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * The OpenID Connect Core 3.1.3.7 ID token checks that AppAuth applies when it
 * exchanges a code itself. The SDK uses its own token exchange, so it runs them
 * here. The token arrives directly from the token endpoint over TLS, which
 * OIDC accepts in place of verifying the signature (rule 6).
 */
internal object IdTokenValidator {
    /** Allowance for device clock drift. */
    private const val CLOCK_SKEW_SECONDS = 120L

    /** iat must be within this window of now (AppAuth's rule 10 window). */
    private const val MAX_IAT_AGE_SECONDS = 600L

    /**
     * @return null when the token is valid, otherwise why it isn't.
     * @param expectedNonce the authorization request's nonce for a code exchange;
     *   null for refreshes, where OIDC doesn't require it.
     */
    fun validate(
        idToken: String,
        expectedIssuer: String,
        clientId: String,
        expectedNonce: String?,
        nowMillis: Long
    ): String? {
        val claims = try {
            val parts = idToken.split('.')
            if (parts.size != 3) return "ID token is not a JWT"
            JSONObject(String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP), Charsets.UTF_8))
        } catch (e: Exception) {
            return "ID token payload is not readable"
        }

        val issuer = claims.optString("iss")
        if (issuer.trimEnd('/') != expectedIssuer.trimEnd('/')) {
            return "ID token issuer '$issuer' does not match '${expectedIssuer.trimEnd('/')}'"
        }

        val audiences = when (val aud = claims.opt("aud")) {
            is String -> listOf(aud)
            is JSONArray -> (0 until aud.length()).map { aud.optString(it) }
            else -> emptyList()
        }
        if (clientId !in audiences && claims.optString("azp") != clientId) {
            return "ID token audience does not include this client"
        }

        val now = nowMillis / 1000
        if (!claims.has("exp")) return "ID token has no expiry"
        if (now > claims.getLong("exp") + CLOCK_SKEW_SECONDS) return "ID token has expired"

        if (!claims.has("iat")) return "ID token has no issue time"
        if (Math.abs(now - claims.getLong("iat")) > MAX_IAT_AGE_SECONDS + CLOCK_SKEW_SECONDS) {
            return "ID token issue time is more than 10 minutes from the device clock"
        }

        if (expectedNonce != null && claims.optString("nonce", null) != expectedNonce) {
            return "ID token nonce does not match the sign-in request"
        }
        return null
    }
}
