package agency.wilde.ashid

/**
 * Maximum supported timestamp (Dec 12, 3084)
 * Beyond this, timestamp encoding exceeds 9 characters
 */
private const val MAX_TIMESTAMP = 35184372088831L

/** Length of the random component when encoded (padded) */
private const val RANDOM_ENCODED_LENGTH = 13
/** Length of the timestamp component when encoded (padded) */
private const val TIMESTAMP_ENCODED_LENGTH = 9
/** Standard base ID length (timestamp + random) */
private const val STANDARD_BASE_LENGTH = 22
/** Ashid4 base ID length (random1 + random2) */
private const val ASHID4_BASE_LENGTH = 26

/**
 * Ashid - A time-sortable unique identifier with optional type prefix
 *
 * Format: [prefix_][timestamp][random]
 *
 * Prefix Rules:
 * - Prefix must be alphabetic only (a-z, A-Z)
 * - Delimiter (_) is automatically added - users should NOT include it
 * - If delimiter is passed in, it is ignored (backward compatibility)
 * - With prefix: variable length base ID (no timestamp padding when 0)
 * - Without prefix: fixed 22-char base ID
 *
 * Examples:
 * - ashid("user") -> "user_1kbg1jmtt4v3x8k9p2m1n" (delimiter auto-added)
 * - ashid("user_") -> "user_1kbg1jmtt4v3x8k9p2m1n" (same result, delimiter ignored)
 * - ashid() -> "1kbg1jmtt4v3x8k9p2m1n0" (no prefix, fixed 22-char base)
 *
 * Timestamp Support:
 * - Minimum: 0 (Unix epoch, Jan 1, 1970)
 * - Maximum: 35184372088831 (Dec 12, 3084)
 */
object Ashid {
    /**
     * Normalize a prefix.
     * - Removes all non-alphanumeric characters
     * - Lowercases result
     * - Adds underscore delimiter
     *
     * @param prefix Raw prefix input
     * @return Normalized prefix with delimiter, or null if empty after cleaning
     */
    private fun normalizePrefix(prefix: String?): String? {
        if (prefix.isNullOrEmpty()) return null
        val cleaned = prefix.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return if (cleaned.isEmpty()) null else cleaned + "_"
    }
    /**
     * Create a new Ashid with optional type prefix
     *
     * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
     * @param time Timestamp in milliseconds (defaults to current time, min 0, max Dec 12 3084)
     * @param randomLong Random number for uniqueness (defaults to secure random)
     * @return Ashid string
     * @throws IllegalArgumentException if timestamp is negative or exceeds maximum
     * @throws IllegalArgumentException if random value is negative
     */
    @JvmStatic
    @JvmOverloads
    fun create(
        prefix: String? = null,
        time: Long = System.currentTimeMillis(),
        randomLong: Long = EncoderBase32Crockford.secureRandomLong()
    ): String {
        val normalizedPrefix = normalizePrefix(prefix)

        // Validate timestamp
        require(time >= 0) { "Ashid timestamp must be non-negative" }
        require(time <= MAX_TIMESTAMP) { "Ashid timestamp must not exceed $MAX_TIMESTAMP (Dec 12, 3084)" }

        // Validate random value
        require(randomLong >= 0) { "Ashid random value must be non-negative" }

        val baseId = if (normalizedPrefix != null) {
            // With prefix: variable length (no padding on timestamp when 0)
            val randomEncoded = if (time > 0) {
                EncoderBase32Crockford.encode(randomLong, padded = true)
            } else {
                EncoderBase32Crockford.encode(randomLong)
            }

            if (time > 0) {
                EncoderBase32Crockford.encode(time) + randomEncoded
            } else {
                randomEncoded
            }
        } else {
            // No prefix: fixed length (pad timestamp to 9, random to 13 = 22 chars)
            val timeEncoded = EncoderBase32Crockford.encode(time).padStart(TIMESTAMP_ENCODED_LENGTH, '0')
            val randomEncoded = EncoderBase32Crockford.encode(randomLong, padded = true)
            timeEncoded + randomEncoded
        }

        return (normalizedPrefix ?: "") + baseId
    }

