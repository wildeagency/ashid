/**
 * Crockford Base32 Encoder
 *
 * Douglas Crockford's Base32 encoding provides:
 * - Case-insensitive encoding (0-9, a-z)
 * - Lookalike character mapping (I/L → 1, O → 0)
 * - No special characters
 * - Human-readable and error-resistant
 *
 * Alphabet: 0123456789abcdefghjkmnpqrstvwxyz (32 characters)
 * Excluded: i, l, o, u (mapped to lookalikes during decode)
 */
export class EncoderBase32Crockford {
  private static readonly alphabet = '0123456789abcdefghjkmnpqrstvwxyz';

  /**
   * Encode a non-negative integer to Crockford Base32 string
   *
   * @param n Non-negative integer to encode
   * @param padded If true, pad result to 13 characters (for consistent Ashid length)
   * @returns Base32 encoded string
   */
  static encode(n: number, padded: boolean = false): string {
    if (!Number.isFinite(n) || n < 0) {
      throw new Error('Input must be a non-negative finite number');
    }

    const encoded = this.encodeRecursive(Math.floor(n));
    return padded ? encoded.padStart(13, '0') : encoded;
  }

  /**
   * Recursive encoding implementation
   */
  private static encodeRecursive(n: number): string {
    if (n === 0) return '0';

    const remainder = n % 32;
    const quotient = Math.floor(n / 32);

    if (quotient === 0) {
      return this.alphabet[remainder];
    }

    return this.encodeRecursive(quotient) + this.alphabet[remainder];
  }

  /**
   * Decode a Crockford Base32 string to integer
   *
   * @param str Base32 encoded string
   * @returns Decoded integer
   */
  static decode(str: string): number {
    if (!str || str.length === 0) {
      throw new Error('Input string cannot be empty');
    }

    let result = 0;
    const normalized = str.toLowerCase();

    for (let i = 0; i < normalized.length; i++) {
      const char = normalized[i];
      const value = this.decodeChar(char);

      if (value === -1) {
        throw new Error(`Invalid character in Base32 string: '${char}'`);
      }

      result = result * 32 + value;
    }

    return result;
  }

  /**
   * Decode a single character, mapping lookalikes
   */
  private static decodeChar(char: string): number {
    switch (char) {
      case '0':
      case 'o':
      case 'O':
        return 0;
      case '1':
      case 'i':
      case 'I':
      case 'l':
      case 'L':
        return 1;
      case '2':
        return 2;
      case '3':
        return 3;
      case '4':
        return 4;
      case '5':
        return 5;
      case '6':
        return 6;
      case '7':
        return 7;
      case '8':
        return 8;
      case '9':
        return 9;
      case 'a':
      case 'A':
        return 10;
      case 'b':
      case 'B':
        return 11;
      case 'c':
      case 'C':
        return 12;
      case 'd':
      case 'D':
        return 13;
      case 'e':
      case 'E':
        return 14;
      case 'f':
      case 'F':
        return 15;
      case 'g':
      case 'G':
        return 16;
      case 'h':
      case 'H':
        return 17;
      case 'j':
      case 'J':
        return 18;
      case 'k':
      case 'K':
        return 19;
      case 'm':
      case 'M':
        return 20;
      case 'n':
      case 'N':
        return 21;
      case 'p':
      case 'P':
        return 22;
      case 'q':
      case 'Q':
        return 23;
      case 'r':
      case 'R':
        return 24;
      case 's':
      case 'S':
        return 25;
      case 't':
      case 'T':
        return 26;
      case 'u':
      case 'U':
      case 'v':
      case 'V':
        return 27;
      case 'w':
      case 'W':
        return 28;
      case 'x':
      case 'X':
        return 29;
      case 'y':
      case 'Y':
        return 30;
      case 'z':
      case 'Z':
        return 31;
      default:
        return -1;
    }
  }

  /**
   * Generate a cryptographically secure random positive integer
   *
   * Uses Node.js crypto module or Web Crypto API
   * Returns a positive integer within safe JavaScript number range
   */
  static secureRandomLong(): number {
    // Maximum safe integer that fits in 53 bits
    const MAX_SAFE_INT = Number.MAX_SAFE_INTEGER;

    // Try Node.js crypto module first
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const crypto = require('crypto') as typeof import('crypto');
      const buffer = crypto.randomBytes(8);

      // Read as BigInt and convert to safe integer range
      const randomBigInt = buffer.readBigUInt64BE(0);
      return Number(randomBigInt % BigInt(MAX_SAFE_INT));
    } catch (e) {
      // Not in Node.js environment or crypto not available
    }

    // Try Web Crypto API (browser or modern environments)
    const cryptoObj = typeof globalThis !== 'undefined' && globalThis.crypto;
    if (cryptoObj && cryptoObj.getRandomValues) {
      const buffer = new Uint32Array(2);
      cryptoObj.getRandomValues(buffer);

      // Combine two 32-bit values into a 53-bit number (max safe integer)
      const high = buffer[0] & 0x1FFFFF; // 21 bits
      const low = buffer[1]; // 32 bits
      return (high * 0x100000000) + low;
    }

    // Fallback for testing environments without crypto
    // NOT cryptographically secure - should only be used in tests
    console.warn('Crypto API not available, using Math.random() (NOT SECURE)');
    return Math.floor(Math.random() * MAX_SAFE_INT);
  }

  /**
   * Validate if a string is valid Crockford Base32
   */
  static isValid(str: string): boolean {
    if (!str || str.length === 0) return false;

    try {
      this.decode(str);
      return true;
    } catch {
      return false;
    }
  }
}
