package au.kinde.sdk

/**
 * @author roman
 * @since 1.0
 */
object NotAuthorizedException : IllegalStateException()

object TokenExpiredException : IllegalStateException()

/** The request was rejected with a token that a refresh has since replaced; retry without refreshing. */
internal object TokenReplacedException : IllegalStateException()

object WrongThreadException : IllegalStateException()

class AuthException(override val message: String?) : Exception()

class TokenException(override val message: String?) : Exception()

class LogoutException(override val message: String?) : Exception()