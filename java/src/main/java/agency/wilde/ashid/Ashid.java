package agency.wilde.ashid;

import java.util.regex.Pattern;

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
 * Timestamp Support:
 * - Minimum: 0 (Unix epoch, Jan 1, 1970)
 * - Maximum: 35184372088831 (Dec 12, 3084)
 */
public final class Ashid {

    /**
     * Maximum supported timestamp (Dec 12, 3084)
     * Beyond this, timestamp encoding exceeds 9 characters
     */
    private static final long MAX_TIMESTAMP = 35184372088831L;

    private static final Pattern PREFIX_PATTERN = Pattern.compile("^[a-zA-Z]+_?$");
    private static final Pattern TRAILING_DASH_PATTERN = Pattern.compile("-$");

    private Ashid() {
        // Utility class, prevent instantiation
    }

    /**
     * Create a new Ashid with current timestamp and secure random
     *
     * @return Ashid string
     */
    public static String create() {
        return create(null, System.currentTimeMillis(), EncoderBase32Crockford.secureRandomLong());
    }

    /**
     * Create a new Ashid with optional prefix
     *
     * @param prefix Optional alphabetic prefix (may include trailing underscore as delimiter)
     * @return Ashid string
     */
    public static String create(String prefix) {
        return create(prefix, System.currentTimeMillis(), EncoderBase32Crockford.secureRandomLong());
    }

    /**
     * Create a new Ashid with optional prefix and specific timestamp
     *
     * @param prefix Optional alphabetic prefix (may include trailing underscore as delimiter)
     * @param time Timestamp in milliseconds (min 0, max Dec 12 3084)
     * @return Ashid string
     */
    public static String create(String prefix, long time) {
        return create(prefix, time, EncoderBase32Crockford.secureRandomLong());
    }

    /**
     * Create a new Ashid with full control over all components
     *
     * @param prefix Optional alphabetic prefix (may include trailing underscore or dash as delimiter)
     * @param time Timestamp in milliseconds (min 0, max Dec 12 3084)
     * @param randomLong Random number for uniqueness
     * @return Ashid string
     * @throws IllegalArgumentException if timestamp is negative or exceeds maximum
     * @throws IllegalArgumentException if random value is negative
     * @throws IllegalArgumentException if prefix contains invalid characters
     */
    public static String create(String prefix, long time, long randomLong) {
        // Validate and normalize prefix
        String normalizedPrefix = null;
        if (prefix != null && !prefix.isEmpty()) {
            // Convert dash to underscore for consistency with TypeScript
            String withUnderscore = TRAILING_DASH_PATTERN.matcher(prefix).replaceAll("_");
            if (!PREFIX_PATTERN.matcher(withUnderscore).matches()) {
                throw new IllegalArgumentException(
                    "Ashid prefix must contain only letters with optional trailing underscore or dash"
                );
            }
            normalizedPrefix = withUnderscore.toLowerCase();
        }

        // Validate timestamp
        if (time < 0) {
            throw new IllegalArgumentException("Ashid timestamp must be non-negative");
        }
        if (time > MAX_TIMESTAMP) {
            throw new IllegalArgumentException(
                "Ashid timestamp must not exceed " + MAX_TIMESTAMP + " (Dec 12, 3084)"
            );
        }

        // Validate random value
        if (randomLong < 0) {
            throw new IllegalArgumentException("Ashid random value must be non-negative");
        }

        // Determine if prefix has underscore delimiter
        boolean hasDelimiter = normalizedPrefix != null && normalizedPrefix.endsWith("_");

        String baseId;
        if (hasDelimiter) {
            // Variable length: no padding on timestamp, pad random only when timestamp > 0
            String randomEncoded;
            if (time > 0) {
                randomEncoded = EncoderBase32Crockford.encode(randomLong, true); // padded to 13
            } else {
                randomEncoded = EncoderBase32Crockford.encode(randomLong); // no padding
            }

            if (time > 0) {
                baseId = EncoderBase32Crockford.encode(time) + randomEncoded;
            } else {
                baseId = randomEncoded; // timestamp 0 is omitted
            }
        } else {
            // Fixed length: pad timestamp to 9, random to 13 = 22 chars total
            String timeEncoded = EncoderBase32Crockford.encode(time);
            StringBuilder sb = new StringBuilder();
            for (int i = timeEncoded.length(); i < 9; i++) {
                sb.append('0');
            }
            sb.append(timeEncoded);
            String randomEncoded = EncoderBase32Crockford.encode(randomLong, true); // padded to 13
            baseId = sb.toString() + randomEncoded;
        }

        return (normalizedPrefix != null ? normalizedPrefix : "") + baseId;
    }

    /**
     * Parse result containing prefix, encoded timestamp, and encoded random
     */
    public static class ParseResult {
        private final String prefix;
        private final String encodedTimestamp;
        private final String encodedRandom;

