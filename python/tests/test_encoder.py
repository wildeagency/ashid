"""Tests for EncoderBase32Crockford."""

import pytest

from ashid import EncoderBase32Crockford


class TestEncode:
    def test_encode_zero(self):
        assert EncoderBase32Crockford.encode(0) == "0"

    def test_encode_small_numbers(self):
        assert EncoderBase32Crockford.encode(1) == "1"
        assert EncoderBase32Crockford.encode(31) == "z"
        assert EncoderBase32Crockford.encode(32) == "10"

    def test_encode_large_numbers(self):
        assert EncoderBase32Crockford.encode(1000) == "z8"
        assert EncoderBase32Crockford.encode(123456) == "3rj0"

    def test_encode_padded(self):
        encoded = EncoderBase32Crockford.encode(123, padded=True)
        assert len(encoded) == 13
        assert encoded == "000000000003v"

    def test_encode_unpadded_by_default(self):
        encoded = EncoderBase32Crockford.encode(123)
        assert len(encoded) < 13
        assert encoded == "3v"

    def test_encode_timestamp_like(self):
        import time

        timestamp = int(time.time() * 1000)
        encoded = EncoderBase32Crockford.encode(timestamp)
        assert encoded
        assert len(encoded) > 0

    def test_encode_negative_raises(self):
        with pytest.raises(Exception):
            EncoderBase32Crockford.encode(-1)


class TestDecode:
    def test_decode_zero(self):
        assert EncoderBase32Crockford.decode("0") == 0

    def test_decode_small_numbers(self):
        assert EncoderBase32Crockford.decode("1") == 1
        assert EncoderBase32Crockford.decode("z") == 31
        assert EncoderBase32Crockford.decode("10") == 32

    def test_decode_large_numbers(self):
        assert EncoderBase32Crockford.decode("z8") == 1000
        assert EncoderBase32Crockford.decode("3rj0") == 123456

    def test_decode_case_insensitive(self):
        assert EncoderBase32Crockford.decode("ABC") == EncoderBase32Crockford.decode("abc")
        assert EncoderBase32Crockford.decode("XyZ") == EncoderBase32Crockford.decode("xyz")

    def test_decode_lookalike_o_to_zero(self):
        assert EncoderBase32Crockford.decode("O") == 0
        assert EncoderBase32Crockford.decode("o") == 0
        assert EncoderBase32Crockford.decode("0") == 0

    def test_decode_lookalike_il_to_one(self):
        assert EncoderBase32Crockford.decode("I") == 1
        assert EncoderBase32Crockford.decode("i") == 1
        assert EncoderBase32Crockford.decode("L") == 1
        assert EncoderBase32Crockford.decode("l") == 1
        assert EncoderBase32Crockford.decode("1") == 1

    def test_decode_lookalike_u_to_v(self):
        assert EncoderBase32Crockford.decode("u") == 27
        assert EncoderBase32Crockford.decode("U") == 27
        assert EncoderBase32Crockford.decode("v") == 27
        assert EncoderBase32Crockford.decode("V") == 27

    def test_decode_empty_raises(self):
        with pytest.raises(Exception):
            EncoderBase32Crockford.decode("")

    def test_decode_invalid_chars_raises(self):
        with pytest.raises(Exception):
            EncoderBase32Crockford.decode("abc-def")
        with pytest.raises(Exception):
            EncoderBase32Crockford.decode("abc def")
        with pytest.raises(Exception):
            EncoderBase32Crockford.decode("abc_def")


class TestRoundtrip:
    def test_int_roundtrip(self):
        import time

        cases = [0, 1, 31, 32, 100, 1000, 123456, int(time.time() * 1000)]
        for n in cases:
            assert EncoderBase32Crockford.decode(EncoderBase32Crockford.encode(n)) == n

    def test_padded_roundtrip(self):
        n = 12345
        encoded = EncoderBase32Crockford.encode(n, padded=True)
        assert EncoderBase32Crockford.decode(encoded) == n


