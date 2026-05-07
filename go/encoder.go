// Package ashid provides time-sortable unique identifiers with optional type prefixes.
//
// Ashid produces double-click-selectable IDs (no separators in the base) using
// Crockford Base32 encoding. The standard form encodes a millisecond timestamp
// followed by 64 bits of secure random data. The ashid4 form replaces the
// timestamp with a second random component for UUID-v4-style entropy.
package ashid

import (
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"strings"
)

const alphabet = "0123456789abcdefghjkmnpqrstvwxyz"

// paddedLength is the canonical 0-padded width for a 64-bit Crockford Base32 value.
const paddedLength = 13

// decodeTable maps every byte to its Crockford Base32 value, or -1 if invalid.
// Lookalikes (I, L -> 1; O -> 0; U -> V) are pre-mapped.
var decodeTable [256]int8

func init() {
	for i := range decodeTable {
		decodeTable[i] = -1
	}
	for i := 0; i < len(alphabet); i++ {
		c := alphabet[i]
		decodeTable[c] = int8(i)
		if c >= 'a' && c <= 'z' {
			decodeTable[c-32] = int8(i) // uppercase
		}
	}
	// Lookalikes
	decodeTable['o'] = 0
	decodeTable['O'] = 0
	decodeTable['i'] = 1
	decodeTable['I'] = 1
	decodeTable['l'] = 1
	decodeTable['L'] = 1
	decodeTable['u'] = 27 // same as v
	decodeTable['U'] = 27
}

// encoderBase32Crockford is a stateless namespace exposing the Crockford
// Base32 encoder. Use the package-level EncoderBase32Crockford value.
type encoderBase32Crockford struct{}

// EncoderBase32Crockford is the entry point for Crockford Base32 encoding.
//
// The Crockford alphabet is "0123456789abcdefghjkmnpqrstvwxyz" (32 chars).
// Decoding is case-insensitive and maps lookalikes I/L -> 1, O -> 0, U -> V.
var EncoderBase32Crockford = encoderBase32Crockford{}

// Encode encodes a non-negative 64-bit value to Crockford Base32.
// If padded is true, the result is left-padded with '0' to 13 characters
// (the width of a full 64-bit value).
func (encoderBase32Crockford) Encode(n uint64, padded bool) string {
	if n == 0 {
		if padded {
			return strings.Repeat("0", paddedLength)
		}
		return "0"
	}

	var buf [paddedLength]byte
	i := len(buf)
	for n > 0 {
		i--
		buf[i] = alphabet[n%32]
		n /= 32
	}
	encoded := string(buf[i:])
	if padded && len(encoded) < paddedLength {
		return strings.Repeat("0", paddedLength-len(encoded)) + encoded
	}
	return encoded
}

// Decode decodes a Crockford Base32 string to a uint64. Decoding is
// case-insensitive and accepts the lookalike characters I/L (-> 1),
// O (-> 0), and U (-> V).
//
// Returns an error if the input is empty or contains an invalid character.
func (encoderBase32Crockford) Decode(s string) (uint64, error) {
	if s == "" {
		return 0, errors.New("input string cannot be empty")
	}

	var result uint64
	for i := 0; i < len(s); i++ {
		c := s[i]
		v := decodeTable[c]
		if v < 0 {
			return 0, fmt.Errorf("invalid character in Base32 string: %q", c)
		}
		result = result*32 + uint64(v)
	}
	return result, nil
}

// SecureRandomLong returns a cryptographically secure random 64-bit value
// using crypto/rand. Panics if the OS RNG fails.
func (encoderBase32Crockford) SecureRandomLong() uint64 {
	var b [8]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic("ashid: crypto/rand failure: " + err.Error())
	}
	return binary.BigEndian.Uint64(b[:])
}

// IsValid reports whether s decodes as a non-empty Crockford Base32 string.
func (encoderBase32Crockford) IsValid(s string) bool {
	if s == "" {
		return false
	}
	_, err := EncoderBase32Crockford.Decode(s)
	return err == nil
}
