package au.kinde.sdk

/**
 * @author roman
 * @since 1.0
 */
interface SDKListener {
    fun onNewToken(token: String)

    fun onLogout()

    fun onException(exception: Exception)
}