import { EncoderBase32Crockford } from './encoder';

/**
 * Largest timestamp that fits in the 9-char standard base (≈ Dec 12, 3084).
 * Used by {@link Ashid.fromUuid} to decide between the 22-char standard base
 * and the 26-char ashid4 base when reconstructing from a UUID.
 */
const MAX_TIMESTAMP = 35184372088831;

const TIMESTAMP_ENCODED_LENGTH = 9;
const RANDOM_ENCODED_LENGTH = 13;
const STANDARD_BASE_LENGTH = 22; // TIMESTAMP_ENCODED_LENGTH + RANDOM_ENCODED_LENGTH
const ASHID4_BASE_LENGTH = 26; // RANDOM_ENCODED_LENGTH * 2

/**
 * Ashid — a double-click-selectable, time-sortable unique identifier built
 * from a prefix and two longs.
 *
 * Shape: `[prefix_]<long1><long2>` in Crockford base32.
 * - {@link create} / {@link create1} write `long1` unpadded (type-1,
 *   time-sortable).
 * - {@link create4} writes both longs 13-char padded (type-4,
 *   UUIDv4-equivalent).
 *
 * The encoding is case-insensitive and maps lookalikes (I/L→1, O→0, U→V) on
 * read. {@link normalize} re-emits an id in canonical form;
 * {@link toUuid} / {@link fromUuid} round-trip any 128-bit UUID byte-identically.
 */
export class Ashid {
  // ── Creators ────────────────────────────────────────────────────────────

  /**
   * Type-1 (time-sortable): `[prefix_]<timestamp><random>`. The timestamp
   * half truncates leading zeros when a prefix is present; the random
   * half is 13-char padded EXCEPT when `time === 0` with a prefix, in
   * which case the timestamp is omitted entirely and the random half is
   * emitted unpadded (minimal form, `prefix_<random>`). The minimal form
   * parses back via the `baseId.length <= RANDOM_ENCODED_LENGTH` branch
   * of {@link parse}, so values round-trip.
   */
  static create(
    prefix?: string,
    time: number | bigint = Date.now(),
    randomLong: number | bigint = EncoderBase32Crockford.secureRandomLong()
  ): string {
    return this.buildBase(prefix, time, randomLong, false);
  }

  /** Explicit alias for {@link create}. Symmetrical with {@link create4}. */
  static create1(
    prefix?: string,
    time: number | bigint = Date.now(),
    randomLong: number | bigint = EncoderBase32Crockford.secureRandomLong()
  ): string {
    return this.buildBase(prefix, time, randomLong, false);
  }

  /**
   * Type-4 (UUIDv4-equivalent): `[prefix_]<random1><random2>` with both halves
   * 13-char padded (26-char base, 30 with prefix). 128 bits of entropy.
   */
  static create4(
    prefix?: string,
    random1: number | bigint = EncoderBase32Crockford.secureRandomLong(),
    random2: number | bigint = EncoderBase32Crockford.secureRandomLong()
  ): string {
    return this.buildBase(prefix, random1, random2, true);
  }

  // ── Readers ─────────────────────────────────────────────────────────────

