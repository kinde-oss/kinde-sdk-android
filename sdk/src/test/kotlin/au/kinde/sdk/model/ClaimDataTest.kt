package au.kinde.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ClaimData] sealed class and its subclasses.
 */
class ClaimDataTest {

    // Claim tests
    @Test
    fun `Claim stores name and value correctly`() {
        val claim = ClaimData.Claim("email", "test@example.com")
        assertEquals("email", claim.name)
        assertEquals("test@example.com", claim.value)
    }

    @Test
    fun `Claim can have null value`() {
        val claim = ClaimData.Claim("missing", null)
        assertEquals("missing", claim.name)
        assertNull(claim.value)
    }

    @Test
    fun `Claim can store different value types`() {
        val stringClaim = ClaimData.Claim("string", "value")
        val intClaim = ClaimData.Claim("int", 42)
        val boolClaim = ClaimData.Claim("bool", true)
        val listClaim = ClaimData.Claim("list", listOf("a", "b", "c"))

        assertEquals("value", stringClaim.value)
        assertEquals(42, intClaim.value)
        assertEquals(true, boolClaim.value)
        assertEquals(listOf("a", "b", "c"), listClaim.value)
    }

    // Organization tests
    @Test
    fun `Organization stores orgCode correctly`() {
        val org = ClaimData.Organization("org_12345")
        assertEquals("org_12345", org.orgCode)
    }

    @Test
    fun `Organization can have empty orgCode`() {
        val org = ClaimData.Organization("")
        assertEquals("", org.orgCode)
    }

    // Organizations tests
    @Test
    fun `Organizations stores list of orgCodes correctly`() {
        val orgs = ClaimData.Organizations(listOf("org_1", "org_2", "org_3"))
        assertEquals(3, orgs.orgCodes.size)
        assertEquals("org_1", orgs.orgCodes[0])
        assertEquals("org_2", orgs.orgCodes[1])
        assertEquals("org_3", orgs.orgCodes[2])
    }

    @Test
    fun `Organizations can have empty list`() {
        val orgs = ClaimData.Organizations(emptyList())
        assertTrue(orgs.orgCodes.isEmpty())
    }

    // Permission tests
    @Test
    fun `Permission stores orgCode and isGranted correctly when granted`() {
        val permission = ClaimData.Permission("org_123", true)
        assertEquals("org_123", permission.orgCode)
        assertTrue(permission.isGranted)
    }

    @Test
    fun `Permission stores orgCode and isGranted correctly when not granted`() {
        val permission = ClaimData.Permission("org_456", false)
        assertEquals("org_456", permission.orgCode)
        assertFalse(permission.isGranted)
    }

    @Test
    fun `Permission extends Organization`() {
        val permission = ClaimData.Permission("org_123", true)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(permission is ClaimData.Organization)
    }

    // Permissions tests
    @Test
    fun `Permissions stores orgCode and permissions list correctly`() {
        val permissions = ClaimData.Permissions(
            "org_123",
            listOf("read:users", "write:users", "delete:users")
        )
        assertEquals("org_123", permissions.orgCode)
        assertEquals(3, permissions.permissions.size)
        assertTrue(permissions.permissions.contains("read:users"))
        assertTrue(permissions.permissions.contains("write:users"))
        assertTrue(permissions.permissions.contains("delete:users"))
    }

    @Test
    fun `Permissions can have empty permissions list`() {
        val permissions = ClaimData.Permissions("org_123", emptyList())
        assertEquals("org_123", permissions.orgCode)
        assertTrue(permissions.permissions.isEmpty())
    }

    @Test
    fun `Permissions extends Organization`() {
        val permissions = ClaimData.Permissions("org_123", emptyList())
        @Suppress("USELESS_IS_CHECK")
        assertTrue(permissions is ClaimData.Organization)
    }

    // Role tests
    @Test
    fun `Role stores orgCode and isGranted correctly when granted`() {
        val role = ClaimData.Role("org_123", true)
        assertEquals("org_123", role.orgCode)
        assertTrue(role.isGranted)
    }

    @Test
    fun `Role stores orgCode and isGranted correctly when not granted`() {
        val role = ClaimData.Role("org_456", false)
        assertEquals("org_456", role.orgCode)
        assertFalse(role.isGranted)
    }

    @Test
    fun `Role extends Organization`() {
        val role = ClaimData.Role("org_123", true)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(role is ClaimData.Organization)
    }

    // Roles tests
    @Test
    fun `Roles stores orgCode and roles list correctly`() {
        val roles = ClaimData.Roles(
            "org_123",
            listOf("admin", "editor", "viewer")
        )
        assertEquals("org_123", roles.orgCode)
        assertEquals(3, roles.roles.size)
        assertTrue(roles.roles.contains("admin"))
        assertTrue(roles.roles.contains("editor"))
        assertTrue(roles.roles.contains("viewer"))
    }

    @Test
    fun `Roles can have empty roles list`() {
        val roles = ClaimData.Roles("org_123", emptyList())
        assertEquals("org_123", roles.orgCode)
        assertTrue(roles.roles.isEmpty())
    }

    @Test
    fun `Roles extends Organization`() {
        val roles = ClaimData.Roles("org_123", emptyList())
        @Suppress("USELESS_IS_CHECK")
        assertTrue(roles is ClaimData.Organization)
    }

    // ClaimData sealed class behavior tests
    @Test
    fun `all subclasses are instances of ClaimData`() {
        val claim = ClaimData.Claim("test", "value")
        val org = ClaimData.Organization("org_123")
        val orgs = ClaimData.Organizations(listOf("org_1"))
        val permission = ClaimData.Permission("org_123", true)
        val permissions = ClaimData.Permissions("org_123", listOf("perm"))
        val role = ClaimData.Role("org_123", true)
        val roles = ClaimData.Roles("org_123", listOf("role"))

        @Suppress("USELESS_IS_CHECK")
        assertTrue(claim is ClaimData)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(org is ClaimData)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(orgs is ClaimData)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(permission is ClaimData)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(permissions is ClaimData)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(role is ClaimData)
        @Suppress("USELESS_IS_CHECK")
        assertTrue(roles is ClaimData)
    }
}

