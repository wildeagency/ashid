import { EncoderBase32Crockford } from './encoder';

/**
 * Maximum supported timestamp (Dec 12, 3084)
 * Beyond this, timestamp encoding exceeds 9 characters
 */
const MAX_TIMESTAMP = 35184372088831;

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
 * Examples:
 * - "user_1kbg1jmtt4v3x8k9p2m1n" (prefix with _, current time, secure random)
 * - "u1kbg1jmtt4v3x8k9p2m1n0" (prefix without _, fixed 22-char base)
 * - "1kbg1jmtt4v3x8k9p2m1n00" (no prefix, fixed 22-char base)
 * - "user_0" (prefix with _, timestamp 0, random 0 - explicitly passed)
 *
 * Timestamp Support:
 * - Minimum: 0 (Unix epoch, Jan 1, 1970)
 * - Maximum: 35184372088831 (Dec 12, 3084)
 */
export class Ashid {
  /**
   * Create a new Ashid with optional type prefix
   *
   * @param prefix Optional alphabetic prefix (may include trailing underscore as delimiter)
   * @param time Timestamp in milliseconds (defaults to current time, min 0, max Dec 12 3084)
   * @param randomLong Random number for uniqueness (defaults to secure random)
   * @returns Ashid string
   * @throws Error if timestamp is negative or exceeds maximum
   * @throws Error if random value is negative
   */
  static create(
    prefix?: string,
    time: number = Date.now(),
    randomLong: number = EncoderBase32Crockford.secureRandomLong()
  ): string {
    // Validate and normalize prefix: must be alphabetic with optional trailing underscore
    // Prefix is lowercased for case-insensitive storage
    if (prefix !== undefined && prefix !== '') {
      if (!/^[a-zA-Z]+_?$/.test(prefix)) {
        throw new Error('Ashid prefix must contain only letters with optional trailing underscore');
      }
      prefix = prefix.toLowerCase();
    }

    // Validate timestamp
    const flooredTime = Math.floor(time);
    if (flooredTime < 0) {
      throw new Error('Ashid timestamp must be non-negative');
    }
    if (flooredTime > MAX_TIMESTAMP) {
      throw new Error(`Ashid timestamp must not exceed ${MAX_TIMESTAMP} (Dec 12, 3084)`);
    }

    // Validate random value
    const flooredRandom = Math.floor(randomLong);
    if (flooredRandom < 0) {
      throw new Error('Ashid random value must be non-negative');
    }

    // Determine if prefix has underscore delimiter
    const hasDelimiter = prefix?.endsWith('_') ?? false;

    let baseId: string;

    if (hasDelimiter) {
      // Variable length: no padding on timestamp, pad random only when timestamp > 0
      const randomEncoded = flooredTime > 0
        ? EncoderBase32Crockford.encode(flooredRandom, true) // padded to 13
        : EncoderBase32Crockford.encode(flooredRandom); // no padding

      baseId = flooredTime > 0
        ? EncoderBase32Crockford.encode(flooredTime) + randomEncoded
        : randomEncoded; // timestamp 0 is omitted
    } else {
      // Fixed length: pad timestamp to 9, random to 13 = 22 chars total
      const timeEncoded = EncoderBase32Crockford.encode(flooredTime).padStart(9, '0');
      const randomEncoded = EncoderBase32Crockford.encode(flooredRandom, true); // padded to 13
      baseId = timeEncoded + randomEncoded;
    }

    return (prefix || '') + baseId;
  }

  /**
   * Parse an Ashid into its components
   *
   * @param id The full Ashid string
   * @returns Tuple of [prefix, encodedTimestamp, encodedRandom]
   *          - prefix: string (empty if none)
   *          - encodedTimestamp: string (base32 encoded)
   *          - encodedRandom: string (base32 encoded)
   */
  static parse(id: string): [string, string, string] {
    if (!id || id.length === 0) {
      throw new Error('Invalid Ashid: cannot be empty');
    }

    // Find the prefix: leading alphabetic characters with optional trailing underscore or dash
    // Dashes are normalized to underscores
    let prefixLength = 0;
    let hasDelimiter = false;

    for (let i = 0; i < id.length; i++) {
      if (/^[a-zA-Z]$/.test(id[i])) {
        prefixLength++;
      } else if ((id[i] === '_' || id[i] === '-') && prefixLength > 0) {
        prefixLength++;
        hasDelimiter = true;
        break;
      } else {
        break;
      }
    }

    // Normalize dash to underscore in prefix
    const prefix = id.substring(0, prefixLength).replace(/-$/, '_');
    const baseId = id.substring(prefixLength);

    if (baseId.length === 0) {
      throw new Error('Invalid Ashid: must have a base ID after prefix');
    }

    let encodedTimestamp: string;
    let encodedRandom: string;

    if (hasDelimiter) {
      // Variable length format
      if (baseId.length <= 13) {
        // Timestamp was 0 (omitted), entire baseId is random
        encodedTimestamp = '0';
        encodedRandom = baseId;
      } else {
        // Timestamp present, random is last 13 chars
        encodedTimestamp = baseId.slice(0, -13);
        encodedRandom = baseId.slice(-13);
      }
    } else {
      // Fixed 22-char format
      if (baseId.length !== 22) {
        throw new Error(`Invalid Ashid: base ID must be 22 characters without underscore delimiter (got ${baseId.length})`);
      }
      encodedTimestamp = baseId.slice(0, 9);
      encodedRandom = baseId.slice(9);
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
   * @returns Random number
   */
  static random(id: string): number {
    const [, , encodedRandom] = this.parse(id);
    return EncoderBase32Crockford.decode(encodedRandom);
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

      // Validate prefix format
      if (prefix && !/^[a-zA-Z]+_?$/.test(prefix)) {
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
    const random = EncoderBase32Crockford.decode(encodedRandom);
    // Lowercase the prefix
    const normalizedPrefix = prefix ? prefix.toLowerCase() : undefined;
    return this.create(normalizedPrefix, timestamp, random);
  }

}

/**
 * Create a new Ashid with optional type prefix
 *
 * @param prefix Optional entity type prefix (letters with optional trailing underscore)
 * @returns Ashid string
 */
export function ashid(prefix?: string): string {
  return Ashid.create(prefix);
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
