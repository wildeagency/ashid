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

    @Test
    fun `secure random uses full 63-bit entropy range`() {
        // With 63-bit entropy, we should see values that exceed 2^53 (JS MAX_SAFE_INTEGER)
        // and approach 2^63 - 1 (Long.MAX_VALUE)
        val samples = 100
        var sawLargeValue = false
        val threshold = 1L shl 53 // 2^53

        repeat(samples) {
            val value = EncoderBase32Crockford.secureRandomLong()
            if (value > threshold) {
                sawLargeValue = true
                return@repeat
            }
        }

        // With uniform 63-bit distribution, ~98% of values should exceed 2^53
        // So we should definitely see at least one in 100 samples
        assertTrue(sawLargeValue, "Expected to see values > 2^53 with 63-bit entropy")
    }

    @Test
    fun `secure random produces values that encode to full 13-char width`() {
        // Values using full 63 bits should frequently encode to 13 chars
        // Long.MAX_VALUE (2^63 - 1) encodes to 13 chars
        val samples = 100
        var saw13CharEncoding = false

        repeat(samples) {
            val value = EncoderBase32Crockford.secureRandomLong()
            val encoded = EncoderBase32Crockford.encode(value)
            if (encoded.length == 13) {
                saw13CharEncoding = true
                return@repeat
            }
        }

        assertTrue(saw13CharEncoding, "Expected to see 13-char encodings with 63-bit values")
    }

    @Test
    fun `encode handles Long MAX_VALUE correctly`() {
        val maxValue = Long.MAX_VALUE
        val encoded = EncoderBase32Crockford.encode(maxValue)
        val decoded = EncoderBase32Crockford.decode(encoded)
        assertEquals(maxValue, decoded)
        assertEquals(13, encoded.length, "Long.MAX_VALUE should encode to 13 chars")
    }

    @Test
    fun `secure random generates unique values`() {
        val values = mutableSetOf<Long>()
        repeat(100) {
            values.add(EncoderBase32Crockford.secureRandomLong())
        }
        // Should have generated many unique values
        assertTrue(values.size > 90, "Expected at least 90 unique values from 100 samples")
    }

    // ==================== ULong (64-bit) tests ====================

    @Test
    fun `secure random ULong generates full 64-bit entropy`() {
        // With 64-bit entropy, we should see values that exceed 2^63 (Long.MAX_VALUE)
        val samples = 100
        var sawValueBeyondLongMax = false
        val threshold = Long.MAX_VALUE.toULong()

        repeat(samples) {
            val value = EncoderBase32Crockford.secureRandomULong()
            if (value > threshold) {
                sawValueBeyondLongMax = true
                return@repeat
            }
        }

        // With uniform 64-bit distribution, ~50% should exceed Long.MAX_VALUE
        assertTrue(sawValueBeyondLongMax, "Expected to see values > Long.MAX_VALUE with 64-bit entropy")
    }

    @Test
    fun `encode ULong handles MAX_VALUE correctly`() {
        val maxValue = ULong.MAX_VALUE
        val encoded = EncoderBase32Crockford.encode(maxValue)
        val decoded = EncoderBase32Crockford.decodeULong(encoded)
        assertEquals(maxValue, decoded)
        assertEquals(13, encoded.length, "ULong.MAX_VALUE should encode to 13 chars")
    }

    @Test
    fun `encode ULong roundtrip works for full range`() {
        val testValues = listOf(
            0UL,
            1UL,
            Long.MAX_VALUE.toULong(),
            Long.MAX_VALUE.toULong() + 1UL,
            ULong.MAX_VALUE - 1UL,
            ULong.MAX_VALUE
        )

        for (value in testValues) {
            val encoded = EncoderBase32Crockford.encode(value)
            val decoded = EncoderBase32Crockford.decodeULong(encoded)
            assertEquals(value, decoded, "Roundtrip failed for value $value")
        }
    }

    @Test
    fun `secure random ULong generates unique values`() {
        val values = mutableSetOf<ULong>()
        repeat(100) {
            values.add(EncoderBase32Crockford.secureRandomULong())
        }
        assertTrue(values.size > 90, "Expected at least 90 unique values from 100 samples")
    }
}
