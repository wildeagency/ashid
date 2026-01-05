package agency.wilde.ashid

import java.security.SecureRandom

/**
 * Crockford Base32 Encoder
 *
 * Douglas Crockford's Base32 encoding provides:
 * - Case-insensitive encoding (0-9, a-z)
 * - Lookalike character mapping (I/L -> 1, O -> 0, U -> V)
 * - No special characters
 * - Human-readable and error-resistant
 *
 * Alphabet: 0123456789abcdefghjkmnpqrstvwxyz (32 characters)
 * Excluded: i, l, o, u (mapped to lookalikes during decode)
 */
object EncoderBase32Crockford {
    private const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
    private val secureRandom = SecureRandom()

    /**
     * Encode a non-negative long to Crockford Base32 string
     *
     * @param n Non-negative long to encode
     * @param padded If true, pad result to 13 characters (for consistent ashid length)
     * @return Base32 encoded string
     */
    @JvmStatic
    @JvmOverloads
    fun encode(n: Long, padded: Boolean = false): String {
        require(n >= 0) { "Input must be a non-negative number" }

        val encoded = encodeRecursive(n)
        return if (padded) encoded.padStart(13, '0') else encoded
    }

    /**
     * Encode an unsigned long (ULong) to Crockford Base32 string
     * Supports full 64-bit range for maximum entropy
     *
     * @param n Unsigned long to encode
     * @param padded If true, pad result to 13 characters (for consistent ashid length)
     * @return Base32 encoded string
     */
    fun encode(n: ULong, padded: Boolean = false): String {
        val encoded = encodeRecursiveULong(n)
        return if (padded) encoded.padStart(13, '0') else encoded
    }

    /**
     * Recursive encoding implementation
     */
    private fun encodeRecursive(n: Long): String {
        if (n == 0L) return "0"

        val remainder = (n % 32).toInt()
        val quotient = n / 32

        return if (quotient == 0L) {
            ALPHABET[remainder].toString()
        } else {
            encodeRecursive(quotient) + ALPHABET[remainder]
        }
    }

    /**
     * Recursive encoding implementation for ULong
     */
    private fun encodeRecursiveULong(n: ULong): String {
        if (n == 0UL) return "0"

        val remainder = (n % 32UL).toInt()
        val quotient = n / 32UL

        return if (quotient == 0UL) {
            ALPHABET[remainder].toString()
        } else {
            encodeRecursiveULong(quotient) + ALPHABET[remainder]
        }
    }

    /**
     * Decode a Crockford Base32 string to long
     *
     * @param str Base32 encoded string
     * @return Decoded long
     */
    @JvmStatic
    fun decode(str: String): Long {
        require(str.isNotEmpty()) { "Input string cannot be empty" }

        var result = 0L
        val normalized = str.lowercase()

        for (char in normalized) {
            val value = decodeChar(char)
            require(value != -1) { "Invalid character in Base32 string: '$char'" }
            result = result * 32 + value
        }

        return result
    }

    /**
     * Decode a Crockford Base32 string to unsigned long (ULong)
     * Preserves full 64-bit precision
     *
     * @param str Base32 encoded string
     * @return Decoded ULong
     */
    fun decodeULong(str: String): ULong {
        require(str.isNotEmpty()) { "Input string cannot be empty" }

        var result = 0UL
        val normalized = str.lowercase()

        for (char in normalized) {
            val value = decodeChar(char)
            require(value != -1) { "Invalid character in Base32 string: '$char'" }
            result = result * 32UL + value.toULong()
        }

        return result
    }

    /**
     * Decode a single character, mapping lookalikes
     */
    private fun decodeChar(char: Char): Int = when (char) {
        '0', 'o', 'O' -> 0
        '1', 'i', 'I', 'l', 'L' -> 1
        '2' -> 2
        '3' -> 3
        '4' -> 4
        '5' -> 5
        '6' -> 6
        '7' -> 7
        '8' -> 8
        '9' -> 9
        'a', 'A' -> 10
        'b', 'B' -> 11
        'c', 'C' -> 12
        'd', 'D' -> 13
        'e', 'E' -> 14
        'f', 'F' -> 15
        'g', 'G' -> 16
        'h', 'H' -> 17
        'j', 'J' -> 18
        'k', 'K' -> 19
        'm', 'M' -> 20
        'n', 'N' -> 21
        'p', 'P' -> 22
        'q', 'Q' -> 23
        'r', 'R' -> 24
        's', 'S' -> 25
        't', 'T' -> 26
        'u', 'U', 'v', 'V' -> 27
        'w', 'W' -> 28
        'x', 'X' -> 29
        'y', 'Y' -> 30
        'z', 'Z' -> 31
        else -> -1
    }

    /**
     * Generate a cryptographically secure random unsigned long (ULong)
     * Returns full 64-bit entropy, like UUID does
     *
     * @return Random ULong with full 64-bit range
     */
    fun secureRandomULong(): ULong {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)

        // Convert to ULong - full 64-bit range, no sign bit masking
        var value = 0UL
        for (byte in bytes) {
            value = (value shl 8) or (byte.toUByte().toULong())
        }

        return value
    }

    /**
     * Generate a cryptographically secure random positive long
     * Note: Limited to 63 bits. For full 64-bit entropy, use secureRandomULong()
     *
     * @return Random long within safe range (0 to Long.MAX_VALUE)
     */
    @JvmStatic
    fun secureRandomLong(): Long {
        // Generate random bytes and convert to positive long
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)

        // Convert to long and ensure positive by masking sign bit
        var value = 0L
        for (i in bytes.indices) {
            value = (value shl 8) or (bytes[i].toLong() and 0xFF)
        }

        // Ensure positive and within reasonable range
        return value and Long.MAX_VALUE
    }

    /**
     * Validate if a string is valid Crockford Base32
     */
    @JvmStatic
    fun isValid(str: String): Boolean {
        if (str.isEmpty()) return false

        return try {
            decode(str)
            true
        } catch (e: Exception) {
            false
        }
    }
}
