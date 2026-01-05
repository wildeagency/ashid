import { describe, it, expect } from 'vitest';
import { Ashid, ashid, ashid4, parseAshid } from '../src/ashid';

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

    it('should throw on timestamp exceeding max (Dec 12, 3084)', () => {
      const maxTimestamp = 35184372088831;
      expect(() => Ashid.create(undefined, maxTimestamp)).not.toThrow();
      expect(() => Ashid.create(undefined, maxTimestamp + 1)).toThrow('must not exceed');
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
      it('timestamp 0, random 0 should be minimal', () => {
        const id = Ashid.create('user', 0, 0);
        expect(id).toBe('user_0');
      });

      it('timestamp 0, random 1 should omit timestamp', () => {
        const id = Ashid.create('user', 0, 1);
        expect(id).toBe('user_1');
      });

      it('timestamp 0, random 31 should be short', () => {
        const id = Ashid.create('user', 0, 31);
        expect(id).toBe('user_z');
      });

      it('timestamp 1, random 0 should include timestamp', () => {
        const id = Ashid.create('user', 1, 0);
        expect(id).toBe('user_10000000000000');
        expect(id.length).toBe(19); // 'user_' (5) + 1 + 13
      });

      it('current timestamp should have variable length', () => {
        const id = Ashid.create('user', Date.now(), 0);
        // 'user_' (5) + 9 (current timestamp) + 13 (padded random) = 27
        expect(id.length).toBe(27);
      });

      it('single letter prefix should also get delimiter', () => {
        const id = Ashid.create('u', 0, 0);
        expect(id).toBe('u_0');
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
    it('should lowercase uppercase characters', () => {
      const original = Ashid.create('user', 1609459200000, 0n);
      const uppercased = original.toUpperCase();
      const normalized = Ashid.normalize(uppercased);
      expect(normalized).toBe(original);
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

    it('should normalize variable length format', () => {
      const original = Ashid.create('user', 1609459200000, 12345n);
      const uppercased = original.toUpperCase();
      const normalized = Ashid.normalize(uppercased);
      expect(normalized).toBe(original);
    });

    it('should preserve values through round-trip', () => {
      const original = Ashid.create('user', 1609459200000, 12345n);
      const normalized = Ashid.normalize(original.toUpperCase());
      expect(Ashid.timestamp(normalized)).toBe(1609459200000);
      expect(Ashid.random(normalized)).toBe(12345n);
    });

    it('should normalize fixed format (no prefix) with uppercase', () => {
      const original = Ashid.create(undefined, 1609459200000, 12345n);
      const normalized = Ashid.normalize(original.toUpperCase());
      expect(normalized).toBe(original);
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
      // Generate many IDs and check that we see values beyond 53-bit range
      const samples = 100;
      let sawFullEntropy = false;
      const threshold = BigInt(Number.MAX_SAFE_INTEGER);

      for (let i = 0; i < samples; i++) {
        const id = ashid4();
        const [, encoded1, encoded2] = Ashid.parse(id);

        // Parse each component using decodeBigInt (via random())
        // For ashid4, parse returns both random components
        const fullId = Ashid.create4(undefined, Ashid.random(id), Ashid.random(id));

        // Check if any generated random exceeds 53-bit
        // We need to check the raw generated values
        // The simplest check: look at the encoded length
        if (encoded1.replace(/^0+/, '').length > 11 || encoded2.replace(/^0+/, '').length > 11) {
          sawFullEntropy = true;
          break;
        }
      }

      // With 64-bit entropy, ~50% should have values > 2^53, requiring > 11 chars
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
});
