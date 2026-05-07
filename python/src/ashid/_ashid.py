"""Ashid - time-sortable unique identifier with optional type prefix.

Format: [prefix_][timestamp][random]

Prefix Rules:
- Prefix must be alphanumeric only (a-z, A-Z, 0-9 — non-alphanumeric chars are stripped)
- Delimiter (_) is automatically added — callers should NOT include it
- If a delimiter is passed in, it is ignored (backward compatibility)
- With prefix: variable length base ID (no timestamp padding when 0)
- Without prefix: fixed 22-char base ID

Timestamp Support:
- Minimum: 0 (Unix epoch, Jan 1, 1970)
- Maximum: 35184372088831 (Dec 12, 3084)
"""

import re
import time as _time
from typing import Optional, Tuple

from ._encoder import EncoderBase32Crockford


_MAX_TIMESTAMP = 35184372088831
_RANDOM_ENCODED_LENGTH = 13
_TIMESTAMP_ENCODED_LENGTH = 9
_STANDARD_BASE_LENGTH = 22
_ASHID4_BASE_LENGTH = 26

_PREFIX_CLEAN_RE = re.compile(r"[^a-zA-Z0-9]")
_PREFIX_VALID_RE = re.compile(r"^[a-zA-Z0-9]+_$")


def _now_ms() -> int:
    return _time.time_ns() // 1_000_000


def _normalize_prefix(prefix: Optional[str]) -> Optional[str]:
    if prefix is None or prefix == "":
        return None
    cleaned = _PREFIX_CLEAN_RE.sub("", prefix).lower()
    if cleaned == "":
        return None
    return cleaned + "_"


