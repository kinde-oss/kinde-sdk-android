package au.kinde.sdk.model

/**
 * @author roman
 * @since 1.0
 */
data class UserDetails(
    val id: String,
    val givenName: String,
    val familyName: String,
    val email: String
)