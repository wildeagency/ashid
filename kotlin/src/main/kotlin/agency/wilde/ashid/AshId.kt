package agency.wilde.ashid

/**
 * Maximum supported timestamp (Dec 12, 3084)
 * Beyond this, timestamp encoding exceeds 9 characters
 */
private const val MAX_TIMESTAMP = 35184372088831L

/**
 * Ashid - A time-sortable unique identifier with optional type prefix
 *
 * Format: [prefix][timestamp][random]
 *
 * Prefix Rules:
 * - With underscore delimiter (e.g., "user_"): variable length base ID allowed
 *   - Timestamp: no padding (variable length)
 *   - Random: padded to 13 chars when timestamp > 0, no padding when timestamp = 0
 * - Without underscore (e.g., "u"): fixed 22-char base ID required for parsing
 *   - Timestamp: padded to 9 chars
 *   - Random: padded to 13 chars
 * - No prefix: same as without underscore (fixed 22-char base ID)
 *
 * This allows reliable parsing of all components in both cases.
 *
 * Timestamp Support:
 * - Minimum: 0 (Unix epoch, Jan 1, 1970)
 * - Maximum: 35184372088831 (Dec 12, 3084)
 */
object Ashid {
    /**
     * Create a new Ashid with optional type prefix
     *
     * @param prefix Optional alphabetic prefix (may include trailing underscore as delimiter)
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
        // Validate and normalize prefix: must be alphabetic with optional trailing underscore/dash
        val normalizedPrefix = if (!prefix.isNullOrEmpty()) {
            // Convert dash to underscore for consistency with TypeScript
            val withUnderscore = prefix.replace(Regex("-$"), "_")
            require(withUnderscore.matches(Regex("^[a-zA-Z]+_?$"))) {
                "Ashid prefix must contain only letters with optional trailing underscore or dash"
            }
            withUnderscore.lowercase()
        } else {
            null
        }

        // Validate timestamp
        require(time >= 0) { "Ashid timestamp must be non-negative" }
        require(time <= MAX_TIMESTAMP) { "Ashid timestamp must not exceed $MAX_TIMESTAMP (Dec 12, 3084)" }

        // Validate random value
        require(randomLong >= 0) { "Ashid random value must be non-negative" }

        // Determine if prefix has underscore delimiter
        val hasDelimiter = normalizedPrefix?.endsWith('_') == true

        val baseId = if (hasDelimiter) {
            // Variable length: no padding on timestamp, pad random only when timestamp > 0
            val randomEncoded = if (time > 0) {
                EncoderBase32Crockford.encode(randomLong, padded = true) // padded to 13
            } else {
                EncoderBase32Crockford.encode(randomLong) // no padding
            }

            if (time > 0) {
                EncoderBase32Crockford.encode(time) + randomEncoded
            } else {
                randomEncoded // timestamp 0 is omitted
            }
        } else {
            // Fixed length: pad timestamp to 9, random to 13 = 22 chars total
            val timeEncoded = EncoderBase32Crockford.encode(time).padStart(9, '0')
            val randomEncoded = EncoderBase32Crockford.encode(randomLong, padded = true) // padded to 13
            timeEncoded + randomEncoded
        }

        return (normalizedPrefix ?: "") + baseId
    }

    /**
     * Parse an Ashid into its components
     *
     * @param id The full Ashid string
     * @return Triple of (prefix, encodedTimestamp, encodedRandom)
     */
    @JvmStatic
    fun parse(id: String): Triple<String, String, String> {
        require(id.isNotEmpty()) { "Invalid Ashid: cannot be empty" }

        // Find the prefix: leading alphabetic characters with optional trailing underscore or dash
        var prefixLength = 0
        var hasDelimiter = false

        // First pass: find if there's a delimiter
        for (i in id.indices) {
            when {
                id[i].isLetter() -> prefixLength++
                (id[i] == '_' || id[i] == '-') && prefixLength > 0 -> {
                    prefixLength++
                    hasDelimiter = true
                    break
                }
                else -> break
            }
        }

        // If no delimiter found, limit prefix so that base is exactly 22 chars
        if (!hasDelimiter && id.length > 22) {
            prefixLength = id.length - 22
            // Validate that the prefix is all letters
            for (i in 0 until prefixLength) {
                if (!id[i].isLetter()) {
                    prefixLength = 0 // No valid prefix
                    break
                }
            }
        }

        // Normalize dash to underscore in prefix
        val prefix = id.substring(0, prefixLength).replace(Regex("-$"), "_")
        val baseId = id.substring(prefixLength)

        require(baseId.isNotEmpty()) { "Invalid Ashid: must have a base ID after prefix" }

        val (encodedTimestamp, encodedRandom) = if (hasDelimiter) {
            // Variable length format
            if (baseId.length <= 13) {
                // Timestamp was 0 (omitted), entire baseId is random
                "0" to baseId
            } else {
                // Timestamp present, random is last 13 chars
                baseId.substring(0, baseId.length - 13) to baseId.substring(baseId.length - 13)
            }
        } else {
            // Fixed 22-char format
            require(baseId.length == 22) {
                "Invalid Ashid: base ID must be 22 characters without underscore delimiter (got ${baseId.length})"
            }
            baseId.substring(0, 9) to baseId.substring(9)
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

            // Validate prefix format
            if (prefix.isNotEmpty() && !prefix.matches(Regex("^[a-zA-Z]+_?$"))) {
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
 * @param prefix Optional entity type prefix (letters with optional trailing underscore)
 * @return Ashid string
 */
fun ashid(prefix: String? = null): String = Ashid.create(prefix)
