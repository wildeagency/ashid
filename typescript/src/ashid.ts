import { EncoderBase32Crockford } from './encoder';

/**
 * Maximum supported timestamp (Dec 12, 3084)
 * Beyond this, timestamp encoding exceeds 9 characters
 */
const MAX_TIMESTAMP = 35184372088831;

/** Length of the random component when encoded (padded) */
const RANDOM_ENCODED_LENGTH = 13;
/** Length of the timestamp component when encoded (padded) */
const TIMESTAMP_ENCODED_LENGTH = 9;
/** Standard base ID length (timestamp + random) */
const STANDARD_BASE_LENGTH = 22;
/** Ashid4 base ID length (random1 + random2) */
const ASHID4_BASE_LENGTH = 26;

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
 * - ashid('user') -> "user_1kbg1jmtt4v3x8k9p2m1n" (delimiter auto-added)
 * - ashid('user_') -> "user_1kbg1jmtt4v3x8k9p2m1n" (same result, delimiter ignored)
 * - ashid() -> "0000000001kbg1jmtt4v3x8k9" (no prefix, fixed 22-char base)
 * - "user_0" (prefix with timestamp 0, random 0 - explicitly passed)
 *
 * Timestamp Support:
 * - Minimum: 0 (Unix epoch, Jan 1, 1970)
 * - Maximum: 35184372088831 (Dec 12, 3084)
 */
export class Ashid {
  /**
   * Normalize a prefix.
   * - Removes all non-alphanumeric characters
   * - Lowercases result
   * - Adds underscore delimiter
   *
   * @param prefix Raw prefix input
   * @returns Normalized prefix with delimiter, or undefined if empty after cleaning
   */
  private static normalizePrefix(prefix?: string): string | undefined {
    if (prefix === undefined || prefix === '') return undefined;
    const cleaned = prefix.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
    return cleaned === '' ? undefined : cleaned + '_';
  }
  /**
   * Create a new Ashid with optional type prefix
   *
   * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
   * @param time Timestamp in milliseconds (defaults to current time, min 0, max Dec 12 3084)
   * @param randomLong Random value for uniqueness (defaults to secure random 64-bit BigInt)
   * @returns Ashid string
   * @throws Error if timestamp is negative or exceeds maximum
   * @throws Error if random value is negative
   */
  static create(
    prefix?: string,
    time: number = Date.now(),
    randomLong: number | bigint = EncoderBase32Crockford.secureRandomLong()
  ): string {
    const normalizedPrefix = this.normalizePrefix(prefix);

    // Validate timestamp
    const flooredTime = Math.floor(time);
    if (flooredTime < 0) {
      throw new Error('Ashid timestamp must be non-negative');
    }
    if (flooredTime > MAX_TIMESTAMP) {
      throw new Error(`Ashid timestamp must not exceed ${MAX_TIMESTAMP} (Dec 12, 3084)`);
    }

    // Validate and normalize random value
    const randomBigInt = typeof randomLong === 'bigint' ? randomLong : BigInt(Math.floor(randomLong));
    if (randomBigInt < 0n) {
      throw new Error('Ashid random value must be non-negative');
    }

    let baseId: string;

    if (normalizedPrefix) {
      // With prefix: variable length (no padding on timestamp when 0)
      const randomEncoded = flooredTime > 0
        ? EncoderBase32Crockford.encodeBigInt(randomBigInt, true)
        : EncoderBase32Crockford.encodeBigInt(randomBigInt);

      baseId = flooredTime > 0
        ? EncoderBase32Crockford.encode(flooredTime) + randomEncoded
        : randomEncoded;
    } else {
      // No prefix: fixed length (pad timestamp to 9, random to 13 = 22 chars)
      const timeEncoded = EncoderBase32Crockford.encode(flooredTime).padStart(TIMESTAMP_ENCODED_LENGTH, '0');
      const randomEncoded = EncoderBase32Crockford.encodeBigInt(randomBigInt, true);
      baseId = timeEncoded + randomEncoded;
    }

    return (normalizedPrefix || '') + baseId;
  }