    /**
     * Parse an Ashid into its components
     *
     * @param id The full Ashid string
     * @return Triple of (prefix with trailing _, encodedTimestamp, encodedRandom)
     */
    @JvmStatic
    fun parse(id: String): Triple<String, String, String> {
        require(id.isNotEmpty()) { "Invalid Ashid: cannot be empty" }

        // Find delimiter (underscore or dash) - no delimiter means no prefix
        var prefixLength = 0
        var hasDelimiter = false

        for (i in id.indices) {
            when {
                id[i].isLetter() -> prefixLength++
                (id[i] == '_' || id[i] == '-') && prefixLength > 0 -> {
                    prefixLength++ // Include delimiter
                    hasDelimiter = true
                    break
                }
                else -> {
                    // No delimiter found - entire string is base ID (no prefix)
                    prefixLength = 0
                    break
                }
            }
        }

        // If no delimiter was found, the whole string is the base — even if it
        // happens to be all-alpha (e.g. an ashid4 whose random component encodes
        // to letters only).
        if (!hasDelimiter) prefixLength = 0

        // Normalize dash to underscore in prefix
        val prefix = if (hasDelimiter) id.substring(0, prefixLength).replace(Regex("-$"), "_") else ""
        val baseId = id.substring(prefixLength)

        require(baseId.isNotEmpty()) { "Invalid Ashid: must have a base ID" }

        val (encodedTimestamp, encodedRandom) = if (hasDelimiter) {
            // Variable length format (has prefix with delimiter)
            if (baseId.length <= RANDOM_ENCODED_LENGTH) {
                // Timestamp was 0 (omitted), entire baseId is random
                "0" to baseId
            } else {
                // Timestamp present, random is last 13 chars
                baseId.substring(0, baseId.length - RANDOM_ENCODED_LENGTH) to baseId.substring(baseId.length - RANDOM_ENCODED_LENGTH)
            }
        } else if (baseId.length == ASHID4_BASE_LENGTH) {
            // Fixed 26-char format (ashid4 - two random components, no prefix)
            baseId.substring(0, RANDOM_ENCODED_LENGTH) to baseId.substring(RANDOM_ENCODED_LENGTH)
        } else if (baseId.length == STANDARD_BASE_LENGTH) {
            // Fixed 22-char format (standard ashid, no prefix)
            baseId.substring(0, TIMESTAMP_ENCODED_LENGTH) to baseId.substring(TIMESTAMP_ENCODED_LENGTH)
        } else {
            throw IllegalArgumentException("Invalid Ashid: base ID must be $STANDARD_BASE_LENGTH or $ASHID4_BASE_LENGTH characters without delimiter (got ${baseId.length})")
        }

        return Triple(prefix, encodedTimestamp, encodedRandom)
    }

    /**
     * Extract the prefix from an Ashid
     *
     * @param id Ashid string
     * @return Prefix string (empty if none)
     */
    @JvmStatic
    fun prefix(id: String): String = parse(id).first

    /**
     * Extract the timestamp from an Ashid
     *
     * @param id Ashid string
     * @return Timestamp in milliseconds
     */
    @JvmStatic
    fun timestamp(id: String): Long {
        val (_, encodedTimestamp, _) = parse(id)
        return EncoderBase32Crockford.decode(encodedTimestamp)
    }

    /**
     * Extract the random component from an Ashid as Long
     * Note: For values > Long.MAX_VALUE (from ashid4), use randomULong()
     *
     * @param id Ashid string
     * @return Random number as Long
     */
    @JvmStatic
    fun random(id: String): Long {
        val (_, _, encodedRandom) = parse(id)
        return EncoderBase32Crockford.decode(encodedRandom)
    }

    /**
     * Extract the random component from an Ashid as ULong
     * Preserves full 64-bit precision for ashid4 values
     *
     * @param id Ashid string
     * @return Random number as ULong
     */
    fun randomULong(id: String): ULong {
        val (_, _, encodedRandom) = parse(id)
        return EncoderBase32Crockford.decodeULong(encodedRandom)
    }

