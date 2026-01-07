package au.kinde.sdk.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TokenType] enum.
 */
class TokenTypeTest {

    @Test
    fun `TokenType has ID_TOKEN value`() {
        val idToken = TokenType.ID_TOKEN
        assertEquals("ID_TOKEN", idToken.name)
    }

    @Test
    fun `TokenType has ACCESS_TOKEN value`() {
        val accessToken = TokenType.ACCESS_TOKEN
        assertEquals("ACCESS_TOKEN", accessToken.name)
    }

    @Test
    fun `TokenType has exactly two values`() {
        val values = TokenType.values()
        assertEquals(2, values.size)
    }

    @Test
    fun `TokenType values contain ID_TOKEN and ACCESS_TOKEN`() {
        val values = TokenType.values().toList()
        assertTrue(values.contains(TokenType.ID_TOKEN))
        assertTrue(values.contains(TokenType.ACCESS_TOKEN))
    }

    @Test
    fun `TokenType valueOf returns correct value for ID_TOKEN`() {
        val result = TokenType.valueOf("ID_TOKEN")
        assertEquals(TokenType.ID_TOKEN, result)
    }

    @Test
    fun `TokenType valueOf returns correct value for ACCESS_TOKEN`() {
        val result = TokenType.valueOf("ACCESS_TOKEN")
        assertEquals(TokenType.ACCESS_TOKEN, result)
    }

    @Test
    fun `TokenType ordinal values are correct`() {
        assertEquals(0, TokenType.ID_TOKEN.ordinal)
        assertEquals(1, TokenType.ACCESS_TOKEN.ordinal)
    }

    @Test
    fun `TokenType can be used in when expression`() {
        fun getTokenDescription(tokenType: TokenType): String {
            return when (tokenType) {
                TokenType.ID_TOKEN -> "Identity Token"
                TokenType.ACCESS_TOKEN -> "Access Token"
            }
        }

        assertEquals("Identity Token", getTokenDescription(TokenType.ID_TOKEN))
        assertEquals("Access Token", getTokenDescription(TokenType.ACCESS_TOKEN))
    }
}

