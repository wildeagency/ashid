"""Tests for Ashid."""

import time

import pytest

from ashid import Ashid, ashid, ashid4, parse_ashid


def now_ms() -> int:
    return int(time.time() * 1000)


class TestCreate:
    def test_no_prefix_fixed_22_chars(self):
        id = Ashid.create()
        assert id
        assert len(id) == 22
        assert id[0].isdigit()

    def test_with_prefix_adds_delimiter(self):
        id = Ashid.create("user")
        assert id.startswith("user_")

    def test_with_trailing_underscore_ignored(self):
        id = Ashid.create("user_")
        assert id.startswith("user_")

    def test_same_id_with_or_without_trailing_delim(self):
        id1 = Ashid.create("user", 1000, 0)
        id2 = Ashid.create("user_", 1000, 0)
        assert id1 == id2

    def test_empty_string_means_no_prefix(self):
        id = Ashid.create("")
        assert id[0].isdigit()
        assert len(id) == 22

    def test_alphanumeric_prefix(self):
        id = Ashid.create("user1", 1000, 0)
        assert id.startswith("user1_")

    def test_strips_non_alphanumeric_from_prefix(self):
        id = Ashid.create("a-b_c", 1000, 0)
        assert id.startswith("abc_")  # strips - and _

    def test_strips_special_chars_from_prefix(self):
        id = Ashid.create("user!@#$%", 1000, 0)
        assert id.startswith("user_")

    def test_no_prefix_when_all_stripped(self):
        id = Ashid.create("___", 1000, 0)
        assert len(id) == 22  # No prefix, fixed format

    def test_negative_timestamp_raises(self):
        with pytest.raises(ValueError, match="non-negative"):
            Ashid.create(time=-1)

    def test_max_timestamp_ok(self):
        max_ts = 35184372088831
        Ashid.create(time=max_ts)  # should not raise

    def test_above_max_timestamp_raises(self):
        max_ts = 35184372088831
        with pytest.raises(ValueError, match="must not exceed"):
            Ashid.create(time=max_ts + 1)

    def test_negative_random_raises(self):
        with pytest.raises(ValueError, match="non-negative"):
            Ashid.create(time=now_ms(), random_long=-1)

    def test_unique_ids(self):
        ids = {Ashid.create() for _ in range(1000)}
        assert len(ids) == 1000

    def test_uppercase_prefix_lowercased(self):
        id = Ashid.create("USER", 1000, 0)
        assert id.startswith("user_")
        assert Ashid.prefix(id) == "user_"


class TestFixedFormat:
    def test_timestamp_zero_padded_to_22(self):
        id = Ashid.create(time=0, random_long=0)
        assert id == "0000000000000000000000"
        assert len(id) == 22

    def test_current_timestamp_22_chars(self):
        id = Ashid.create()
        assert len(id) == 22


class TestVariableFormat:
    def test_zero_timestamp_zero_random_minimal(self):
        id = Ashid.create("user", 0, 0)
        assert id == "user_0"

    def test_zero_timestamp_random_1(self):
        id = Ashid.create("user", 0, 1)
        assert id == "user_1"

    def test_zero_timestamp_random_31(self):
        id = Ashid.create("user", 0, 31)
        assert id == "user_z"

    def test_timestamp_1_random_0(self):
        id = Ashid.create("user", 1, 0)
        assert id == "user_10000000000000"
        assert len(id) == 19  # 'user_' (5) + 1 + 13

    def test_current_timestamp_27_chars(self):
        id = Ashid.create("user", now_ms(), 0)
        # 'user_' (5) + 9 (current timestamp) + 13 (padded random) = 27
        assert len(id) == 27

    def test_single_letter_prefix(self):
        id = Ashid.create("u", 0, 0)
        assert id == "u_0"


class TestParse:
    def test_no_prefix_22_char_base(self):
        id = "0000000000000000000000"
        prefix, ts, rand = Ashid.parse(id)
        assert prefix == ""
        assert ts == "000000000"
        assert rand == "0000000000000"

    def test_with_delimiter_prefix(self):
        id = "user_1kbg1jmtt0000000000000"
        prefix, ts, rand = Ashid.parse(id)
        assert prefix == "user_"
        assert ts == "1kbg1jmtt"
        assert rand == "0000000000000"

    def test_underscore_prefix_with_zero_timestamp(self):
        id = "user_c1s"  # timestamp 0, random 12345
        prefix, ts, rand = Ashid.parse(id)
        assert prefix == "user_"
        assert ts == "0"
        assert rand == "c1s"

    def test_empty_raises(self):
        with pytest.raises(ValueError, match="cannot be empty"):
            Ashid.parse("")

    def test_prefix_only_raises(self):
        with pytest.raises(ValueError, match="must have a base ID"):
            Ashid.parse("user_")

    def test_non_delimited_22_char_no_prefix(self):
        # Old-style ID without delimiter — entire string is base (must be exactly 22 chars)
        id = "u1234567890123456789ab"  # exactly 22 chars
        prefix, ts, rand = Ashid.parse(id)
        assert prefix == ""  # No delimiter = no prefix
        assert ts == "u12345678"
        assert rand == "90123456789ab"

    def test_wrong_length_no_delimiter_raises(self):
        with pytest.raises(ValueError, match="must be 22 or 26 characters"):
            Ashid.parse("abc123")

    def test_dash_normalized_to_underscore(self):
        id = "user-1kbg1jmtt0000000000000"
        prefix, ts, rand = Ashid.parse(id)
        assert prefix == "user_"  # dash normalized to underscore
        assert ts == "1kbg1jmtt"
        assert rand == "0000000000000"

    def test_dash_and_underscore_yield_same_timestamp(self):
        id_dash = "user-1kbg1jmtt0000000000000"
        id_under = "user_1kbg1jmtt0000000000000"
        assert Ashid.timestamp(id_dash) == Ashid.timestamp(id_under)


