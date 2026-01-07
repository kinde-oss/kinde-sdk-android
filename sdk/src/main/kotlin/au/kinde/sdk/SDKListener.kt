package au.kinde.sdk

interface SDKListener {
    fun onNewToken(token: String)

    fun onLogout()

    fun onException(exception: Exception)
}