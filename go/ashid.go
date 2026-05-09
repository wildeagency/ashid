package ashid

import (
	"errors"
	"fmt"
	"math/big"
	"strings"
	"time"
)

// MaxTimestamp is the largest timestamp (ms since Unix epoch) that fits in
// 9 Crockford Base32 characters: Dec 12, 3084.
const MaxTimestamp int64 = 35184372088831

const (
	randomEncodedLength    = 13
	timestampEncodedLength = 9
	standardBaseLength     = 22
	ashid4BaseLength       = 26

	// MaxIDLen is enough headroom for any Ashid: longest prefix users typically
	// pick (~32 chars) + 9 timestamp + 13 random + 1 delimiter. Use as the
	// capacity hint for stack-allocated Append buffers.
	MaxIDLen = 64
)

// New returns a time-sortable Ashid. With no argument the result is the
// fixed 22-char base form; with a prefix it returns the variable-length form.
//
// Pass at most one prefix; additional arguments are ignored.
func New(prefix ...string) string {
	var buf [MaxIDLen]byte
	return string(AppendNew(buf[:0], prefix...))
}

// New4 returns an Ashid with two random components (UUID v4 equivalent).
// The base ID is always 26 chars (13 + 13 = ~106 bits of entropy).
//
// Pass at most one prefix; additional arguments are ignored.
func New4(prefix ...string) string {
	var buf [MaxIDLen]byte
	return string(AppendNew4(buf[:0], prefix...))
}

// AppendNew appends a freshly-generated time-sortable Ashid to dst and returns
// the extended slice. Avoids the string allocation of New for callers writing
// into a pooled buffer.
//
// Pass at most one prefix; additional arguments are ignored.
func AppendNew(dst []byte, prefix ...string) []byte {
	p := ""
	if len(prefix) > 0 {
		p = prefix[0]
	}
	out, _ := Append(dst, p, nowMs(), SecureRandomLong())
	return out
}

// AppendNew4 appends a freshly-generated UUID-v4-style Ashid to dst.
//
// Pass at most one prefix; additional arguments are ignored.
func AppendNew4(dst []byte, prefix ...string) []byte {
	p := ""
	if len(prefix) > 0 {
		p = prefix[0]
	}
	out, _ := Append4(dst, p, SecureRandomLong(), SecureRandomLong())
	return out
}

