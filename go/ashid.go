package ashid

import (
	"errors"
	"fmt"
	"strings"
	"time"
)

// MaxTimestamp is the largest supported timestamp (Dec 12, 3084).
// Beyond this the timestamp encoding would exceed 9 characters.
const MaxTimestamp int64 = 35184372088831

const (
	randomEncodedLength    = 13
	timestampEncodedLength = 9
	standardBaseLength     = 22
	ashid4BaseLength       = 26
)

// ashidNamespace exposes the Ashid API as a struct namespace. Use the
// package-level Ashid value (e.g. ashid.Ashid.Create("user", t, r)).
type ashidNamespace struct{}

// Ashid is the entry point for creating, parsing and validating ashids.
//
// Format: [prefix_][timestamp][random]
//
// Prefix rules:
//   - Prefix must be alphabetic only (a-z, A-Z) when used with parse/extract;
//     non-alphanumeric characters are stripped on Create.
//   - The "_" delimiter is auto-added; users should not include it.
//   - With prefix: variable-length base ID (no timestamp padding when 0).
//   - Without prefix: fixed 22-character base ID.
//
// Timestamp range: 0 (Unix epoch) to MaxTimestamp (Dec 12, 3084).
var Ashid = ashidNamespace{}

// normalizePrefix strips non-alphanumeric characters, lowercases, and appends
// the "_" delimiter. Returns "" if the prefix is empty after cleaning.
func normalizePrefix(prefix string) string {
	if prefix == "" {
		return ""
	}
	var b strings.Builder
	b.Grow(len(prefix) + 1)
	for i := 0; i < len(prefix); i++ {
		c := prefix[i]
		switch {
		case c >= 'a' && c <= 'z':
			b.WriteByte(c)
		case c >= 'A' && c <= 'Z':
			b.WriteByte(c + 32) // lowercase
		case c >= '0' && c <= '9':
			b.WriteByte(c)
		}
	}
	if b.Len() == 0 {
		return ""
	}
	b.WriteByte('_')
	return b.String()
}

// Create produces an ashid with the given prefix, timestamp (ms since epoch),
// and random component.
//
// Pass an empty prefix to get the fixed 22-character format. Returns an error
// if timestamp is negative or exceeds MaxTimestamp.
func (ashidNamespace) Create(prefix string, t int64, randomLong uint64) (string, error) {
	normalized := normalizePrefix(prefix)

	if t < 0 {
		return "", errors.New("Ashid timestamp must be non-negative")
	}
	if t > MaxTimestamp {
		return "", fmt.Errorf("Ashid timestamp must not exceed %d (Dec 12, 3084)", MaxTimestamp)
	}

	var baseID string
	if normalized != "" {
		// With prefix: variable length (no padding on timestamp when 0)
		var randomEncoded string
		if t > 0 {
			randomEncoded = EncoderBase32Crockford.Encode(randomLong, true)
			baseID = EncoderBase32Crockford.Encode(uint64(t), false) + randomEncoded
		} else {
			randomEncoded = EncoderBase32Crockford.Encode(randomLong, false)
			baseID = randomEncoded
		}
	} else {
		// No prefix: fixed length (pad timestamp to 9, random to 13 = 22 chars)
		timeEncoded := EncoderBase32Crockford.Encode(uint64(t), false)
		if len(timeEncoded) < timestampEncodedLength {
			timeEncoded = strings.Repeat("0", timestampEncodedLength-len(timeEncoded)) + timeEncoded
		}
		randomEncoded := EncoderBase32Crockford.Encode(randomLong, true)
		baseID = timeEncoded + randomEncoded
	}

	return normalized + baseID, nil
}

// Create4 produces a UUID-v4-style ashid with two 64-bit random components
// and consistent 0-padding (26-char base, ~128 bits of entropy).
//
// Unlike Create, Create4 uses two random values instead of a timestamp +
// random, sacrificing time-sortability for maximum unpredictability.
func (ashidNamespace) Create4(prefix string, random1, random2 uint64) (string, error) {
	normalized := normalizePrefix(prefix)
	encoded1 := EncoderBase32Crockford.Encode(random1, true)
	encoded2 := EncoderBase32Crockford.Encode(random2, true)
	return normalized + encoded1 + encoded2, nil
}

