# Ashid

**Time-sortable unique identifiers with type prefixes.**

```
ashid("user_")  →  user_1kbg1jmtt4v3x8k9p2m1n
ashid("tx_")    →  tx_1kbg1jmts7h2w5r8q4n3m
ashid()         →  1kbg1jmtr9k5v2x8p4m1n3w
```

## Why Ashid?

**Know what you're looking at.** UUIDs are opaque. When you see `550e8400-e29b-41d4-a716-446655440000` in a log, is it a user? A transaction? An asset? Ashid prefixes tell you instantly:

```
user_1kbg1jmtt...  ← User ID
tx_1kbg1jmts...    ← Transaction
asset_1kbg1jmtr... ← Asset
```

**Developer experience matters.** Ashid uses [Crockford Base32](https://www.crockford.com/base32.html):

- **Case-insensitive** — `ABC` and `abc` decode identically
- **Character correction** — `I`/`L`/`l` → `1`, `O`/`o` → `0`
- **Double-click selectable** — no hyphens or special characters
- **URL-safe** — no encoding required

**Time-sorted by default.** Lexicographic sort = chronological sort. Database indexes cluster naturally.

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

## Comparison

| Feature | Ashid | UUID | ULID | NanoID |
|---------|-------|------|------|--------|
| Type prefixes | Yes | No | No | No |
| Case-insensitive | Yes | No | No | No |
| Lookalike correction | Yes | No | No | No |
| Time-sortable | Yes | v1 only | Yes | No |
| Double-click select | Yes | No | Yes | Yes |
| Length | 22+ | 36 | 26 | 21 |

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

## License

MIT

---

Built by [Wilde Agency](https://wilde.agency)
