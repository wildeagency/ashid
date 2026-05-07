# ashid

[![npm version](https://img.shields.io/npm/v/ashid.svg)](https://www.npmjs.com/package/ashid)
[![npm downloads](https://img.shields.io/npm/dw/ashid.svg)](https://www.npmjs.com/package/ashid)
[![license](https://img.shields.io/npm/l/ashid.svg)](https://github.com/wildeagency/ashid/blob/main/LICENSE)

**Time-sortable unique identifiers with type prefixes.**

```
ashid("user_")  →  user_1kbg1jmtt4v3x8k9p2m1n
ashid("tx_")    →  tx_1kbg1jmts7h2w5r8q4n3m
ashid()         →  1kbg1jmtr9k5v2x8p4m1n3w
```

## Why ashid?

UUIDs are opaque. When you see `550e8400-e29b-41d4-a716-446655440000` in a log, you have no idea what it represents. You have to grep, cross-reference, and waste debugging time.

ashid generates IDs that tell you what they are:

```
user_1kbg1jmtt4v3x8k9p2m1n  ← Obviously a user
tx_1kbg1jmts7h2w5r8q4n3m    ← Obviously a transaction
asset_1kbg1jmtr9k5v2x8p4m1n ← Obviously an asset
```

### Key Features

- **Type prefixes** — Self-documenting IDs, just like Stripe (`sk_`, `pi_`, `cus_`)
- **Time-sortable** — Lexicographic sort = chronological sort
- **Crockford Base32** — Case-insensitive, `I`/`L`→`1`, `O`→`0` (no more "is that a zero or an O?")
- **Double-click selectable** — No hyphens or special characters
- **URL-safe** — No encoding required
- **Zero dependencies**

## Packages

| Package | Platform | Install |
|---------|----------|---------|
| [ashid](./typescript/) | TypeScript/JavaScript | `npm install ashid` |
| ashid | Kotlin/Java | `implementation("agency.wilde:ashid:1.0.0")` |

## Quick Start

### TypeScript/JavaScript

```typescript
import { ashid } from 'ashid';

const userId = ashid('user_');   // "user_1kbg1jmtt4v3x8k9p2m1n"
const rawId = ashid();           // "1kbg1jmtt4v3x8k9p2m1n0w"
```

See the [TypeScript README](./typescript/README.md) for full API documentation.

### Kotlin/Java

```kotlin
import agency.wilde.ashid.ashid

val userId = ashid("user_")   // "user_1kbg1jmtt4v3x8k9p2m1n"
val rawId = ashid()           // "1kbg1jmtt4v3x8k9p2m1n0w"
```

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

ashid is the only one of these formats that combines **time-sortability**, **type prefixes**, **case-insensitive Crockford Base32**, **and** lookalike correction (I/L→1, O→0). ULID and TypeID get most of the way there but neither does the I/L/O correction. KSUID is the longest-running of the time-sortable formats but uses case-sensitive Base62.

## Inspired By

- [Stripe's ID format](https://stripe.com/docs/api) — The `sk_`, `pi_`, `cus_` prefix convention
- [Douglas Crockford's Base32](https://www.crockford.com/base32.html) — Human-friendly encoding
- [ULID](https://github.com/ulid/spec) — Time-sortable unique identifiers
- [TypeID](https://github.com/jetpack-io/typeid) — Type-safe, K-sortable IDs
- [KSUID](https://github.com/segmentio/ksuid) — K-sortable IDs with second-precision timestamps

## Roadmap

ashid currently ships for **TypeScript/JavaScript** and the **JVM** (Kotlin + Java). The next set of languages we'd like to add, with shared cross-language test vectors so any ID generated in one language decodes identically in every other:

- **Python** — `pip install ashid` for the data, scripting, and AI ecosystems
- **Go** — backend-heavy fit; the home turf of ULID, KSUID, and TypeID
- **Rust** — `cargo add ashid` for systems and embedded use
- **Swift** — first-class native iOS / macOS package
- **C# / .NET** — NuGet for enterprise stacks
- **Ruby** — gem for Rails apps
- **Elixir** — hex package for Phoenix / OTP services

Every implementation should pass the same parity test suite (encoding, decoding, prefix handling, lookalike correction, monotonic sort within the same millisecond). If you want to lead one of these, [open an issue](https://github.com/wildeagency/ashid/issues) — happy to hand off a starter spec and test vectors.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

## License

MIT

## Author

Created by **Dathan Guiley** at [Wilde Agency](https://wilde.agency).

---

Built by [Wilde Agency](https://wilde.agency)
