# ashid

[![npm version](https://img.shields.io/npm/v/ashid.svg)](https://www.npmjs.com/package/ashid)
[![npm downloads](https://img.shields.io/npm/dw/ashid.svg)](https://www.npmjs.com/package/ashid)
[![license](https://img.shields.io/npm/l/ashid.svg)](https://github.com/wildeagency/ashid/blob/main/LICENSE)

**Time-sortable unique identifiers with type prefixes.**

```
ashid("user")  →  user_1kbg1jmtt4v3x8k9p2m1n0
ashid("tx")    →  tx_1kbg1jmts7h2w5r8q4n3m0
ashid()        →  1kbg1jmtt4v3x8k9p2m1n0
```

## Why ashid?

UUIDs are opaque. When you see `550e8400-e29b-41d4-a716-446655440000` in a log, you have no idea what it represents. You have to grep, cross-reference, and waste debugging time.

ashid generates IDs that tell you what they are:

```
user_1kbg1jmtt4v3x8k9p2m1n0  ← Obviously a user
tx_1kbg1jmts7h2w5r8q4n3m0    ← Obviously a transaction
asset_1kbg1jmtr9k5v2x8p4m1n0 ← Obviously an asset
```

ashid sits in the same family as ULID, KSUID, and TypeID — time-sortable, prefix-friendly identifiers. It's distinguished by being **case-insensitive with lookalike correction** (`I`/`L`→`1`, `O`→`0`, `U`→`V`), **22 characters instead of 26-27**, and **one-to-one convertible with a UUID** so the storage layer can keep UUIDs if it wants to. See [How ashid Compares](#how-ashid-compares) and the [FAQ](#faq) for the side-by-side breakdown.

### Key Features

- **Type prefixes** — Self-documenting IDs, just like Stripe (`sk_`, `pi_`, `cus_`)
- **Time-sortable** — Lexicographic sort = chronological sort
- **Crockford Base32** — Case-insensitive, `I`/`L`→`1`, `O`→`0`, `U`→`V` (no more "is that a zero or an O?")
- **Double-click selectable** — No hyphens or special characters
- **UUID-convertible** — `Ashid.toUuid` / `Ashid.fromUuid` for round-tripping with native `uuid` columns
- **URL-safe** — No encoding required
- **Zero dependencies**

## Packages

| Package | Platform | Install |
|---------|----------|---------|
| [ashid](./typescript/) | TypeScript/JavaScript | `npm install ashid` |
| [ashid](./kotlin/) | Kotlin/Java | `implementation("agency.wilde:ashid:1.0.0")` |
| [ashid](./python/) | Python | `pip install ashid` |
| [ashid](./rust/) | Rust | `cargo add ashid` |
| [ashid](./go/) | Go | `go get github.com/wildeagency/ashid/go` |

## Quick Start

### TypeScript/JavaScript

```typescript
import { ashid } from 'ashid';

const userId = ashid('user');    // "user_1kbg1jmtt4v3x8k9p2m1n0"
const rawId = ashid();           // "1kbg1jmtt4v3x8k9p2m1n0"
```

See the [TypeScript README](./typescript/README.md) for full API documentation.

### Kotlin/Java

```kotlin
import agency.wilde.ashid.ashid

val userId = ashid("user")    // "user_1kbg1jmtt4v3x8k9p2m1n0"
val rawId = ashid()           // "1kbg1jmtt4v3x8k9p2m1n0"
```

### Python

```python
from ashid import ashid

user_id = ashid("user")       # "user_1kbg1jmtt4v3x8k9p2m1n0"
raw_id = ashid()              # "1kbg1jmtt4v3x8k9p2m1n0"
```

See the [Python README](./python/README.md) for full API documentation.

### Rust

```rust
use ashid::new;

let user_id = new(Some("user"));    // "user_1kbg1jmtt4v3x8k9p2m1n0"
let raw_id = new(None);             // "1kbg1jmtt4v3x8k9p2m1n0"
```

See the [Rust README](./rust/README.md) for full API documentation.

### Go

```go
import ashid "github.com/wildeagency/ashid/go"

userID := ashid.New("user")   // "user_1kbg1jmtt4v3x8k9p2m1n0"
rawID := ashid.New()          // "1kbg1jmtt4v3x8k9p2m1n0"
```

See the [Go README](./go/README.md) for full API documentation.

## Common Patterns

### Prefix-as-URL-route

Because the prefix already identifies the entity type, you can use the ID directly in a URL and skip the redundant type segment:

```
mysite.com/user_1kbg1jmtt4v3x8k9p2m1n0     ← clear, no /user/ duplication
mysite.com/order_1kbg1jmts7h2w5r8q4n3m0    ← order detail page

vs.

mysite.com/user/user_1kbg1jmtt4v3x8k9p2m1n0
mysite.com/order/order_1kbg1jmts7h2w5r8q4n3m0
```

A single dispatch route can read the prefix and dispatch to the right handler. The reverse (URL → entity type) is also free — no separate lookup required.

### Skip the type column on audit-trail tables

Audit columns like `created_by` typically need both the user ID and the entity type that created the row (a service account, a user, a worker, etc.). With ashid the prefix carries the type:

```sql
-- Without ashid
created_by uuid not null,
created_by_type text not null  -- 'user', 'service', 'worker'

-- With ashid
created_by ashid not null  -- "user_…", "svc_…", "wkr_…" — type is inline
```

The same pattern applies to polymorphic foreign keys, generic comment threads, and any other "what kind of thing is this?" column.

### Round-trip through UUID

Every ashid encodes 128 bits — the same as a UUID — and `Ashid.toUuid` / `Ashid.fromUuid` convert losslessly between the two:

```typescript
const id = ashid('user');                    // "user_1kbg1jmtt4v3x8k9p2m1n0"
const uuid = Ashid.toUuid(id);               // "00001kbg-1jmt-t4v3-x8k9-p2m1n00000000"
const restored = Ashid.fromUuid(uuid, 'user'); // "user_1kbg1jmtt4v3x8k9p2m1n0"
```

This means a DevOps team can store ashids as native `uuid` columns in Postgres if they prefer the storage efficiency, with no loss of information. We don't — the ability to paste an ID straight from a bug report or log line into a `SELECT … WHERE id = '…'` query has been worth more than the column-size savings — but the option is there, and the round-trip works in both directions for `Ashid` (UUID-1-shaped, 22 chars) and any other UUID (UUIDv4 / UUIDv7 → 26-char `ashid4` form).

## How ashid Compares

| Feature | ashid | uuid v4 | nanoid | cuid2 | ulid | typeid | ksuid |
|---------|-------|---------|--------|-------|------|--------|-------|
| Encoding | Crockford Base32 | Hex | URL-safe (64-char) | Base36 (lowercase) | Crockford Base32 | Crockford Base32 (lowercase) | Base62 |
| Type prefixes | ✅ Built-in | ❌ | ❌ | ❌ | ❌ | ✅ Built-in | ❌ |
| Time-sortable | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ (UUIDv7) | ✅ |
| Case-insensitive | ✅ | ✅ | ❌ | ⚠️¹ | ✅ | ⚠️² | ❌ |
| Lookalike correction (I→1, O→0) | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ❌ |
| Double-click selectable | ✅ | ❌ Hyphens | ⚠️³ | ✅ | ✅ | ✅ | ✅ |

¹ CUID2 emits only lowercase characters; uppercased IDs are invalid.
² TypeID's canonical encoding is lowercase; decoders may accept uppercase per spec.
³ NanoID's default 64-char alphabet includes `-`, which breaks double-click selection in most browsers. Use a custom alphabet to fix.

ashid is the only entry that maps `I` / `L` / `O` / `U` to their canonical Crockford digits — every other format here is silent on lookalike characters. Combined with type prefixes and case-insensitive parsing, that's the pivot ashid wins on. ULID and TypeID get most of the way there but neither does the lookalike correction. KSUID is the longest-running of the time-sortable formats but uses case-sensitive Base62.

## FAQ

### How does this compare to TypeID, KSUID, ULID, and UUIDv7?

- **TypeID** is the closest neighbor — also uses Crockford Base32 and type prefixes. The differences: TypeID encodes a UUIDv7 (so version + variant bits eat into entropy, leaving 12 bits in the high half) and is canonically lowercase-only. ashid uses an epoch-ms timestamp + 64 bits of secure random, normalizes lookalikes (`I`/`L`/`O`/`U`) on decode, and is case-insensitive end-to-end.
- **KSUID** is excellent — second-precision timestamp, case-sensitive Base62, 27 chars. ashid trades the wider alphabet for case-insensitivity and lookalike correction, lands at 22 chars instead of 27, and has a one-to-one transform with UUID.
- **ULID** is the prior art for "Crockford Base32 + time-sortable" — 26 chars, no type prefix. ashid is essentially "ULID minus the version bits in the timestamp, plus a type prefix, minus 4 characters (22 vs 26)."
- **UUIDv7** is the IETF answer to time-sortable UUIDs. The wins of ashid over v7 are the prefix and the encoding: 32 hex chars with hyphens vs. 22 Crockford chars without. v7 keeps you in the standard UUID ecosystem; ashid is a different ergonomic profile aimed at logs, URLs, and human-facing surfaces.

The full feature matrix is in [How ashid Compares](#how-ashid-compares).

### What about collision safety with only 64 bits of randomness?

The standard ashid carries a 41-bit ms timestamp (45-bit window, currently ~41 bits used) plus a 64-bit random component. For collision resistance the random component is what matters, and 64 bits of secure random within a single millisecond gives you a 50% birthday-collision probability at roughly 5 billion IDs — generated in the same millisecond on the same host. In practice the hot path is "one or a few IDs per request, multiple requests per millisecond" — collision risk is not the bottleneck.

If you need 128-bit randomness, use `ashid4()`. It drops the timestamp and uses two 64-bit secure random components (~106 effective bits combined, given that one bit per 13-char block stays zero), 26 characters, same prefix support — UUIDv4-equivalent in the same library.

### How does it handle same-millisecond ID generation?

Two ashids generated in the same millisecond on the same host carry the same encoded timestamp and different random components. They will not collide, but they are not strictly time-sorted relative to each other — the lex-sort tiebreaker is the random value, not the generation order. If your application needs strict monotonicity at sub-millisecond resolution, layer a sequence counter or accept the random tiebreak.

### Is it really convertible to a UUID?

Yes. `Ashid.toUuid(id)` returns a 36-character dashed hex string; `Ashid.fromUuid(uuid)` accepts the same shape (or 32-char undashed) and returns an ashid. Round-trip is byte-identical. UUIDs whose high half fits in 45 bits (UUID-1-shaped, with realistic ms timestamps until year 3084) round-trip into the standard 22-char ashid base. UUIDv4 and UUIDv7 — whose high halves exceed that window because of randomness or version bits — round-trip into the 26-char `ashid4` base. Both directions are supported in all five language implementations.

The resulting UUID is shape-compatible with RFC 4122 (8-4-4-4-12 hex) but the version/variant bits reflect the underlying ashid bytes. It is not guaranteed to be a valid v1/v4/v7 UUID unless the source was — that part is documented in each library's API reference.

### Doesn't sortability leak information?

The standard `ashid()` form embeds a millisecond timestamp, so anyone with the ID can recover when it was created. For IDs where that's a problem, use `ashid4()` — two random components, no timestamp, same 22+4 character budget, UUIDv4-equivalent collision profile. Same call site, different security profile. Use `ashid()` for time-sortable internal records, `ashid4()` for tokens, secrets, and any ID that shouldn't reveal its provenance.

### Why type prefixes when most tables only have one type?

Inside a single table the prefix is redundant — the type is already implicit from the table name. The prefix earns its keep across system boundaries: logs, URLs, error messages, bug reports, support tickets, JSON payloads, RPC traces. In any context where the ID travels separately from its table, the prefix is the difference between "what is `1kbg1jmtt4v3x8k9p2m1n0`?" and "oh, that's a user." The redundancy inside the table is the price of the clarity outside it.

## Inspired By

- [Stripe's ID format](https://stripe.com/docs/api) — The `sk_`, `pi_`, `cus_` prefix convention
- [Douglas Crockford's Base32](https://www.crockford.com/base32.html) — Human-friendly encoding
- [ULID](https://github.com/ulid/spec) — Time-sortable unique identifiers
- [TypeID](https://github.com/jetpack-io/typeid) — Type-safe, K-sortable IDs
- [KSUID](https://github.com/segmentio/ksuid) — K-sortable IDs with second-precision timestamps
- [TSID](https://vladmihalcea.com/uuid-database-primary-key/) — Time-sorted IDs as DB primary keys (Vlad Mihalcea's writeup)

## Roadmap

ashid ships for **TypeScript/JavaScript**, the **JVM** (Kotlin + Java), **Python**, **Rust**, and **Go**. The next set of languages, with shared cross-language test vectors so any ID generated in one language decodes identically in every other:

- **Swift** — first-class native iOS / macOS package
- **C# / .NET** — NuGet for enterprise stacks
- **Ruby** — gem for Rails apps
- **Elixir** — hex package for Phoenix / OTP services

Every implementation passes the same parity test suite (encoding, decoding, prefix handling, lookalike correction, monotonic sort within the same millisecond, UUID round-trip). If you want to lead one of these, [open an issue](https://github.com/wildeagency/ashid/issues) — happy to hand off a starter spec and test vectors.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

## License

MIT

## Author

Created by **Dathan Guiley** at [Wilde Agency](https://wilde.agency).

---

Built by [Wilde Agency](https://wilde.agency)
