# ashid (Rust)

Time-sortable unique identifiers with optional type prefixes. Rust port of the
[ashid](https://github.com/wildeagency/ashid) library.

## Install

```toml
[dependencies]
ashid = "1.2"
```

## Usage

```rust
use ashid::{Ashid, new, new4};

let id = new(None);                       // "1fvszawr42tve3gxvx9900" (22 chars)
let user_id = new(Some("user"));          // "user_1fvszawr42tve3gxvx9900"
let token = new4(Some("tok"));            // "tok_x7k9m2p4q8r1s5t3v6w0y1z3" (UUID v4 style)

// Parse components
let (prefix, ts, rand) = Ashid::parse("user_1fvszawr42tve3gxvx9900").unwrap();

// Extract pieces
let timestamp = Ashid::timestamp("user_1fvszawr42tve3gxvx9900").unwrap();
let random = Ashid::random("user_1fvszawr42tve3gxvx9900").unwrap();

// Validate
assert!(Ashid::is_valid("user_1fvszawr42tve3gxvx9900"));

// Normalize (lowercase + canonicalize ambiguous chars)
let canon = Ashid::normalize("USER_1FVSZAWR42TVE3GXVX9900").unwrap();
```

## Format

- Crockford Base32 alphabet: `0123456789abcdefghjkmnpqrstvwxyz`
- Lookalikes mapped on decode: `I/L → 1`, `O → 0`, `U → V`
- `MAX_TIMESTAMP`: 35184372088831 (Dec 12, 3084)
- Without prefix: fixed 22 chars (9 timestamp + 13 random) for v1, or 26 chars (13 + 13) for v4
- With prefix: variable-length base after `prefix_`
