# Changelog

All notable changes to ashid will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

No unreleased changes.

## 2026-06-27 (Go)

### Changed
- `Normalize()` now preserves the **minimal form** for `time === 0` +
  prefix inputs, matching TypeScript 1.7.0. `Normalize("user_0")` returns
  `"user_0"` (not `"user_00000000000000"`); `Normalize("user_c1s")` returns
  `"user_c1s"` (not `"user_00000000000c1s"`). The 1.6.0-equivalent
  always-padded form still parses correctly and collapses to minimal on
  normalize — values survive. `Normalize()` of a v1 input with `time > 0`,
  any ashid4 input, or the no-prefix form is unchanged.
- `buildBase()` now branches on `(hasPrefix && !padded && n1.Sign() == 0)`
  to emit the minimal form, mirroring the TS 1.7.0 builder. `Create()` was
  already emitting minimal form; only the `Normalize()` path needed
  alignment.

### Tests
- Added 9 `TestNormalize_*` cases pinning minimal-form preservation
  (`user_0`, `user_1`, `user_z`, `user_c1s`), the padded → minimal collapse
  (`user_00000000000c1s` → `user_c1s`), uppercase-prefix normalization,
  `Create` → `Normalize` round-trip, and idempotence.

## [1.4.0] - 2026-06-27 (Kotlin)

### Changed
- `Ashid.normalize()` now preserves the **minimal form** for `time === 0` +
  prefix inputs, matching TypeScript 1.7.0. `normalize("user_0")` returns
  `"user_0"` (not `"user_00000000000000"`); `normalize("user_c1s")` returns
  `"user_c1s"` (not `"user_00000000000c1s"`). The 1.6.0-equivalent
  always-padded form still parses correctly and collapses to minimal on
  normalize — values survive. `normalize()` of a v1 input with `time > 0`,
  any ashid4 input, or the no-prefix form is unchanged.
- `buildBase()` now branches on `(hasPrefix && !padded && n1 == 0uL)` to
  emit the minimal form, mirroring the TS 1.7.0 builder. `Ashid.create()`
  was already emitting minimal form; only the `normalize()` path needed
  alignment.

### Tests
- Added 9 `AshIdTest` cases pinning minimal-form preservation
  (`user_0`, `user_1`, `user_z`, `user_c1s`), the padded → minimal collapse
  (`user_00000000000c1s` → `user_c1s`), uppercase-prefix normalization,
  `create` → `normalize` round-trip, and idempotence.

## [1.2.0] - 2026-06-27 (Python)

### Changed
- `Ashid.normalize()` now preserves the **minimal form** for `time === 0` +
  prefix inputs, matching TypeScript 1.7.0. `normalize('user_0')` returns
  `'user_0'` (not `'user_00000000000000'`); `normalize('user_c1s')` returns
  `'user_c1s'` (not `'user_00000000000c1s'`). The 1.6.0-equivalent
  always-padded form still parses correctly and collapses to minimal on
  normalize — values survive. `normalize()` of a v1 input with `time > 0`,
  any ashid4 input, or the no-prefix form is unchanged.
- `_build_base()` now branches on `(normalized_prefix and not padded and
  n1 == 0)` to emit the minimal form, mirroring the TS 1.7.0 builder.
  `Ashid.create()` was already emitting minimal form; only the
  `normalize()` path needed alignment.

### Tests
- Added 9 `TestNormalize` cases pinning minimal-form preservation
  (`user_0`, `user_1`, `user_z`, `user_c1s`), the padded → minimal collapse
  (`user_00000000000c1s` → `user_c1s`), uppercase-prefix normalization,
  `create` → `normalize` round-trip, and idempotence. Suite goes from 142
  → 151 tests, all passing.

## [1.7.0] - 2026-06-24 (TypeScript)

