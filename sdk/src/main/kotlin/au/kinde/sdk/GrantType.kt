package au.kinde.sdk

/**
 * How the authorization code is protected.
 *
 * PKCE (S256) is used by default: pass `null` or [PKCE]. The SDK performs the
 * code exchange itself, so PKCE needs nothing from the app.
 *
 * @author roman
 * @since 1.0
 */
enum class GrantType {
    PKCE,

    /** Disables PKCE. Native apps should always use PKCE (RFC 8252). */
    @Deprecated("Disables PKCE, which native apps should always use (RFC 8252). Omit the grant type to use PKCE.")
    NONE
}
