import { describe, it, expect } from 'vitest';
import { EncoderBase32Crockford } from '../src/encoder';

describe('EncoderBase32Crockford', () => {
  describe('encode', () => {
    it('should encode zero', () => {
      expect(EncoderBase32Crockford.encode(0)).toBe('0');
    });

    it('should encode small numbers', () => {
      expect(EncoderBase32Crockford.encode(1)).toBe('1');
      expect(EncoderBase32Crockford.encode(31)).toBe('z');
      expect(EncoderBase32Crockford.encode(32)).toBe('10');
    });

    it('should encode large numbers', () => {
      expect(EncoderBase32Crockford.encode(1000)).toBe('z8');
      expect(EncoderBase32Crockford.encode(123456)).toBe('3rj0');
    });

    it('should pad when requested', () => {
      const encoded = EncoderBase32Crockford.encode(123, true);
      expect(encoded).toHaveLength(13);
      expect(encoded).toBe('000000000003v');
    });

    it('should not pad by default', () => {
      const encoded = EncoderBase32Crockford.encode(123);
      expect(encoded.length).toBeLessThan(13);
      expect(encoded).toBe('3v');
    });

    it('should handle timestamp-like numbers', () => {
      const timestamp = Date.now();
      const encoded = EncoderBase32Crockford.encode(timestamp);
      expect(encoded).toBeTruthy();
      expect(encoded.length).toBeGreaterThan(0);
    });

    it('should throw on negative numbers', () => {
      expect(() => EncoderBase32Crockford.encode(-1)).toThrow();
    });

    it('should throw on non-finite numbers', () => {
      expect(() => EncoderBase32Crockford.encode(NaN)).toThrow();
      expect(() => EncoderBase32Crockford.encode(Infinity)).toThrow();
    });
  });

  describe('decode', () => {
    it('should decode zero', () => {
      expect(EncoderBase32Crockford.decode('0')).toBe(0);
    });

    it('should decode small numbers', () => {
      expect(EncoderBase32Crockford.decode('1')).toBe(1);
      expect(EncoderBase32Crockford.decode('z')).toBe(31);
      expect(EncoderBase32Crockford.decode('10')).toBe(32);
    });

    it('should decode large numbers', () => {
      expect(EncoderBase32Crockford.decode('z8')).toBe(1000);
      expect(EncoderBase32Crockford.decode('3rj0')).toBe(123456);
    });

    it('should be case-insensitive', () => {
      expect(EncoderBase32Crockford.decode('ABC')).toBe(
        EncoderBase32Crockford.decode('abc')
      );
      expect(EncoderBase32Crockford.decode('XyZ')).toBe(
        EncoderBase32Crockford.decode('xyz')
      );
    });

    it('should map lookalike characters (O → 0)', () => {
      expect(EncoderBase32Crockford.decode('O')).toBe(0);
      expect(EncoderBase32Crockford.decode('o')).toBe(0);
      expect(EncoderBase32Crockford.decode('0')).toBe(0);
    });

    it('should map lookalike characters (I/L → 1)', () => {
      expect(EncoderBase32Crockford.decode('I')).toBe(1);
      expect(EncoderBase32Crockford.decode('i')).toBe(1);
      expect(EncoderBase32Crockford.decode('L')).toBe(1);
      expect(EncoderBase32Crockford.decode('l')).toBe(1);
      expect(EncoderBase32Crockford.decode('1')).toBe(1);
    });

    it('should throw on empty string', () => {
      expect(() => EncoderBase32Crockford.decode('')).toThrow();
    });

    it('should throw on invalid characters', () => {
      expect(() => EncoderBase32Crockford.decode('abc-def')).toThrow();
      expect(() => EncoderBase32Crockford.decode('abc def')).toThrow();
      expect(() => EncoderBase32Crockford.decode('abc_def')).toThrow();
    });
  });

  describe('roundtrip encoding/decoding', () => {
    it('should encode and decode to same value', () => {
      const testCases = [0, 1, 31, 32, 100, 1000, 123456, Date.now()];

      testCases.forEach((num) => {
        const encoded = EncoderBase32Crockford.encode(num);
        const decoded = EncoderBase32Crockford.decode(encoded);
        expect(decoded).toBe(num);
      });
    });

    it('should handle padded encoding roundtrip', () => {
      const num = 12345;
      const encoded = EncoderBase32Crockford.encode(num, true);
      const decoded = EncoderBase32Crockford.decode(encoded);
      expect(decoded).toBe(num);
    });
  });

  describe('secureRandomLong', () => {
    it('should generate positive numbers', () => {
      for (let i = 0; i < 100; i++) {
        const random = EncoderBase32Crockford.secureRandomLong();
        expect(random).toBeGreaterThanOrEqual(0);
      }
    });

    it('should generate different values', () => {
      const values = new Set<number>();
      for (let i = 0; i < 100; i++) {
        values.add(EncoderBase32Crockford.secureRandomLong());
      }
      // Should have generated many unique values (allow some collisions)
      expect(values.size).toBeGreaterThan(90);
    });

    it('should generate safe integers', () => {
      for (let i = 0; i < 100; i++) {
        const random = EncoderBase32Crockford.secureRandomLong();
        expect(Number.isSafeInteger(random)).toBe(true);
      }
    });
  });

  describe('isValid', () => {
    it('should validate correct Base32 strings', () => {
      expect(EncoderBase32Crockford.isValid('0')).toBe(true);
      expect(EncoderBase32Crockford.isValid('1')).toBe(true);
      expect(EncoderBase32Crockford.isValid('abc123')).toBe(true);
      expect(EncoderBase32Crockford.isValid('1fvszawr42tve3gxvx9900')).toBe(true);
    });

    it('should reject invalid strings', () => {
      expect(EncoderBase32Crockford.isValid('')).toBe(false);
      expect(EncoderBase32Crockford.isValid('abc-def')).toBe(false);
      expect(EncoderBase32Crockford.isValid('abc def')).toBe(false);
      expect(EncoderBase32Crockford.isValid('abc_def')).toBe(false);
    });

    it('should accept lookalike characters', () => {
      expect(EncoderBase32Crockford.isValid('OIL')).toBe(true); // O→0, I→1, L→1
      expect(EncoderBase32Crockford.isValid('oil')).toBe(true);
    });
  });
});