// Create builds an Ashid from explicit components. Use New for the common case.
//
// Format: [prefix_][timestamp][random]
//
// Prefix rules:
//   - Non-alphanumeric characters are stripped from the prefix; the result is
//     lowercased and a trailing "_" is appended automatically.
//   - An empty prefix (after stripping) produces the no-prefix fixed form.
//
// Timestamp must be in [0, MaxTimestamp]. Random is a non-negative uint64.
func Create(prefix string, timestampMs int64, randomLong uint64) (string, error) {
	var buf [MaxIDLen]byte
	out, err := Append(buf[:0], prefix, timestampMs, randomLong)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

// Create4 builds a UUID-v4-style Ashid from explicit random components.
// Both components are encoded with 13-char zero padding for a 26-char base ID.
func Create4(prefix string, random1, random2 uint64) (string, error) {
	var buf [MaxIDLen]byte
	out, err := Append4(buf[:0], prefix, random1, random2)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

// Append builds an Ashid from explicit components and appends it to dst,
// returning the extended slice. Equivalent to Create but avoids the string
// allocation when writing into a pooled buffer.
func Append(dst []byte, prefix string, timestampMs int64, randomLong uint64) ([]byte, error) {
	if timestampMs < 0 {
		return dst, errors.New("ashid timestamp must be non-negative")
	}
	if timestampMs > MaxTimestamp {
		return dst, fmt.Errorf("ashid timestamp must not exceed %d (Dec 12, 3084)", MaxTimestamp)
	}

	prefixStart := len(dst)
	dst = appendNormalizedPrefix(dst, prefix)
	hasPrefix := len(dst) > prefixStart

	if hasPrefix {
		if timestampMs > 0 {
			dst = AppendEncode(dst, uint64(timestampMs), false)
			dst = AppendEncode(dst, randomLong, true)
		} else {
			dst = AppendEncode(dst, randomLong, false)
		}
	} else {
		dst = appendEncodeWidth(dst, uint64(timestampMs), true, timestampEncodedLength)
		dst = AppendEncode(dst, randomLong, true)
	}
	return dst, nil
}

// Append4 builds a UUID-v4-style Ashid from explicit components and appends
// it to dst.
func Append4(dst []byte, prefix string, random1, random2 uint64) ([]byte, error) {
	dst = appendNormalizedPrefix(dst, prefix)
	dst = AppendEncode(dst, random1, true)
	dst = AppendEncode(dst, random2, true)
	return dst, nil
}

// Parse splits an Ashid into its three components: prefix (with trailing "_"
// or empty), encoded timestamp, and encoded random. Dashes in the prefix
// delimiter are normalized to underscores.
func Parse(id string) (prefix, encodedTimestamp, encodedRandom string, err error) {
	if id == "" {
		err = errors.New("invalid ashid: cannot be empty")
		return
	}

	prefixLength := 0
	hasDelimiter := false

	for i := 0; i < len(id); i++ {
		ch := id[i]
		if isAlpha(ch) {
			prefixLength++
		} else if (ch == '_' || ch == '-') && prefixLength > 0 {
			prefixLength++
			hasDelimiter = true
			break
		} else {
			prefixLength = 0
			break
		}
	}

	if hasDelimiter {
		raw := id[:prefixLength]
		if strings.HasSuffix(raw, "-") {
			raw = raw[:len(raw)-1] + "_"
		}
		prefix = raw
	}

	baseID := id[prefixLength:]
	if baseID == "" {
		err = errors.New("invalid ashid: must have a base ID")
		return
	}

	switch {
	case hasDelimiter:
		if len(baseID) <= randomEncodedLength {
			encodedTimestamp = "0"
			encodedRandom = baseID
		} else {
			encodedTimestamp = baseID[:len(baseID)-randomEncodedLength]
			encodedRandom = baseID[len(baseID)-randomEncodedLength:]
		}
	case len(baseID) == ashid4BaseLength:
		encodedTimestamp = baseID[:randomEncodedLength]
		encodedRandom = baseID[randomEncodedLength:]
	case len(baseID) == standardBaseLength:
		encodedTimestamp = baseID[:timestampEncodedLength]
		encodedRandom = baseID[timestampEncodedLength:]
	default:
		err = fmt.Errorf("invalid ashid: base ID must be %d or %d characters without delimiter (got %d)",
			standardBaseLength, ashid4BaseLength, len(baseID))
	}
	return
}

// Prefix returns the prefix (with trailing "_") or "" if none.
func Prefix(id string) (string, error) {
	p, _, _, err := Parse(id)
	return p, err
}

// Timestamp returns the embedded timestamp in milliseconds since Unix epoch.
func Timestamp(id string) (int64, error) {
	_, ts, _, err := Parse(id)
	if err != nil {
		return 0, err
	}
	v, err := Decode(ts)
	if err != nil {
		return 0, err
	}
	return int64(v), nil
}

// Random returns the embedded random component as a *big.Int. Using big.Int
// preserves full precision for IDs whose random component encodes more than
// 64 bits (mirrors the BigInt return in TypeScript and arbitrary-precision int
// in Python).
func Random(id string) (*big.Int, error) {
	_, _, r, err := Parse(id)
	if err != nil {
		return nil, err
	}
	return DecodeBigInt(r)
}

// IsValid reports whether s is a well-formed Ashid.
func IsValid(id string) bool {
	if id == "" {
		return false
	}
	prefix, ts, r, err := Parse(id)
	if err != nil {
		return false
	}
	if prefix != "" && !validPrefix(prefix) {
		return false
	}
	if !IsValidBase32(ts) || !IsValidBase32(r) {
		return false
	}
	return true
}

// Normalize lowercases the prefix and re-encodes the ID through canonical form,
// mapping ambiguous characters (I/L -> 1, O -> 0, U -> V) along the way.
func Normalize(id string) (string, error) {
	prefix, ts, r, err := Parse(id)
	if err != nil {
		return "", err
	}
	timestamp, err := Decode(ts)
	if err != nil {
		return "", err
	}
	random, err := DecodeBigInt(r)
	if err != nil {
		return "", err
	}

	var normalizedPrefix string
	if prefix != "" {
		normalizedPrefix = strings.ToLower(prefix)
	}

	if random.IsUint64() {
		return Create(normalizedPrefix, int64(timestamp), random.Uint64())
	}
	return createBigInt(normalizedPrefix, int64(timestamp), random)
}

// createBigInt mirrors Create but accepts a *big.Int random for full-precision
// round-trips. Used by Normalize for IDs whose random component exceeds 64 bits.
func createBigInt(prefix string, timestampMs int64, randomLong *big.Int) (string, error) {
	if timestampMs < 0 {
		return "", errors.New("ashid timestamp must be non-negative")
	}
	if timestampMs > MaxTimestamp {
		return "", fmt.Errorf("ashid timestamp must not exceed %d (Dec 12, 3084)", MaxTimestamp)
	}
	if randomLong == nil || randomLong.Sign() < 0 {
		return "", errors.New("ashid random value must be non-negative")
	}

	var buf [MaxIDLen]byte
	dst := buf[:0]

	prefixStart := len(dst)
	dst = appendNormalizedPrefix(dst, prefix)
	hasPrefix := len(dst) > prefixStart

	if hasPrefix {
		if timestampMs > 0 {
			dst = AppendEncode(dst, uint64(timestampMs), false)
			out, err := AppendEncodeBigInt(dst, randomLong, true)
			if err != nil {
				return "", err
			}
			dst = out
		} else {
			out, err := AppendEncodeBigInt(dst, randomLong, false)
			if err != nil {
				return "", err
			}
			dst = out
		}
	} else {
		dst = appendEncodeWidth(dst, uint64(timestampMs), true, timestampEncodedLength)
		out, err := AppendEncodeBigInt(dst, randomLong, true)
		if err != nil {
			return "", err
		}
		dst = out
	}
	return string(dst), nil
}

func appendNormalizedPrefix(dst []byte, prefix string) []byte {
	if prefix == "" {
		return dst
	}
	start := len(dst)
	for i := 0; i < len(prefix); i++ {
		ch := prefix[i]
		switch {
		case ch >= 'a' && ch <= 'z':
			dst = append(dst, ch)
		case ch >= 'A' && ch <= 'Z':
			dst = append(dst, ch+32)
		case ch >= '0' && ch <= '9':
			dst = append(dst, ch)
		}
	}
	if len(dst) == start {
		return dst
	}
	return append(dst, '_')
}

func validPrefix(prefix string) bool {
	if !strings.HasSuffix(prefix, "_") {
		return false
	}
	body := prefix[:len(prefix)-1]
	if body == "" {
		return false
	}
	for i := 0; i < len(body); i++ {
		ch := body[i]
		ok := (ch >= 'a' && ch <= 'z') ||
			(ch >= 'A' && ch <= 'Z') ||
			(ch >= '0' && ch <= '9')
		if !ok {
			return false
		}
	}
	return true
}

func isAlpha(ch byte) bool {
	return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
}

func nowMs() int64 {
	return time.Now().UnixMilli()
}
