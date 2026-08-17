package au.kinde.sdk.store

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `state round-trips through encryption`() {
        val store = Store(context, "domain-a.kinde.com")

        store.saveState("""{"auth":"state"}""")

        assertEquals("""{"auth":"state"}""", store.getState())
    }

    @Test
    fun `clearState removes stored state`() {
        val store = Store(context, "domain-a.kinde.com")
        store.saveState("some-state")

        store.clearState()

        assertNull(store.getState())
    }

    @Test
    fun `keys round-trip through encryption`() {
        val store = Store(context, "domain-a.kinde.com")

        store.saveKeys("""{"keys":[]}""")

        assertEquals("""{"keys":[]}""", store.getKeys())
    }

    @Test
    fun `stores for different domains are isolated`() {
        val storeA = Store(context, "domain-a.kinde.com")
        val storeB = Store(context, "domain-b.kinde.com")

        storeA.saveState("state-for-a")

        assertNull(storeB.getState())
        assertEquals("state-for-a", storeA.getState())
    }

    @Test
    fun `same domain shares one store`() {
        val first = Store(context, "domain-a.kinde.com")
        val second = Store(context, "domain-a.kinde.com")

        first.saveState("shared-state")

        assertEquals("shared-state", second.getState())
    }
}