### Changed
- **Restore minimal-form output for the `time === 0` + prefix corner.** `create('user', 0, 0n)` now emits `'user_0'` again (6 chars); `create('user', 0, 1n)` → `'user_1'`; `create('user', 0, 12345n)` → `'user_c1s'`. Reverses the 1.6.0 "behavior shift" that always padded the random half to 13 chars even when the timestamp half was empty. Values still round-trip via `Ashid.timestamp()` / `Ashid.random()` either way; this change is purely about brevity at the wire. The padded form (`prefix + '0' + 13-char-padded random`) was never the canonical shape — `parse()` has always recognized minimal form via its `baseId.length <= RANDOM_ENCODED_LENGTH` branch (the "pre-1.6.0 minimal form" comment).
- The change makes `create()` the natural way to mint short, sequence-driven ids (e.g. `create('url', 0, BigInt(nextval))` → `'url_AB'`) without needing a separate `createShort` helper. The minimal form parses back to type-1 with `time=0`, so it slots into the existing parse / normalize contract.
- `buildBase` now branches on `(prefix && !padded && n1 === 0n)` to emit the minimal form. All other shapes — `n1 !== 0n` with prefix (random still 13-padded as the parse anchor), `padded=true` (create4), no-prefix (fixed 22 / 26 char base) — are unchanged.

### Tests
- Updated the "timestamp 0, random N" assertions in `test/ashid.test.ts` to lock the minimal-form output. Added a `timestamp 1, random 1 → 'user_10000000000001'` case so the `n1 !== 0n` path stays pinned at 13-char padding for the random half.
- All 155 tests pass.

### Other language ports
The Python, Kotlin, Rust, and Go ports still carry the 1.6.0-equivalent always-padded behavior. Restoring minimal form in those ports is tracked as separate issues — the TypeScript port leads here.

## [1.3.0] - 2026-06-10 (Rust)

### Fixed
- `Ashid::normalize()` corrupted ashid4 inputs whose first long encoded with leading zeros. The previous implementation routed every input through `Self::create()` (timestamp + random), which writes the first component unpadded — dropping the leading zeros of a 13-char-padded ashid4 random component and producing a different value. Closes #8.

### Changed
- `normalize()` now routes through a shared `build_base(prefix, n1, n2, padded)` helper (called with `padded=false`) that mirrors the TypeScript 1.6.0 builder. A v1 input still normalizes to v1 shape; a full-entropy ashid4 input round-trips to ashid4 shape (the first long naturally fills 13 chars). A pathological ashid4 with a small first long collapses to v1 shape — the two longs survive, only the string shape changes. Idempotent: `normalize(normalize(x))? == normalize(x)?`.

### Added
- `create4` 13-char padding lockdown test suite (8 tests) covering `(0, 0)`, `(1, 0)`, Crockford `z`, max u64 in either half, and length invariants across input magnitudes. Mirrors the equivalent suites in TypeScript / Python / Kotlin / Go.

## 2026-06-10 (Go)

### Fixed
- `Normalize()` corrupted ashid4 inputs whose first long encoded with leading zeros. The previous implementation routed every input through `Create()` (timestamp + random), which writes the first component unpadded — dropping the leading zeros of a 13-char-padded ashid4 random component and producing a different value. Closes #6.

### Changed
- `Normalize()` now routes through a shared `buildBase(prefix, n1, n2, padded)` helper (called with `padded=false`) that mirrors the TypeScript 1.6.0 builder. A v1 input still normalizes to v1 shape; a full-entropy ashid4 input round-trips to ashid4 shape (the first long naturally fills 13 chars). A pathological ashid4 with a small first long collapses to v1 shape — the two longs survive, only the string shape changes. Idempotent: `Normalize(Normalize(x)) == Normalize(x)`.

## [1.3.0] - 2026-06-06 (Kotlin)

### Fixed
- `Ashid.normalize()` corrupted ashid4 inputs whose first long encoded with leading zeros. The previous implementation routed every input through `Ashid.create()` (timestamp + random), which writes the first component unpadded — dropping the leading zeros of a 13-char-padded ashid4 random component and producing a different value. Closes #9.

