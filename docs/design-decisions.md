# Ashid Design Decisions

This document explains the reasoning behind Ashid's design choices.

## Core Philosophy

**Developer Experience First**: Every decision prioritizes how developers interact with IDs in their daily work—copying from logs, pasting into queries, reading aloud, identifying entity types.

## Decision 1: Crockford Base32 Encoding

### The Choice
Use Douglas Crockford's Base32 alphabet instead of standard Base32, Base64, or hexadecimal.

### Alphabet
```
0123456789abcdefghjkmnpqrstvwxyz
```

Excludes: `i`, `l`, `o`, `u`

### Why

1. **Case-insensitive**: `ABC` and `abc` decode to the same value. No "was that uppercase?" confusion.

2. **Lookalike mapping**:
   - `O` and `o` → `0` (zero)
   - `I`, `i`, `L`, `l` → `1` (one)
   - `U` and `u` → `v`

   Reading an ID over the phone? Transcribing from a screenshot? These errors are auto-corrected.

3. **URL-safe**: No special characters that need encoding.

4. **Reasonable density**: 32 symbols = 5 bits per character. Better than hex (4 bits) but not as error-prone as Base64.

### Alternatives Considered

| Encoding | Bits/char | Case-sensitive | URL-safe | Lookalike handling |
|----------|-----------|----------------|----------|-------------------|
| Hex | 4 | No | Yes | No |
| Base32 (RFC) | 5 | No | Yes | No |
| Crockford Base32 | 5 | No | Yes | **Yes** |
| Base64 | 6 | Yes | No | No |
| Base58 | ~5.86 | Yes | Yes | Partial |

Crockford Base32 uniquely combines case-insensitivity with lookalike correction.

## Decision 2: Time-Sortable Structure

### The Choice
Place timestamp in the most significant position, ensuring lexicographic sort = chronological sort.

### Format
```
[prefix][timestamp][random]
         ↑ most significant
```

### Why

1. **Database efficiency**: B-tree indexes work optimally when new entries append to the end. Time-sorted IDs minimize page splits.

2. **Log analysis**: `grep` and `sort` just work. No need for timestamp parsing.

3. **Intuitive ordering**: `id1 < id2` means `id1` was created first.

4. **Range queries**: Find all orders from today with a simple string comparison.

### Tradeoffs

- **Predictability**: Attackers can guess ID ranges. Mitigated by the random component.
- **Clock dependency**: Relies on system clock accuracy. Mitigated by random tiebreaker.

### Why Not Random-First (like UUID v4)?

Random-first distributes writes across index pages, which sounds good but:
1. Causes random I/O patterns (slow)
2. Fragments indexes over time
3. Loses valuable temporal information

## Decision 3: Optional Type Prefixes

### The Choice
Support optional alphabetic prefixes (e.g., `user_`, `order_`, `inv`) to identify entity types.

### Why

1. **Self-documenting**: Looking at `user_1fvszawr...` vs `550e8400-...`, you immediately know it's a user ID.

2. **Debugging**: Logs become readable: "Processing user_abc123 for order_xyz789"

3. **Type safety**: Catch bugs where a user ID is used as an order ID.

4. **No ambiguity**: Prefixed IDs start with letters; raw IDs start with numbers.

### Two Formats

**With underscore delimiter** (`user_`):
- Variable length base ID
- Underscore acts as clear separator
- Best for APIs and logs

**Without delimiter** (`u`):
- Fixed 22-char base ID
- More compact
- Best for storage-sensitive applications

### Why Lowercase-Only Output

Prefixes are normalized to lowercase:
```typescript
ashid('USER_')  // Returns: user_...
ashid('User')   // Returns: user...
```

Reasons:
1. Consistent appearance in logs and databases
2. Case-insensitive comparison without normalization
3. Matches typical programming conventions

## Decision 4: 22-Character Base Length

### The Choice
Fixed base: 9 chars timestamp + 13 chars random = 22 characters.

### Why These Numbers

**Timestamp (9 chars)**:
- 9 Crockford Base32 chars = 45 bits
- Max value: 35,184,372,088,831 milliseconds
- Supports dates up to December 12, 3084
- That's 1,059 years of headroom

**Random (13 chars)**:
- 13 Crockford Base32 chars = 65 bits
- But limited to 53 bits (JavaScript safe integer)
- ~9 quadrillion possible values per millisecond
- Collision probability: negligible in practice

