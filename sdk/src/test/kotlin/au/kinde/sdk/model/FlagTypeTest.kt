package au.kinde.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [FlagType] enum.
 */
class FlagTypeTest {

    @Test
    fun `fromLetter returns Boolean for letter 'b'`() {
        val result = FlagType.fromLetter("b")
        assertEquals(FlagType.Boolean, result)
    }

    @Test
    fun `fromLetter returns String for letter 's'`() {
        val result = FlagType.fromLetter("s")
        assertEquals(FlagType.String, result)
    }

    @Test
    fun `fromLetter returns Integer for letter 'i'`() {
        val result = FlagType.fromLetter("i")
        assertEquals(FlagType.Integer, result)
    }

    @Test
    fun `fromLetter returns null for unknown letter`() {
        val result = FlagType.fromLetter("x")
        assertNull(result)
    }

    @Test
    fun `fromLetter returns null for empty string`() {
        val result = FlagType.fromLetter("")
        assertNull(result)
    }

    @Test
    fun `Boolean type has correct letter`() {
        assertEquals("b", FlagType.Boolean.letter)
    }

    @Test
    fun `String type has correct letter`() {
        assertEquals("s", FlagType.String.letter)
    }

    @Test
    fun `Integer type has correct letter`() {
        assertEquals("i", FlagType.Integer.letter)
    }
}