  /**
   * Parse an Ashid into its components
   *
   * @param id The full Ashid string
   * @returns Tuple of [prefix, encodedTimestamp, encodedRandom]
   *          - prefix: string with trailing _ (empty if none)
   *          - encodedTimestamp: string (base32 encoded)
   *          - encodedRandom: string (base32 encoded)
   */
  static parse(id: string): [string, string, string] {
    if (!id || id.length === 0) {
      throw new Error('Invalid Ashid: cannot be empty');
    }

    // Find delimiter (underscore or dash) - no delimiter means no prefix
    let prefixLength = 0;
    let hasDelimiter = false;

    for (let i = 0; i < id.length; i++) {
      if (/^[a-zA-Z]$/.test(id[i])) {
        prefixLength++;
      } else if ((id[i] === '_' || id[i] === '-') && prefixLength > 0) {
        prefixLength++; // Include delimiter
        hasDelimiter = true;
        break;
      } else {
        // No delimiter found - entire string is base ID (no prefix)
        prefixLength = 0;
        break;
      }
    }

    // Normalize dash to underscore in prefix
    const prefix = hasDelimiter ? id.substring(0, prefixLength).replace(/-$/, '_') : '';
    const baseId = id.substring(prefixLength);

    if (baseId.length === 0) {
      throw new Error('Invalid Ashid: must have a base ID');
    }

    let encodedTimestamp: string;
    let encodedRandom: string;

    if (hasDelimiter) {
      // Variable length format (has prefix with delimiter)
      if (baseId.length <= RANDOM_ENCODED_LENGTH) {
        // Timestamp was 0 (omitted), entire baseId is random
        encodedTimestamp = '0';
        encodedRandom = baseId;
      } else {
        // Timestamp present, random is last 13 chars
        encodedTimestamp = baseId.slice(0, -RANDOM_ENCODED_LENGTH);
        encodedRandom = baseId.slice(-RANDOM_ENCODED_LENGTH);
      }
    } else if (baseId.length === ASHID4_BASE_LENGTH) {
      // Fixed 26-char format (ashid4 - two random components, no prefix)
      encodedTimestamp = baseId.slice(0, RANDOM_ENCODED_LENGTH);
      encodedRandom = baseId.slice(RANDOM_ENCODED_LENGTH);
    } else if (baseId.length === STANDARD_BASE_LENGTH) {
      // Fixed 22-char format (standard ashid, no prefix)
      encodedTimestamp = baseId.slice(0, TIMESTAMP_ENCODED_LENGTH);
      encodedRandom = baseId.slice(TIMESTAMP_ENCODED_LENGTH);
    } else {
      throw new Error(`Invalid Ashid: base ID must be ${STANDARD_BASE_LENGTH} or ${ASHID4_BASE_LENGTH} characters without delimiter (got ${baseId.length})`);
    }

    return [prefix, encodedTimestamp, encodedRandom];
  }

  /**
   * Extract the prefix from an Ashid
   *
   * @param id Ashid string
   * @returns Prefix string (empty if none)
   */
  static prefix(id: string): string {
    return this.parse(id)[0];
  }

  /**
   * Extract the timestamp from an Ashid
   *
   * @param id Ashid string
   * @returns Timestamp in milliseconds
   */
  static timestamp(id: string): number {
    const [, encodedTimestamp] = this.parse(id);
    return EncoderBase32Crockford.decode(encodedTimestamp);
  }

  /**
   * Extract the random component from an Ashid
   *
   * @param id Ashid string
   * @returns Random value as BigInt (preserves full 64-bit precision)
   */
  static random(id: string): bigint {
    const [, , encodedRandom] = this.parse(id);
    return EncoderBase32Crockford.decodeBigInt(encodedRandom);
  }

  /**
   * Validate if a string is a valid Ashid
   *
   * @param id String to validate
   * @returns true if valid Ashid format
   */
  static isValid(id: string): boolean {
    if (!id || id.length === 0) {
      return false;
    }

    try {
      const [prefix, encodedTimestamp, encodedRandom] = this.parse(id);

      // Validate prefix format (must be alphanumeric ending with _)
      if (prefix && !/^[a-zA-Z0-9]+_$/.test(prefix)) {
        return false;
      }

      // Validate base32 encoding
      EncoderBase32Crockford.decode(encodedTimestamp);
      EncoderBase32Crockford.decode(encodedRandom);

      return true;
    } catch {
      return false;
    }
  }

