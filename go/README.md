# ashid (Go)

Time-sortable unique identifiers with optional type prefixes. Go port of the
[ashid](https://github.com/wildeagency/ashid) library.

Pure Go stdlib — no external dependencies.

## Install

```bash
go get github.com/wildeagency/ashid/go
```

```go
import "github.com/wildeagency/ashid/go"
```

## Usage

```go
package main

import (
    "fmt"

    ashid "github.com/wildeagency/ashid/go"
)

func main() {
    id := ashid.New()                  // "1fvszawr42tve3gxvx9900" (22 chars, time-sortable)
    userID := ashid.New("user")        // "user_1fvszawr42tve3gxvx9900"
    token := ashid.New4("tok")         // "tok_x7k9m2p4q8r1s5t3v6w0y1z3" (UUID v4 style)

    // Parse components
    prefix, ts, rnd, _ := ashid.Ashid.Parse("user_1fvszawr42tve3gxvx9900")

    // Extract pieces
    timestamp, _ := ashid.Ashid.Timestamp("user_1fvszawr42tve3gxvx9900") // int64 ms
    random, _ := ashid.Ashid.Random("user_1fvszawr42tve3gxvx9900")       // uint64

    // Validate
    ok := ashid.Ashid.IsValid("user_1fvszawr42tve3gxvx9900")

    // Normalize (lowercase + canonicalize ambiguous chars)
    canon, _ := ashid.Ashid.Normalize("USER-1FVSZAWR42TVE3GXVX9900")

    fmt.Println(id, userID, token, prefix, ts, rnd, timestamp, random, ok, canon)
}
```

## API

### Top-level

```go
func New(prefix ...string) string  // current time + secure random uint64
func New4(prefix ...string) string // two secure random uint64s
```

`prefix` is variadic but only the first value is used; pass nothing for no prefix.

### `Ashid` namespace

```go
Ashid.Create(prefix string, time int64, random uint64) (string, error)
Ashid.Create4(prefix string, random1, random2 uint64) (string, error)
Ashid.Parse(id string) (prefix, encodedTimestamp, encodedRandom string, err error)
Ashid.Prefix(id string) (string, error)
Ashid.Timestamp(id string) (int64, error)
Ashid.Random(id string) (uint64, error)
Ashid.IsValid(id string) bool
Ashid.Normalize(id string) (string, error)
```

`Create` returns an error when `time < 0` or `time > MaxTimestamp`. `Parse` and the extractors return an error on malformed input.

### `EncoderBase32Crockford` namespace

```go
EncoderBase32Crockford.Encode(n uint64, padded bool) string
EncoderBase32Crockford.Decode(s string) (uint64, error)
EncoderBase32Crockford.SecureRandomLong() uint64
EncoderBase32Crockford.IsValid(s string) bool
```

## Format

- Crockford Base32 alphabet: `0123456789abcdefghjkmnpqrstvwxyz`
- Lookalikes mapped on decode: `I`/`L` → `1`, `O` → `0`, `U` → `V`
- `MaxTimestamp`: 35184372088831 (Dec 12, 3084)
- No prefix: fixed 22 chars (9 timestamp + 13 random) for v1, or 26 chars (13 + 13) for v4
- With prefix: variable-length base after `prefix_`; timestamp omitted when 0
- Random uses `crypto/rand` for full 64-bit entropy

## Test

```bash
go test ./...
go test -race ./...
```
