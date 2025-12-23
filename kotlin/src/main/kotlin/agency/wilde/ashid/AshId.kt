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
 * - ashid() -> "0000000001kbg1jmtt4v3x8k9" (no prefix, fixed 22-char base)
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
     * Extract the random component from an Ashid
     *
     * @param id Ashid string
     * @return Random number
     */
    @JvmStatic
    fun random(id: String): Long {
        val (_, _, encodedRandom) = parse(id)
        return EncoderBase32Crockford.decode(encodedRandom)
    }

    /**
     * Normalize an Ashid by lowercasing and converting ambiguous characters
     * (I/L→1, O→0, etc.) to their canonical form.
     *
     * @param id Ashid string (potentially with mixed case or ambiguous chars)
     * @return Normalized Ashid string
     */
    @JvmStatic
    fun normalize(id: String): String {
        val (prefix, encodedTimestamp, encodedRandom) = parse(id)
        val timestamp = EncoderBase32Crockford.decode(encodedTimestamp)
        val random = EncoderBase32Crockford.decode(encodedRandom)
        // Lowercase the prefix
        val normalizedPrefix = prefix.lowercase().ifEmpty { null }
        return create(normalizedPrefix, timestamp, random)
    }

    /**
     * Create a random Ashid (UUID v4 equivalent) with consistent 0-padded format
     *
     * Unlike create() which uses timestamp + random for time-sortability,
     * create4() uses two random values with consistent padding for maximum entropy.
     * Both components are padded to 13 chars each = 26 char base (~106 bits of entropy).
     *
     * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
     * @param random1 First random number (defaults to secure random)
     * @param random2 Second random number (defaults to secure random)
     * @return Ashid string with two random components, consistently padded
     * @throws IllegalArgumentException if random values are negative
     */
    @JvmStatic
    @JvmOverloads
    fun create4(
        prefix: String? = null,
        random1: Long = EncoderBase32Crockford.secureRandomLong(),
        random2: Long = EncoderBase32Crockford.secureRandomLong()
    ): String {
        val normalizedPrefix = normalizePrefix(prefix)

        // Validate random values
        require(random1 >= 0 && random2 >= 0) { "Ashid random values must be non-negative" }

        // Both components padded to 13 chars for maximum entropy (26 char base)
        val encoded1 = EncoderBase32Crockford.encode(random1, padded = true)
        val encoded2 = EncoderBase32Crockford.encode(random2, padded = true)
        val baseId = encoded1 + encoded2

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
