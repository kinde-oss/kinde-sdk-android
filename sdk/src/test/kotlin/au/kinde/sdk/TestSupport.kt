package au.kinde.sdk

import android.content.Context
import android.os.Bundle
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.Shadows.shadowOf

/**
 * Listener that records every callback so tests can assert on what reached it.
 * Thread-safe: the client notifies from background threads while tests read
 * from the test thread. onException is recorded but never asserted against
 * zero: the client's init fires an async JWKS fetch at an unroutable test
 * domain, which legitimately reports a failure.
 */
class RecordingListener : SDKListener {
    val tokens = CopyOnWriteArrayList<String>()
    val exceptions = CopyOnWriteArrayList<Exception>()
    private val logouts = AtomicInteger(0)

    val logoutCount: Int
        get() = logouts.get()

    override fun onNewToken(token: String) {
        tokens.add(token)
    }

    override fun onLogout() {
        logouts.incrementAndGet()
    }

    override fun onException(exception: Exception) {
        exceptions.add(exception)
    }
}

/** The .invalid TLD is guaranteed unresolvable, so init's keys fetch fails harmlessly offline. */
const val TEST_DOMAIN = "unit-test.kinde.invalid"
const val TEST_CLIENT_ID = "test_client_id"

/** Injects the manifest meta-data KindeClient's constructor requires. */
fun installKindeMetaData(
    context: Context,
    domain: String? = TEST_DOMAIN,
    clientId: String? = TEST_CLIENT_ID
) {
    val bundle = Bundle().apply {
        domain?.let { putString(KindeClient.DOMAIN_KEY, it) }
        clientId?.let { putString(KindeClient.CLIENT_ID_KEY, it) }
    }
    shadowOf(context.packageManager)
        .getInternalMutablePackageInfo(context.packageName)
        .applicationInfo!!
        .metaData = bundle
}

/**
 * Runs main-looper tasks and polls until [condition] is true or [timeoutMs]
 * elapses. Needed for flows that hop background thread -> main handler
 * (e.g. logout).
 */
fun awaitCondition(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return true
        Thread.sleep(20)
    }
    return condition()
}