  /**
   * Split an Ashid into `[prefix, encodedFirst, encodedSecond]`. The prefix
   * keeps its trailing underscore (`''` if absent); the two encoded halves
   * are Crockford base32 strings.
   *
   * Accepts both `_` and `-` as the prefix delimiter; dashes are normalized
   * to underscore in the returned prefix.
   */
  static parse(id: string): [string, string, string] {
    if (!id || id.length === 0) {
      throw new Error('Invalid Ashid: cannot be empty');
    }

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
        prefixLength = 0;
        break;
      }
    }
    // No delimiter → whole string is the base, even if it's all-alpha (e.g.
    // an ashid4 whose random half encodes to letters only).
    if (!hasDelimiter) prefixLength = 0;

    const prefix = hasDelimiter
      ? id.substring(0, prefixLength).replace(/-$/, '_')
      : '';
    const baseId = id.substring(prefixLength);
    if (baseId.length === 0) {
      throw new Error('Invalid Ashid: must have a base ID');
    }

    if (hasDelimiter) {
      if (baseId.length <= RANDOM_ENCODED_LENGTH) {
        // Pre-1.6.0 minimal form (timestamp=0 omitted, entire base is random).
        return [prefix, '0', baseId];
      }
      return [
        prefix,
        baseId.slice(0, -RANDOM_ENCODED_LENGTH),
        baseId.slice(-RANDOM_ENCODED_LENGTH),
      ];
    }
    if (baseId.length === ASHID4_BASE_LENGTH) {
      return [
        prefix,
        baseId.slice(0, RANDOM_ENCODED_LENGTH),
        baseId.slice(RANDOM_ENCODED_LENGTH),
      ];
    }
    if (baseId.length === STANDARD_BASE_LENGTH) {
      return [
        prefix,
        baseId.slice(0, TIMESTAMP_ENCODED_LENGTH),
        baseId.slice(TIMESTAMP_ENCODED_LENGTH),
      ];
    }
    throw new Error(
      `Invalid Ashid: base ID must be ${STANDARD_BASE_LENGTH} or ${ASHID4_BASE_LENGTH} characters without delimiter (got ${baseId.length})`
    );
  }

  /** Prefix with trailing `_`, or `''` if the id has no prefix. */
  static prefix(id: string): string {
    return this.parse(id)[0];
  }

  /** Timestamp in milliseconds. For an ashid4, this is the first long. */
  static timestamp(id: string): number {
    return EncoderBase32Crockford.decode(this.parse(id)[1]);
  }

  /** Random component as a BigInt (preserves full 64-bit precision). */
  static random(id: string): bigint {
    return EncoderBase32Crockford.decodeBigInt(this.parse(id)[2]);
  }

  // ── Validation & normalization ──────────────────────────────────────────

  /** True if `id` parses as a valid Ashid. */
  static isValid(id: string): boolean {
    if (!id || id.length === 0) return false;
    try {
      const [prefix, encodedTimestamp, encodedRandom] = this.parse(id);
      if (prefix && !/^[a-zA-Z0-9]+_$/.test(prefix)) return false;
      EncoderBase32Crockford.decode(encodedTimestamp);
      EncoderBase32Crockford.decode(encodedRandom);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Lowercase the prefix, map lookalikes (I/L→1, O→0, U→V), and re-emit through
   * {@link create}. Type-1 inputs round-trip to type-1 shape; full-entropy
   * type-4 inputs round-trip to type-4 shape (the first long naturally fills
   * 13 chars). A type-4 input with a small first long collapses to type-1
   * shape — the two longs survive, only the string shape changes.
   */
  static normalize(id: string): string {
    const [prefix, encodedFirst, encodedSecond] = this.parse(id);
    const normalizedPrefix = prefix ? prefix.toLowerCase() : undefined;
    const long1 = EncoderBase32Crockford.decodeBigInt(encodedFirst);
    const long2 = EncoderBase32Crockford.decodeBigInt(encodedSecond);
    return this.create(normalizedPrefix, long1, long2);
  }

  // ── UUID interop ────────────────────────────────────────────────────────

  /**
   * Emit the underlying 128 bits as a 36-char dashed-hex UUID (8-4-4-4-12).
   * Round-trips losslessly with {@link fromUuid}. The output is shape-
   * compatible with RFC 4122 but version/variant bits reflect the underlying
   * Ashid bytes unless the Ashid was originally created from a UUID.
   */
  static toUuid(id: string): string {
    const [, encodedTimestamp, encodedRandom] = this.parse(id);
    const high = EncoderBase32Crockford.decodeBigInt(encodedTimestamp);
    const low = EncoderBase32Crockford.decodeBigInt(encodedRandom);
    const highHex = high.toString(16).padStart(16, '0');
    const lowHex = low.toString(16).padStart(16, '0');
    return `${highHex.slice(0, 8)}-${highHex.slice(8, 12)}-${highHex.slice(12, 16)}-${lowHex.slice(0, 4)}-${lowHex.slice(4, 16)}`;
  }

  /**
   * Reconstruct an Ashid from any UUID (dashed 36-char or undashed 32-char hex).
   * Routes to the 22-char standard base when the high half fits in 45 bits
   * (realistic UUIDv1 timestamps), otherwise to the 26-char ashid4 base.
   * Round-trip through {@link toUuid} is byte-identical.
   */
  static fromUuid(uuid: string, prefix?: string): string {
    const hex = uuid.replace(/-/g, '');
    if (!/^[0-9a-fA-F]{32}$/.test(hex)) {
      throw new Error(`Invalid UUID: must be 32 or 36 hex characters (got "${uuid}")`);
    }
    const high = BigInt('0x' + hex.slice(0, 16));
    const low = BigInt('0x' + hex.slice(16, 32));
    return high <= BigInt(MAX_TIMESTAMP)
      ? this.create(prefix, Number(high), low)
      : this.create4(prefix, high, low);
  }

  // ── Private internals ───────────────────────────────────────────────────

  /**
   * Shared builder for the `createN` family.
   *
   * Layout is `[prefix_]<encoded1><encoded2>` with three shapes:
   * - **Minimal form** (prefix + `!padded` + `n1 === 0n`): encoded1 is
   *   omitted entirely and encoded2 is unpadded → `prefix_<encoded2>`.
   *   Round-trips via the `baseId.length <= RANDOM_ENCODED_LENGTH`
   *   branch of {@link parse}. Suitable for sequential-id encodings where
   *   brevity matters more than time-sortability.
   * - **Type-1 with prefix** (prefix + `!padded` + `n1 !== 0n`): encoded1
   *   is unpadded, encoded2 is 13-char padded (the parse anchor).
   * - **No prefix or `padded`**: encoded2 is 13-char padded; encoded1 is
   *   padded to either 9 (standard 22-char base, `n1` fits) or 13
   *   (ashid4 26-char base) so `parse()` has a known split point without
   *   a delimiter.
   *
   * Negative inputs throw via the encoder.
   */
  private static buildBase(
    prefix: string | undefined,
    n1: number | bigint,
    n2: number | bigint,
    padded: boolean
  ): string {
    const b1 = typeof n1 === 'bigint' ? n1 : BigInt(n1);
    const b2 = typeof n2 === 'bigint' ? n2 : BigInt(n2);
    const normalizedPrefix = this.normalizePrefix(prefix);

    // Minimal form: prefix + n1=0 + unpadded → drop encoded1, unpad
    // encoded2. parse() recognises this when the post-prefix base is
    // <= RANDOM_ENCODED_LENGTH chars.
    if (normalizedPrefix && !padded && b1 === 0n) {
      return normalizedPrefix + EncoderBase32Crockford.encodeBigInt(b2, false);
    }

    let encoded1: string;
    if (normalizedPrefix || padded) {
      encoded1 = EncoderBase32Crockford.encodeBigInt(b1, padded);
    } else {
      const raw = EncoderBase32Crockford.encodeBigInt(b1, false);
      encoded1 = raw.length <= TIMESTAMP_ENCODED_LENGTH
        ? raw.padStart(TIMESTAMP_ENCODED_LENGTH, '0')
        : raw.padStart(RANDOM_ENCODED_LENGTH, '0');
    }
    const encoded2 = EncoderBase32Crockford.encodeBigInt(b2, true);
    return (normalizedPrefix || '') + encoded1 + encoded2;
  }

  /**
   * Strip non-alphanumeric chars from the prefix, lowercase, and append `_`.
   * Returns `undefined` if nothing remains.
   */
  private static normalizePrefix(prefix?: string): string | undefined {
    if (prefix === undefined || prefix === '') return undefined;
    const cleaned = prefix.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
    return cleaned === '' ? undefined : cleaned + '_';
  }
}

// ── Top-level convenience functions ───────────────────────────────────────

/** Shorthand for {@link Ashid.create}. */
export function ashid(prefix?: string): string {
  return Ashid.create(prefix);
}

/** Shorthand for {@link Ashid.create4}. */
export function ashid4(prefix?: string): string {
  return Ashid.create4(prefix);
}

/** Shorthand for {@link Ashid.parse}. */
export function parseAshid(id: string): [string, string, string] {
  return Ashid.parse(id);
}
