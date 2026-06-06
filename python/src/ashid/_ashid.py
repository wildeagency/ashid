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


def _build_base(prefix: Optional[str], n1: int, n2: int, padded: bool) -> str:
    """Encode two non-negative ints into the canonical base ID form.

    Mirrors the TypeScript buildBase routed through create()/create4():
      - padded=True   -> both halves 13-char zero-padded (ashid4 shape).
      - padded=False  -> first half encoded unpadded when a prefix is present;
                         else padded to 9 chars (timestamp width) when the raw
                         encoding fits in 9 chars, else padded to 13.
    The second half is always 13-char padded.
    """
    if n1 < 0 or n2 < 0:
        raise ValueError("Ashid random value must be non-negative")
    normalized_prefix = _normalize_prefix(prefix)

    if normalized_prefix or padded:
        encoded1 = EncoderBase32Crockford.encode(n1, padded=padded)
    else:
        raw = EncoderBase32Crockford.encode(n1)
        width = (
            _TIMESTAMP_ENCODED_LENGTH
            if len(raw) <= _TIMESTAMP_ENCODED_LENGTH
            else _RANDOM_ENCODED_LENGTH
        )
        encoded1 = raw.rjust(width, "0")
    encoded2 = EncoderBase32Crockford.encode(n2, padded=True)
    return (normalized_prefix or "") + encoded1 + encoded2


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

        # If no delimiter was found, the whole string is the base — even if it
        # happens to be all-alpha (e.g. an ashid4 whose random component encodes
        # to letters only).
        if not has_delimiter:
            prefix_length = 0

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
        """Normalize an Ashid: lowercase prefix and re-encode through canonical form.

        Type-1 inputs round-trip to type-1 shape; full-entropy ashid4 inputs
        round-trip to ashid4 shape (the first long naturally fills 13 chars).
        An ashid4 with a small first long collapses to type-1 shape — the two
        longs survive, only the string shape changes.
        """
        prefix, encoded_first, encoded_second = Ashid.parse(id)
        normalized_prefix = prefix.lower() if prefix else None
        long1 = EncoderBase32Crockford.decode(encoded_first)
        long2 = EncoderBase32Crockford.decode(encoded_second)
        return _build_base(normalized_prefix, long1, long2, False)

    @staticmethod
    def to_uuid(id: str) -> str:
        """Convert an Ashid to its UUID-shaped representation.

        An Ashid encodes 128 bits of information (a 64-bit timestamp or first random
        component plus a 64-bit random component); this method emits those 128 bits
        as a standard 36-character UUID string with dashes (8-4-4-4-12).

        Round-trips losslessly with Ashid.from_uuid. The resulting UUID is shape-
        compatible with RFC 4122 but the version/variant bits reflect the underlying
        Ashid bytes, not RFC 4122 conventions, unless the Ashid was originally
        created from a v1/v4/v7 UUID.
        """
        _, encoded_timestamp, encoded_random = Ashid.parse(id)
        high = EncoderBase32Crockford.decode(encoded_timestamp)
        low = EncoderBase32Crockford.decode(encoded_random)
        high_hex = f"{high:016x}"
        low_hex = f"{low:016x}"
        return f"{high_hex[:8]}-{high_hex[8:12]}-{high_hex[12:16]}-{low_hex[:4]}-{low_hex[4:16]}"

    @staticmethod
    def from_uuid(uuid: str, prefix: Optional[str] = None) -> str:
        """Convert a UUID into an Ashid.

        Splits the 128-bit UUID into two 64-bit halves: the high half becomes the
        timestamp (or first random component, see below) and the low half becomes
        the random component.

        - If the high half fits in 45 bits (<= MAX_TIMESTAMP), the result is a
          standard 22-char Ashid base.
        - Otherwise -- for UUIDv4 (random) and UUIDv7 (whose version bits force
          the high half above 2^45) -- the result is a 26-char ashid4 base.

        Round-trip through Ashid.to_uuid is byte-identical in both cases.
        """
        hex_str = uuid.replace("-", "").lower()
        if len(hex_str) != 32 or any(c not in "0123456789abcdef" for c in hex_str):
            raise ValueError(f'Invalid UUID: must be 32 or 36 hex characters (got "{uuid}")')
        high = int(hex_str[:16], 16)
        low = int(hex_str[16:32], 16)
        if high <= _MAX_TIMESTAMP:
            return Ashid.create(prefix, high, low)
        return Ashid.create4(prefix, high, low)


def ashid(prefix: Optional[str] = None) -> str:
    """Create a new Ashid with optional type prefix."""
    return Ashid.create(prefix)


def ashid4(prefix: Optional[str] = None) -> str:
    """Create a UUID-v4-style Ashid with two padded random components."""
    return Ashid.create4(prefix)


def parse_ashid(id: str) -> Tuple[str, str, str]:
    """Parse an Ashid into (prefix, encoded_timestamp, encoded_random)."""
    return Ashid.parse(id)
