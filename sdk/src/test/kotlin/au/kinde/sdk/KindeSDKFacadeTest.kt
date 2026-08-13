package au.kinde.sdk

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.test.core.app.ApplicationProvider
import au.kinde.sdk.model.TokenType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KindeSDKFacadeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetSingleton() {
        KindeClient.resetInstance()
        installKindeMetaData(context)
    }

    private fun buildSdk(listener: SDKListener): Pair<org.robolectric.android.controller.ActivityController<ComponentActivity>, KindeSDK> {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.create()
        val sdk = KindeSDK(
            activity = controller.get(),
            loginRedirect = "test.scheme://callback",
            logoutRedirect = "test.scheme://logout",
            sdkListener = listener
        )
        return controller to sdk
    }

    @Test
    fun `construction replays onLogout to its listener when no session exists`() {
        val listener = RecordingListener()

        buildSdk(listener)

        assertEquals(1, listener.logoutCount)
        assertEquals(0, listener.tokens.size)
    }

    @Test
    fun `facade delegates token reads to the shared core`() {
        val listener = RecordingListener()
        val (_, sdk) = buildSdk(listener)

        assertNull(sdk.getToken(TokenType.ACCESS_TOKEN))
        assertNull(sdk.getRefreshToken())
        assertFalse(sdk.isAuthenticated())
    }

    @Test
    fun `two facades share one core and both listeners hear core events`() {
        val listenerA = RecordingListener()
        val listenerB = RecordingListener()
        buildSdk(listenerA)
        buildSdk(listenerB)
        val initialA = listenerA.logoutCount
        val initialB = listenerB.logoutCount

        KindeClient.getInstance(context).onEndSessionResult(Activity.RESULT_OK, null)

        assertEquals(initialA + 1, listenerA.logoutCount)
        assertEquals(initialB + 1, listenerB.logoutCount)
    }

    @Test
    fun `destroying the activity detaches its listener while others keep receiving`() {
        val listenerA = RecordingListener()
        val listenerB = RecordingListener()
        val (controllerA, _) = buildSdk(listenerA)
        buildSdk(listenerB)

        controllerA.destroy()
        val countAAfterDestroy = listenerA.logoutCount
        val initialB = listenerB.logoutCount

        KindeClient.getInstance(context).onEndSessionResult(Activity.RESULT_OK, null)

        assertEquals(countAAfterDestroy, listenerA.logoutCount)
        assertEquals(initialB + 1, listenerB.logoutCount)
    }

    @Test
    fun `core state survives activity recreation`() {
        val listenerA = RecordingListener()
        val (controllerA, _) = buildSdk(listenerA)
        val coreBefore = KindeClient.getInstance(context)

        // Simulate a configuration change: old activity destroyed, new one created.
        controllerA.destroy()
        val listenerB = RecordingListener()
        buildSdk(listenerB)

        org.junit.Assert.assertSame(coreBefore, KindeClient.getInstance(context))
    }
}
