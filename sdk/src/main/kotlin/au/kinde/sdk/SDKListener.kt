package au.kinde.sdk

/**
 * Session events from the SDK.
 *
 * Callbacks can arrive on any thread: [onNewToken] after a sign-in or a refresh comes
 * from a background thread, while other events may come from the main thread. Switch
 * to the main thread before touching UI, for example with `runOnUiThread { … }` or by
 * posting the value to a `StateFlow`/`LiveData`. Keep the callbacks short; the SDK
 * calls them while it is processing the event.
 *
 * @author roman
 * @since 1.0
 */
interface SDKListener {
    /** A new access token: after a sign-in, a refresh, or when a saved session is restored. */
    fun onNewToken(token: String)

    /** The user is signed out, or there was no session to restore. */
    fun onLogout()

    /** Something failed; see the exception type (for example [WrongThreadException]). */
    fun onException(exception: Exception)
}