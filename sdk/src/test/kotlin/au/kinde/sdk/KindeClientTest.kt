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
    fun `cancelled end-session resets logging-out flag so logout can be retried`() {
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)
        val listener = RecordingListener()
        client.attachListener(listener)

        // Simulate the browser round-trip being cancelled by the user.
        client.onEndSessionResult(Activity.RESULT_CANCELED, null)

        // Session must remain usable and a fresh logout must still be possible.
        client.logout()
        assertTrue(
            "expected logout after a cancelled end-session to complete",
            awaitCondition { listener.logoutCount == 1 }
        )
    }
}
