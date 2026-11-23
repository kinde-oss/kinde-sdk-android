package au.kinde.sdk.utils

import android.util.Base64
import au.kinde.sdk.api.model.FeatureFlagsResponse
import au.kinde.sdk.model.TokenType
import com.google.gson.Gson
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClaimDelegateTest {

    private fun jwt(claims: Map<String, Any?>): String {
        fun b64(s: String) = Base64.encodeToString(s.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return "${b64("""{"alg":"RS256"}""")}.${b64(JSONObject(claims).toString())}.sig"
    }

    private val claims = mapOf(
        "exp" to 1_900_000_000L,
        "email_verified" to true,
        "org_code" to "org_1",
        "permissions" to listOf("read", "write"),
        "roles" to listOf(mapOf("id" to "r1", "key" to "admin", "name" to "Admin")),
        "x_hasura" to null,
        "feature_flags" to mapOf(
            "theme" to mapOf("t" to "s", "v" to "dark"),
            "max_items" to mapOf("t" to "i", "v" to 25),
        ),
    )

    @Before
    fun tokens() {
        ClaimDelegate.tokenProvider = object : TokenProvider {
            override fun getToken(tokenType: TokenType): String = jwt(claims)
        }
    }

    @After
    fun reset() {
        ClaimDelegate.tokenProvider = null
    }

    @Test
    fun `getClaimValue keeps each claim's JSON type`() {
        assertEquals(1_900_000_000, ClaimDelegate.getClaimValue("exp"))
        assertEquals(true, ClaimDelegate.getClaimValue("email_verified"))
        assertEquals("org_1", ClaimDelegate.getClaimValue("org_code"))
        assertEquals(listOf("read", "write"), ClaimDelegate.getClaimValue("permissions"))
        assertEquals(listOf(mapOf("id" to "r1", "key" to "admin", "name" to "Admin")), ClaimDelegate.getClaimValue("roles"))
        assertNull(ClaimDelegate.getClaimValue("x_hasura"))
        assertNull(ClaimDelegate.getClaimValue("not_a_claim"))
    }

    @Test
    fun `getClaim still returns text`() {
        assertEquals("1900000000", ClaimDelegate.getClaim("exp").value)
    }

    @Test
    fun `getClaimValue is null without a token`() {
        ClaimDelegate.tokenProvider = null
        assertNull(ClaimDelegate.getClaimValue("exp", TokenType.ID_TOKEN))
    }

    @Test
    fun `a token flag of the wrong type falls back to the default`() {
        assertEquals(true, ClaimDelegate.getBooleanFlag("theme", true))
        assertNull(ClaimDelegate.getBooleanFlag("theme"))
        assertEquals("dark", ClaimDelegate.getStringFlag("theme", "light"))
    }

    @Test
    fun `integer flags are Ints from the token and from the API`() {
        val api = Gson().fromJson(
            """{"success":true,"data":{"feature_flags":[{"key":"max_items","type":"Integer","value":25}]}}""",
            FeatureFlagsResponse::class.java
        ).toFlagMap()

        assertTrue(api.getValue("max_items").value is Int)
        assertEquals(ClaimDelegate.getAllFlags().getValue("max_items").value, api.getValue("max_items").value)
    }
}
