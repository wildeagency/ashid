"""Crockford Base32 encoder.

Douglas Crockford's Base32 encoding provides:
- Case-insensitive encoding (0-9, a-z)
- Lookalike character mapping (I/L -> 1, O -> 0, U -> V)
- No special characters
- Human-readable and error-resistant

Alphabet: 0123456789abcdefghjkmnpqrstvwxyz (32 characters)
Excluded: i, l, o, u (mapped to lookalikes during decode)
"""

import os


_ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
_PAD_LENGTH = 13


def _build_decode_table() -> dict:
    table = {}
    for index, char in enumerate(_ALPHABET):
        table[char] = index
        table[char.upper()] = index
    # Lookalikes
    for c in ("o", "O"):
        table[c] = 0
    for c in ("i", "I", "l", "L"):
        table[c] = 1
    # 'u'/'U' map to v (27)
    for c in ("u", "U"):
        table[c] = 27
    return table


_DECODE_TABLE = _build_decode_table()


class EncoderBase32Crockford:
    """Crockford Base32 encoder with lookalike character handling."""

    @staticmethod
    def encode(n: int, padded: bool = False) -> str:
        """Encode a non-negative integer to a Crockford Base32 string."""
        if not isinstance(n, int) or isinstance(n, bool):
            raise TypeError("Input must be a non-negative integer")
        if n < 0:
            raise ValueError("Input must be a non-negative integer")

        if n == 0:
            return "0".rjust(_PAD_LENGTH, "0") if padded else "0"

        # Iterative encoding using a bytearray for efficiency
        chars = bytearray()
        while n > 0:
            n, remainder = divmod(n, 32)
            chars.append(ord(_ALPHABET[remainder]))
        chars.reverse()
        encoded = chars.decode("ascii")
        return encoded.rjust(_PAD_LENGTH, "0") if padded else encoded

    @staticmethod
    def encode_bigint(n: int, padded: bool = False) -> str:
        """Encode a non-negative integer (Python ints are arbitrary precision)."""
        return EncoderBase32Crockford.encode(n, padded=padded)

    @staticmethod
    def decode(s: str) -> int:
        """Decode a Crockford Base32 string to an integer."""
        if not s:
            raise ValueError("Input string cannot be empty")

        result = 0
        for char in s:
            value = _DECODE_TABLE.get(char)
            if value is None:
                raise ValueError(f"Invalid character in Base32 string: '{char}'")
            result = result * 32 + value
        return result

    @staticmethod
    def decode_bigint(s: str) -> int:
        """Decode a Crockford Base32 string to an integer (alias for decode)."""
        return EncoderBase32Crockford.decode(s)

    @staticmethod
    def secure_random_long() -> int:
        """Generate a cryptographically secure random unsigned 64-bit integer."""
        return int.from_bytes(os.urandom(8), "big")

    @staticmethod
    def is_valid(s: str) -> bool:
        """Validate whether a string is decodable as Crockford Base32."""
        if not s:
            return False
        try:
            EncoderBase32Crockford.decode(s)
            return True
        except (ValueError, TypeError):
            return False