class TestEncodeBigInt:
    def test_encode_zero(self):
        assert EncoderBase32Crockford.encode_bigint(0) == "0"

    def test_encode_small(self):
        assert EncoderBase32Crockford.encode_bigint(1) == "1"
        assert EncoderBase32Crockford.encode_bigint(31) == "z"
        assert EncoderBase32Crockford.encode_bigint(32) == "10"

    def test_encode_beyond_safe_int(self):
        beyond = (2**53) + 1
        encoded = EncoderBase32Crockford.encode_bigint(beyond)
        assert encoded
        assert len(encoded) > 10

    def test_encode_full_64bit(self):
        max_64 = (1 << 64) - 1  # 18446744073709551615
        encoded = EncoderBase32Crockford.encode_bigint(max_64)
        assert encoded == "fzzzzzzzzzzzz"

    def test_encode_padded(self):
        encoded = EncoderBase32Crockford.encode_bigint(123, padded=True)
        assert len(encoded) == 13
        assert encoded == "000000000003v"

    def test_encode_negative_raises(self):
        with pytest.raises(Exception):
            EncoderBase32Crockford.encode_bigint(-1)


class TestDecodeBigInt:
    def test_decode_zero(self):
        assert EncoderBase32Crockford.decode_bigint("0") == 0

    def test_decode_small(self):
        assert EncoderBase32Crockford.decode_bigint("1") == 1
        assert EncoderBase32Crockford.decode_bigint("z") == 31
        assert EncoderBase32Crockford.decode_bigint("10") == 32

    def test_decode_beyond_safe(self):
        beyond = (2**53) + 1
        encoded = EncoderBase32Crockford.encode_bigint(beyond)
        assert EncoderBase32Crockford.decode_bigint(encoded) == beyond

    def test_decode_full_64bit(self):
        max_64 = (1 << 64) - 1
        encoded = EncoderBase32Crockford.encode_bigint(max_64)
        assert EncoderBase32Crockford.decode_bigint(encoded) == max_64

    def test_decode_case_insensitive(self):
        assert EncoderBase32Crockford.decode_bigint("ABC") == EncoderBase32Crockford.decode_bigint("abc")


class TestBigIntRoundtrip:
    def test_roundtrip_small(self):
        import time

        cases = [0, 1, 31, 32, 100, 1000, 123456, int(time.time() * 1000)]
        for n in cases:
            assert EncoderBase32Crockford.decode_bigint(EncoderBase32Crockford.encode_bigint(n)) == n

    def test_roundtrip_beyond_safe(self):
        cases = [
            (2**53) + 1,
            (2**53) * 2,
            (1 << 64) - 1,  # max 64-bit
        ]
        for n in cases:
            assert EncoderBase32Crockford.decode_bigint(EncoderBase32Crockford.encode_bigint(n)) == n


class TestSecureRandomLong:
    def test_returns_non_negative_int(self):
        for _ in range(100):
            random = EncoderBase32Crockford.secure_random_long()
            assert isinstance(random, int)
            assert random >= 0

    def test_generates_distinct_values(self):
        values = {EncoderBase32Crockford.secure_random_long() for _ in range(100)}
        # Allow some collisions but expect mostly unique
        assert len(values) > 90

    def test_full_64bit_entropy(self):
        # With uniform 64-bit distribution, ~50% > 2^53 — so we should see at least one in 100 samples
        threshold = 2**53
        saw_beyond = False
        for _ in range(100):
            random = EncoderBase32Crockford.secure_random_long()
            if random > threshold:
                saw_beyond = True
                break
        assert saw_beyond

    def test_produces_13_char_encodings(self):
        saw_13 = False
        for _ in range(100):
            random = EncoderBase32Crockford.secure_random_long()
            encoded = EncoderBase32Crockford.encode_bigint(random)
            if len(encoded) == 13:
                saw_13 = True
                break
        assert saw_13

    def test_within_64bit_range(self):
        max_64 = (1 << 64) - 1
        for _ in range(100):
            random = EncoderBase32Crockford.secure_random_long()
            assert 0 <= random <= max_64


class TestIsValid:
    def test_valid_strings(self):
        assert EncoderBase32Crockford.is_valid("0") is True
        assert EncoderBase32Crockford.is_valid("1") is True
        assert EncoderBase32Crockford.is_valid("abc123") is True
        assert EncoderBase32Crockford.is_valid("1fvszawr42tve3gxvx9900") is True

    def test_invalid_strings(self):
        assert EncoderBase32Crockford.is_valid("") is False
        assert EncoderBase32Crockford.is_valid("abc-def") is False
        assert EncoderBase32Crockford.is_valid("abc def") is False
        assert EncoderBase32Crockford.is_valid("abc_def") is False

    def test_lookalike_chars_valid(self):
        assert EncoderBase32Crockford.is_valid("OIL") is True  # O→0, I→1, L→1
        assert EncoderBase32Crockford.is_valid("oil") is True
