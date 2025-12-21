# Ashid vs Other ID Libraries

A comprehensive comparison of Ashid with popular unique identifier libraries.

## Quick Comparison Table

| Feature | Ashid | UUID v4 | NanoID | ULID | CUID2 |
|---------|-------|---------|--------|------|-------|
| **Length** | 22 chars | 36 chars | 21 chars | 26 chars | 24 chars |
| **Time-sortable** | ✅ Yes | ❌ No | ❌ No | ✅ Yes | ❌ No |
| **Double-click select** | ✅ Yes | ❌ No (hyphens) | ✅ Yes | ✅ Yes | ✅ Yes |
| **Case-insensitive** | ✅ Yes | ❌ No | ❌ No | ✅ Yes* | ❌ No |
| **Lookalike mapping** | ✅ Yes | ❌ No | ❌ No | ❌ No | ❌ No |
| **Type prefixes** | ✅ Built-in | ❌ No | ❌ No | ❌ No | ❌ No |
| **Encoding** | Crockford Base32 | Hex | Base64-like | Base32 | Base36 |

*ULID uses standard Base32 which is case-insensitive but doesn't map lookalikes

## Detailed Comparisons

### Ashid vs UUID

**UUID (Universally Unique Identifier)** is the most widely used ID format, but has significant usability issues.

```
UUID:   550e8400-e29b-41d4-a716-446655440000  (36 chars)
Ashid:  u1fvszawr42tve3gxvx9900               (23 chars with prefix)
```

#### Where Ashid Wins

1. **Double-click selection**: UUID's hyphens break selection. You have to drag-select the entire ID. Ashid has no separators—double-click anywhere to select.

2. **39% shorter**: 22 characters vs 36. Less storage, shorter URLs, easier to copy.

3. **Human-friendly**: Crockford Base32 is case-insensitive and maps lookalikes:
   - Reading UUID aloud: "Was that a capital O or zero? Lowercase L or one?"
   - Reading Ashid aloud: All ambiguous characters map to the same value.

4. **Type prefixes**: Know what you're looking at: `user_abc123` vs `550e8400-...`

5. **Time-sortable**: Ashids sort chronologically. UUIDv4 is random.

#### Where UUID Wins

1. **Ubiquity**: Every language, database, and tool supports UUIDs natively.
2. **Standardization**: RFC 4122 defines the format precisely.
3. **More entropy**: 122 random bits vs ~66 bits in Ashid's random component.

#### When to Use Which

- **Use Ashid** when developer experience matters: logging, debugging, APIs, URLs
- **Use UUID** when you need maximum compatibility with legacy systems

---

### Ashid vs NanoID

**NanoID** is a tiny, fast, URL-friendly unique ID generator.

```
NanoID: V1StGXR8_Z5jdHi6B-myT   (21 chars default)
Ashid:  1fvszawr42tve3gxvx9900  (22 chars)
```

#### Where Ashid Wins

1. **Time-sortable**: NanoID is purely random. Ashid's timestamp prefix means IDs sort chronologically—crucial for database indexing and logs.

2. **Crockford encoding**: NanoID uses characters that look alike (1/l/I, 0/O). Ashid maps these to canonical forms.

3. **Type prefixes**: Built-in support for `user_`, `order_`, etc.

4. **Predictable length**: NanoID's length is configurable but the default alphabet includes `-` and `_`. Ashid is always alphanumeric.

#### Where NanoID Wins

1. **Speed**: NanoID is optimized for raw generation speed.
2. **Slightly shorter**: 21 vs 22 characters by default.
3. **Customizable alphabet**: You can use any character set.
4. **Tiny bundle**: ~130 bytes minified.

#### When to Use Which

- **Use Ashid** when you need chronological sorting or human-readable IDs
- **Use NanoID** when raw speed matters and you don't need sorting

---

### Ashid vs ULID

**ULID (Universally Unique Lexicographically Sortable Identifier)** is the closest alternative to Ashid.

```
ULID:  01ARZ3NDEKTSV4RRFFQ69G5FAV  (26 chars)
Ashid: 1fvszawr42tve3gxvx9900       (22 chars)
```

#### Where Ashid Wins

1. **Lookalike mapping**: ULID uses Crockford's alphabet but doesn't map I→1, O→0, L→1. Ashid does.

2. **Type prefixes**: ULID has no prefix support. Ashid lets you tag entities: `user_`, `order_`, `inv_`.

3. **Shorter**: 22 chars vs 26 chars.

4. **Case handling**: While ULID is technically case-insensitive, many implementations don't normalize. Ashid always normalizes.

#### Where ULID Wins

1. **Established**: ULID has been around longer with more implementations.
2. **Millisecond precision**: ULID encodes milliseconds directly. Ashid uses the same approach.
3. **More random bits**: 80 bits vs ~66 bits.

#### When to Use Which

- **Use Ashid** when you want prefixes and foolproof human readability
- **Use ULID** when you need an established standard with broad support

---

### Ashid vs CUID/CUID2

**CUID2** is the successor to CUID, designed for security and horizontal scalability.

```
CUID2: ckopqwooh000001me7x2m9pb7  (25 chars)
Ashid: 1fvszawr42tve3gxvx9900     (22 chars)
```

#### Where Ashid Wins

1. **Shorter**: 22 chars vs 25 chars.

2. **No fixed prefix**: CUID always starts with 'c'. Ashid prefixes are optional and customizable.

3. **Time-sortable**: CUID2 explicitly does NOT guarantee sortability. Ashid does.

4. **Simpler design**: Ashid is timestamp + random. CUID2 uses counter, fingerprint, and random.

5. **Case-insensitive**: CUID2 is case-sensitive (lowercase only). Ashid normalizes case.

#### Where CUID2 Wins

1. **Security-focused**: Designed to prevent enumeration attacks.
2. **Horizontal scaling**: Built-in fingerprint prevents collisions across machines.
3. **No timestamp leakage**: CUID2 intentionally obscures creation time.

#### When to Use Which

- **Use Ashid** when you want time-sorting and readable IDs
- **Use CUID2** when security against enumeration is critical

---

## Performance Comparison

All libraries are "fast enough" for typical use cases. Here's a rough comparison:

| Library | Ops/second (approx) |
|---------|---------------------|
| NanoID | ~5,000,000 |
| ULID | ~1,500,000 |
| Ashid | ~1,000,000 |
| CUID2 | ~500,000 |
| UUID v4 | ~2,000,000 |

**Note**: These numbers vary by implementation and runtime. For most applications, generation speed is not a bottleneck.

---

## Summary

Choose **Ashid** when you want:
- Time-sortable IDs for efficient database queries
- Human-readable IDs that work in logs, URLs, and verbal communication
- Type prefixes to identify entity types at a glance
- Compact representation (22-26 chars)

Choose alternatives when:
- **UUID**: Maximum compatibility with existing systems
- **NanoID**: Raw speed and minimal bundle size
- **ULID**: Established standard with broad ecosystem support
- **CUID2**: Security-first design preventing enumeration