### Why Not Longer?

More characters = more entropy = fewer collisions. But:
1. Diminishing returns: 53 bits is already extremely safe
2. Usability cost: Longer IDs are harder to work with
3. Storage cost: Every character costs bytes across millions of rows

### Why Not Shorter?

We could use 20 characters, but:
1. Timestamp headroom matters for long-lived systems
2. Random component size affects collision probability
3. The difference (2 chars) isn't significant for most use cases

## Decision 5: Millisecond Precision

### The Choice
Timestamps are in milliseconds since Unix epoch.

### Why Milliseconds

1. **Standard**: JavaScript `Date.now()`, Java `System.currentTimeMillis()` all return milliseconds.

2. **Sufficient precision**: Sub-millisecond ordering is handled by the random component.

3. **Reasonable range**: 45 bits of milliseconds = 1,000+ years.

### Why Not Microseconds/Nanoseconds

1. **Cross-platform issues**: Not all systems provide sub-millisecond precision reliably.
2. **Overkill**: Millisecond precision with random tiebreaker covers all practical cases.
3. **Would need more bits**: Reducing random component or extending length.

## Decision 6: Cryptographically Secure Random

### The Choice
Use the platform's cryptographic random number generator (CSPRNG).

### Implementation
- **Node.js**: `crypto.randomBytes()`
- **Browser**: `crypto.getRandomValues()`
- **JVM**: `java.security.SecureRandom`

### Why Secure Random

1. **Unpredictability**: Prevents attackers from guessing valid IDs.
2. **Collision resistance**: True randomness minimizes collision probability.
3. **Best practice**: No reason to use inferior randomness for IDs.

### Performance

CSPRNGs are fast enough. Modern implementations:
- Linux: Uses hardware RNG when available
- macOS: ChaCha20-based
- Windows: BCryptGenRandom

Generating a million IDs per second is feasible on commodity hardware.

## Decision 7: No External Dependencies

### The Choice
Zero runtime dependencies in all implementations.

### Why

1. **Security**: No supply chain vulnerabilities from ID generation.
2. **Bundle size**: Ashid adds minimal weight to your build.
3. **Stability**: No breaking changes from upstream dependencies.
4. **Simplicity**: Easy to audit the entire codebase.

### What We Implement Ourselves

- Base32 encoding/decoding
- Secure random number generation (using platform APIs)
- Timestamp handling

## Decision 8: Dash-to-Underscore Normalization

### The Choice
Accept dashes as prefix delimiters but normalize to underscores.

```typescript
Ashid.parse('user-abc123...')  // Returns prefix: 'user_'
```

### Why

1. **User forgiveness**: Easy to type dash instead of underscore
2. **URL friendliness**: Some systems encode underscores but not dashes
3. **Consistency**: Output is always underscore, input accepts both

## Decision 9: Validation Without Exceptions

### The Choice
Provide `isValid()` methods that return boolean, alongside parsing that throws.

```typescript
if (Ashid.isValid(input)) {
  // Safe to use
}

// OR

try {
  Ashid.parse(input);
} catch (e) {
  // Invalid
}
```

### Why Both

1. **Validation** (`isValid`): For user input, form validation, quick checks
2. **Parsing** (throws): For trusted input where invalid data indicates a bug

## Decision 10: Static Methods over Instance

### The Choice
Use static factory methods and utilities rather than instance methods.

```typescript
// Static approach (chosen)
const id = Ashid.create('user_');
const ts = Ashid.timestamp(id);

// Instance approach (not chosen)
const id = new Ashid('user_');
const ts = id.timestamp();
```

### Why Static

1. **IDs are strings**: The result is a plain string, not a wrapper object.
2. **Interoperability**: Works seamlessly with JSON, databases, APIs.
3. **No object overhead**: Memory-efficient for high-throughput systems.
4. **Familiarity**: Matches how UUID libraries typically work.

## Summary

Ashid's design prioritizes:

| Priority | Decisions |
|----------|-----------|
| Developer Experience | Crockford encoding, type prefixes, normalization |
| Performance | Time-sorted structure, efficient encoding |
| Security | CSPRNG, sufficient entropy |
| Simplicity | No dependencies, static methods, string output |
| Compatibility | Case-insensitive, lookalike mapping, standard lengths |

Every character, every bit, every API choice serves these goals.
