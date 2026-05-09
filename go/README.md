# ashid (Go)

**Time-sortable unique identifiers with type prefixes.**

```go
ashid.New("user")  // "user_1kbg1jmtt4v3x8k9p2m1n"
ashid.New("tx")    // "tx_1kbg1jmts7h2w5r8q4n3m"
ashid.New()        // "1kbg1jmtt4v3x8k9p2m1n0" (22 chars, fixed width)
```

Pure Go, standard library only (`crypto/rand`, `encoding/binary`, `math/big`, `time`). Full parity with the [TypeScript](../typescript/), [Kotlin](../kotlin/), and [Python](../python/) implementations.

## Installation

```bash
go get github.com/wildeagency/ashid/go
```

## Quick Start

```go
import ashid "github.com/wildeagency/ashid/go"

// Time-sortable IDs with type prefix
userID := ashid.New("user")   // "user_1kbg1jmtt4v3x8k9p2m1n"
shortID := ashid.New("u")     // "u_1kbg1jmtt4v3x8k9p2m1n"
rawID := ashid.New()          // "0000001kbg1jmtt4v3x8k9" (22 chars)

// UUID-v4-style (two random components, no timestamp)
token := ashid.New4("tok")    // "tok_x7k9m2p4q8r1s5t3v6w0y1z3"
secret := ashid.New4()        // "a3b5c7d9e1f2g4h6j8k0m2n4p6"

// Parse and inspect
prefix, _ := ashid.Prefix(userID)         // "user_"
createdMs, _ := ashid.Timestamp(userID)   // int64 ms since epoch
random, _ := ashid.Random(userID)         // *big.Int (full 64-bit precision)

// Validate
ashid.IsValid(userID)        // true
ashid.IsValid("not-an-id")   // false

// Normalize ambiguous characters (I/L→1, O→0, U→V) and lowercase
ashid.Normalize("USER-1KBG1JMTT0000000000000")
// "user_1kbg1jmtt0000000000000"
```

## API Reference

### Top-level functions

```go
func New(prefix ...string) string
```
Create a new time-sortable Ashid. With a prefix the result is variable-length; without one it is a fixed 22-char base. Pass at most one prefix; additional arguments are ignored.

```go
func New4(prefix ...string) string
```
Create a UUID-v4-style Ashid with two padded random components (26-char base, ~106 bits of entropy).

```go
func Create(prefix string, timestampMs int64, randomLong uint64) (string, error)
func Create4(prefix string, random1, random2 uint64) (string, error)
```
Build an Ashid from explicit components. Returns an error if the timestamp is out of range.

```go
func AppendNew(dst []byte, prefix ...string) []byte
func AppendNew4(dst []byte, prefix ...string) []byte
func Append(dst []byte, prefix string, timestampMs int64, randomLong uint64) ([]byte, error)
func Append4(dst []byte, prefix string, random1, random2 uint64) ([]byte, error)
```
Append the encoded ID to `dst` and return the extended slice. These avoid the result-string allocation of `New`/`Create` — useful when batch-generating IDs into a pooled buffer. `MaxIDLen` (64) is a safe stack-buffer capacity for any Ashid.

```go
func Parse(id string) (prefix, encodedTimestamp, encodedRandom string, err error)
func Prefix(id string) (string, error)
func Timestamp(id string) (int64, error)
func Random(id string) (*big.Int, error)
func IsValid(id string) bool
func Normalize(id string) (string, error)
```

`Random` returns `*big.Int` to preserve full precision for IDs whose random component encodes more than 64 bits — for the typical 64-bit case use `r.Uint64()`.

### Encoder

```go
func Encode(n uint64, padded bool) string
func EncodeBigInt(n *big.Int, padded bool) (string, error)
func Decode(s string) (uint64, error)
func DecodeBigInt(s string) (*big.Int, error)
func SecureRandomLong() uint64
func IsValidBase32(s string) bool
```

`Decode` returns an error if the value would overflow `uint64` — use `DecodeBigInt` for arbitrary precision.

## Format

```
[prefix_][timestamp][random]
```

- **Prefix:** alphanumeric, lowercase, trailing `_` auto-added (passed-in `_` or `-` is ignored). Dashes in the prefix are normalized to underscores on parse.
- **Timestamp:** ms since Unix epoch, Crockford Base32. Range: `0` to `35184372088831` (Dec 12, 3084).
- **Random:** 64-bit unsigned, Crockford Base32. Encoded to 13 chars when padded.

| Variant | Format | Example |
| --- | --- | --- |
| `New()` (no prefix) | fixed 22 chars: 9 ts + 13 rand | `1kbg1jmtt4v3x8k9p2m1n0` |
| `New("user")` (prefix, ts > 0) | `prefix_` + variable ts + 13 rand | `user_1kbg1jmtt4v3x8k9p2m1n` |
| `New("user")` (prefix, ts = 0) | `prefix_` + variable rand only | `user_z` |
| `New4()` (no prefix) | fixed 26 chars: 13 + 13 rand | `a3b5c7d9e1f2g4h6j8k0m2n4p6` |
| `New4("tok")` (prefix) | `tok_` + 26 rand | `tok_x7k9m2p4q8r1s5t3v6w0y1z3` |

## Performance

- Iterative encoding using a stack-allocated byte buffer for the `uint64` hot path.
- Pre-built `[256]int8` decode lookup table.
- `*big.Int` only on the slow path (values beyond 64 bits or when full precision is requested explicitly).
- The `Append*` family is allocation-free; the `New`/`Create` family allocates only the result string.

Indicative numbers from `go test -bench=. -benchmem` (Ryzen 9 3900X, Go 1.26):

| Benchmark | Time | Allocs |
| --- | --- | --- |
| `Encode` (uint64 → string, padded) | ~18 ns | 0 |
| `AppendEncode` (uint64 → []byte, padded) | ~15 ns | 0 |
| `Parse` | ~5 ns | 0 |
| `Create` (deterministic, no random/clock) | ~118 ns | 1 (32 B result string) |
| `Append` (deterministic, no random/clock) | ~36 ns | 0 |
| `New` / `AppendNew` | ~580 ns | 0 |
| `SecureRandomLong` | ~530 ns | 0 |

`New` is dominated by `crypto/rand.Read`; the encoding work is sub-50 ns. If you need to generate IDs in a tight loop, `Append` reuses your buffer for zero-allocation output.

## Development

```bash
cd go
go test ./...
go vet ./...
go test -bench=. -benchmem -run=^$ ./...
```

## License

MIT
