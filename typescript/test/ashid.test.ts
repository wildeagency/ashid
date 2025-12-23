import { describe, it, expect } from 'vitest';
import { Ashid, ashid, parseAshid } from '../src/ashid';

describe('Ashid', () => {
  describe('create', () => {
    it('should create Ashid without prefix (fixed 22-char format)', () => {
      const id = Ashid.create();
      expect(id).toBeTruthy();
      expect(id.length).toBe(22);
      expect(/^[0-9]/.test(id)).toBe(true);
    });

    it('should create Ashid with prefix without underscore (fixed 22-char base)', () => {
      const id = Ashid.create('u');
      expect(id).toMatch(/^u[0-9]/);
      expect(id.length).toBe(23); // 'u' + 22 chars
    });

    it('should create Ashid with prefix with underscore (variable length)', () => {
      const id = Ashid.create('user_');
      expect(id).toMatch(/^user_/);
    });

    it('should accept empty string as no prefix', () => {
      const id = Ashid.create('');
      expect(/^[0-9]/.test(id)).toBe(true);
      expect(id.length).toBe(22);
    });

    it('should throw on prefix with numbers', () => {
      expect(() => Ashid.create('u1')).toThrow('only letters');
    });

    it('should throw on prefix with hyphens', () => {
      expect(() => Ashid.create('a-b')).toThrow('only letters');
    });

    it('should throw on prefix with underscore in middle', () => {
      expect(() => Ashid.create('a_b')).toThrow('only letters');
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
      const id = Ashid.create('USER_', 1000, 0);
      expect(id).toMatch(/^user_/);
      expect(Ashid.prefix(id)).toBe('user_');
    });

    it('should lowercase uppercase prefix without underscore in create', () => {
      const id = Ashid.create('U', 1000, 0);
      expect(id).toMatch(/^u[0-9]/);
      expect(Ashid.prefix(id)).toBe('u');
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

    describe('prefix without underscore (fixed 22-char base)', () => {
      it('timestamp 0 should produce padded base ID', () => {
        const id = Ashid.create('u', 0, 0);
        expect(id).toBe('u0000000000000000000000');
        expect(id.length).toBe(23);
      });

      it('should always have 22-char base ID', () => {
        const id = Ashid.create('user', Date.now(), 12345);
        expect(id.length).toBe(26); // 'user' (4) + 22
      });
    });

    describe('prefix with underscore (variable length)', () => {
      it('timestamp 0, random 0 should be minimal', () => {
        const id = Ashid.create('user_', 0, 0);
        expect(id).toBe('user_0');
      });

      it('timestamp 0, random 1 should omit timestamp', () => {
        const id = Ashid.create('user_', 0, 1);
        expect(id).toBe('user_1');
      });

      it('timestamp 0, random 31 should be short', () => {
        const id = Ashid.create('user_', 0, 31);
        expect(id).toBe('user_z');
      });

      it('timestamp 1, random 0 should include timestamp', () => {
        const id = Ashid.create('user_', 1, 0);
        expect(id).toBe('user_10000000000000');
        expect(id.length).toBe(19); // 'user_' (5) + 1 + 13
      });

      it('current timestamp should have variable length', () => {
        const id = Ashid.create('user_', Date.now(), 0);
        // 'user_' (5) + 9 (current timestamp) + 13 (padded random) = 27
        expect(id.length).toBe(27);
      });
    });
  });

  describe('parse', () => {
    it('should parse Ashid without prefix', () => {
      const id = '1kbg1jmtt0000000000000';
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe('');
      expect(timestamp).toBe('1kbg1jmtt');
      expect(random).toBe('0000000000000');
    });

    it('should parse Ashid with prefix without underscore', () => {
      const id = 'u1kbg1jmtt0000000000000';
      const [prefix, timestamp, random] = Ashid.parse(id);
      expect(prefix).toBe('u');
      expect(timestamp).toBe('1kbg1jmtt');
      expect(random).toBe('0000000000000');
    });

    it('should parse Ashid with prefix with underscore (variable)', () => {
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

    it('should throw on wrong length without underscore', () => {
      expect(() => Ashid.parse('u12345')).toThrow('must be 22 or 26 characters');
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

    it('should extract prefix without underscore', () => {
      const id = Ashid.create('u', 1000, 0);
      expect(Ashid.prefix(id)).toBe('u');
    });

    it('should extract prefix with underscore', () => {
      const id = Ashid.create('user_', 1000, 0);
      expect(Ashid.prefix(id)).toBe('user_');
    });
  });

  describe('timestamp()', () => {
    it('should extract timestamp from fixed format', () => {
      const timestamp = 1609459200000;
      const id = Ashid.create('u', timestamp, 0);
      expect(Ashid.timestamp(id)).toBe(timestamp);
    });

    it('should extract timestamp from variable format', () => {
      const timestamp = 1609459200000;
      const id = Ashid.create('user_', timestamp, 0);
      expect(Ashid.timestamp(id)).toBe(timestamp);
    });

    it('should extract timestamp 0 from fixed format', () => {
      const id = Ashid.create('u', 0, 12345);
      expect(Ashid.timestamp(id)).toBe(0);
    });

    it('should extract timestamp 0 from variable format', () => {
      const id = Ashid.create('user_', 0, 12345);
      expect(Ashid.timestamp(id)).toBe(0);
    });
  });

  describe('random()', () => {
    it('should extract random from fixed format', () => {
      const random = 123456789;
      const id = Ashid.create('u', Date.now(), random);
      expect(Ashid.random(id)).toBe(random);
    });

    it('should extract random from variable format', () => {
      const random = 123456789;
      const id = Ashid.create('user_', Date.now(), random);
      expect(Ashid.random(id)).toBe(random);
    });

    it('should extract random 0 from fixed format', () => {
      const id = Ashid.create('u', 1000, 0);
      expect(Ashid.random(id)).toBe(0);
    });

    it('should extract random from variable format with timestamp 0', () => {
      const id = Ashid.create('user_', 0, 12345);
      expect(Ashid.random(id)).toBe(12345);
    });
  });

  describe('normalize()', () => {
    it('should lowercase uppercase characters', () => {
      // Create a valid ID then uppercase it
      const original = Ashid.create('u', 1609459200000, 0);
      const uppercased = original.toUpperCase();
      const normalized = Ashid.normalize(uppercased);
      expect(normalized).toBe(original);
    });

    it('should convert ambiguous characters via decode/encode round-trip', () => {
      // Use underscore prefix so we can manipulate base without length issues
      const original = Ashid.create('user_', 1609459200000, 1111);
      // I, L map to 1; O maps to 0
      // Replace in the base32 portion only
      const [prefix, ts, rand] = Ashid.parse(original);
      const modifiedTs = ts.replace(/1/g, 'I').replace(/0/g, 'O');
      const modifiedRand = rand.replace(/1/g, 'L').replace(/0/g, 'O');
      const modified = prefix + modifiedTs + modifiedRand;

      const normalized = Ashid.normalize(modified);
      expect(Ashid.timestamp(normalized)).toBe(1609459200000);
      expect(Ashid.random(normalized)).toBe(1111);
    });

    it('should normalize variable length format', () => {
      const original = Ashid.create('user_', 1609459200000, 12345);
      const uppercased = original.toUpperCase();
      const normalized = Ashid.normalize(uppercased);
      expect(normalized).toBe(original);
    });

    it('should preserve values through round-trip', () => {
      const original = Ashid.create('user_', 1609459200000, 12345);
      const normalized = Ashid.normalize(original.toUpperCase());
      expect(Ashid.timestamp(normalized)).toBe(1609459200000);
      expect(Ashid.random(normalized)).toBe(12345);
    });

    it('should normalize fixed format with uppercase', () => {
      const original = Ashid.create('u', 1609459200000, 12345);
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
  });

  describe('isValid', () => {
    it('should validate correct Ashids', () => {
      expect(Ashid.isValid(Ashid.create())).toBe(true);
      expect(Ashid.isValid(Ashid.create('u'))).toBe(true);
      expect(Ashid.isValid(Ashid.create('user_'))).toBe(true);
    });

    it('should validate timestamp 0 IDs', () => {
      expect(Ashid.isValid(Ashid.create(undefined, 0, 0))).toBe(true);
      expect(Ashid.isValid(Ashid.create('u', 0, 0))).toBe(true);
      expect(Ashid.isValid(Ashid.create('user_', 0, 0))).toBe(true);
    });

    it('should reject empty strings', () => {
      expect(Ashid.isValid('')).toBe(false);
    });

    it('should reject invalid base32', () => {
      expect(Ashid.isValid('u1234567890-invalid!@#')).toBe(false);
    });

    it('should reject wrong length without underscore', () => {
      expect(Ashid.isValid('u12345')).toBe(false);
    });
  });

  describe('time-sortability', () => {
    it('should be sortable by creation time (fixed format)', () => {
      const base = Date.now();
      const id1 = Ashid.create('u', base, 0);
      const id2 = Ashid.create('u', base + 1000, 0);
      const id3 = Ashid.create('u', base + 2000, 0);

      const sorted = [id3, id1, id2].sort();
      expect(sorted).toEqual([id1, id2, id3]);
    });

    it('should be sortable by creation time (variable format)', () => {
      const base = Date.now();
      const id1 = Ashid.create('user_', base, 0);
      const id2 = Ashid.create('user_', base + 1000, 0);
      const id3 = Ashid.create('user_', base + 2000, 0);

      const sorted = [id3, id1, id2].sort();
      expect(sorted).toEqual([id1, id2, id3]);
    });

    it('should sort by random when timestamp is same', () => {
      const timestamp = Date.now();
      const id1 = Ashid.create('u', timestamp, 1000);
      const id2 = Ashid.create('u', timestamp, 2000);

      const sorted = [id2, id1].sort();
      expect(sorted).toEqual([id1, id2]);
    });
  });

  describe('convenience functions', () => {
    it('ashid() should create Ashid', () => {
      const id = ashid();
      expect(Ashid.isValid(id)).toBe(true);
    });

    it('ashid(prefix) should create Ashid with prefix', () => {
      const id = ashid('u');
      expect(id).toMatch(/^u[0-9]/);
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
      const id = ashid('u');
      expect(id).not.toContain('-');
    });

    it('should not contain spaces', () => {
      const id = ashid('u');
      expect(id).not.toContain(' ');
    });

    it('base ID should only contain alphanumeric and underscore', () => {
      const id1 = ashid();
      const id2 = ashid('user_');
      expect(id1).toMatch(/^[a-z0-9]+$/);
      expect(id2).toMatch(/^[a-z_][a-z0-9_]+$/);
    });
  });
});
