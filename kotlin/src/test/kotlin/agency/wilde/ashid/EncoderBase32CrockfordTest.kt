package agency.wilde.ashid

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncoderBase32CrockfordTest {

    @Test
    fun `encode zero`() {
        assertEquals("0", EncoderBase32Crockford.encode(0))
    }

    @Test
    fun `encode small numbers`() {
        assertEquals("1", EncoderBase32Crockford.encode(1))
        assertEquals("a", EncoderBase32Crockford.encode(10))
        assertEquals("z", EncoderBase32Crockford.encode(31))
    }

    @Test
    fun `encode larger numbers`() {
        assertEquals("10", EncoderBase32Crockford.encode(32))
        assertEquals("11", EncoderBase32Crockford.encode(33))
        assertEquals("100", EncoderBase32Crockford.encode(1024))
    }

    @Test
    fun `encode with padding`() {
        assertEquals("0000000000000", EncoderBase32Crockford.encode(0, padded = true))
        assertEquals("0000000000001", EncoderBase32Crockford.encode(1, padded = true))
    }

    @Test
    fun `throw on negative input`() {
        assertThrows<IllegalArgumentException> {
            EncoderBase32Crockford.encode(-1)
        }
    }

    @Test
    fun `decode zero`() {
        assertEquals(0L, EncoderBase32Crockford.decode("0"))
    }

    @Test
    fun `decode small numbers`() {
        assertEquals(1L, EncoderBase32Crockford.decode("1"))
        assertEquals(10L, EncoderBase32Crockford.decode("a"))
        assertEquals(31L, EncoderBase32Crockford.decode("z"))
    }

    @Test
    fun `decode larger numbers`() {
        assertEquals(32L, EncoderBase32Crockford.decode("10"))
        assertEquals(33L, EncoderBase32Crockford.decode("11"))
        assertEquals(1024L, EncoderBase32Crockford.decode("100"))
    }

    @Test
    fun `decode case insensitive`() {
        assertEquals(EncoderBase32Crockford.decode("ABC"), EncoderBase32Crockford.decode("abc"))
        assertEquals(EncoderBase32Crockford.decode("XYZ"), EncoderBase32Crockford.decode("xyz"))
    }

    @Test
    fun `decode lookalike characters`() {
        // I, i, L, l -> 1
        assertEquals(1L, EncoderBase32Crockford.decode("I"))
        assertEquals(1L, EncoderBase32Crockford.decode("i"))
        assertEquals(1L, EncoderBase32Crockford.decode("L"))
        assertEquals(1L, EncoderBase32Crockford.decode("l"))

        // O, o -> 0
        assertEquals(0L, EncoderBase32Crockford.decode("O"))
        assertEquals(0L, EncoderBase32Crockford.decode("o"))

        // U, u -> V (27)
        assertEquals(27L, EncoderBase32Crockford.decode("U"))
        assertEquals(27L, EncoderBase32Crockford.decode("u"))
    }

    @Test
    fun `throw on empty string`() {
        assertThrows<IllegalArgumentException> {
            EncoderBase32Crockford.decode("")
        }
    }

    @Test
    fun `throw on invalid character`() {
        assertThrows<IllegalArgumentException> {
            EncoderBase32Crockford.decode("abc-def")
        }
    }

    @Test
    fun `roundtrip encoding`() {
        val testValues = listOf(0L, 1L, 100L, 1000L, 1_000_000L, Long.MAX_VALUE / 2)
        for (value in testValues) {
            val encoded = EncoderBase32Crockford.encode(value)
            val decoded = EncoderBase32Crockford.decode(encoded)
            assertEquals(value, decoded)
        }
    }

    @Test
    fun `validate valid strings`() {
        assertTrue(EncoderBase32Crockford.isValid("0"))
        assertTrue(EncoderBase32Crockford.isValid("abc"))
        assertTrue(EncoderBase32Crockford.isValid("123"))
    }

    @Test
    fun `reject invalid strings`() {
        assertFalse(EncoderBase32Crockford.isValid(""))
        assertFalse(EncoderBase32Crockford.isValid("abc-def"))
    }

    @Test
    fun `secure random generates positive values`() {
        repeat(100) {
            val value = EncoderBase32Crockford.secureRandomLong()
            assertTrue(value >= 0)
        }
    }
}