    /**
     * Normalize an Ashid by lowercasing and converting ambiguous characters
     * (I/L→1, O→0, etc.) to their canonical form.
     *
     * Type-1 inputs round-trip to type-1 shape; full-entropy ashid4 inputs
     * round-trip to ashid4 shape (the first long naturally fills 13 chars).
     * An ashid4 with a small first long collapses to type-1 shape — the two
     * longs survive, only the string shape changes.
     *
     * @param id Ashid string (potentially with mixed case or ambiguous chars)
     * @return Normalized Ashid string
     */
    @JvmStatic
    fun normalize(id: String): String {
        val (prefix, encodedFirst, encodedSecond) = parse(id)
        val long1 = EncoderBase32Crockford.decodeULong(encodedFirst)
        val long2 = EncoderBase32Crockford.decodeULong(encodedSecond)
        val normalizedPrefix = prefix.lowercase().ifEmpty { null }
        return buildBase(normalizedPrefix, long1, long2, padded = false)
    }

    /**
     * Encode two non-negative ULong components into the canonical base ID form.
     *
     * Mirrors the TypeScript buildBase routed through create()/create4():
     *   - padded = true  → both halves 13-char zero-padded (ashid4 shape).
     *   - padded = false → first half encoded unpadded when a prefix is present;
     *                      else padded to 9 chars (timestamp width) when the raw
     *                      encoding fits in 9 chars, else padded to 13.
     * The second half is always 13-char padded.
     */
    private fun buildBase(prefix: String?, n1: ULong, n2: ULong, padded: Boolean): String {
        val normalizedPrefix = normalizePrefix(prefix)
        val hasPrefix = normalizedPrefix != null

        val encoded1 = if (hasPrefix || padded) {
            EncoderBase32Crockford.encode(n1, padded = padded)
        } else {
            val raw = EncoderBase32Crockford.encode(n1, padded = false)
            val width = if (raw.length <= TIMESTAMP_ENCODED_LENGTH) TIMESTAMP_ENCODED_LENGTH else RANDOM_ENCODED_LENGTH
            raw.padStart(width, '0')
        }
        val encoded2 = EncoderBase32Crockford.encode(n2, padded = true)
        return (normalizedPrefix ?: "") + encoded1 + encoded2
    }

    /**
     * Create a random Ashid (UUID v4 equivalent) with consistent 0-padded format
     *
     * Unlike create() which uses timestamp + random for time-sortability,
     * create4() uses two random values with consistent padding for maximum entropy.
     * Both components are padded to 13 chars each = 26 char base (128 bits of entropy).
     *
     * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
     * @param random1 First random value (defaults to secure random ULong)
     * @param random2 Second random value (defaults to secure random ULong)
     * @return Ashid string with two random components, consistently padded
     */
    fun create4(
        prefix: String? = null,
        random1: ULong = EncoderBase32Crockford.secureRandomULong(),
        random2: ULong = EncoderBase32Crockford.secureRandomULong()
    ): String {
        val normalizedPrefix = normalizePrefix(prefix)

        // Both components padded to 13 chars for maximum entropy (26 char base)
        val encoded1 = EncoderBase32Crockford.encode(random1, padded = true)
        val encoded2 = EncoderBase32Crockford.encode(random2, padded = true)
        val baseId = encoded1 + encoded2

        return (normalizedPrefix ?: "") + baseId
    }

    /**
     * Create a random Ashid (UUID v4 equivalent) - Long overload for Java compatibility
     * Note: For full 64-bit entropy, use the ULong version
     *
     * @param prefix Optional alphabetic prefix
     * @param random1 First random number (non-negative Long)
     * @param random2 Second random number (non-negative Long)
     * @return Ashid string with two random components
     */
    @JvmStatic
    fun create4Long(
        prefix: String? = null,
        random1: Long,
        random2: Long
    ): String {
        require(random1 >= 0 && random2 >= 0) { "Ashid random values must be non-negative" }
        return create4(prefix, random1.toULong(), random2.toULong())
    }

    /**
     * Convert an Ashid to its UUID-shaped representation.
     *
     * An Ashid encodes 128 bits of information (a 64-bit timestamp or first random
     * component plus a 64-bit random component); this method emits those 128 bits
     * as a [java.util.UUID].
     *
     * Round-trips losslessly with [fromUuid]. The resulting UUID is shape-compatible
     * with RFC 4122 but the version/variant bits reflect the underlying Ashid bytes,
     * not RFC 4122 conventions, unless the Ashid was originally created from a
     * v1/v4/v7 UUID.
     */
    @JvmStatic
    fun toUuid(id: String): java.util.UUID {
        val (_, encodedTimestamp, encodedRandom) = parse(id)
        val high = EncoderBase32Crockford.decodeULong(encodedTimestamp)
        val low = EncoderBase32Crockford.decodeULong(encodedRandom)
        return java.util.UUID(high.toLong(), low.toLong())
    }

