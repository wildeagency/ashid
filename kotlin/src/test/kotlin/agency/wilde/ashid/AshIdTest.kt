package agency.wilde.ashid

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `create Ashid with prefix - auto adds delimiter`() {
        val id = Ashid.create("user")
        assertTrue(id.startsWith("user_"))
    }

    @Test
    fun `create Ashid with trailing underscore - ignored and re-added`() {
        val id = Ashid.create("user_")
        assertTrue(id.startsWith("user_"))
    }

    @Test
    fun `create same ID with or without trailing delimiter`() {
        val id1 = Ashid.create("user", time = 1000, randomLong = 0)
        val id2 = Ashid.create("user_", time = 1000, randomLong = 0)
        assertEquals(id1, id2)
    }

    @Test
    fun `create Ashid with trailing dash - converted to underscore`() {
        val id = Ashid.create("user-", time = 1000, randomLong = 0)
        assertTrue(id.startsWith("user_"))
    }

    @Test
    fun `create Ashid with timestamp 0 and prefix - omits timestamp`() {
        val id = Ashid.create("user", time = 0, randomLong = 0)
        assertEquals("user_0", id) // timestamp 0 omitted, random 0 = "0"
    }

    @Test
    fun `create Ashid with timestamp 0 and no random - just zero`() {
        val id = Ashid.create("user", time = 0, randomLong = 0)
        assertEquals("user_0", id)
    }

    @Test
    fun `create Ashid with timestamp 0 and random value - no timestamp padding`() {
        val id = Ashid.create("user", time = 0, randomLong = 12345)
        // timestamp 0 omitted, random not padded when timestamp = 0
        val expected = "user_" + EncoderBase32Crockford.encode(12345)
        assertEquals(expected, id)
    }

    @Test
    fun `create Ashid without prefix and timestamp 0 - fixed 22 chars`() {
        val id = Ashid.create(time = 0, randomLong = 0)
        assertEquals(22, id.length)
        assertEquals("0000000000000000000000", id)
    }

    @Test
    fun `single letter prefix should also get delimiter`() {
        val id = Ashid.create("u", time = 0, randomLong = 0)
        assertEquals("u_0", id)
    }

    @Test
    fun `allow alphanumeric prefix`() {
        val id = Ashid.create("user1", time = 1000, randomLong = 0)
        assertTrue(id.startsWith("user1_"))
    }

    @Test
    fun `strip non-alphanumeric characters from prefix`() {
        val id = Ashid.create("a-b_c", time = 1000, randomLong = 0)
        assertTrue(id.startsWith("abc_")) // strips - and _
    }

    @Test
    fun `strip special characters from prefix`() {
        val id = Ashid.create("user!@#\$%", time = 1000, randomLong = 0)
        assertTrue(id.startsWith("user_"))
    }

    @Test
    fun `return no prefix if all chars stripped`() {
        val id = Ashid.create("___", time = 1000, randomLong = 0)
        assertEquals(22, id.length) // No prefix, fixed format
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

    @Test
    fun `lowercase uppercase prefix in create`() {
        val id = Ashid.create("USER", time = 1000, randomLong = 0)
        assertTrue(id.startsWith("user_"))
        assertEquals("user_", Ashid.prefix(id))
    }

    // ==================== SORTABILITY TESTS ====================

    @Test
    fun `be sortable by creation time - no prefix`() {
        val baseTime = 1000000000000L
        val id1 = Ashid.create(time = baseTime)
        val id2 = Ashid.create(time = baseTime + 1000)
        val id3 = Ashid.create(time = baseTime + 2000)

        val sorted = listOf(id3, id1, id2).sorted()
        assertEquals(listOf(id1, id2, id3), sorted)
    }

    @Test
    fun `be sortable by creation time - with prefix`() {
        val baseTime = 1000000000000L
        val id1 = Ashid.create("user", time = baseTime)
        val id2 = Ashid.create("user", time = baseTime + 1000)
        val id3 = Ashid.create("user", time = baseTime + 2000)

        val sorted = listOf(id3, id1, id2).sorted()
        assertEquals(listOf(id1, id2, id3), sorted)
    }

    @Test
    fun `sort chronologically with same timestamp but different random`() {
        val timestamp = System.currentTimeMillis()
        val id1 = Ashid.create(time = timestamp, randomLong = 1000)
        val id2 = Ashid.create(time = timestamp, randomLong = 2000)

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
    fun `parse Ashid with delimiter prefix - variable format`() {
        val id = Ashid.create("user", time = 1000, randomLong = 2000)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("user_", prefix)
        assertEquals(1000L, EncoderBase32Crockford.decode(encodedTimestamp))
        assertEquals(2000L, EncoderBase32Crockford.decode(encodedRandom))
    }

    @Test
    fun `parse Ashid with underscore prefix and timestamp 0`() {
        val id = Ashid.create("user", time = 0, randomLong = 12345)
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("user_", prefix)
        assertEquals("0", encodedTimestamp) // timestamp 0 returns "0"
        assertEquals(12345L, EncoderBase32Crockford.decode(encodedRandom))
    }

    @Test
    fun `treat non-delimited ID as no prefix`() {
        // Old-style ID without delimiter - entire string is base (must be exactly 22 chars)
        val id = "u1234567890123456789ab" // exactly 22 chars
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("", prefix) // No delimiter = no prefix
        assertEquals("u12345678", encodedTimestamp)
        assertEquals("90123456789ab", encodedRandom)
    }

    @Test
    fun `throw on empty string`() {
        assertThrows<IllegalArgumentException> {
            Ashid.parse("")
        }
    }

    @Test
    fun `throw on wrong length without delimiter`() {
        assertThrows<IllegalArgumentException> {
            Ashid.parse("abc123") // Not 22 or 26 chars, no delimiter
        }
    }

    @Test
    fun `parse dash as underscore delimiter`() {
        val id = "user-1kbg1jmtt0000000000000"
        val (prefix, encodedTimestamp, encodedRandom) = Ashid.parse(id)
        assertEquals("user_", prefix) // dash normalized to underscore
        assertEquals("1kbg1jmtt", encodedTimestamp)
        assertEquals("0000000000000", encodedRandom)
    }

    // ==================== EXTRACTION TESTS ====================

    @Test
    fun `prefix extraction`() {
        assertEquals("", Ashid.prefix(Ashid.create(time = 1000)))
        assertEquals("u_", Ashid.prefix(Ashid.create("u", time = 1000)))
        assertEquals("user_", Ashid.prefix(Ashid.create("user", time = 1000)))
    }

    @Test
    fun `timestamp extraction`() {
        val timestamp = 1234567890L
        assertEquals(timestamp, Ashid.timestamp(Ashid.create(time = timestamp)))
        assertEquals(timestamp, Ashid.timestamp(Ashid.create("u", time = timestamp)))
        assertEquals(timestamp, Ashid.timestamp(Ashid.create("user", time = timestamp)))
    }

    @Test
    fun `timestamp extraction with timestamp 0`() {
        assertEquals(0L, Ashid.timestamp(Ashid.create(time = 0)))
        assertEquals(0L, Ashid.timestamp(Ashid.create("u", time = 0)))
        assertEquals(0L, Ashid.timestamp(Ashid.create("user", time = 0)))
    }

    @Test
    fun `random extraction`() {
        val random = 9876543210L
        assertEquals(random, Ashid.random(Ashid.create(randomLong = random)))
        assertEquals(random, Ashid.random(Ashid.create("u", randomLong = random)))
        assertEquals(random, Ashid.random(Ashid.create("user", randomLong = random)))
    }

    // ==================== NORMALIZE TESTS ====================

    @Test
    fun `normalize lowercases prefix`() {
        val id = Ashid.create("USER", time = 1000, randomLong = 2000)
        val normalized = Ashid.normalize(id.uppercase())
        assertTrue(normalized.startsWith("user_"))
    }

    @Test
    fun `normalize converts ambiguous characters`() {
        val original = Ashid.create("user", time = 1000, randomLong = 2000)
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
        val id = Ashid.create("User", time = timestamp, randomLong = random)
        val normalized = Ashid.normalize(id)

        assertEquals("user_", Ashid.prefix(normalized))
        assertEquals(timestamp, Ashid.timestamp(normalized))
        assertEquals(random, Ashid.random(normalized))
    }

    @Test
    fun `normalize fixed format ID - no prefix`() {
        val id = Ashid.create(time = 1000, randomLong = 2000)
        val normalized = Ashid.normalize(id.uppercase())
        assertEquals("", Ashid.prefix(normalized))
        assertEquals(22, normalized.length) // Fixed format preserved
    }

    @Test
    fun `normalize converts dash to underscore in prefix`() {
        val withDash = "user-1kbg1jmtt0000000000000"
        val normalized = Ashid.normalize(withDash)
        assertTrue(normalized.startsWith("user_"))
        assertEquals("user_", Ashid.prefix(normalized))
    }

    // ---- Normalize: ashid4 round-trip ----
    //
    // Regression coverage: prior to the unified normalize, the function routed
    // through create() (timestamp + random) for every input, which encoded the
    // first half unpadded. For ashid4 inputs with leading zeros at the front of
    // the first long, those zeros got dropped — corrupting the value.

    @Test
    fun `normalize ashid4 with prefix full entropy is identity`() {
        val original = Ashid.create4("tok", 0x112210f47de98115uL, 0x88f7bfa8ac471d31uL)
        assertEquals(original, Ashid.normalize(original))
    }

    @Test
    fun `normalize ashid4 no prefix full entropy is identity`() {
        val original = Ashid.create4(null, 0x112210f47de98115uL, 0x88f7bfa8ac471d31uL)
        assertEquals(original, Ashid.normalize(original))
    }

    @Test
    fun `normalize uppercased ashid4 with prefix back to canonical`() {
        val original = Ashid.create4("tok", 0xdeadbeefcafebabeuL, 0x0123456789abcdefuL)
        assertEquals(original, Ashid.normalize(original.uppercase()))
    }

    @Test
    fun `normalize uppercased ashid4 no prefix back to canonical`() {
        val original = Ashid.create4(null, 0xdeadbeefcafebabeuL, 0x0123456789abcdefuL)
        assertEquals(original, Ashid.normalize(original.uppercase()))
    }

    @Test
    fun `normalize ashid4 small first long collapses to v1 shape`() {
        // Small first long → the canonical type-1 path truncates leading zeros, so
        // the shape collapses to v1. The values survive, which is the point of
        // normalize().
        val r1: ULong = 1uL
        val r2: ULong = 0uL
        val original = Ashid.create4("tok", r1, r2)
        val normalized = Ashid.normalize(original)
        assertEquals(r1.toLong(), Ashid.timestamp(normalized))
        assertEquals(r2.toLong(), Ashid.random(normalized))
    }

    @Test
    fun `normalize is idempotent across v1 and ashid4`() {
        val v1 = Ashid.create("user", time = 1_609_459_200_000L, randomLong = 12345L)
        val once = Ashid.normalize(v1)
        assertEquals(once, Ashid.normalize(once))

        val a4 = Ashid.create4("tok", 0xdeadbeefcafebabeuL, 0x0123456789abcdefuL)
        val a4Once = Ashid.normalize(a4)
        assertEquals(a4, a4Once)
        assertEquals(a4Once, Ashid.normalize(a4Once))
    }

    @Test
    fun `normalize v1 and matching ashid4 collapse to same canonical`() {
        // normalize routes through buildBase(padded = false), so a v1 and an
        // ashid4 with the same two longs (when the first long is small enough
        // to truncate) produce the same canonical (v1) shape.
        val long1: Long = 1_609_459_200_000L
        val long2: Long = 12345L
        val v1 = Ashid.create("tok", time = long1, randomLong = long2)
        val a4 = Ashid.create4("tok", long1.toULong(), long2.toULong())
        assertEquals(Ashid.normalize(a4), Ashid.normalize(v1))
        assertEquals(v1, Ashid.normalize(v1))
    }

    // ==================== VALIDATION TESTS ====================

    @Test
    fun `validate correct Ashids - no prefix`() {
        assertTrue(Ashid.isValid(Ashid.create()))
        assertTrue(Ashid.isValid(Ashid.create(time = 0, randomLong = 0)))
    }

    @Test
    fun `validate correct Ashids - with prefix`() {
        assertTrue(Ashid.isValid(Ashid.create("u")))
        assertTrue(Ashid.isValid(Ashid.create("user")))
        assertTrue(Ashid.isValid(Ashid.create("user", time = 0, randomLong = 0)))
    }

    @Test
    fun `reject empty string`() {
        assertFalse(Ashid.isValid(""))
    }

    @Test
    fun `reject wrong length without delimiter`() {
        assertFalse(Ashid.isValid("abc123"))
        assertFalse(Ashid.isValid("u12345")) // Too short, no delimiter
    }

    @Test
    fun `validate variable format with short base`() {
        // user_ with just "0" is valid (timestamp 0, random 0)
        assertTrue(Ashid.isValid("user_0"))
    }

    @Test
    fun `validate 22-char base without delimiter`() {
        assertTrue(Ashid.isValid("0000000000000000000000"))
    }

    // ==================== CONVENIENCE FUNCTION TESTS ====================

    @Test
    fun `convenience function ashid - no prefix`() {
        val id = ashid()
        assertEquals(22, id.length)
    }

    @Test
    fun `convenience function ashid - with prefix`() {
        val id = ashid("user")
        assertTrue(id.startsWith("user_"))
        assertTrue(Ashid.isValid(id))
    }

    @Test
    fun `convenience function ashid - with underscore prefix`() {
        val id = ashid("user_")
        assertTrue(id.startsWith("user_"))
        assertTrue(Ashid.isValid(id))
    }

    // ==================== CREATE4 TESTS (UUID v4 equivalent) ====================

    @Test
    fun `create4 without prefix - fixed 26 char format`() {
        val id = Ashid.create4()
        assertEquals(26, id.length) // 13 + 13 = 26 char base
    }

    @Test
    fun `create4 with prefix - auto adds delimiter`() {
        val id = Ashid.create4("tok")
        assertTrue(id.startsWith("tok_"))
        assertEquals(30, id.length) // 4 prefix (tok_) + 26 base
    }

    @Test
    fun `create4 with trailing underscore - ignored and re-added`() {
        val id = Ashid.create4("tok_")
        assertEquals(30, id.length) // 4 prefix + 26 base
        assertTrue(id.startsWith("tok_"))
    }

    @Test
    fun `create4 lowercases prefix`() {
        val id = Ashid.create4("TOKEN")
        assertTrue(id.startsWith("token_"))
    }

    @Test
    fun `create4 allows alphanumeric prefix`() {
        val id = Ashid.create4("tok1")
        assertTrue(id.startsWith("tok1_"))
    }

    @Test
    fun `create4 strips special characters from prefix`() {
        val id = Ashid.create4("tok!@#")
        assertTrue(id.startsWith("tok_"))
    }

    @Test
    fun `create4Long throws on negative random values`() {
        assertThrows<IllegalArgumentException> {
            Ashid.create4Long(random1 = -1, random2 = 0)
        }
        assertThrows<IllegalArgumentException> {
            Ashid.create4Long(random1 = 0, random2 = -1)
        }
    }

    @Test
    fun `create4 deterministic output with known randoms`() {
        val id1 = Ashid.create4("tok", 1000UL, 2000UL)
        val id2 = Ashid.create4("tok", 1000UL, 2000UL)
        assertEquals(id1, id2)
    }

    @Test
    fun `create4 generates unique IDs`() {
        val ids = mutableSetOf<String>()
        repeat(1000) {
            ids.add(Ashid.create4())
        }
        assertEquals(1000, ids.size)
    }

    @Test
    fun `create4 is parseable as valid ashid`() {
        val id = Ashid.create4("tok")
        assertTrue(Ashid.isValid(id))
        val (prefix, _, _) = Ashid.parse(id)
        assertEquals("tok_", prefix)
    }

    @Test
    fun `create4 uses 0-padding for consistent length`() {
        // With small random values that would normally be short
        val id = Ashid.create4(random1 = 1UL, random2 = 1UL)
        assertEquals(26, id.length)
        assertTrue(id.startsWith("000000000000")) // Padded first component (13 chars)
    }

    @Test
    fun `ashid4 convenience function`() {
        val id = ashid4("tok")
        assertEquals(30, id.length) // 4 (tok_) + 26
        assertTrue(id.startsWith("tok_"))
        assertTrue(Ashid.isValid(id))
    }

    @Test
    fun `ashid4 without prefix`() {
        val id = ashid4()
        assertEquals(26, id.length)
        assertTrue(Ashid.isValid(id))
    }

    @Test
    fun `create4 uses full 64-bit entropy for both components`() {
        // With 64-bit entropy per component, we should see values > 2^53
        // Values > 2^53 encode to > 11 chars in base32
        val samples = 100
        var sawFullEntropy = false

        repeat(samples) {
            val id = ashid4()
            val (_, encoded1, encoded2) = Ashid.parse(id)

            // Check if any encoded component (stripped of leading zeros) is > 11 chars
            // Values > 2^53 encode to > 11 chars in base32
            if (encoded1.trimStart('0').length > 11 || encoded2.trimStart('0').length > 11) {
                sawFullEntropy = true
                return@repeat
            }
        }

        // With uniform 64-bit distribution, ~99% should have values > 2^53
        assertTrue(sawFullEntropy, "Expected to see 64-bit values in ashid4 components")
    }

    @Test
    fun `create4 handles ULong MAX_VALUE random values`() {
        val maxValue = ULong.MAX_VALUE
        val id = Ashid.create4("tok", maxValue, maxValue)
        assertTrue(id.startsWith("tok_"))
        assertEquals(30, id.length)

        // Should be parseable
        val (prefix, encoded1, encoded2) = Ashid.parse(id)
        assertEquals("tok_", prefix)
        assertEquals(13, encoded1.length)
        assertEquals(13, encoded2.length)
    }

    @Test
    fun `create4 preserves large random values in roundtrip`() {
        val random1 = ULong.MAX_VALUE - 1000UL
        val random2 = ULong.MAX_VALUE / 2UL
        val id = Ashid.create4("tok", random1, random2)

        // Parse and verify the values can be decoded
        val (_, encoded1, encoded2) = Ashid.parse(id)
        val decoded1 = EncoderBase32Crockford.decodeULong(encoded1)
        val decoded2 = EncoderBase32Crockford.decodeULong(encoded2)

        assertEquals(random1, decoded1)
        assertEquals(random2, decoded2)
    }

    // ---- create4 padding lockdown ----
    //
    // create4 must always emit both halves 13-char padded, regardless of
    // input magnitude. These pin the wire format so a refactor that drops
    // padding (e.g. routing create4 through an unpadded builder) fails
    // loudly. Mirrors typescript/test/ashid.test.ts.

    @Test
    fun `create4 padding zero zero with prefix`() {
        assertEquals("tok_00000000000000000000000000", Ashid.create4("tok", 0uL, 0uL))
    }

    @Test
    fun `create4 padding zero zero no prefix`() {
        assertEquals("00000000000000000000000000", Ashid.create4(null, 0uL, 0uL))
    }

    @Test
    fun `create4 padding one zero with prefix`() {
        assertEquals("tok_00000000000010000000000000", Ashid.create4("tok", 1uL, 0uL))
    }

    @Test
    fun `create4 padding crockford z with prefix`() {
        assertEquals("tok_000000000000z0000000000000", Ashid.create4("tok", 31uL, 0uL))
    }

    @Test
    fun `create4 padding max ULong first half`() {
        val id = Ashid.create4("tok", ULong.MAX_VALUE, 0uL)
        assertEquals("tok_fzzzzzzzzzzzz0000000000000", id)
        assertEquals(3 + 1 + 26, id.length)
    }

    @Test
    fun `create4 padding max ULong second half`() {
        val id = Ashid.create4("tok", 0uL, ULong.MAX_VALUE)
        assertEquals("tok_0000000000000fzzzzzzzzzzzz", id)
        assertEquals(3 + 1 + 26, id.length)
    }

    @Test
    fun `create4 padding no prefix always 26 chars`() {
        val samples = listOf(
            0uL to 0uL,
            1uL to 0uL,
            0uL to 1uL,
            31uL to 31uL,
            ULong.MAX_VALUE to 0uL,
            0uL to ULong.MAX_VALUE,
        )
        for ((r1, r2) in samples) {
            assertEquals(26, Ashid.create4(null, r1, r2).length)
        }
    }

    @Test
    fun `create4 padding with prefix length invariant`() {
        val samples = listOf(
            0uL to 0uL,
            1uL to 0uL,
            ULong.MAX_VALUE to ULong.MAX_VALUE,
        )
        for ((r1, r2) in samples) {
            assertEquals(3 + 1 + 26, Ashid.create4("tok", r1, r2).length)
        }
    }

    // --- UUID round-trip ---

    @Test
    fun `toUuid produces a 36-char uuid`() {
        val id = Ashid.create("user", 1733140800000L, 8234567890123456789L)
        assertEquals(36, Ashid.toUuid(id).toString().length)
    }

    @Test
    fun `toUuid is prefix-agnostic`() {
        val ts = 1733140800000L
        val rand = 8234567890123456789L
        assertEquals(
            Ashid.toUuid(Ashid.create("user", ts, rand)),
            Ashid.toUuid(Ashid.create(null, ts, rand))
        )
    }

    @Test
    fun `roundtrips a standard-form Ashid through UUID`() {
        val original = Ashid.create("user", 1733140800000L, 8234567890123456789L)
        assertEquals(original, Ashid.fromUuid(Ashid.toUuid(original), "user"))
    }

    @Test
    fun `roundtrips an ashid4 through UUID`() {
        val original = Ashid.create4("tok", ULong.MAX_VALUE, 1UL shl 63)
        assertEquals(original, Ashid.fromUuid(Ashid.toUuid(original), "tok"))
    }

    @Test
    fun `UUID-1-shaped UUID routes to 22-char Ashid`() {
        val uuid = "00000123-abcd-ef01-2345-6789abcdef01"
        val id = Ashid.fromUuid(uuid)
        assertEquals(22, id.length)
        assertEquals(uuid, Ashid.toUuid(id).toString())
    }

    @Test
    fun `UUIDv4 routes to 26-char ashid4`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        val id = Ashid.fromUuid(uuid)
        assertEquals(26, id.length)
        assertEquals(uuid, Ashid.toUuid(id).toString())
    }

    @Test
    fun `UUIDv7 routes to 26-char ashid4`() {
        val uuid = "019e008b-edc4-7265-8312-f6a278b46b11"
        val id = Ashid.fromUuid(uuid)
        assertEquals(26, id.length)
        assertEquals(uuid, Ashid.toUuid(id).toString())
    }

    @Test
    fun `roundtrips canonical UUIDs from the post`() {
        listOf(
            "550e8400-e29b-41d4-a716-446655440000",
            "7c9e6679-7425-40de-944b-e07fc1f90ae7",
            "f47ac10b-58cc-4372-a567-0e02b2c3d479"
        ).forEach { uuid ->
            assertEquals(uuid, Ashid.toUuid(Ashid.fromUuid(uuid)).toString())
        }
    }

    @Test
    fun `accepts undashed 32-char hex input`() {
        val dashed = "550e8400-e29b-41d4-a716-446655440000"
        val undashed = "550e8400e29b41d4a716446655440000"
        assertEquals(Ashid.fromUuid(dashed), Ashid.fromUuid(undashed))
    }

    @Test
    fun `preserves prefix through fromUuid`() {
        val id = Ashid.fromUuid("550e8400-e29b-41d4-a716-446655440000", "user")
        assertTrue(id.startsWith("user_"))
        assertEquals(id, Ashid.fromUuid(Ashid.toUuid(id), "user"))
    }

    @Test
    fun `handles all-zero UUID`() {
        val uuid = "00000000-0000-0000-0000-000000000000"
        assertEquals(uuid, Ashid.toUuid(Ashid.fromUuid(uuid)).toString())
    }

    @Test
    fun `handles all-FF UUID`() {
        val uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff"
        val id = Ashid.fromUuid(uuid)
        assertEquals(26, id.length)
        assertEquals(uuid, Ashid.toUuid(id).toString())
    }

    @Test
    fun `throws on invalid UUID input`() {
        assertFailsWith<IllegalArgumentException> { Ashid.fromUuid("not-a-uuid") }
        assertFailsWith<IllegalArgumentException> { Ashid.fromUuid("550e8400e29b41d4a71644665544000") }
        assertFailsWith<IllegalArgumentException> { Ashid.fromUuid("550e8400-e29b-41d4-a716-44665544000g") }
    }

}