// Parse splits an ashid into (prefix, encodedTimestamp, encodedRandom).
//
// The returned prefix includes the trailing "_" (or "" if absent). A dash
// delimiter in the input is normalized to "_". Returns an error for empty
// input, prefix-only strings, or non-delimited strings whose base length
// is not 22 or 26.
func (ashidNamespace) Parse(id string) (prefix, encodedTimestamp, encodedRandom string, err error) {
	if id == "" {
		return "", "", "", errors.New("Invalid Ashid: cannot be empty")
	}

	prefixLength := 0
	hasDelimiter := false
	for i := 0; i < len(id); i++ {
		c := id[i]
		switch {
		case (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'):
			prefixLength++
		case (c == '_' || c == '-') && prefixLength > 0:
			prefixLength++
			hasDelimiter = true
			i = len(id) // break
		default:
			prefixLength = 0
			i = len(id) // break
		}
	}

	if hasDelimiter {
		prefix = id[:prefixLength]
		// Normalize trailing dash to underscore.
		if prefix[len(prefix)-1] == '-' {
			prefix = prefix[:len(prefix)-1] + "_"
		}
	} else {
		prefix = ""
	}
	baseID := id[prefixLength:]

	if baseID == "" {
		return "", "", "", errors.New("Invalid Ashid: must have a base ID")
	}

	if hasDelimiter {
		// Variable-length format
		if len(baseID) <= randomEncodedLength {
			encodedTimestamp = "0"
			encodedRandom = baseID
		} else {
			encodedTimestamp = baseID[:len(baseID)-randomEncodedLength]
			encodedRandom = baseID[len(baseID)-randomEncodedLength:]
		}
	} else if len(baseID) == ashid4BaseLength {
		encodedTimestamp = baseID[:randomEncodedLength]
		encodedRandom = baseID[randomEncodedLength:]
	} else if len(baseID) == standardBaseLength {
		encodedTimestamp = baseID[:timestampEncodedLength]
		encodedRandom = baseID[timestampEncodedLength:]
	} else {
		return "", "", "", fmt.Errorf("Invalid Ashid: base ID must be %d or %d characters without delimiter (got %d)", standardBaseLength, ashid4BaseLength, len(baseID))
	}

	return prefix, encodedTimestamp, encodedRandom, nil
}

// Prefix returns the prefix (including trailing "_") of an ashid, or "" if absent.
func (ashidNamespace) Prefix(id string) (string, error) {
	prefix, _, _, err := Ashid.Parse(id)
	return prefix, err
}

// Timestamp returns the encoded millisecond timestamp from an ashid.
func (ashidNamespace) Timestamp(id string) (int64, error) {
	_, encodedTimestamp, _, err := Ashid.Parse(id)
	if err != nil {
		return 0, err
	}
	v, err := EncoderBase32Crockford.Decode(encodedTimestamp)
	if err != nil {
		return 0, err
	}
	return int64(v), nil
}

// Random returns the random component of an ashid as a uint64.
// Preserves full 64-bit precision.
func (ashidNamespace) Random(id string) (uint64, error) {
	_, _, encodedRandom, err := Ashid.Parse(id)
	if err != nil {
		return 0, err
	}
	return EncoderBase32Crockford.Decode(encodedRandom)
}

// IsValid reports whether s is a structurally and lexically valid ashid.
func (ashidNamespace) IsValid(id string) bool {
	if id == "" {
		return false
	}
	prefix, ts, rnd, err := Ashid.Parse(id)
	if err != nil {
		return false
	}
	if prefix != "" {
		// Must be alphanumeric followed by trailing "_".
		if prefix[len(prefix)-1] != '_' {
			return false
		}
		body := prefix[:len(prefix)-1]
		if body == "" {
			return false
		}
		for i := 0; i < len(body); i++ {
			c := body[i]
			if !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
				return false
			}
		}
	}
	if _, err := EncoderBase32Crockford.Decode(ts); err != nil {
		return false
	}
	if _, err := EncoderBase32Crockford.Decode(rnd); err != nil {
		return false
	}
	return true
}

// Normalize lowercases an ashid and converts ambiguous characters
// (I/L -> 1, O -> 0, U -> V) to their canonical Crockford form.
func (ashidNamespace) Normalize(id string) (string, error) {
	prefix, encodedTimestamp, encodedRandom, err := Ashid.Parse(id)
	if err != nil {
		return "", err
	}
	ts, err := EncoderBase32Crockford.Decode(encodedTimestamp)
	if err != nil {
		return "", err
	}
	rnd, err := EncoderBase32Crockford.Decode(encodedRandom)
	if err != nil {
		return "", err
	}
	normalizedPrefix := strings.ToLower(prefix)
	return Ashid.Create(normalizedPrefix, int64(ts), rnd)
}

// New returns a new ashid using the current time and a secure random uint64.
// Pass at most one prefix (additional values are ignored).
func New(prefix ...string) string {
	p := ""
	if len(prefix) > 0 {
		p = prefix[0]
	}
	id, err := Ashid.Create(p, time.Now().UnixMilli(), EncoderBase32Crockford.SecureRandomLong())
	if err != nil {
		// With current time and a uint64 random, Create cannot fail unless
		// the system clock is past Dec 12, 3084 — at which point we panic
		// because there is no sensible recovery.
		panic("ashid: New failed: " + err.Error())
	}
	return id
}

// New4 returns a new ashid4 (UUID-v4-equivalent) using two secure random uint64s.
// Pass at most one prefix (additional values are ignored).
func New4(prefix ...string) string {
	p := ""
	if len(prefix) > 0 {
		p = prefix[0]
	}
	id, err := Ashid.Create4(p, EncoderBase32Crockford.SecureRandomLong(), EncoderBase32Crockford.SecureRandomLong())
	if err != nil {
		panic("ashid: New4 failed: " + err.Error())
	}
	return id
}
