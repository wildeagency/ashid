package agency.wilde.ashid;

import java.security.SecureRandom;

/**
 * Crockford Base32 encoder/decoder for Ashid
 *
 * Uses the Crockford Base32 alphabet which:
 * - Is case-insensitive
 * - Maps lookalike characters (O→0, I/L→1, U→V)
 * - Excludes I, L, O, U from the alphabet
 */
public final class EncoderBase32Crockford {

    /**
     * The Crockford Base32 alphabet (excludes I, L, O, U)
     */
    private static final String ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz";

    /**
     * Maximum safe integer for JavaScript compatibility (2^53 - 1)
     */
    private static final long MAX_SAFE_INTEGER = 9007199254740991L;

    /**
     * Standard padding length for random component
     */
    private static final int PADDING_LENGTH = 13;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private EncoderBase32Crockford() {
        // Utility class, prevent instantiation
    }

    /**
     * Encode a non-negative long to Crockford Base32
     *
     * @param n The number to encode (must be non-negative)
     * @return The Base32 encoded string
     * @throws IllegalArgumentException if n is negative
     */
    public static String encode(long n) {
        return encode(n, false);
    }

    /**
     * Encode a non-negative long to Crockford Base32
     *
     * @param n The number to encode (must be non-negative)
     * @param padded If true, pad to 13 characters with leading zeros
     * @return The Base32 encoded string
     * @throws IllegalArgumentException if n is negative
     */
    public static String encode(long n, boolean padded) {
        if (n < 0) {
            throw new IllegalArgumentException("Input must be a non-negative number");
        }

        String encoded = encodeRecursive(n);

        if (padded) {
            StringBuilder sb = new StringBuilder();
            for (int i = encoded.length(); i < PADDING_LENGTH; i++) {
                sb.append('0');
            }
            sb.append(encoded);
            return sb.toString();
        }

        return encoded;
    }

    private static String encodeRecursive(long n) {
        if (n == 0) {
            return "0";
        }

        int remainder = (int) (n % 32);
        long quotient = n / 32;

        if (quotient == 0) {
            return String.valueOf(ALPHABET.charAt(remainder));
        }

        return encodeRecursive(quotient) + ALPHABET.charAt(remainder);
    }

    /**
     * Decode a Crockford Base32 string to a long
     *
     * Handles case-insensitivity and lookalike character mapping:
     * - O/o → 0
     * - I/i, L/l → 1
     * - U/u → V (value 27)
     *
     * @param str The Base32 string to decode
     * @return The decoded number
     * @throws IllegalArgumentException if string is empty or contains invalid characters
     */
    public static long decode(String str) {
        if (str == null || str.isEmpty()) {
            throw new IllegalArgumentException("Input string cannot be empty");
        }

        long result = 0;
        String normalized = str.toLowerCase();

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            int value = decodeChar(c);

            if (value == -1) {
                throw new IllegalArgumentException("Invalid character in Base32 string: '" + c + "'");
            }

            result = result * 32 + value;
        }

        return result;
    }

    /**
     * Decode a single character to its numeric value
     * Handles lookalike character mapping
     */
    private static int decodeChar(char c) {
        switch (c) {
            case '0': case 'o': return 0;
            case '1': case 'i': case 'l': return 1;
            case '2': return 2;
            case '3': return 3;
            case '4': return 4;
            case '5': return 5;
            case '6': return 6;
            case '7': return 7;
            case '8': return 8;
            case '9': return 9;
            case 'a': return 10;
            case 'b': return 11;
            case 'c': return 12;
            case 'd': return 13;
            case 'e': return 14;
            case 'f': return 15;
            case 'g': return 16;
            case 'h': return 17;
            case 'j': return 18;
            case 'k': return 19;
            case 'm': return 20;
            case 'n': return 21;
            case 'p': return 22;
            case 'q': return 23;
            case 'r': return 24;
            case 's': return 25;
            case 't': return 26;
            case 'u': case 'v': return 27;
            case 'w': return 28;
            case 'x': return 29;
            case 'y': return 30;
            case 'z': return 31;
            default: return -1;
        }
    }

    /**
     * Generate a cryptographically secure random long
     *
     * @return A random long between 0 and MAX_SAFE_INTEGER (inclusive)
     */
    public static long secureRandomLong() {
        // Generate 7 random bytes (56 bits) to stay within safe integer range
        byte[] bytes = new byte[7];
        SECURE_RANDOM.nextBytes(bytes);

        long result = 0;
        for (int i = 0; i < 7; i++) {
            result = (result << 8) | (bytes[i] & 0xFF);
        }

        // Ensure positive and within safe integer range
        return Math.abs(result) % (MAX_SAFE_INTEGER + 1);
    }

    /**
     * Check if a string is valid Crockford Base32
     *
     * @param str The string to validate
     * @return true if valid Base32, false otherwise
     */
    public static boolean isValid(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        try {
            decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