class TestPrefix:
    def test_no_prefix_returns_empty(self):
        id = Ashid.create(time=1000, random_long=0)
        assert Ashid.prefix(id) == ""

    def test_with_prefix(self):
        id = Ashid.create("user", 1000, 0)
        assert Ashid.prefix(id) == "user_"

    def test_single_letter_prefix(self):
        id = Ashid.create("u", 1000, 0)
        assert Ashid.prefix(id) == "u_"


class TestTimestamp:
    def test_extract_timestamp_fixed_format(self):
        ts = 1609459200000
        id = Ashid.create(time=ts, random_long=0)
        assert Ashid.timestamp(id) == ts

    def test_extract_timestamp_variable_format(self):
        ts = 1609459200000
        id = Ashid.create("user", ts, 0)
        assert Ashid.timestamp(id) == ts

    def test_zero_timestamp_fixed(self):
        id = Ashid.create(time=0, random_long=12345)
        assert Ashid.timestamp(id) == 0

    def test_zero_timestamp_variable(self):
        id = Ashid.create("user", 0, 12345)
        assert Ashid.timestamp(id) == 0


class TestRandom:
    def test_extract_random_fixed_format(self):
        random = 123456789
        id = Ashid.create(time=now_ms(), random_long=random)
        assert Ashid.random(id) == random

    def test_extract_random_variable_format(self):
        random = 123456789
        id = Ashid.create("user", now_ms(), random)
        assert Ashid.random(id) == random

    def test_zero_random_fixed(self):
        id = Ashid.create(time=1000, random_long=0)
        assert Ashid.random(id) == 0

    def test_random_with_zero_timestamp_variable(self):
        id = Ashid.create("user", 0, 12345)
        assert Ashid.random(id) == 12345

    def test_returns_int(self):
        id = Ashid.create("user", now_ms(), 12345)
        assert isinstance(Ashid.random(id), int)

    def test_64bit_random_roundtrip(self):
        random = (1 << 64) - 1  # max 64-bit
        id = Ashid.create("user", now_ms(), random)
        assert Ashid.random(id) == random


class TestNormalize:
    def test_lowercases_uppercase(self):
        original = Ashid.create("user", 1609459200000, 0)
        normalized = Ashid.normalize(original.upper())
        assert normalized == original

    def test_converts_ambiguous_chars(self):
        original = Ashid.create("user", 1609459200000, 1111)
        prefix, ts, rand = Ashid.parse(original)
        modified_ts = ts.replace("1", "I").replace("0", "O")
        modified_rand = rand.replace("1", "L").replace("0", "O")
        modified = prefix + modified_ts + modified_rand

        normalized = Ashid.normalize(modified)
        assert Ashid.timestamp(normalized) == 1609459200000
        assert Ashid.random(normalized) == 1111

    def test_normalizes_variable_length(self):
        original = Ashid.create("user", 1609459200000, 12345)
        normalized = Ashid.normalize(original.upper())
        assert normalized == original

    def test_preserves_values_through_roundtrip(self):
        original = Ashid.create("user", 1609459200000, 12345)
        normalized = Ashid.normalize(original.upper())
        assert Ashid.timestamp(normalized) == 1609459200000
        assert Ashid.random(normalized) == 12345

    def test_normalizes_fixed_format(self):
        original = Ashid.create(time=1609459200000, random_long=12345)
        normalized = Ashid.normalize(original.upper())
        assert normalized == original

    def test_dash_to_underscore(self):
        with_dash = "user-1kbg1jmtt0000000000000"
        normalized = Ashid.normalize(with_dash)
        assert normalized.startswith("user_")
        assert Ashid.prefix(normalized) == "user_"

    def test_dash_and_underscore_normalize_same(self):
        with_dash = "user-1kbg1jmtt0000000000000"
        with_under = "user_1kbg1jmtt0000000000000"
        assert Ashid.normalize(with_dash) == Ashid.normalize(with_under)

    def test_preserves_64bit_entropy(self):
        random = (1 << 64) - 1
        original = Ashid.create("user", 1609459200000, random)
        normalized = Ashid.normalize(original.upper())
        assert Ashid.random(normalized) == random


