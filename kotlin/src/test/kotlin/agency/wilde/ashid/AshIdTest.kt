package agency.wilde.ashid

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AshidTest {

    // ==================== CREATE TESTS ====================

    @Test
    fun `create Ashid without prefix - fixed 22 char format`() {
        val id = Ashid.create()
        assertEquals(22, id.length)
        assertTrue(id[0].isDigit())
    }

    @Test
    fun `create Ashid with single char prefix without underscore - fixed format`() {
        val id = Ashid.create("u")
        assertEquals(23, id.length) // 1 prefix + 22 base
        assertTrue(id.startsWith("u"))
    }

    @Test
    fun `create Ashid with multi char prefix without underscore - fixed format`() {
        val id = Ashid.create("user")
        assertEquals(26, id.length) // 4 prefix + 22 base
        assertTrue(id.startsWith("user"))
    }

    @Test
    fun `create Ashid with underscore delimiter - variable format`() {
        val id = Ashid.create("user_")
        assertTrue(id.startsWith("user_"))
        // Variable length: 5 prefix + timestamp (variable) + 13 random
        assertTrue(id.length > 5) // At least prefix + some base
    }

    @Test
    fun `create Ashid with timestamp 0 and underscore - omits timestamp`() {
        val id = Ashid.create("user_", time = 0, randomLong = 0)
        assertEquals("user_0", id) // timestamp 0 omitted, random 0 = "0"
    }

    @Test
    fun `create Ashid with timestamp 0 and no random - just zero`() {
        val id = Ashid.create("user_", time = 0, randomLong = 0)
        assertEquals("user_0", id)
    }

    @Test
    fun `create Ashid with timestamp 0 and random value - no timestamp padding`() {
        val id = Ashid.create("user_", time = 0, randomLong = 12345)
        // timestamp 0 omitted, random not padded when timestamp = 0
        val expected = "user_" + EncoderBase32Crockford.encode(12345)
        assertEquals(expected, id)
    }

    @Test
    fun `create Ashid without underscore and timestamp 0 - fixed 22 chars`() {
        val id = Ashid.create("u", time = 0, randomLong = 0)
        assertEquals(23, id.length) // 1 prefix + 22 base
        assertEquals("u0000000000000000000000", id)
    }

    @Test
    fun `throw on prefix with numbers`() {
        assertThrows<IllegalArgumentException> {
            Ashid.create("u1")
        }
    }

    @Test
    fun `throw on prefix with underscore in middle`() {
        assertThrows<IllegalArgumentException> {
            Ashid.create("a_b")
        }
    }

    @Test
    fun `throw on negative timestamp`() {
        assertThrows<IllegalArgumentException> {
            Ashid.create(time = -1)
        }
    }

    @Test
    fun `throw on timestamp exceeding max`() {
        assertThrows<IllegalArgumentException> {
            Ashid.create(time = 35184372088832L) // MAX_TIMESTAMP + 1
        }
    }

    @Test
    fun `throw on negative random`() {
        assertThrows<IllegalArgumentException> {
            Ashid.create(randomLong = -1)
        }
    }

    @Test
    fun `allow max timestamp`() {
        val id = Ashid.create(time = 35184372088831L)
        assertTrue(id.isNotEmpty())
    }

    @Test
    fun `generate unique IDs`() {
        val ids = mutableSetOf<String>()
        repeat(1000) {
            ids.add(Ashid.create())
        }
        assertEquals(1000, ids.size)
    }

    // ==================== SORTABILITY TESTS ====================

    @Test
    fun `be sortable by creation time - fixed format`() {
        // Use timestamps with same encoding length for proper sorting
        val baseTime = 1000000000000L // Same length when encoded
        val id1 = Ashid.create("u", time = baseTime)
        val id2 = Ashid.create("u", time = baseTime + 1000)
        val id3 = Ashid.create("u", time = baseTime + 2000)

        val sorted = listOf(id3, id1, id2).sorted()
        assertEquals(listOf(id1, id2, id3), sorted)
    }

    @Test
    fun `be sortable by creation time - variable format with underscore`() {
        // Use timestamps with same encoding length
        val baseTime = 1000000000000L
        val id1 = Ashid.create("user_", time = baseTime)
        val id2 = Ashid.create("user_", time = baseTime + 1000)
        val id3 = Ashid.create("user_", time = baseTime + 2000)

        val sorted = listOf(id3, id1, id2).sorted()
        assertEquals(listOf(id1, id2, id3), sorted)
    }

    @Test
    fun `sort chronologically with same timestamp but different random`() {
        val timestamp = System.currentTimeMillis()
        val id1 = Ashid.create("u", time = timestamp, randomLong = 1000)
        val id2 = Ashid.create("u", time = timestamp, randomLong = 2000)

        val sorted = listOf(id2, id1).sorted()
        assertEquals(listOf(id1, id2), sorted)
    }

    // ==================== PARSE TESTS ====================

    @Test
    fun `parse Ashid without prefix - fixed format`() {
        val id = Ashid.create(time = 1000, randomLong = 2000)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("", prefix)
        assertEquals(1000L, EncoderBase32Crockford.decode(encodedTimestamp))
        assertEquals(2000L, EncoderBase32Crockford.decode(encodedRandom))
    }

    @Test
    fun `parse Ashid with single char prefix - fixed format`() {
        val id = Ashid.create("u", time = 1000, randomLong = 2000)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("u", prefix)
        assertEquals(1000L, EncoderBase32Crockford.decode(encodedTimestamp))
        assertEquals(2000L, EncoderBase32Crockford.decode(encodedRandom))
    }

    @Test
    fun `parse Ashid with underscore prefix - variable format`() {
        val id = Ashid.create("user_", time = 1000, randomLong = 2000)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("user_", prefix)
        assertEquals(1000L, EncoderBase32Crockford.decode(encodedTimestamp))
        assertEquals(2000L, EncoderBase32Crockford.decode(encodedRandom))
    }

    @Test
    fun `parse Ashid with underscore prefix and timestamp 0`() {
        val id = Ashid.create("user_", time = 0, randomLong = 12345)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("user_", prefix)
        assertEquals("0", encodedTimestamp) // timestamp 0 returns "0"
        assertEquals(12345L, EncoderBase32Crockford.decode(encodedRandom))
    }

    @Test
    fun `throw on empty string`() {
        assertThrows<IllegalArgumentException> {
            Ashid.parse("")
        }
    }

    @Test
    fun `throw on invalid fixed format length`() {
        assertThrows<IllegalArgumentException> {
            Ashid.parse("ushort") // prefix without underscore, base not 22 chars
        }
    }

    // ==================== EXTRACTION TESTS ====================

    @Test
    fun `prefix extraction`() {
        assertEquals("", Ashid.prefix(Ashid.create(time = 1000)))
        assertEquals("u", Ashid.prefix(Ashid.create("u", time = 1000)))
        assertEquals("user_", Ashid.prefix(Ashid.create("user_", time = 1000)))
    }

    @Test
    fun `timestamp extraction`() {
        val timestamp = 1234567890L
        assertEquals(timestamp, Ashid.timestamp(Ashid.create(time = timestamp)))
        assertEquals(timestamp, Ashid.timestamp(Ashid.create("u", time = timestamp)))
        assertEquals(timestamp, Ashid.timestamp(Ashid.create("user_", time = timestamp)))
    }

    @Test
    fun `timestamp extraction with timestamp 0`() {
        assertEquals(0L, Ashid.timestamp(Ashid.create(time = 0)))
        assertEquals(0L, Ashid.timestamp(Ashid.create("u", time = 0)))
        assertEquals(0L, Ashid.timestamp(Ashid.create("user_", time = 0)))
    }

    @Test
    fun `random extraction`() {
        val random = 9876543210L
        assertEquals(random, Ashid.random(Ashid.create(randomLong = random)))
        assertEquals(random, Ashid.random(Ashid.create("u", randomLong = random)))
        assertEquals(random, Ashid.random(Ashid.create("user_", randomLong = random)))
    }

    // ==================== NORMALIZE TESTS ====================

    @Test
    fun `normalize lowercases prefix`() {
        val id = Ashid.create("USER_", time = 1000, randomLong = 2000)
        val normalized = Ashid.normalize(id.uppercase())
        assertTrue(normalized.startsWith("user_"))
    }

    @Test
    fun `normalize converts ambiguous characters`() {
        // Create a valid ID first, then manually modify with ambiguous chars
        val original = Ashid.create("user_", time = 1000, randomLong = 2000)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(original)

        // Replace some chars with ambiguous equivalents
        val ambiguous = prefix.uppercase() +
            encodedTimestamp.replace('0', 'O') +
            encodedRandom.replace('1', 'L').uppercase()

        val normalized = Ashid.normalize(ambiguous)

        // Should decode to same values
        assertEquals(Ashid.timestamp(original), Ashid.timestamp(normalized))
        assertEquals(Ashid.random(original), Ashid.random(normalized))
        assertEquals(prefix.lowercase(), Ashid.prefix(normalized))
    }

    @Test
    fun `normalize preserves timestamp and random values`() {
        val timestamp = 1234567890L
        val random = 9876543210L
        val id = Ashid.create("User_", time = timestamp, randomLong = random)
        val normalized = Ashid.normalize(id)

        assertEquals("user_", Ashid.prefix(normalized))
        assertEquals(timestamp, Ashid.timestamp(normalized))
        assertEquals(random, Ashid.random(normalized))
    }

    @Test
    fun `normalize fixed format ID`() {
        val id = Ashid.create("U", time = 1000, randomLong = 2000)
        val normalized = Ashid.normalize(id.uppercase())
        assertEquals("u", Ashid.prefix(normalized))
        assertEquals(23, normalized.length) // Fixed format preserved
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    fun `validate correct Ashids - fixed format`() {
        assertTrue(Ashid.isValid(Ashid.create()))
        assertTrue(Ashid.isValid(Ashid.create("u")))
        assertTrue(Ashid.isValid(Ashid.create("user")))
    }

    @Test
    fun `validate correct Ashids - variable format`() {
        assertTrue(Ashid.isValid(Ashid.create("user_")))
        assertTrue(Ashid.isValid(Ashid.create("user_", time = 0)))
        assertTrue(Ashid.isValid(Ashid.create("user_", time = 0, randomLong = 0)))
    }

    @Test
    fun `reject empty string`() {
        assertFalse(Ashid.isValid(""))
    }

    @Test
    fun `reject invalid fixed format length`() {
        assertFalse(Ashid.isValid("ushort"))
        assertFalse(Ashid.isValid("u12345")) // Too short for fixed format
    }

    @Test
    fun `validate variable format with short base`() {
        // user_ with just "0" is valid (timestamp 0, random 0)
        assertTrue(Ashid.isValid("user_0"))
    }

    // ==================== CONVENIENCE FUNCTION TESTS ====================

    @Test
    fun `convenience function ashid - no prefix`() {
        val id = ashid()
        assertEquals(22, id.length)
    }

    @Test
    fun `convenience function ashid - with prefix no underscore`() {
        val id = ashid("u")
        assertEquals(23, id.length)
        assertTrue(id.startsWith("u"))
    }

    @Test
    fun `convenience function ashid - with underscore prefix`() {
        val id = ashid("user_")
        assertTrue(id.startsWith("user_"))
        // Variable length, just verify it's valid
        assertTrue(Ashid.isValid(id))
    }

}