  /**
   * Normalize an Ashid by lowercasing and converting ambiguous characters
   * (I/L→1, O→0, etc.) to their canonical form.
   *
   * @param id Ashid string (potentially with mixed case or ambiguous chars)
   * @returns Normalized Ashid string
   */
  static normalize(id: string): string {
    const [prefix, encodedTimestamp, encodedRandom] = this.parse(id);
    const timestamp = EncoderBase32Crockford.decode(encodedTimestamp);
    const random = EncoderBase32Crockford.decodeBigInt(encodedRandom);
    // Lowercase the prefix
    const normalizedPrefix = prefix ? prefix.toLowerCase() : undefined;
    return this.create(normalizedPrefix, timestamp, random);
  }

  /**
   * Create a random Ashid (UUID v4 equivalent) with consistent 0-padded format
   *
   * Unlike create() which uses timestamp + random for time-sortability,
   * create4() uses two random values with consistent padding for maximum entropy.
   * Both components are padded to 13 chars each = 26 char base (128 bits of entropy).
   *
   * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
   * @param random1 First random value (defaults to secure random 64-bit BigInt)
   * @param random2 Second random value (defaults to secure random 64-bit BigInt)
   * @returns Ashid string with two random components, consistently padded
   */
  static create4(
    prefix?: string,
    random1: number | bigint = EncoderBase32Crockford.secureRandomLong(),
    random2: number | bigint = EncoderBase32Crockford.secureRandomLong()
  ): string {
    const normalizedPrefix = this.normalizePrefix(prefix);

    // Validate and normalize random values
    const randomBigInt1 = typeof random1 === 'bigint' ? random1 : BigInt(Math.floor(random1));
    const randomBigInt2 = typeof random2 === 'bigint' ? random2 : BigInt(Math.floor(random2));
    if (randomBigInt1 < 0n || randomBigInt2 < 0n) {
      throw new Error('Ashid random values must be non-negative');
    }

    // Both components padded to 13 chars for maximum entropy (26 char base)
    const encoded1 = EncoderBase32Crockford.encodeBigInt(randomBigInt1, true);
    const encoded2 = EncoderBase32Crockford.encodeBigInt(randomBigInt2, true);
    const baseId = encoded1 + encoded2;

    return (normalizedPrefix || '') + baseId;
  }

}

/**
 * Create a new Ashid with optional type prefix
 *
 * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
 * @returns Ashid string
 */
export function ashid(prefix?: string): string {
  return Ashid.create(prefix);
}

/**
 * Create a new Ashid with two random components (UUID v4 equivalent)
 *
 * Unlike the standard ashid() which uses timestamp + random for time-sortability,
 * ashid4() uses two random values for maximum entropy when time-sorting is not needed.
 * Always produces consistently 0-padded output (26 char base = 13 + 13, ~106 bits entropy).
 * Useful for tokens, secrets, or any ID where unpredictability is more important than ordering.
 *
 * @param prefix Optional alphabetic prefix (delimiter auto-added, omit trailing _ or -)
 * @returns Ashid string with two random components, consistently padded
 *
 * @example
 * ```typescript
 * const token = ashid4('tok');   // "tok_x7k9m2p4q8r1s5t3v6w0y1z3" (30 chars with prefix)
 * const secret = ashid4();       // "a3b5c7d9e1f2g4h6j8k0m2n4p6q8" (26 chars)
 * ```
 */
export function ashid4(prefix?: string): string {
  return Ashid.create4(prefix);
}

/**
 * Parse an Ashid into its components
 *
 * @param id The Ashid to parse
 * @returns Tuple of [prefix, encodedTimestamp, encodedRandom]
 */
export function parseAshid(id: string): [string, string, string] {
  return Ashid.parse(id);
}
