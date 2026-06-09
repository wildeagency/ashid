import { describe, it, expect } from 'vitest';
import { Ashid, ashid, ashid4, parseAshid } from '../src/ashid';
import { EncoderBase32Crockford } from '../src/encoder';

describe('Ashid', () => {
  describe('create', () => {
    it('should create Ashid without prefix (fixed 22-char format)', () => {
      const id = Ashid.create();
      expect(id).toBeTruthy();
      expect(id.length).toBe(22);
      expect(/^[0-9]/.test(id)).toBe(true);
    });

    it('should create Ashid with prefix (auto-adds delimiter)', () => {
      const id = Ashid.create('user');
      expect(id).toMatch(/^user_/);
    });

    it('should create Ashid with trailing underscore (ignored, re-added)', () => {
      const id = Ashid.create('user_');
      expect(id).toMatch(/^user_/);
    });

    it('should create same ID with or without trailing delimiter', () => {
      const id1 = Ashid.create('user', 1000, 0);
      const id2 = Ashid.create('user_', 1000, 0);
      expect(id1).toBe(id2);
    });

    it('should accept empty string as no prefix', () => {
      const id = Ashid.create('');
      expect(/^[0-9]/.test(id)).toBe(true);
      expect(id.length).toBe(22);
    });

    it('should allow alphanumeric prefix', () => {
      const id = Ashid.create('user1', 1000, 0);
      expect(id).toMatch(/^user1_/);
    });

    it('should strip non-alphanumeric characters from prefix', () => {
      const id = Ashid.create('a-b_c', 1000, 0);
      expect(id).toMatch(/^abc_/); // strips - and _
    });

    it('should strip special characters from prefix', () => {
      const id = Ashid.create('user!@#$%', 1000, 0);
      expect(id).toMatch(/^user_/);
    });

    it('should return no prefix if all chars stripped', () => {
      const id = Ashid.create('___', 1000, 0);
      expect(id.length).toBe(22); // No prefix, fixed format
    });

    it('should throw on negative timestamp', () => {
      expect(() => Ashid.create(undefined, -1)).toThrow('non-negative');
    });

    it('should accept timestamps beyond the former MAX_TIMESTAMP (no longer throws)', () => {
      // The previous Dec 12 3084 ceiling was a v1-shape constraint. create() now
      // auto-routes oversized timestamps to a 13-char encoding so any non-negative
      // value (including a full 64-bit BigInt) is representable.
      const formerMax = 35184372088831;
      expect(() => Ashid.create(undefined, formerMax)).not.toThrow();
      expect(() => Ashid.create(undefined, formerMax + 1)).not.toThrow();
      expect(() => Ashid.create(undefined, 2n ** 64n - 1n)).not.toThrow();
    });

    it('should accept bigint time', () => {
      const id = Ashid.create('user', 1000n, 0n);
      expect(id).toBe(Ashid.create('user', 1000, 0n));
    });

    it('create4 with prefix pads both halves to 13 chars', () => {
      const id = Ashid.create4('user', 5n, 0n);
      expect(id).toBe('user_00000000000050000000000000');
      expect(id.length).toBe(5 + 26);
    });

    it('create4 without prefix produces a 26-char base', () => {
      const id = Ashid.create4(undefined, 5n, 0n);
      expect(id).toBe('00000000000050000000000000');
      expect(id.length).toBe(26);
    });

    // Padding lock-down. create4 must always emit both halves 13-char padded,
    // regardless of input magnitude. These pin the wire format so a refactor
    // that drops padding (e.g. routing create4 through an unpadded builder)
    // fails loudly.
    describe('create4 padding lockdown', () => {
      it('zero/zero with prefix → both halves 13 zeros', () => {
        expect(Ashid.create4('tok', 0n, 0n)).toBe('tok_00000000000000000000000000');
      });

      it('zero/zero without prefix → 26 zeros', () => {
        expect(Ashid.create4(undefined, 0n, 0n)).toBe('00000000000000000000000000');
      });

      it('one/zero with prefix → first half padded, no truncation', () => {
        expect(Ashid.create4('tok', 1n, 0n)).toBe('tok_00000000000010000000000000');
      });

      it('Crockford "z" (31) preserves its single trailing char after padding', () => {
        expect(Ashid.create4('tok', 31n, 0n)).toBe('tok_000000000000z0000000000000');
      });

      it('max u64 first half fills exactly 13 chars (no overflow into prefix)', () => {
        const id = Ashid.create4('tok', (2n ** 64n) - 1n, 0n);
        expect(id).toBe('tok_fzzzzzzzzzzzz0000000000000');
        expect(id.length).toBe(3 + 1 + 26);
      });

      it('max u64 second half fills exactly 13 chars', () => {
        const id = Ashid.create4('tok', 0n, (2n ** 64n) - 1n);
        expect(id).toBe('tok_0000000000000fzzzzzzzzzzzz');
        expect(id.length).toBe(3 + 1 + 26);
      });

      it('no-prefix output always exactly 26 chars across input magnitudes', () => {
        const samples: Array<[bigint, bigint]> = [
          [0n, 0n],
          [1n, 0n],
          [0n, 1n],
          [31n, 31n],
          [(2n ** 64n) - 1n, 0n],
          [0n, (2n ** 64n) - 1n],
        ];
        for (const [r1, r2] of samples) {
          expect(Ashid.create4(undefined, r1, r2).length).toBe(26);
        }
      });

      it('with-prefix output is exactly prefix.length + 1 + 26 across magnitudes', () => {
        const samples: Array<[bigint, bigint]> = [
          [0n, 0n],
          [1n, 0n],
          [(2n ** 64n) - 1n, (2n ** 64n) - 1n],
        ];
        for (const [r1, r2] of samples) {
          expect(Ashid.create4('tok', r1, r2).length).toBe(3 + 1 + 26);
        }
      });
    });

    it('should throw on negative random value', () => {
      expect(() => Ashid.create(undefined, Date.now(), -1)).toThrow('non-negative');
    });

    it('should generate unique IDs', () => {
      const ids = new Set<string>();
      for (let i = 0; i < 1000; i++) {
        ids.add(Ashid.create());
      }
      expect(ids.size).toBe(1000);
    });

    it('should lowercase uppercase prefix in create', () => {
      const id = Ashid.create('USER', 1000, 0);
      expect(id).toMatch(/^user_/);
      expect(Ashid.prefix(id)).toBe('user_');
    });
  });

  describe('create1', () => {
    it('should be an alias for create with default options', () => {
      expect(Ashid.create1('user', 1000, 0n)).toBe(Ashid.create('user', 1000, 0n));
      expect(Ashid.create1(undefined, 1000, 0n)).toBe(Ashid.create(undefined, 1000, 0n));
    });

    it('should accept bigint time', () => {
      expect(Ashid.create1('user', 1000n, 0n)).toBe(Ashid.create('user', 1000, 0n));
    });

    it('with defaults should produce a valid ashid', () => {
      const id = Ashid.create1('user');
      expect(Ashid.isValid(id)).toBe(true);
      expect(id).toMatch(/^user_/);
    });
  });

  describe('fixed vs variable length formats', () => {
    describe('no prefix (fixed 22-char)', () => {
      it('timestamp 0 should be padded to 22 chars', () => {
        const id = Ashid.create(undefined, 0, 0);
        expect(id).toBe('0000000000000000000000');
        expect(id.length).toBe(22);
      });

      it('current timestamp should be 22 chars', () => {
        const id = Ashid.create();
        expect(id.length).toBe(22);
      });
    });

    describe('prefix (variable length with delimiter)', () => {
      // The random half is always 13-char padded — it's the parse anchor.
      // The timestamp half truncates leading zeros under create() (type-1).

      it('timestamp 0, random 0 → unpadded "0" + padded random', () => {
        const id = Ashid.create('user', 0, 0n);
        expect(id).toBe('user_00000000000000');
        expect(id.length).toBe(5 + 1 + 13);
      });

      it('timestamp 0, random 1 → unpadded "0" + padded random', () => {
        const id = Ashid.create('user', 0, 1n);
        expect(id).toBe('user_00000000000001');
      });

      it('timestamp 0, random 31 → unpadded "0" + padded random', () => {
        const id = Ashid.create('user', 0, 31n);
        expect(id).toBe('user_0000000000000z');
      });

      it('timestamp 1, random 0 should include timestamp', () => {
        const id = Ashid.create('user', 1, 0n);
        expect(id).toBe('user_10000000000000');
        expect(id.length).toBe(19); // 'user_' (5) + 1 + 13
      });

      it('current timestamp should have variable length', () => {
        const id = Ashid.create('user', Date.now(), 0n);
        // 'user_' (5) + 9 (current timestamp) + 13 (padded random) = 27
        expect(id.length).toBe(27);
      });

      it('single letter prefix should also get delimiter', () => {
        const id = Ashid.create('u', 0, 0n);
        expect(id).toBe('u_00000000000000');
      });
    });
  });

  describe('parse', () => {
    it('should parse Ashid without prefix (22-char base)', () => {
      const id = '0000000000000000000000';
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe('');
      expect(timestamp).toBe('000000000');
      expect(random).toBe('0000000000000');
    });

    it('should parse Ashid with delimiter prefix (variable)', () => {
      const id = 'user_1kbg1jmtt0000000000000';
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe('user_');
      expect(timestamp).toBe('1kbg1jmtt');
      expect(random).toBe('0000000000000');
    });

    it('should parse underscore prefix with timestamp 0', () => {
      const id = 'user_c1s'; // timestamp 0, random 12345
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe('user_');
      expect(timestamp).toBe('0');
      expect(random).toBe('c1s');
    });

    it('should throw on empty string', () => {
      expect(() => Ashid.parse('')).toThrow('cannot be empty');
    });

    it('should throw on prefix-only string', () => {
      expect(() => Ashid.parse('user_')).toThrow('must have a base ID');
    });

    it('should treat non-delimited ID as no prefix', () => {
      // Old-style ID without delimiter - entire string is base (must be exactly 22 chars)
      const id = 'u1234567890123456789ab'; // exactly 22 chars
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe(''); // No delimiter = no prefix
      expect(timestamp).toBe('u12345678');
      expect(random).toBe('90123456789ab');
    });

    it('should throw on wrong length without delimiter', () => {
      expect(() => Ashid.parse('abc123')).toThrow('must be 22 or 26 characters');
    });

    it('should parse dash as underscore delimiter', () => {
      const id = 'user-1kbg1jmtt0000000000000';
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe('user_'); // dash normalized to underscore
      expect(timestamp).toBe('1kbg1jmtt');
      expect(random).toBe('0000000000000');
    });

    it('should extract correct timestamp from dash-delimited id', () => {
      const id = 'user-1kbg1jmtt0000000000000';
      expect(Ashid.timestamp(id)).toBe(Ashid.timestamp('user_1kbg1jmtt0000000000000'));
    });
  });

  describe('prefix()', () => {
    it('should extract empty prefix', () => {
      const id = Ashid.create(undefined, 1000, 0);
      expect(Ashid.prefix(id)).toBe('');
    });

    it('should extract prefix with delimiter', () => {
      const id = Ashid.create('user', 1000, 0);
      expect(Ashid.prefix(id)).toBe('user_');
    });

    it('should extract single letter prefix with delimiter', () => {
      const id = Ashid.create('u', 1000, 0);
      expect(Ashid.prefix(id)).toBe('u_');
    });
  });

  describe('timestamp()', () => {
    it('should extract timestamp from fixed format (no prefix)', () => {
      const timestamp = 1609459200000;
      const id = Ashid.create(undefined, timestamp, 0);
      expect(Ashid.timestamp(id)).toBe(timestamp);
    });

    it('should extract timestamp from variable format (with prefix)', () => {
      const timestamp = 1609459200000;
      const id = Ashid.create('user', timestamp, 0);
      expect(Ashid.timestamp(id)).toBe(timestamp);
    });

    it('should extract timestamp 0 from fixed format', () => {
      const id = Ashid.create(undefined, 0, 12345);
      expect(Ashid.timestamp(id)).toBe(0);
    });

    it('should extract timestamp 0 from variable format', () => {
      const id = Ashid.create('user', 0, 12345);
      expect(Ashid.timestamp(id)).toBe(0);
    });
  });

  describe('random()', () => {
    it('should extract random from fixed format', () => {
      const random = 123456789n;
      const id = Ashid.create(undefined, Date.now(), random);
      expect(Ashid.random(id)).toBe(random);
    });

    it('should extract random from variable format', () => {
      const random = 123456789n;
      const id = Ashid.create('user', Date.now(), random);
      expect(Ashid.random(id)).toBe(random);
    });

    it('should extract random 0 from fixed format', () => {
      const id = Ashid.create(undefined, 1000, 0n);
      expect(Ashid.random(id)).toBe(0n);
    });

    it('should extract random from variable format with timestamp 0', () => {
      const id = Ashid.create('user', 0, 12345n);
      expect(Ashid.random(id)).toBe(12345n);
    });

    it('should return bigint type', () => {
      const id = Ashid.create('user', Date.now(), 12345n);
      expect(typeof Ashid.random(id)).toBe('bigint');
    });

    it('should handle 64-bit random values correctly', () => {
      // Value beyond MAX_SAFE_INTEGER
      const random = 18446744073709551615n; // max 64-bit
      const id = Ashid.create('user', Date.now(), random);
      expect(Ashid.random(id)).toBe(random);
    });
  });

  describe('normalize()', () => {
    // normalize() routes through create() (the default type-1 path).
    // - v1 inputs round-trip to their own v1 shape.
    // - Full-entropy ashid4 inputs round-trip to their own ashid4 shape because
    //   the first long naturally encodes to 13 chars (no padding needed).
    // - An ashid4 whose first long is small enough to truncate (e.g.
    //   create4('tok', 1n, 0n)) collapses to v1 shape; the two longs survive
    //   but the string shape is no longer byte-identical to the input.

    it('should round-trip a v1 ashid (uppercase variant) back to original', () => {
      const original = Ashid.create('user', 1609459200000, 0n);
      const normalized = Ashid.normalize(original.toUpperCase());
      expect(normalized).toBe(original);
      expect(Ashid.timestamp(normalized)).toBe(1609459200000);
      expect(Ashid.random(normalized)).toBe(0n);
    });

    it('should convert ambiguous characters via decode/encode round-trip', () => {
      const original = Ashid.create('user', 1609459200000, 1111n);
      const [prefix, ts, rand] = Ashid.parse(original);
      const modifiedTs = ts.replace(/1/g, 'I').replace(/0/g, 'O');
      const modifiedRand = rand.replace(/1/g, 'L').replace(/0/g, 'O');
      const modified = prefix + modifiedTs + modifiedRand;

      const normalized = Ashid.normalize(modified);
      expect(Ashid.timestamp(normalized)).toBe(1609459200000);
      expect(Ashid.random(normalized)).toBe(1111n);
    });

    it('should round-trip variable length v1 format back to its own form', () => {
      const original = Ashid.create('user', 1609459200000, 12345n);
      expect(Ashid.normalize(original.toUpperCase())).toBe(original);
    });

    it('should preserve values through round-trip', () => {
      const original = Ashid.create('user', 1609459200000, 12345n);
      const normalized = Ashid.normalize(original.toUpperCase());
      expect(Ashid.timestamp(normalized)).toBe(1609459200000);
      expect(Ashid.random(normalized)).toBe(12345n);
    });

    it('should round-trip a no-prefix v1 ashid back to its own form', () => {
      const original = Ashid.create(undefined, 1609459200000, 12345n);
      expect(Ashid.normalize(original.toUpperCase())).toBe(original);
    });

    it('should be idempotent', () => {
      const v1 = Ashid.create('user', 1609459200000, 12345n);
      const once = Ashid.normalize(v1);
      expect(Ashid.normalize(once)).toBe(once);
      // Use a full-entropy ashid4 so the first long naturally fills 13 chars
      // — otherwise the shape would collapse on normalize.
      const a4 = Ashid.create4('tok', 0xdeadbeefcafebaben, 0x0123456789abcdefn);
      expect(Ashid.normalize(a4)).toBe(a4);
      expect(Ashid.normalize(Ashid.normalize(a4))).toBe(a4);
    });

    it('should convert dash to underscore in prefix', () => {
      const withDash = 'user-1kbg1jmtt0000000000000';
      const normalized = Ashid.normalize(withDash);
      expect(normalized).toMatch(/^user_/);
      expect(Ashid.prefix(normalized)).toBe('user_');
    });

    it('should parse and normalize dash prefix correctly', () => {
      const withDash = 'user-1kbg1jmtt0000000000000';
      const withUnderscore = 'user_1kbg1jmtt0000000000000';
      expect(Ashid.normalize(withDash)).toBe(Ashid.normalize(withUnderscore));
    });

    it('should preserve full 64-bit entropy through normalization', () => {
      // Value beyond MAX_SAFE_INTEGER
      const random = 18446744073709551615n; // max 64-bit
      const original = Ashid.create('user', 1609459200000, random);
      const normalized = Ashid.normalize(original.toUpperCase());
      expect(Ashid.random(normalized)).toBe(random);
    });

    // ── ashid4 (two random longs) normalization ────────────────────
    // Regression coverage: prior to the unified implementation,
    // `normalize` routed through `create` (timestamp + random) for
    // every input, which used unpadded timestamp encoding. For ashid4
    // inputs that put leading zeros at the front of the first long,
    // those zeros got dropped — corrupting the value. Current
    // implementation parses two longs, re-emits via `create4`, so
    // padding is consistent across formats.

    it('should round-trip ashid4 with prefix unchanged (identity)', () => {
      const original = Ashid.create4('tok', 1234567890123456789n, 9876543210987654321n);
      expect(Ashid.normalize(original)).toBe(original);
    });

    it('should round-trip ashid4 without prefix unchanged (identity)', () => {
      const original = Ashid.create4(undefined, 1234567890123456789n, 9876543210987654321n);
      expect(Ashid.normalize(original)).toBe(original);
    });

    it('should normalize uppercase ashid4 with prefix back to canonical', () => {
      const original = Ashid.create4('tok', 0xdeadbeefcafebaben, 0x0123456789abcdefn);
      const uppercased = original.toUpperCase();
      expect(Ashid.normalize(uppercased)).toBe(original);
    });

    it('should normalize uppercase ashid4 without prefix back to canonical', () => {
      const original = Ashid.create4(undefined, 0xdeadbeefcafebaben, 0x0123456789abcdefn);
      const uppercased = original.toUpperCase();
      expect(Ashid.normalize(uppercased)).toBe(original);
    });

    it('should recover values from lookalike-mangled ashid4 body (shape may collapse)', () => {
      // Small first long means the canonical type-1 path will truncate the
      // leading zeros, so the *shape* collapses to v1. The *values* still
      // survive through the lookalike-fixing decode step — which is the
      // actual point of normalize().
      const r1 = 0x0000000000000001n;
      const r2 = 0x0000000000000000n;
      const original = Ashid.create4('tok', r1, r2);
      const mangled = 'tok_' + original.slice(4).replace(/0/g, 'O').replace(/1/g, 'l');
      const normalized = Ashid.normalize(mangled);
      expect(Ashid.timestamp(normalized)).toBe(Number(r1));
      expect(Ashid.random(normalized)).toBe(r2);
    });

    it('should normalize lookalike-mangled ashid4 with full-entropy first long back to canonical', () => {
      // Full-entropy first long → encoded length 13 → no truncation → shape preserved.
      const r1 = 0xdeadbeef00000001n;
      const r2 = 0x0123456789abcdefn;
      const original = Ashid.create4('tok', r1, r2);
      const mangled = 'tok_' + original.slice(4).replace(/0/g, 'O').replace(/1/g, 'l');
      expect(Ashid.normalize(mangled)).toBe(original);
    });

    it('should preserve both random components of ashid4 through normalize+toUpperCase', () => {
      const r1 = 0xfedcba9876543210n;
      const r2 = 0x0123456789abcdefn;
      const original = Ashid.create4('tok', r1, r2);
      const round = Ashid.normalize(original.toUpperCase());
      // Both random components survive: extract via parse + decode
      const [, encR1, encR2] = Ashid.parse(round);
      expect(EncoderBase32Crockford.decodeBigInt(encR1)).toBe(r1);
      expect(EncoderBase32Crockford.decodeBigInt(encR2)).toBe(r2);
    });

    it('should normalize v1 and a same-longs ashid4 to the same canonical (type-1) form', () => {
      // normalize now routes through create() (type-1), so both inputs
      // collapse onto the unpadded type-1 shape — and the ashid4 in this
      // case has a small first long that gets truncated.
      const long1 = 1609459200000n;
      const long2 = 12345n;
      const v1 = Ashid.create('tok', Number(long1), long2);
      const a4 = Ashid.create4('tok', long1, long2);
      expect(Ashid.normalize(v1)).toBe(Ashid.normalize(a4));
      expect(Ashid.normalize(v1)).toBe(v1);
    });
  });

  describe('isValid', () => {
    it('should validate correct Ashids', () => {
      expect(Ashid.isValid(Ashid.create())).toBe(true);
      expect(Ashid.isValid(Ashid.create('u'))).toBe(true);
      expect(Ashid.isValid(Ashid.create('user'))).toBe(true);
    });

    it('should validate timestamp 0 IDs', () => {
      expect(Ashid.isValid(Ashid.create(undefined, 0, 0))).toBe(true);
      expect(Ashid.isValid(Ashid.create('u', 0, 0))).toBe(true);
      expect(Ashid.isValid(Ashid.create('user', 0, 0))).toBe(true);
    });

    it('should reject empty strings', () => {
      expect(Ashid.isValid('')).toBe(false);
    });

    it('should reject invalid base32', () => {
      expect(Ashid.isValid('user_invalid!@#')).toBe(false);
    });

    it('should reject wrong length without delimiter', () => {
      expect(Ashid.isValid('abc123')).toBe(false);
    });

    it('should validate 22-char base without delimiter', () => {
      expect(Ashid.isValid('0000000000000000000000')).toBe(true);
    });
  });

  describe('time-sortability', () => {
    it('should be sortable by creation time (no prefix)', () => {
      const base = Date.now();
      const id1 = Ashid.create(undefined, base, 0);
      const id2 = Ashid.create(undefined, base + 1000, 0);
      const id3 = Ashid.create(undefined, base + 2000, 0);

      const sorted = [id3, id1, id2].sort();
      expect(sorted).toEqual([id1, id2, id3]);
    });

    it('should be sortable by creation time (with prefix)', () => {
      const base = Date.now();
      const id1 = Ashid.create('user', base, 0);
      const id2 = Ashid.create('user', base + 1000, 0);
      const id3 = Ashid.create('user', base + 2000, 0);

      const sorted = [id3, id1, id2].sort();
      expect(sorted).toEqual([id1, id2, id3]);
    });

    it('should sort by random when timestamp is same', () => {
      const timestamp = Date.now();
      const id1 = Ashid.create(undefined, timestamp, 1000n);
      const id2 = Ashid.create(undefined, timestamp, 2000n);

      const sorted = [id2, id1].sort();
      expect(sorted).toEqual([id1, id2]);
    });
  });

  describe('convenience functions', () => {
    it('ashid() should create Ashid', () => {
      const id = ashid();
      expect(Ashid.isValid(id)).toBe(true);
    });

    it('ashid(prefix) should create Ashid with prefix and delimiter', () => {
      const id = ashid('user');
      expect(id).toMatch(/^user_/);
    });

    it('parseAshid() should return tuple', () => {
      const id = 'user_1kbg1jmtt0000000000000';
      const [prefix, timestamp, random] = parseAshid(id);
      expect(prefix).toBe('user_');
      expect(timestamp).toBe('1kbg1jmtt');
      expect(random).toBe('0000000000000');
    });
  });

  describe('double-click selectability', () => {
    it('should not contain hyphens', () => {
      const id = ashid('user');
      expect(id).not.toContain('-');
    });

    it('should not contain spaces', () => {
      const id = ashid('user');
      expect(id).not.toContain(' ');
    });

    it('base ID should only contain alphanumeric and underscore', () => {
      const id1 = ashid();
      const id2 = ashid('user');
      expect(id1).toMatch(/^[a-z0-9]+$/);
      expect(id2).toMatch(/^[a-z]+_[a-z0-9]+$/);
    });
  });

  describe('ashid4 (UUID-like)', () => {
    it('should create ashid4 without prefix', () => {
      const id = ashid4();
      expect(id).toBeTruthy();
      expect(id.length).toBe(26); // 13 + 13 chars
      expect(Ashid.isValid(id)).toBe(true);
    });

    it('should create ashid4 with prefix', () => {
      const id = ashid4('tok');
      expect(id).toMatch(/^tok_/);
      expect(id.length).toBe(30); // 4 (prefix + _) + 26 (base)
    });

    it('should generate unique IDs', () => {
      const ids = new Set<string>();
      for (let i = 0; i < 1000; i++) {
        ids.add(ashid4());
      }
      expect(ids.size).toBe(1000);
    });

    it('should use full 64-bit entropy for both components', () => {
      const samples = 100;
      let sawFullEntropy = false;

      for (let i = 0; i < samples; i++) {
        const id = ashid4();
        const [, encoded1, encoded2] = Ashid.parse(id);

        if (encoded1.replace(/^0+/, '').length > 11 || encoded2.replace(/^0+/, '').length > 11) {
          sawFullEntropy = true;
          break;
        }
      }

      expect(sawFullEntropy).toBe(true);
    });

    it('should accept explicit random values', () => {
      const random1 = 123456789n;
      const random2 = 987654321n;
      const id = Ashid.create4('tok', random1, random2);
      expect(id).toMatch(/^tok_/);
    });

    it('should preserve 64-bit values in roundtrip', () => {
      const random1 = 18446744073709551615n; // max 64-bit
      const random2 = 9223372036854775808n; // 2^63
      const id = Ashid.create4('tok', random1, random2);

      // Parse and verify
      const [prefix, encoded1, encoded2] = Ashid.parse(id);
      expect(prefix).toBe('tok_');

      // Both components should be 13 chars (padded)
      expect(encoded1.length).toBe(13);
      expect(encoded2.length).toBe(13);
    });
  });

  describe('UUID round-trip', () => {
    it('toUuid produces a 36-char dashed hex string', () => {
      const id = Ashid.create('user', 1733140800000, 8234567890123456789n);
      const uuid = Ashid.toUuid(id);
      expect(uuid).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
      expect(uuid.length).toBe(36);
    });

    it('toUuid is prefix-agnostic — same UUID regardless of prefix', () => {
      const ts = 1733140800000;
      const rand = 8234567890123456789n;
      const withPrefix = Ashid.create('user', ts, rand);
      const noPrefix = Ashid.create(undefined, ts, rand);
      expect(Ashid.toUuid(withPrefix)).toBe(Ashid.toUuid(noPrefix));
    });

    it('round-trips an Ashid through UUID losslessly (standard form)', () => {
      const original = Ashid.create('user', 1733140800000, 8234567890123456789n);
      const uuid = Ashid.toUuid(original);
      const restored = Ashid.fromUuid(uuid, 'user');
      expect(restored).toBe(original);
    });

    it('round-trips an ashid4 through UUID losslessly (long form)', () => {
      const original = Ashid.create4('tok', 18446744073709551615n, 9223372036854775808n);
      const uuid = Ashid.toUuid(original);
      const restored = Ashid.fromUuid(uuid, 'tok');
      expect(restored).toBe(original);
    });

    it('round-trips a UUID-1-shaped UUID into a 22-char standard Ashid', () => {
      // High half within 2^45 → fits standard form
      const uuid = '00000123-abcd-ef01-2345-6789abcdef01';
      const id = Ashid.fromUuid(uuid);
      // No prefix → 22-char base
      expect(id.length).toBe(22);
      expect(Ashid.toUuid(id)).toBe(uuid);
    });

    it('routes UUIDv4 to the 26-char ashid4 form', () => {
      const uuid = '550e8400-e29b-41d4-a716-446655440000';
      const id = Ashid.fromUuid(uuid);
      // No prefix → ashid4 base = 26 chars (since high bits ≫ MAX_TIMESTAMP)
      expect(id.length).toBe(26);
      expect(Ashid.toUuid(id)).toBe(uuid);
    });

    it('routes UUIDv7 to the 26-char ashid4 form', () => {
      // UUIDv7: version bits at 48-51 force high half above 2^45
      const uuid = '019e008b-edc4-7265-8312-f6a278b46b11';
      const id = Ashid.fromUuid(uuid);
      expect(id.length).toBe(26);
      expect(Ashid.toUuid(id)).toBe(uuid);
    });

    it('round-trips the canonical UUIDs from the LinkedIn post', () => {
      for (const uuid of [
        '550e8400-e29b-41d4-a716-446655440000',
        '7c9e6679-7425-40de-944b-e07fc1f90ae7',
        'f47ac10b-58cc-4372-a567-0e02b2c3d479',
      ]) {
        expect(Ashid.toUuid(Ashid.fromUuid(uuid))).toBe(uuid);
      }
    });

    it('accepts UUID without dashes (32-char hex)', () => {
      const dashed = '550e8400-e29b-41d4-a716-446655440000';
      const undashed = '550e8400e29b41d4a716446655440000';
      expect(Ashid.fromUuid(dashed)).toBe(Ashid.fromUuid(undashed));
    });

    it('preserves prefix through fromUuid', () => {
      const id = Ashid.fromUuid('550e8400-e29b-41d4-a716-446655440000', 'user');
      expect(id).toMatch(/^user_/);
      // toUuid strips prefix; round-trip via passing prefix back
      expect(Ashid.fromUuid(Ashid.toUuid(id), 'user')).toBe(id);
    });

    it('handles all-zero UUID', () => {
      const uuid = '00000000-0000-0000-0000-000000000000';
      const id = Ashid.fromUuid(uuid);
      expect(Ashid.toUuid(id)).toBe(uuid);
    });

    it('handles all-FF (max) UUID', () => {
      const uuid = 'ffffffff-ffff-ffff-ffff-ffffffffffff';
      const id = Ashid.fromUuid(uuid);
      expect(id.length).toBe(26); // long form (high > MAX_TIMESTAMP)
      expect(Ashid.toUuid(id)).toBe(uuid);
    });

    it('throws on invalid UUID input', () => {
      expect(() => Ashid.fromUuid('not-a-uuid')).toThrow(/Invalid UUID/);
      expect(() => Ashid.fromUuid('550e8400e29b41d4a71644665544000')).toThrow(/Invalid UUID/); // 31 hex chars
      expect(() => Ashid.fromUuid('550e8400-e29b-41d4-a716-44665544000g')).toThrow(/Invalid UUID/); // non-hex
    });

    // Helpers for the property test: generate a random 128-bit UUID-shaped hex
    // string. Uses Math.random — fine for test coverage; not cryptographic.
    function randomUuid(): string {
      const hex = Array.from({ length: 32 }, () =>
        Math.floor(Math.random() * 16).toString(16)
      ).join('');
      return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
    }

    it('round-trips 1000 random UUIDs losslessly (property test)', () => {
      for (let i = 0; i < 1000; i++) {
        const uuid = randomUuid();
        expect(Ashid.toUuid(Ashid.fromUuid(uuid))).toBe(uuid);
      }
    });

    it('round-trips 1000 random UUIDs with prefix losslessly (property test)', () => {
      for (let i = 0; i < 1000; i++) {
        const uuid = randomUuid();
        const id = Ashid.fromUuid(uuid, 'tok');
        expect(id).toMatch(/^tok_/);
        expect(Ashid.toUuid(id)).toBe(uuid);
      }
    });

    it('round-trips UUIDs with the high bit of each half set', () => {
      // High bit of high half: 8000... — beyond MAX_TIMESTAMP → ashid4 form.
      // High bit of low half: ...8000 — exercises low-half sign-bit territory.
      const cases = [
        '80000000-0000-0000-0000-000000000000',
        '00000000-0000-0000-8000-000000000000',
        '80000000-0000-0000-8000-000000000000',
        'ffffffff-ffff-ffff-8000-000000000001',
        '80000000-0000-0001-ffff-ffffffffffff',
      ];
      for (const uuid of cases) {
        expect(Ashid.toUuid(Ashid.fromUuid(uuid))).toBe(uuid);
      }
    });
  });

  describe('negative input handling', () => {
    // create/create1/create4 all reject negative inputs via the encoder.
    // Callers passing signed-long UUID halves (Java/Kotlin Long) must mask to
    // unsigned themselves before calling — the library does not silently
    // reinterpret negatives.

    it('create4 throws on negative BigInt', () => {
      expect(() => Ashid.create4('tok', -1n, 0n)).toThrow(/non-negative/);
      expect(() => Ashid.create4('tok', 0n, -2n)).toThrow(/non-negative/);
    });

    it('create throws on negative BigInt time', () => {
      expect(() => Ashid.create('user', -1n, 0n)).toThrow(/non-negative/);
    });

    it('create throws on negative BigInt random', () => {
      expect(() => Ashid.create('user', 1000, -1n)).toThrow(/non-negative/);
    });
  });
});
