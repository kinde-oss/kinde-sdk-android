package au.kinde.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GrantType] enum.
 */
class GrantTypeTest {

    @Test
    fun `GrantType has PKCE value`() {
        val pkce = GrantType.PKCE
        assertEquals("PKCE", pkce.name)
    }

    @Test
    fun `GrantType has NONE value`() {
        val none = GrantType.NONE
        assertEquals("NONE", none.name)
    }

    @Test
    fun `GrantType has exactly two values`() {
        val values = GrantType.values()
        assertEquals(2, values.size)
    }

    @Test
    fun `GrantType values contain PKCE and NONE`() {
        val values = GrantType.values().toList()
        assertTrue(values.contains(GrantType.PKCE))
        assertTrue(values.contains(GrantType.NONE))
    }

    @Test
    fun `GrantType valueOf returns correct value for PKCE`() {
        val result = GrantType.valueOf("PKCE")
        assertEquals(GrantType.PKCE, result)
    }

    @Test
    fun `GrantType valueOf returns correct value for NONE`() {
        val result = GrantType.valueOf("NONE")
        assertEquals(GrantType.NONE, result)
    }
}