        public ParseResult(String prefix, String encodedTimestamp, String encodedRandom) {
            this.prefix = prefix;
            this.encodedTimestamp = encodedTimestamp;
            this.encodedRandom = encodedRandom;
        }

        public String getPrefix() { return prefix; }
        public String getEncodedTimestamp() { return encodedTimestamp; }
        public String getEncodedRandom() { return encodedRandom; }
    }

    /**
     * Parse an Ashid into its components
     *
     * @param id The full Ashid string
     * @return ParseResult containing (prefix, encodedTimestamp, encodedRandom)
     * @throws IllegalArgumentException if the Ashid is invalid
     */
    public static ParseResult parse(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("Invalid Ashid: cannot be empty");
        }

        // Find the prefix: leading alphabetic characters with optional trailing underscore or dash
        int prefixLength = 0;
        boolean hasDelimiter = false;

        // First pass: find if there's a delimiter
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isLetter(c)) {
                prefixLength++;
            } else if ((c == '_' || c == '-') && prefixLength > 0) {
                prefixLength++;
                hasDelimiter = true;
                break;
            } else {
                break;
            }
        }

        // If no delimiter found, limit prefix so that base is exactly 22 chars
        if (!hasDelimiter && id.length() > 22) {
            prefixLength = id.length() - 22;
            // Validate that the prefix is all letters
            for (int i = 0; i < prefixLength; i++) {
                if (!Character.isLetter(id.charAt(i))) {
                    prefixLength = 0; // No valid prefix
                    break;
                }
            }
        }

        // Normalize dash to underscore in prefix
        String prefix = id.substring(0, prefixLength).replaceAll("-$", "_");
        String baseId = id.substring(prefixLength);

        if (baseId.isEmpty()) {
            throw new IllegalArgumentException("Invalid Ashid: must have a base ID after prefix");
        }

        String encodedTimestamp;
        String encodedRandom;

        if (hasDelimiter) {
            // Variable length format
            if (baseId.length() <= 13) {
                // Timestamp was 0 (omitted), entire baseId is random
                encodedTimestamp = "0";
                encodedRandom = baseId;
            } else {
                // Timestamp present, random is last 13 chars
                encodedTimestamp = baseId.substring(0, baseId.length() - 13);
                encodedRandom = baseId.substring(baseId.length() - 13);
            }
        } else {
            // Fixed 22-char format
            if (baseId.length() != 22) {
                throw new IllegalArgumentException(
                    "Invalid Ashid: base ID must be 22 characters without underscore delimiter (got " + baseId.length() + ")"
                );
            }
            encodedTimestamp = baseId.substring(0, 9);
            encodedRandom = baseId.substring(9);
        }

        return new ParseResult(prefix, encodedTimestamp, encodedRandom);
    }

    /**
     * Extract the prefix from an Ashid
     *
     * @param id Ashid string
     * @return Prefix string (empty if none)
     */
    public static String prefix(String id) {
        return parse(id).getPrefix();
    }

    /**
     * Extract the timestamp from an Ashid
     *
     * @param id Ashid string
     * @return Timestamp in milliseconds
     */
    public static long timestamp(String id) {
        ParseResult result = parse(id);
        return EncoderBase32Crockford.decode(result.getEncodedTimestamp());
    }

    /**
     * Extract the random component from an Ashid
     *
     * @param id Ashid string
     * @return Random number
     */
    public static long random(String id) {
        ParseResult result = parse(id);
        return EncoderBase32Crockford.decode(result.getEncodedRandom());
    }

    /**
     * Normalize an Ashid by lowercasing and converting ambiguous characters
     * (I/L→1, O→0, etc.) to their canonical form.
     *
     * @param id Ashid string (potentially with mixed case or ambiguous chars)
     * @return Normalized Ashid string
     */
    public static String normalize(String id) {
        ParseResult result = parse(id);
        long timestamp = EncoderBase32Crockford.decode(result.getEncodedTimestamp());
        long random = EncoderBase32Crockford.decode(result.getEncodedRandom());
        String normalizedPrefix = result.getPrefix().isEmpty() ? null : result.getPrefix().toLowerCase();
        return create(normalizedPrefix, timestamp, random);
    }

    /**
     * Validate if a string is a valid Ashid
     *
     * @param id String to validate
     * @return true if valid Ashid format
     */
    public static boolean isValid(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }

        try {
            ParseResult result = parse(id);

            // Validate prefix format
            String prefix = result.getPrefix();
            if (!prefix.isEmpty() && !PREFIX_PATTERN.matcher(prefix).matches()) {
                return false;
            }

            // Validate base32 encoding
            EncoderBase32Crockford.decode(result.getEncodedTimestamp());
            EncoderBase32Crockford.decode(result.getEncodedRandom());

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