class Ashid:
    """Time-sortable unique identifier with optional type prefix."""

    @staticmethod
    def create(
        prefix: Optional[str] = None,
        time: Optional[int] = None,
        random_long: Optional[int] = None,
    ) -> str:
        """Create a new Ashid."""
        normalized_prefix = _normalize_prefix(prefix)

        if time is None:
            time = _now_ms()
        floored_time = int(time)
        if floored_time < 0:
            raise ValueError("Ashid timestamp must be non-negative")
        if floored_time > _MAX_TIMESTAMP:
            raise ValueError(
                f"Ashid timestamp must not exceed {_MAX_TIMESTAMP} (Dec 12, 3084)"
            )

        if random_long is None:
            random_long = EncoderBase32Crockford.secure_random_long()
        if random_long < 0:
            raise ValueError("Ashid random value must be non-negative")

        if normalized_prefix:
            if floored_time > 0:
                random_encoded = EncoderBase32Crockford.encode(random_long, padded=True)
                base_id = EncoderBase32Crockford.encode(floored_time) + random_encoded
            else:
                random_encoded = EncoderBase32Crockford.encode(random_long)
                base_id = random_encoded
        else:
            time_encoded = EncoderBase32Crockford.encode(floored_time).rjust(
                _TIMESTAMP_ENCODED_LENGTH, "0"
            )
            random_encoded = EncoderBase32Crockford.encode(random_long, padded=True)
            base_id = time_encoded + random_encoded

        return (normalized_prefix or "") + base_id

    @staticmethod
    def create4(
        prefix: Optional[str] = None,
        random1: Optional[int] = None,
        random2: Optional[int] = None,
    ) -> str:
        """Create a random Ashid (UUID v4 equivalent) with two padded random components."""
        normalized_prefix = _normalize_prefix(prefix)

        if random1 is None:
            random1 = EncoderBase32Crockford.secure_random_long()
        if random2 is None:
            random2 = EncoderBase32Crockford.secure_random_long()
        if random1 < 0 or random2 < 0:
            raise ValueError("Ashid random values must be non-negative")

        encoded1 = EncoderBase32Crockford.encode(random1, padded=True)
        encoded2 = EncoderBase32Crockford.encode(random2, padded=True)
        base_id = encoded1 + encoded2

        return (normalized_prefix or "") + base_id

    @staticmethod
    def parse(id: str) -> Tuple[str, str, str]:
        """Parse an Ashid into (prefix_with_trailing_, encoded_timestamp, encoded_random)."""
        if not id:
            raise ValueError("Invalid Ashid: cannot be empty")

        prefix_length = 0
        has_delimiter = False

        for ch in id:
            if ch.isascii() and ch.isalpha():
                prefix_length += 1
            elif (ch == "_" or ch == "-") and prefix_length > 0:
                prefix_length += 1
                has_delimiter = True
                break
            else:
                prefix_length = 0
                break

        if has_delimiter:
            raw_prefix = id[:prefix_length]
            if raw_prefix.endswith("-"):
                raw_prefix = raw_prefix[:-1] + "_"
            prefix = raw_prefix
        else:
            prefix = ""

        base_id = id[prefix_length:]

        if not base_id:
            raise ValueError("Invalid Ashid: must have a base ID")

        if has_delimiter:
            if len(base_id) <= _RANDOM_ENCODED_LENGTH:
                encoded_timestamp = "0"
                encoded_random = base_id
            else:
                encoded_timestamp = base_id[:-_RANDOM_ENCODED_LENGTH]
                encoded_random = base_id[-_RANDOM_ENCODED_LENGTH:]
        elif len(base_id) == _ASHID4_BASE_LENGTH:
            encoded_timestamp = base_id[:_RANDOM_ENCODED_LENGTH]
            encoded_random = base_id[_RANDOM_ENCODED_LENGTH:]
        elif len(base_id) == _STANDARD_BASE_LENGTH:
            encoded_timestamp = base_id[:_TIMESTAMP_ENCODED_LENGTH]
            encoded_random = base_id[_TIMESTAMP_ENCODED_LENGTH:]
        else:
            raise ValueError(
                f"Invalid Ashid: base ID must be {_STANDARD_BASE_LENGTH} or "
                f"{_ASHID4_BASE_LENGTH} characters without delimiter (got {len(base_id)})"
            )

        return prefix, encoded_timestamp, encoded_random

    @staticmethod
    def prefix(id: str) -> str:
        """Extract the prefix (with trailing _) from an Ashid; empty string if none."""
        return Ashid.parse(id)[0]

    @staticmethod
    def timestamp(id: str) -> int:
        """Extract the timestamp (in ms) from an Ashid."""
        _, encoded_timestamp, _ = Ashid.parse(id)
        return EncoderBase32Crockford.decode(encoded_timestamp)

    @staticmethod
    def random(id: str) -> int:
        """Extract the random component from an Ashid as an int (full 64-bit precision)."""
        _, _, encoded_random = Ashid.parse(id)
        return EncoderBase32Crockford.decode(encoded_random)

    @staticmethod
    def is_valid(id: str) -> bool:
        """Validate whether a string is a well-formed Ashid."""
        if not id:
            return False
        try:
            prefix, encoded_timestamp, encoded_random = Ashid.parse(id)
            if prefix and not _PREFIX_VALID_RE.match(prefix):
                return False
            EncoderBase32Crockford.decode(encoded_timestamp)
            EncoderBase32Crockford.decode(encoded_random)
            return True
        except (ValueError, TypeError):
            return False

    @staticmethod
    def normalize(id: str) -> str:
        """Normalize an Ashid: lowercase prefix and re-encode through canonical form."""
        prefix, encoded_timestamp, encoded_random = Ashid.parse(id)
        timestamp = EncoderBase32Crockford.decode(encoded_timestamp)
        random = EncoderBase32Crockford.decode(encoded_random)
        normalized_prefix = prefix.lower() if prefix else None
        return Ashid.create(normalized_prefix, timestamp, random)


def ashid(prefix: Optional[str] = None) -> str:
    """Create a new Ashid with optional type prefix."""
    return Ashid.create(prefix)


def ashid4(prefix: Optional[str] = None) -> str:
    """Create a UUID-v4-style Ashid with two padded random components."""
    return Ashid.create4(prefix)


def parse_ashid(id: str) -> Tuple[str, str, str]:
    """Parse an Ashid into (prefix, encoded_timestamp, encoded_random)."""
    return Ashid.parse(id)
