package au.kinde.sdk.utils

import au.kinde.sdk.model.TokenType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClaimDelegateTest {

    private fun jwt(claims: Map<String, Any?>): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        fun json(v: Any?): Any? = when (v) {
            is Map<*, *> -> JSONObject().apply { v.forEach { (k, x) -> put(k.toString(), json(x)) } }
            is List<*> -> JSONArray().apply { v.forEach { put(json(it)) } }
            else -> v
        }
        return enc.encodeToString("""{"alg":"RS256"}""".toByteArray()) + "." +
            enc.encodeToString(json(claims).toString().toByteArray()) + ".sig"
    }

    private fun signIn(access: Map<String, Any?>, id: Map<String, Any?> = emptyMap()) {
        val tokens = mapOf(TokenType.ACCESS_TOKEN to jwt(access), TokenType.ID_TOKEN to jwt(id))
        ClaimDelegate.tokenProvider = object : TokenProvider {
            override fun getToken(tokenType: TokenType) = tokens[tokenType]
        }
    }

    @After
    fun signOut() {
        ClaimDelegate.tokenProvider = null
    }

    private val kindeRoles = listOf(
        mapOf("id" to "0191-a", "key" to "role_admin", "name" to "Admin"),
        mapOf("id" to "0191-b", "key" to "role_read", "name" to "Read"),
    )

    @Test
    fun `roles in Kinde's object format are read by key`() {
        signIn(mapOf("org_code" to "org_1", "roles" to kindeRoles))

        val roles = ClaimDelegate.getRoles()

        assertEquals(listOf("role_admin", "role_read"), roles.roles)
        assertEquals("org_1", roles.orgCode)
    }

    @Test
    fun `getRole finds a role in Kinde's object format`() {
        signIn(mapOf("roles" to kindeRoles))

        assertTrue(ClaimDelegate.getRole("role_admin").isGranted)
        assertFalse(ClaimDelegate.getRole("owner").isGranted)
    }

    @Test
    fun `roles as plain strings still work`() {
        signIn(mapOf("roles" to listOf("admin", "editor")))

        assertEquals(listOf("admin", "editor"), ClaimDelegate.getRoles().roles)
        assertTrue(ClaimDelegate.getRole("editor").isGranted)
    }

    @Test
    fun `role entries without a key are skipped`() {
        signIn(mapOf("roles" to listOf(mapOf("id" to "x", "name" to "Nameless"), mapOf("key" to "role_read"))))

        assertEquals(listOf("role_read"), ClaimDelegate.getRoles().roles)
    }

    @Test
    fun `permissions and organizations are unchanged`() {
        signIn(
            mapOf("permissions" to listOf("read", "write"), "roles" to kindeRoles),
            mapOf("org_codes" to listOf("org_1", "org_2"))
        )

        assertEquals(listOf("read", "write"), ClaimDelegate.getPermissions().permissions)
        assertTrue(ClaimDelegate.getPermission("write").isGranted)
        assertEquals(listOf("org_1", "org_2"), ClaimDelegate.getUserOrganizations().orgCodes)
    }
}
