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

| Feature | ashid | uuid | nanoid | cuid2 | ulid |
|---------|-------|------|--------|-------|------|
| Type prefixes | ✅ Built-in | ❌ | ❌ | ❌ | ❌ |
| Time-sortable | ✅ | ❌ | ❌ | ❌ | ✅ |
| Human-readable | ✅ Crockford Base32 | ❌ Hex | ⚠️ Base64 | ⚠️ | ⚠️ |
| Case-insensitive | ✅ | ✅ | ❌ | ✅ | ❌ |
| Character correction | ✅ I→1, O→0 | ❌ | ❌ | ❌ | ❌ |
| Double-click selectable | ✅ | ❌ Hyphens | ✅ | ✅ | ✅ |
| URL-safe | ✅ | ⚠️ Needs encoding | ✅ | ✅ | ✅ |
| Zero dependencies | ✅ | ✅ | ✅ | ✅ | ✅ |

## Inspired By

- [Stripe's ID format](https://stripe.com/docs/api) — The `sk_`, `pi_`, `cus_` prefix convention
- [Douglas Crockford's Base32](https://www.crockford.com/base32.html) — Human-friendly encoding
- [ULID](https://github.com/ulid/spec) — Time-sortable unique identifiers
- [TypeID](https://github.com/jetpack-io/typeid) — Type-safe, K-sortable IDs

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

## License

MIT

## Author

Created by **Dathan Guiley** at [Wilde Agency](https://wilde.agency).

---

Built by [Wilde Agency](https://wilde.agency)
