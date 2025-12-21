package agency.wilde.ashid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EncoderBase32CrockfordTest {

    @Test
    @DisplayName("encode: zero returns '0'")
    void encodeZero() {
        assertEquals("0", EncoderBase32Crockford.encode(0));
    }

    @Test
    @DisplayName("encode: small numbers")
    void encodeSmallNumbers() {
        assertEquals("1", EncoderBase32Crockford.encode(1));
        assertEquals("v", EncoderBase32Crockford.encode(27));
        assertEquals("z", EncoderBase32Crockford.encode(31));
        assertEquals("10", EncoderBase32Crockford.encode(32));
    }

    @Test
    @DisplayName("encode: large numbers")
    void encodeLargeNumbers() {
        assertEquals("z8", EncoderBase32Crockford.encode(1000));
        assertEquals("3zt", EncoderBase32Crockford.encode(4090));
        assertEquals("7zzzzzzzzzz", EncoderBase32Crockford.encode(9007199254740991L));
    }

    @Test
    @DisplayName("encode: with padding")
    void encodeWithPadding() {
        assertEquals("0000000000000", EncoderBase32Crockford.encode(0, true));
        assertEquals("0000000000001", EncoderBase32Crockford.encode(1, true));
        assertEquals("00000000000z8", EncoderBase32Crockford.encode(1000, true));
    }

    @Test
    @DisplayName("encode: throws on negative number")
    void encodeNegativeThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncoderBase32Crockford.encode(-1));
    }

    @Test
    @DisplayName("decode: zero")
    void decodeZero() {
        assertEquals(0, EncoderBase32Crockford.decode("0"));
    }

    @Test
    @DisplayName("decode: small numbers")
    void decodeSmallNumbers() {
        assertEquals(1, EncoderBase32Crockford.decode("1"));
        assertEquals(27, EncoderBase32Crockford.decode("v"));
        assertEquals(31, EncoderBase32Crockford.decode("z"));
        assertEquals(32, EncoderBase32Crockford.decode("10"));
    }

    @Test
    @DisplayName("decode: case insensitive")
    void decodeCaseInsensitive() {
        assertEquals(EncoderBase32Crockford.decode("abc"), EncoderBase32Crockford.decode("ABC"));
        assertEquals(EncoderBase32Crockford.decode("xyz"), EncoderBase32Crockford.decode("XYZ"));
    }

    @Test
    @DisplayName("decode: lookalike character mapping O->0")
    void decodeLookalikeO() {
        assertEquals(EncoderBase32Crockford.decode("0"), EncoderBase32Crockford.decode("o"));
        assertEquals(EncoderBase32Crockford.decode("0"), EncoderBase32Crockford.decode("O"));
    }

    @Test
    @DisplayName("decode: lookalike character mapping I/L->1")
    void decodeLookalikeIL() {
        assertEquals(EncoderBase32Crockford.decode("1"), EncoderBase32Crockford.decode("i"));
        assertEquals(EncoderBase32Crockford.decode("1"), EncoderBase32Crockford.decode("I"));
        assertEquals(EncoderBase32Crockford.decode("1"), EncoderBase32Crockford.decode("l"));
        assertEquals(EncoderBase32Crockford.decode("1"), EncoderBase32Crockford.decode("L"));
    }

    @Test
    @DisplayName("decode: U maps to V")
    void decodeLookalikeU() {
        assertEquals(EncoderBase32Crockford.decode("v"), EncoderBase32Crockford.decode("u"));
        assertEquals(EncoderBase32Crockford.decode("v"), EncoderBase32Crockford.decode("U"));
    }

    @Test
    @DisplayName("decode: throws on empty string")
    void decodeEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncoderBase32Crockford.decode(""));
        assertThrows(IllegalArgumentException.class, () -> EncoderBase32Crockford.decode(null));
    }

    @Test
    @DisplayName("decode: throws on invalid character")
    void decodeInvalidCharThrows() {
        assertThrows(IllegalArgumentException.class, () -> EncoderBase32Crockford.decode("abc!"));
        assertThrows(IllegalArgumentException.class, () -> EncoderBase32Crockford.decode("abc-def"));
    }

    @Test
    @DisplayName("roundtrip: encode then decode returns original")
    void roundtrip() {
        long[] testValues = {0, 1, 31, 32, 1000, 123456789, 9007199254740991L};
        for (long value : testValues) {
            assertEquals(value, EncoderBase32Crockford.decode(EncoderBase32Crockford.encode(value)));
        }
    }

    @Test
    @DisplayName("roundtrip: works with padding")
    void roundtripWithPadding() {
        long[] testValues = {0, 1, 1000, 9007199254740991L};
        for (long value : testValues) {
            assertEquals(value, EncoderBase32Crockford.decode(EncoderBase32Crockford.encode(value, true)));
        }
    }

    @Test
    @DisplayName("secureRandomLong: generates non-negative numbers")
    void secureRandomLongNonNegative() {
        for (int i = 0; i < 100; i++) {
            long value = EncoderBase32Crockford.secureRandomLong();
            assertTrue(value >= 0, "Random value should be non-negative");
        }
    }

    @Test
    @DisplayName("secureRandomLong: generates different values")
    void secureRandomLongVariety() {
        Set<Long> values = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            values.add(EncoderBase32Crockford.secureRandomLong());
        }
        assertTrue(values.size() > 90, "Should generate mostly unique values");
    }

    @Test
    @DisplayName("isValid: returns true for valid Base32")
    void isValidTrue() {
        assertTrue(EncoderBase32Crockford.isValid("0"));
        assertTrue(EncoderBase32Crockford.isValid("abc123"));
        assertTrue(EncoderBase32Crockford.isValid("ABCXYZ"));
    }

    @Test
    @DisplayName("isValid: returns false for invalid strings")
    void isValidFalse() {
        assertFalse(EncoderBase32Crockford.isValid(""));
        assertFalse(EncoderBase32Crockford.isValid(null));
        assertFalse(EncoderBase32Crockford.isValid("abc!"));
        assertFalse(EncoderBase32Crockford.isValid("hello world"));
    }

    @Test
    @DisplayName("isValid: accepts lookalike characters")
    void isValidLookalikes() {
        assertTrue(EncoderBase32Crockford.isValid("oOiIlLuU"));
    }
}
