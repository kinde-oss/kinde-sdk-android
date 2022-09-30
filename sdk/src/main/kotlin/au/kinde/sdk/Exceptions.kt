package au.kinde.sdk

/**
 * @author roman
 * @since 1.0
 */
object NotAuthorizedException : IllegalStateException()

object WrongThreadException : IllegalStateException()

class AuthException(override val message: String?) : Exception()

class TokenException(override val message: String?) : Exception()

class LogoutException(override val message: String?) : Exception()