    /**
     * Convert a UUID into an Ashid.
     *
     * Splits the 128-bit UUID into two 64-bit halves. If the high half fits in 45 bits
     * (≤ MAX_TIMESTAMP), the result is a standard 22-char Ashid base. Otherwise — for
     * UUIDv4 (random) and UUIDv7 (whose version bits force the high half above 2^45) —
     * the result is a 26-char ashid4 base.
     *
     * Round-trip through [toUuid] is byte-identical in both cases.
     */
    @JvmStatic
    @JvmOverloads
    fun fromUuid(uuid: java.util.UUID, prefix: String? = null): String {
        val high = uuid.mostSignificantBits.toULong()
        val low = uuid.leastSignificantBits.toULong()
        return if (high <= MAX_TIMESTAMP.toULong()) {
            createWithULongRandom(prefix, high.toLong(), low)
        } else {
            create4(prefix, high, low)
        }
    }

    /**
     * Convert a UUID string (36-char dashed or 32-char hex) into an Ashid.
     * See [fromUuid] for routing behavior.
     */
    @JvmStatic
    @JvmOverloads
    fun fromUuid(uuid: String, prefix: String? = null): String {
        val hex = uuid.replace("-", "").lowercase()
        require(hex.length == 32 && hex.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Invalid UUID: must be 32 or 36 hex characters (got \"$uuid\")"
        }
        return fromUuid(java.util.UUID(hex.substring(0, 16).toULong(16).toLong(), hex.substring(16, 32).toULong(16).toLong()), prefix)
    }

    /**
     * Internal builder used by [fromUuid] when the random component might exceed
     * Long.MAX_VALUE. Mirrors [create] but takes the random as ULong so a UUID's
     * full low-64-bits round-trip cleanly.
     */
    private fun createWithULongRandom(prefix: String?, time: Long, randomLong: ULong): String {
        val normalizedPrefix = normalizePrefix(prefix)
        require(time in 0..MAX_TIMESTAMP) { "Ashid timestamp must be in [0, $MAX_TIMESTAMP]" }

        val baseId = if (normalizedPrefix != null) {
            val randomEncoded = if (time > 0) {
                EncoderBase32Crockford.encode(randomLong, padded = true)
            } else {
                EncoderBase32Crockford.encode(randomLong)
            }
            if (time > 0) {
                EncoderBase32Crockford.encode(time) + randomEncoded
            } else {
                randomEncoded
            }
        } else {
            val timeEncoded = EncoderBase32Crockford.encode(time).padStart(TIMESTAMP_ENCODED_LENGTH, '0')
            val randomEncoded = EncoderBase32Crockford.encode(randomLong, padded = true)
            timeEncoded + randomEncoded
        }
        return (normalizedPrefix ?: "") + baseId
    }

    /**
     * Validate if a string is a valid Ashid
     *
     * @param id String to validate
     * @return true if valid Ashid format
     */
    @JvmStatic
    fun isValid(id: String): Boolean {
        if (id.isEmpty()) return false

        return try {
            val (prefix, encodedTimestamp, encodedRandom) = parse(id)

            // Validate prefix format (must be alphanumeric ending with _)
            if (prefix.isNotEmpty() && !prefix.matches(Regex("^[a-zA-Z0-9]+_$"))) {
                return false
            }

            // Validate base32 encoding
            EncoderBase32Crockford.decode(encodedTimestamp)
            EncoderBase32Crockford.decode(encodedRandom)

            true
        } catch (e: Exception) {
            false
        }
    }

}

/**
 * Create a new Ashid with optional type prefix
 *
 * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
 * @return Ashid string
 */
fun ashid(prefix: String? = null): String = Ashid.create(prefix)

/**
 * Create a new Ashid with two random components (UUID v4 equivalent)
 *
 * Unlike the standard ashid() which uses timestamp + random for time-sortability,
 * ashid4() uses two random values for maximum entropy when time-sorting is not needed.
 * Always produces consistently 0-padded output (26 char base).
 * Useful for tokens, secrets, or any ID where unpredictability is more important than ordering.
 *
 * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
 * @return Ashid string with two random components, consistently padded
 */
fun ashid4(prefix: String? = null): String = Ashid.create4(prefix)
