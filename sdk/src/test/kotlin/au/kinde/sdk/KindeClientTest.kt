package au.kinde.sdk

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import au.kinde.sdk.model.TokenType

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KindeClientTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetSingleton() {
        KindeClient.resetInstance()
    }

    @Test
    fun `getInstance returns the same instance on every call`() {
        installKindeMetaData(context)

        val first = KindeClient.getInstance(context)
        val second = KindeClient.getInstance(context)

        assertSame(first, second)
    }

    @Test
    fun `construction fails fast when domain meta-data is missing`() {
        installKindeMetaData(context, domain = null)

        assertThrows(IllegalStateException::class.java) {
            KindeClient.getInstance(context)
        }
    }

    @Test
    fun `construction fails fast when clientId meta-data is missing`() {
        installKindeMetaData(context, clientId = null)

        assertThrows(IllegalStateException::class.java) {
            KindeClient.getInstance(context)
        }
    }

    @Test
    fun `config domain is read and trimmed from meta-data`() {
        installKindeMetaData(context, domain = "  $TEST_DOMAIN  ")

        val client = KindeClient.getInstance(context)

        assertEquals(TEST_DOMAIN, client.configDomain)
    }

    @Test
    fun `getToken and isAuthenticated report signed-out state with empty store`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)

        assertNull(client.getToken(TokenType.ACCESS_TOKEN))
        assertNull(client.getToken(TokenType.ID_TOKEN))
        assertNull(client.getRefreshToken())
        assertFalse(client.isAuthenticated())
    }

    @Test
    fun `initial state replay fires onLogout for empty store`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()

        client.notifyInitialState(listener)

        assertEquals(1, listener.logoutCount)
        assertTrue(listener.tokens.isEmpty())
    }

    @Test
    fun `events broadcast to all attached listeners`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listenerA = RecordingListener()
        val listenerB = RecordingListener()
        client.attachListener(listenerA)
        client.attachListener(listenerB)

        // RESULT_OK on the end-session launcher is a completed logout.
        client.onEndSessionResult(Activity.RESULT_OK, null)

        assertEquals(1, listenerA.logoutCount)
        assertEquals(1, listenerB.logoutCount)
    }

    @Test
    fun `detached listener stops receiving events`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listenerA = RecordingListener()
        val listenerB = RecordingListener()
        client.attachListener(listenerA)
        client.attachListener(listenerB)

        client.detachListener(listenerB)
        client.onEndSessionResult(Activity.RESULT_OK, null)

        assertEquals(1, listenerA.logoutCount)
        assertEquals(0, listenerB.logoutCount)
    }

    @Test
    fun `logout with no attached launcher clears the session locally`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()
        client.attachListener(listener)

        client.logout()

        assertTrue(
            "expected local logout to complete and notify onLogout",
            awaitCondition { listener.logoutCount == 1 }
        )
        assertFalse(client.isAuthenticated())
        // A second logout must work: the isLoggingOut flag has to be reset.
        client.logout()
        assertTrue(
            "expected a second logout to complete (isLoggingOut was reset)",
            awaitCondition { listener.logoutCount == 2 }
        )
    }

    @Test
    fun `back navigation keeps an earlier facade's end-session launcher available`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val launcherA = object : KindeClient.EndSessionLauncher {
            override fun launchEndSession(intent: android.content.Intent) = Unit
        }
        val launcherB = object : KindeClient.EndSessionLauncher {
            override fun launchEndSession(intent: android.content.Intent) = Unit
        }

        // Activity A attaches, activity B attaches on top, then B is destroyed
        // (back navigation). A's launcher must remain the active fallback.
        client.attachEndSessionLauncher(launcherA, "test.scheme://logout-a")
        client.attachEndSessionLauncher(launcherB, "test.scheme://logout-b")
        client.detachEndSessionLauncher(launcherB)

        assertSame(launcherA, client.activeEndSessionLauncher())
    }

    @Test
    fun `cancelled invitation flow can be retried`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        client.invitationState.startHandling("invite-1")

        client.onAuthorizationResult(Activity.RESULT_CANCELED, null)

        assertFalse(client.invitationState.isHandling)
        assertFalse(client.invitationState.isProcessed("invite-1"))
    }

    @Test
    fun `cancelled login notifies only the initiating listener`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val initiator = RecordingListener()
        val bystander = RecordingListener()
        client.attachListener(initiator)
        client.attachListener(bystander)

        client.onAuthorizationResult(Activity.RESULT_CANCELED, null, initiator)

        assertEquals(1, initiator.logoutCount)
        assertEquals(0, bystander.logoutCount)
    }

    @Test
    fun `logout completes locally when the browser launch fails`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()
        client.attachListener(listener)
        // Robolectric has no browser installed, so building the end-session
        // intent throws inside AppAuth — the launch must fail, and logout must
        // still complete instead of staying pending forever.
        val launcher = object : KindeClient.EndSessionLauncher {
            override fun launchEndSession(intent: android.content.Intent) = Unit
        }
        client.attachEndSessionLauncher(launcher, "test.scheme://logout")

        client.logout()

        assertTrue(
            "expected logout to fall back to local completion",
            awaitCondition { listener.logoutCount == 1 }
        )
        assertFalse(client.isLogoutInProgress())
    }

    @Test
    fun `logout stuck on a lost end-session result can be retried`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()
        client.attachListener(listener)
        // Simulate a logout whose browser result was lost long ago.
        client.markLoggingOutForTest(startedAtMs = 0L)

        client.logout()

        assertTrue(
            "expected retried logout to complete after the stale one",
            awaitCondition { listener.logoutCount == 1 }
        )
        assertFalse(client.isLogoutInProgress())
    }

    @Test
    fun `recent pending logout is not restarted`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()
        client.attachListener(listener)
        client.markLoggingOutForTest(startedAtMs = System.currentTimeMillis())

        client.logout()

        // The first logout is still within its result window; the second call
        // must not tear the session down underneath it.
        awaitCondition(timeoutMs = 250) { false }
        assertEquals(0, listener.logoutCount)
        assertTrue(client.isLogoutInProgress())
    }

    @Test
    fun `attaching a facade clears stale logout state`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        client.markLoggingOutForTest(startedAtMs = 0L)

        client.notifyInitialState(RecordingListener())

        assertFalse(client.isLogoutInProgress())
    }

    @Test
    fun `refresh is skipped only when another refresh rotated the token mid-wait`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)

        // A concurrent refresh rotated the token: replaying the old one would be
        // treated as reuse, so the stale request must be skipped.
        assertTrue(client.shouldSkipStaleRefresh("old-token", "rotated-token"))
        // Same token means the earlier refresh failed or didn't rotate: retrying
        // with it is the correct behavior.
        assertFalse(client.shouldSkipStaleRefresh("same-token", "same-token"))
        // Missing either side (fresh login, cleared session): proceed and let the
        // normal error handling decide.
        assertFalse(client.shouldSkipStaleRefresh(null, "rotated-token"))
        assertFalse(client.shouldSkipStaleRefresh("old-token", null))
    }

    @Test
    fun `cancelled end-session resets logging-out flag so logout can be retried`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()
        client.attachListener(listener)
        // A logout must actually be pending for the cancellation to have state to
        // clear; a recent timestamp keeps the stale-recovery path out of play.
        client.markLoggingOutForTest(startedAtMs = System.currentTimeMillis())

        // Simulate the browser round-trip being cancelled by the user.
        client.onEndSessionResult(Activity.RESULT_CANCELED, null)

        assertFalse(client.isLogoutInProgress())

        // Session must remain usable and a fresh logout must still be possible.
        // This only works if the cancellation cleared the flag: the pending logout
        // was recent, so the stale-recovery path would not let a retry through.
        client.logout()
        assertTrue(
            "expected logout after a cancelled end-session to complete",
            awaitCondition { listener.logoutCount == 1 }
        )
    }
}
