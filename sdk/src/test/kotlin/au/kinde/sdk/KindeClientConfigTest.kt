package au.kinde.sdk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KindeClientConfigTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetSingleton() {
        KindeClient.resetInstance()
    }

    @Test
    fun `programmatic config creates the client without any manifest meta-data`() {
        val client = KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID))

        assertEquals(TEST_DOMAIN, client.configDomain)
        assertNull(client.audience)
    }

    @Test
    fun `programmatic config wins over installed manifest values`() {
        installKindeMetaData(context, audience = TEST_AUDIENCE)

        val client = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN_US, "us_client_id")
        )

        assertEquals(TEST_DOMAIN_US, client.configDomain)
        assertNull(client.audience)
    }

    @Test
    fun `programmatic audience is used when provided`() {
        val client = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, TEST_AUDIENCE)
        )

        assertEquals(TEST_AUDIENCE, client.audience)
    }

    @Test
    fun `blank programmatic audience is treated as absent, not as a manifest fallback`() {
        installKindeMetaData(context, audience = TEST_AUDIENCE)

        val client = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, audience = "   ")
        )

        assertNull(client.audience)
    }

    @Test
    fun `invalid domain in config throws and a valid retry succeeds`() {
        assertThrows(IllegalArgumentException::class.java) {
            KindeClient.getInstance(context, KindeConfig("https://$TEST_DOMAIN", TEST_CLIENT_ID))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KindeClient.getInstance(context, KindeConfig("bad domain.invalid", TEST_CLIENT_ID))
        }

        // A failed call must not leave a half-created instance behind.
        val client = KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID))
        assertEquals(TEST_DOMAIN, client.configDomain)
    }

    @Test
    fun `invalid clientId in config throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, "client id with spaces"))
        }
    }

    @Test
    fun `structurally equal config on a later call returns the same instance`() {
        val first = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, TEST_AUDIENCE)
        )
        val second = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, TEST_AUDIENCE)
        )

        assertSame(first, second)
    }

    @Test
    fun `equivalent config differing only in audience whitespace returns the same instance`() {
        val first = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, TEST_AUDIENCE)
        )

        val padded = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, "  $TEST_AUDIENCE  ")
        )

        assertSame(first, padded)
    }

    @Test
    fun `blank audience on a later call matches an instance created without one`() {
        val first = KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID))

        val blank = KindeClient.getInstance(
            context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, audience = "   ")
        )

        assertSame(first, blank)
    }

    @Test
    fun `conflicting config on a later call throws IllegalStateException`() {
        KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID))

        assertThrows(IllegalStateException::class.java) {
            KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN_US, TEST_CLIENT_ID))
        }
        // Equality covers all three fields: an audience-only difference conflicts too.
        assertThrows(IllegalStateException::class.java) {
            KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID, TEST_AUDIENCE))
        }
    }

    @Test
    fun `getInstance without config returns the programmatically configured instance`() {
        val configured = KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN_US, TEST_CLIENT_ID))

        val resolved = KindeClient.getInstance(context)

        assertSame(configured, resolved)
        assertEquals(TEST_DOMAIN_US, resolved.configDomain)
    }

    @Test
    fun `config equal to the manifest-resolved values returns the manifest-created instance`() {
        installKindeMetaData(context)
        val fromManifest = KindeClient.getInstance(context)

        val fromConfig = KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN, TEST_CLIENT_ID))

        assertSame(fromManifest, fromConfig)
    }

    @Test
    fun `config conflicting with the manifest-created instance throws IllegalStateException`() {
        installKindeMetaData(context)
        KindeClient.getInstance(context)

        assertThrows(IllegalStateException::class.java) {
            KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN_US, TEST_CLIENT_ID))
        }
    }

    @Test
    fun `resetInstance drops the programmatic config along with the instance`() {
        KindeClient.getInstance(context, KindeConfig(TEST_DOMAIN_US, TEST_CLIENT_ID))

        KindeClient.resetInstance()
        installKindeMetaData(context)
        val client = KindeClient.getInstance(context)

        assertEquals(TEST_DOMAIN, client.configDomain)
    }

    @Test
    fun `manifest namespaced audience is read when no config is passed`() {
        installKindeMetaData(context, audience = TEST_AUDIENCE, legacyAudience = "legacy-audience")

        val client = KindeClient.getInstance(context)

        assertEquals(TEST_AUDIENCE, client.audience)
    }

    @Test
    fun `legacy audience key is used when the namespaced key is absent`() {
        installKindeMetaData(context, legacyAudience = "legacy-audience")

        val client = KindeClient.getInstance(context)

        assertEquals("legacy-audience", client.audience)
    }
}