### Changed
- `normalize()` now routes through a shared `buildBase(prefix, n1, n2, padded = false)` helper that mirrors the TypeScript 1.6.0 builder. A v1 input still normalizes to v1 shape; a full-entropy ashid4 input round-trips to ashid4 shape (the first long naturally fills 13 chars). A pathological ashid4 with a small first long collapses to v1 shape — the two longs survive, only the string shape changes. Idempotent: `normalize(normalize(x)) == normalize(x)`.
- Decoded values now flow through `ULong` (consistent with the encoder's unsigned long path); existing `create()`/`create4()` public signatures unchanged.

## [1.1.0] - 2026-06-06 (Python)

### Fixed
- `Ashid.normalize()` corrupted ashid4 inputs whose first long encoded with leading zeros. The previous implementation routed every input through `Ashid.create()` (timestamp + random), which writes the first component unpadded — dropping the leading zeros of a 13-char-padded ashid4 random component and producing a different value. Closes #7.

### Changed
- `normalize()` now routes through a shared `_build_base(prefix, n1, n2, padded=False)` helper that mirrors the TypeScript 1.6.0 builder. A v1 input still normalizes to v1 shape; a full-entropy ashid4 input round-trips to ashid4 shape (the first long naturally fills 13 chars). A pathological ashid4 with a small first long collapses to v1 shape — the two longs survive, only the string shape changes. Idempotent: `normalize(normalize(x)) == normalize(x)`.

## [1.6.0] - 2026-05-25 (TypeScript)

### Added
- `Ashid.create1()` — explicit UUID-v1-equivalent entry point. Thin alias for `Ashid.create()`. Sets the naming pattern for future `createN` siblings (alongside the existing `create4()`).
- `Ashid.create()` (and `create1()` / `create4()`) now accept their numeric arguments as either `number` or `bigint`, matching the existing `randomLong` parameter shape. This lets `normalize()` pass decoded BigInts directly without a lossy `Number()` cast.
- 1000-sample UUID round-trip property test (with and without prefix), plus explicit high-bit-set edge cases — verifies any 128-bit UUID survives `fromUuid` → `toUuid` byte-identically.

### Changed
- The `createN` family now shares a single internal `buildBase(prefix, n1, n2, padded)` builder. `create()` / `create1()` call it with `padded=false` (type-1: timestamp half truncates leading zeros when a prefix is present). `create4()` calls it with `padded=true` (type-4: both halves 13-char padded). This collapses three slightly different implementations into one and makes the type-N pattern uniform for future additions.
- **Behavior shift on the time-0 + prefix corner:** the random half is now always 13-char padded — it's the parse anchor. The pre-1.6.0 minimal-form output (e.g. `'user_0'`, `'user_1'`, `'user_z'` for `create('user', 0, n)`) is gone. `create('user', 0, 0n)` now produces `'user_00000000000000'` (19 chars). The values still round-trip — `Ashid.timestamp()` / `Ashid.random()` extract them correctly — but the string is no longer the maximally-compact form. Pre-1.6.0 minimal-form ids still parse correctly.
- `normalize()` routes through `create()` (type-1) instead of `create4()` (1.5.0 behavior). A v1 input normalizes back to v1 shape; a full-entropy ashid4 input normalizes back to ashid4 shape (its first long naturally fills 13 chars without padding). A pathological ashid4 with a small first long collapses to v1 shape — the two longs survive but the string is no longer byte-identical.
- `Ashid.create()` no longer throws on timestamps exceeding the former `MAX_TIMESTAMP` (Dec 12, 3084). Oversized values auto-route to the 13-char encoding so any non-negative value (up to a full 64-bit BigInt) is representable. The negative-input guard remains.

### Removed
- The `options.noTruncate` argument on `create()` (added earlier in this release cycle but never published). Use `create4()` for the padded-both-halves shape instead.
- Signed-long interop on `create4()` (also added and reverted within this cycle). `create4()` now strictly rejects negative inputs via the encoder. Callers interop'ing with Java `long` / Kotlin `Long` must mask the signed halves to unsigned themselves: `(signedLong & 0xFFFFFFFFFFFFFFFFn)` before calling. The library does not silently reinterpret negatives.

### Other language ports
Go, Python, Rust, and Kotlin still carry the original 1.5.0 normalize gap (tracked as separate issues). The naming pattern established here — `create1`/`create4` as the explicit type-N entry points — applies as cross-language guidance.

## [1.5.0] - 2026-05-24 (TypeScript)

### Fixed
- `Ashid.normalize()` corrupted ashid4 inputs whose first long encoded with leading zeros. The previous implementation routed every input through `create()` (timestamp + random), which writes the first component unpadded — dropping the leading zeros of a 13-char-padded ashid4 random component and producing a different value. Now `normalize()` always re-emits through `create4()` (two padded longs), unifying the canonical output regardless of input format.

### Changed
- **Behavior shift:** `normalize()` on a v1 Ashid (timestamp + random) now emits the canonical 26-char ashid4 form (both longs padded to 13 chars) instead of the original variable-width v1 form. The two long values are preserved intact — extraction via `Ashid.timestamp()` and `Ashid.random()` still works — but the output string is no longer byte-identical to a v1 input. Idempotent: `normalize(normalize(x)) === normalize(x)`. Round-trip identity is preserved for ashid4 inputs (already canonical). Callers relying on `normalize(v1Input) === v1Input` need to update their expectations to "the two longs survive."
- `normalize()` is now branch-free: parse → decode two BigInts → `create4`. The previous fix for ashid4 was a length-discriminator hack; the unified shape removes that.

### Other language ports
Tracked as separate issues — Go, Python, Rust, and Kotlin have the same `normalize()` bug. Java is currently stub-only (README + examples).

## [1.4.0] - 2026-05-12

### Added
- `Ashid.toUuid` / `Ashid.fromUuid` for lossless round-trip between any UUID and an Ashid. UUIDs whose high half fits in 45 bits (realistic UUIDv1 timestamps) map to the standard 22-char base; UUIDv4/v7 and any other UUID with a high half >= 2^45 map to the 26-char ashid4 base. Output is shape-compatible with RFC 4122 8-4-4-4-12 hex but version/variant bits reflect the underlying Ashid bytes.

### Fixed
- `parse()` walked through the entire string as "prefix" when the base happened to be all-alphabetic (e.g. the ashid4 form of an all-FF UUID), leaving an empty base. Now resets prefix length to 0 when no delimiter is found.
- Removed unused locals in the ashid4 entropy test that were breaking `pnpm lint` / CI.

### Changed
- Co-located the TypeScript example under `typescript/examples/` and wired `pnpm run example`.
- Reconciled README / docstring / example length claims to match the actual implementation (prefixes always insert an underscore delimiter).

## [1.0.3] - 2024-12-19

### Changed
- Restructured README: root for project overview, typescript/ for npm API docs
- Updated homepage URL to point to typescript subdirectory

### Added
- Badges (npm version, downloads, bundle size, license, TypeScript)
- Comparison table with uuid, nanoid, cuid2, ulid
- Real-world use cases section
- Full API reference documentation
- "Inspired By" section
- CONTRIBUTING.md

## [1.0.2] - 2024-12-XX

### Changed
- Renamed from NiceId to ashid
- Factory function is now `ashid()` (was `niceid()`)
- Class is now `Ashid` (was `NiceId`) - note: class uses PascalCase
- Package name changed to `ashid`

## [1.0.0] - 2024-11-28

### Changed
- Renamed from NUID to NiceId (now ashid)
- Prefix validation: now allows any length, letters only with optional trailing underscore
- Removed 3-character prefix limit
- Removed built-in prefix constants (project-specific, define your own)

### Added
- Support for trailing underscore in prefixes (e.g., `user_`, `tx_`)

### Added
- Initial TypeScript/JavaScript implementation
- Initial Kotlin/JVM implementation
- Crockford Base32 encoding with lookalike character mapping
- Time-sortable ID generation
- Optional type prefixes
- Zero dependencies (TypeScript)
- Comprehensive test suites for both implementations
- Maven Central publishing configuration
- npm publishing configuration

### Features
- **Double-click selectable**: No separators, no special characters
- **Time-sortable**: Lexicographic sorting matches chronological ordering
- **Type-prefixed**: Self-documenting IDs (user_, tx_, etc.)
- **Crockford Base32**: Case-insensitive with lookalike mapping
- **Compact**: 22 characters base + prefix length
- **Cross-platform**: Works in TypeScript/JavaScript and Kotlin/Java

---

## Version Scheme

This project uses [Semantic Versioning](https://semver.org/):

- **MAJOR** version for incompatible API changes
- **MINOR** version for backwards-compatible functionality additions
- **PATCH** version for backwards-compatible bug fixes