class TestIsValid:
    def test_valid_ashids(self):
        assert Ashid.is_valid(Ashid.create()) is True
        assert Ashid.is_valid(Ashid.create("u")) is True
        assert Ashid.is_valid(Ashid.create("user")) is True

    def test_zero_timestamp_ids_valid(self):
        assert Ashid.is_valid(Ashid.create(time=0, random_long=0)) is True
        assert Ashid.is_valid(Ashid.create("u", 0, 0)) is True
        assert Ashid.is_valid(Ashid.create("user", 0, 0)) is True

    def test_empty_invalid(self):
        assert Ashid.is_valid("") is False

    def test_invalid_base32(self):
        assert Ashid.is_valid("user_invalid!@#") is False

    def test_wrong_length_no_delimiter(self):
        assert Ashid.is_valid("abc123") is False

    def test_22_char_base_valid(self):
        assert Ashid.is_valid("0000000000000000000000") is True


class TestTimeSortability:
    def test_sortable_no_prefix(self):
        base = now_ms()
        id1 = Ashid.create(time=base, random_long=0)
        id2 = Ashid.create(time=base + 1000, random_long=0)
        id3 = Ashid.create(time=base + 2000, random_long=0)
        assert sorted([id3, id1, id2]) == [id1, id2, id3]

    def test_sortable_with_prefix(self):
        base = now_ms()
        id1 = Ashid.create("user", base, 0)
        id2 = Ashid.create("user", base + 1000, 0)
        id3 = Ashid.create("user", base + 2000, 0)
        assert sorted([id3, id1, id2]) == [id1, id2, id3]

    def test_sort_by_random_when_timestamp_equal(self):
        ts = now_ms()
        id1 = Ashid.create(time=ts, random_long=1000)
        id2 = Ashid.create(time=ts, random_long=2000)
        assert sorted([id2, id1]) == [id1, id2]


class TestConvenienceFunctions:
    def test_ashid_creates_valid(self):
        id = ashid()
        assert Ashid.is_valid(id)

    def test_ashid_with_prefix(self):
        id = ashid("user")
        assert id.startswith("user_")

    def test_parse_ashid_returns_tuple(self):
        id = "user_1kbg1jmtt0000000000000"
        prefix, ts, rand = parse_ashid(id)
        assert prefix == "user_"
        assert ts == "1kbg1jmtt"
        assert rand == "0000000000000"


class TestDoubleClickSelectability:
    def test_no_hyphens(self):
        id = ashid("user")
        assert "-" not in id

    def test_no_spaces(self):
        id = ashid("user")
        assert " " not in id

    def test_alphanumeric_and_underscore_only(self):
        import re

        id1 = ashid()
        id2 = ashid("user")
        assert re.fullmatch(r"[a-z0-9]+", id1)
        assert re.fullmatch(r"[a-z]+_[a-z0-9]+", id2)


class TestAshid4:
    def test_no_prefix_26_chars(self):
        id = ashid4()
        assert id
        assert len(id) == 26  # 13 + 13 chars
        assert Ashid.is_valid(id)

    def test_with_prefix(self):
        id = ashid4("tok")
        assert id.startswith("tok_")
        assert len(id) == 30  # 4 (prefix + _) + 26 (base)

    def test_unique_ids(self):
        ids = {ashid4() for _ in range(1000)}
        assert len(ids) == 1000

    def test_full_64bit_entropy(self):
        # With 64-bit entropy, ~50% should have values > 2^53, requiring > 11 chars unpadded
        saw_full = False
        for _ in range(100):
            id = ashid4()
            _, encoded1, encoded2 = Ashid.parse(id)
            if len(encoded1.lstrip("0")) > 11 or len(encoded2.lstrip("0")) > 11:
                saw_full = True
                break
        assert saw_full

    def test_explicit_random_values(self):
        random1 = 123456789
        random2 = 987654321
        id = Ashid.create4("tok", random1, random2)
        assert id.startswith("tok_")

    def test_64bit_roundtrip(self):
        random1 = (1 << 64) - 1
        random2 = (1 << 63)
        id = Ashid.create4("tok", random1, random2)
        prefix, encoded1, encoded2 = Ashid.parse(id)
        assert prefix == "tok_"
        assert len(encoded1) == 13
        assert len(encoded2) == 13

    def test_negative_random_raises(self):
        with pytest.raises(ValueError, match="non-negative"):
            Ashid.create4("tok", -1, 100)
        with pytest.raises(ValueError, match="non-negative"):
            Ashid.create4("tok", 100, -1)
