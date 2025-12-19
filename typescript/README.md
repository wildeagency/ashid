# Ashid

[![npm version](https://img.shields.io/npm/v/ashid.svg)](https://www.npmjs.com/package/ashid)
[![npm downloads](https://img.shields.io/npm/dw/ashid.svg)](https://www.npmjs.com/package/ashid)
[![bundle size](https://img.shields.io/bundlephobia/minzip/ashid)](https://bundlephobia.com/package/ashid)
[![license](https://img.shields.io/npm/l/ashid.svg)](https://github.com/wildeagency/ashid/blob/main/LICENSE)
[![TypeScript](https://img.shields.io/badge/TypeScript-Ready-blue.svg)](https://www.typescriptlang.org/)

**Time-sortable unique identifiers with type prefixes.**

```
ashid("user_")  →  user_1kbg1jmtt4v3x8k9p2m1n
ashid("tx_")    →  tx_1kbg1jmts7h2w5r8q4n3m
ashid()         →  1kbg1jmtr9k5v2x8p4m1n3w
```

## Why Ashid?

### The Problem

UUIDs are opaque. When you see `550e8400-e29b-41d4-a716-446655440000` in a log, a URL, or a database query, you have no idea what it represents. Is it a user? A transaction? An asset? You have to grep, cross-reference, and waste debugging time.

### The Solution

Ashid generates IDs that tell you what they are:

```typescript
user_1kbg1jmtt4v3x8k9p2m1n  // ← Obviously a user
tx_1kbg1jmts7h2w5r8q4n3m    // ← Obviously a transaction
asset_1kbg1jmtr9k5v2x8p4m1n // ← Obviously an asset
```

### Why Crockford Base32?

Douglas Crockford designed Base32 specifically for human-readable identifiers:

- **Case-insensitive** — `ABC` and `abc` decode identically
- **Character correction** — `I`, `L`, `l` → `1` and `O`, `o` → `0` (no more "is that a zero or an O?")
- **No ambiguous characters** — excludes `I`, `L`, `O`, `U` entirely
- **Double-click selectable** — no hyphens or special characters
- **URL-safe** — no encoding required

This is the same approach Stripe uses for their IDs (`sk_live_...`, `pi_...`, `cus_...`).

### Time-Sorted by Default

ASHID embeds a timestamp, so lexicographic sort = chronological sort. Your database indexes cluster naturally. Your logs are in order. No extra work required.

## Installation

```bash
npm install ashid
```

```typescript
import { ashid } from 'ashid';

const userId = ashid('user_');   // "user_1kbg1jmtt4v3x8k9p2m1n"
const shortId = ashid('u');      // "u1kbg1jmtt4v3x8k9p2m1n0w"
const rawId = ashid();           // "1kbg1jmtt4v3x8k9p2m1n0w"
```

## Prefix Formats

Ashid supports two formats based on prefix style:

### With underscore delimiter (variable length)
```typescript
ashid('user_')   // user_1kbg1jmtt4v3x8k9p2m1n
ashid('order_')  // order_1kbg1jmts7h2w5r8q4n3m
```
- Timestamp: variable length (no padding)
- Random: 13 chars (padded)
- Underscore acts as delimiter for reliable parsing

### Without underscore (fixed 22-char base)
```typescript
ashid('u')   // u1kbg1jmtt4v3x8k9p2m1n0w (1 + 22 = 23 chars)
ashid()      // 1kbg1jmtt4v3x8k9p2m1n0w  (22 chars)
```
- Timestamp: 9 chars (zero-padded)
- Random: 13 chars (padded)
- Fixed length enables parsing without delimiter

## Features

### Crockford Base32

The encoding excludes ambiguous characters and maps lookalikes during decoding:

| If you type | Decodes as |
|-------------|------------|
| `I`, `i`, `L`, `l` | `1` |
| `O`, `o` | `0` |
| `U`, `u` | `v` |

Read IDs aloud without confusion. Type without shift key. Copy without encoding errors.

### Time-Sorted

```typescript
const id1 = ashid('tx_');  // Created at T
const id2 = ashid('tx_');  // Created at T+1ms

id1 < id2  // true — lexicographic sort is chronological
```

Benefits:
- Database indexes cluster by creation time
- Range queries work naturally
- Pagination is straightforward

### Parsing & Extraction

```typescript
import { Ashid, parseAshid } from 'ashid';

const id = ashid('user_');

// Validate
Ashid.isValid(id);  // true

// Parse components (returns [prefix, encodedTimestamp, encodedRandom])
const [prefix, ts, rand] = parseAshid(id);

// Extract as native types
Ashid.prefix(id);     // "user_"
Ashid.timestamp(id);  // 1733140800000 (milliseconds)
Ashid.random(id);     // 8234567890123

// Normalize (lowercase + fix ambiguous chars like I→1, O→0)
Ashid.normalize('USER_1KBG1JMTT...');  // "user_1kbg1jmtt..."
```

## Format

```
[prefix][timestamp][random]
   ↓        ↓         ↓
 user_  1kbg1jmtt  4v3x8k9p2m1n

Prefix:    Letters + optional underscore (user defined)
Timestamp: Variable or 9 chars Crockford Base32 (milliseconds since epoch)
Random:    13 chars Crockford Base32 (cryptographically secure)
```

**Timestamp range:** 0 (Unix epoch) to 35184372088831 (Dec 12, 3084)

## How ASHID Compares

| Feature | ASHID | uuid | nanoid | cuid2 | ulid |
|---------|-------|------|--------|-------|------|
| Type prefixes | ✅ Built-in | ❌ | ❌ | ❌ | ❌ |
| Time-sortable | ✅ | ❌ | ❌ | ❌ | ✅ |
| Human-readable | ✅ Crockford Base32 | ❌ Hex | ⚠️ Base64 | ⚠️ | ⚠️ |
| Case-insensitive | ✅ | ✅ | ❌ | ✅ | ❌ |
| Character correction | ✅ I→1, O→0 | ❌ | ❌ | ❌ | ❌ |
| Double-click selectable | ✅ | ❌ Hyphens | ✅ | ✅ | ✅ |
| URL-safe | ✅ | ⚠️ Needs encoding | ✅ | ✅ | ✅ |
| Zero dependencies | ✅ | ✅ | ✅ | ✅ | ✅ |
| Self-documenting in logs | ✅ | ❌ | ❌ | ❌ | ❌ |

**The key difference:** When you see `user_1kbg1jmtt4v3x8k9p2m1n` in a log, you know it's a user. When you see `550e8400-e29b-41d4-a716-446655440000`, you have to grep.

## Real-World Use Cases

### API Resources
```typescript
const userId = ashid('user_');     // user_1kbg1jmtt4v3x8k9p2m1n
const orderId = ashid('order_');   // order_1kbg1jmts7h2w5r8q4n3m
const invoiceId = ashid('inv_');   // inv_1kbg1jmtr9k5v2x8p4m1n
```

### Database Primary Keys
```typescript
// IDs sort chronologically without additional indexes
const posts = await db.query('SELECT * FROM posts ORDER BY id');
```

### Log Debugging
```
[ERROR] Payment failed for user_1kbg1jmtt4v3x8k9p2m1n on order_1kbg1jmts7h2w5r8q4n3m
// Instantly know: it's a user and an order. No grepping required.
```

### URL Slugs
```
https://app.example.com/users/user_1kbg1jmtt4v3x8k9p2m1n
https://app.example.com/orders/order_1kbg1jmts7h2w5r8q4n3m
// Self-documenting URLs that are still opaque enough for security
```

## API

```typescript
// Create (defaults: time = Date.now(), random = secure random)
ashid(prefix?: string): string
Ashid.create(prefix?: string, time?: number, randomLong?: number): string

// Parse - returns [prefix, encodedTimestamp, encodedRandom]
parseAshid(id: string): [string, string, string]
Ashid.parse(id: string): [string, string, string]

// Extract components
Ashid.prefix(id: string): string      // "" if none
Ashid.timestamp(id: string): number   // milliseconds
Ashid.random(id: string): number      // random value

// Utilities
Ashid.isValid(id: string): boolean
Ashid.normalize(id: string): string   // lowercase + fix ambiguous chars
```

## Inspired By

ASHID stands on the shoulders of giants:

- [Stripe's ID format](https://stripe.com/docs/api) — The `sk_`, `pi_`, `cus_` prefix convention
- [Douglas Crockford's Base32](https://www.crockford.com/base32.html) — Human-friendly encoding
- [ULID](https://github.com/ulid/spec) — Time-sortable unique identifiers
- [TypeID](https://github.com/jetpack-io/typeid) — Type-safe, K-sortable IDs (similar goals, different implementation)

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](./CONTRIBUTING.md) for guidelines.

## License

MIT

## Author

Created by **Dathan Guiley** at [Wilde Agency](https://wilde.agency) in 2016 for the End of Shopping project.

---

Built by [Wilde Agency](https://wilde.agency)
