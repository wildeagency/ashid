# ashid (Python)

**Time-sortable unique identifiers with type prefixes.**

```python
ashid("user")  # → "user_1kbg1jmtt4v3x8k9p2m1n"
ashid("tx")    # → "tx_1kbg1jmts7h2w5r8q4n3m"
ashid()        # → "0000001kbg1jmtt4v3x8k9p2m1n" (22 chars, fixed width)
```

Pure Python, stdlib only (`os`, `time`, `re`). Full parity with the [TypeScript](../typescript/) and [Kotlin](../kotlin/) implementations.

## Installation

```bash
pip install ashid
```

## Quick Start

```python
from ashid import ashid, ashid4, parse_ashid, Ashid

# Time-sortable IDs with type prefix
user_id = ashid("user")        # → "user_1kbg1jmtt4v3x8k9p2m1n"
short_id = ashid("u")          # → "u_1kbg1jmtt4v3x8k9p2m1n"
raw_id = ashid()               # → "0000001kbg1jmtt4v3x8k9" (22 chars)

# UUID-v4-style (two random components, no timestamp)
token = ashid4("tok")          # → "tok_x7k9m2p4q8r1s5t3v6w0y1z3"
secret = ashid4()              # → "a3b5c7d9e1f2g4h6j8k0m2n4p6"

# Parse and inspect
prefix = Ashid.prefix(user_id)         # "user_"
created_ms = Ashid.timestamp(user_id)  # int (ms since epoch)
random_value = Ashid.random(user_id)   # int (full 64-bit precision)

# Validate
Ashid.is_valid(user_id)        # True
Ashid.is_valid("not-an-id")    # False

# Normalize ambiguous characters (I/L→1, O→0, U→V) and lowercase
Ashid.normalize("USER-1KBG1JMTT0000000000000")
# → "user_1kbg1jmtt0000000000000"
```

## API Reference

### Top-level functions

```python
ashid(prefix: str | None = None) -> str
```
Create a new time-sortable Ashid. With a prefix the result is variable-length; without one it is a fixed 22-char base.

```python
ashid4(prefix: str | None = None) -> str
```
Create a UUID-v4-style Ashid with two padded random components (26-char base, ~106 bits of entropy).

```python
parse_ashid(id: str) -> tuple[str, str, str]
```
Parse an Ashid into `(prefix_with_trailing_, encoded_timestamp, encoded_random)`.

### Ashid class

```python
Ashid.create(prefix=None, time=None, random_long=None) -> str
Ashid.create4(prefix=None, random1=None, random2=None) -> str
Ashid.parse(id) -> tuple[str, str, str]
Ashid.prefix(id) -> str         # "user_" or ""
Ashid.timestamp(id) -> int      # ms since Unix epoch
Ashid.random(id) -> int         # full 64-bit precision
Ashid.is_valid(id) -> bool
Ashid.normalize(id) -> str
```

### EncoderBase32Crockford

```python
EncoderBase32Crockford.encode(n, padded=False) -> str
EncoderBase32Crockford.encode_bigint(n, padded=False) -> str  # alias for encode
EncoderBase32Crockford.decode(s) -> int
EncoderBase32Crockford.decode_bigint(s) -> int  # alias for decode
EncoderBase32Crockford.secure_random_long() -> int   # full 64-bit unsigned
EncoderBase32Crockford.is_valid(s) -> bool
```

Python ints are arbitrary-precision, so `encode`/`encode_bigint` and `decode`/`decode_bigint` are aliases for parity with the TypeScript API.

## Format

```
[prefix_][timestamp][random]
```

- **Prefix:** alphanumeric, lowercase, trailing `_` auto-added (passed-in `_` or `-` is ignored). Dashes in the prefix are normalized to underscores on parse.
- **Timestamp:** ms since Unix epoch, Crockford Base32. Range: `0` to `35184372088831` (Dec 12, 3084).
- **Random:** 64-bit unsigned, Crockford Base32. Encoded to 13 chars when padded.

| Variant | Format | Example |
| --- | --- | --- |
| `ashid()` (no prefix) | fixed 22 chars: 9 ts + 13 rand | `1kbg1jmtt4v3x8k9p2m1n0` |
| `ashid("user")` (prefix, ts > 0) | `prefix_` + variable ts + 13 rand | `user_1kbg1jmtt4v3x8k9p2m1n` |
| `ashid("user")` (prefix, ts = 0) | `prefix_` + variable rand only | `user_z` |
| `ashid4()` (no prefix) | fixed 26 chars: 13 + 13 rand | `a3b5c7d9e1f2g4h6j8k0m2n4p6` |
| `ashid4("tok")` (prefix) | `tok_` + 26 rand | `tok_x7k9m2p4q8r1s5t3v6w0y1z3` |

## Performance

- Iterative encoding (no recursion — no stack-depth risk on large values).
- Pre-built decode lookup table.
- `bytearray` accumulator in the encode hot path; no string concatenation.

## Development

```bash
cd python
pip install -e ".[test]"
pytest
```

## License

MIT
