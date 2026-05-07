// Package ashid generates time-sortable unique identifiers with optional type prefixes.
//
// Ashid IDs are encoded with Crockford Base32, which provides:
//   - Case-insensitive encoding (0-9, a-z)
//   - Lookalike character mapping (I/L -> 1, O -> 0, U -> V)
//   - No special characters
//   - Human-readable and error-resistant output
//
// Alphabet: 0123456789abcdefghjkmnpqrstvwxyz (32 characters).
// Excluded: i, l, o, u (mapped to lookalikes during decode).
package ashid

import (
	"crypto/rand"
	"encoding/binary"
	"errors"
	"fmt"
	"math/big"
)

const (
	encoderAlphabet = "0123456789abcdefghjkmnpqrstvwxyz"
	encoderPadLen   = 13
)

var encoderDecodeTable = buildDecodeTable()

func buildDecodeTable() [256]int8 {
	var table [256]int8
	for i := range table {
		table[i] = -1
	}
	for i, c := range encoderAlphabet {
		table[c] = int8(i)
		// Uppercase variant
		if c >= 'a' && c <= 'z' {
			table[c-32] = int8(i)
		}
	}
	// Lookalikes
	table['o'] = 0
	table['O'] = 0
	table['i'] = 1
	table['I'] = 1
	table['l'] = 1
	table['L'] = 1
	table['u'] = 27
	table['U'] = 27
	return table
}

// AppendEncode appends the Crockford Base32 encoding of n to dst and returns
// the extended slice. If padded is true the encoded portion is left-padded
// with '0' to 13 characters.
//
// This is the primitive on which Encode is built; use it to avoid the string
// allocation when writing into a pooled buffer.
func AppendEncode(dst []byte, n uint64, padded bool) []byte {
	return appendEncodeWidth(dst, n, padded, encoderPadLen)
}

// appendEncodeWidth encodes n into dst with a minimum width. If width > 0 the
// result is left-padded with '0' to that width; if width == 0 and n == 0 the
// result is a single '0'.
func appendEncodeWidth(dst []byte, n uint64, padded bool, padWidth int) []byte {
	minWidth := 0
	if padded {
		minWidth = padWidth
	} else if n == 0 {
		return append(dst, '0')
	}

	var buf [encoderPadLen]byte
	pos := len(buf)
	for n > 0 {
		pos--
		buf[pos] = encoderAlphabet[n%32]
		n /= 32
	}
	encoded := buf[pos:]
	for i := len(encoded); i < minWidth; i++ {
		dst = append(dst, '0')
	}
	return append(dst, encoded...)
}

// Encode encodes a non-negative uint64 into a Crockford Base32 string.
// If padded is true the result is left-padded with '0' to 13 characters.
func Encode(n uint64, padded bool) string {
	var buf [encoderPadLen]byte
	return string(AppendEncode(buf[:0], n, padded))
}

// AppendEncodeBigInt appends the Crockford Base32 encoding of n (non-negative,
// arbitrary precision) to dst and returns the extended slice.
func AppendEncodeBigInt(dst []byte, n *big.Int, padded bool) ([]byte, error) {
	if n == nil {
		return dst, errors.New("input must not be nil")
	}
	if n.Sign() < 0 {
		return dst, errors.New("input must be a non-negative integer")
	}
	if n.Sign() == 0 {
		if padded {
			return append(dst, "0000000000000"...), nil
		}
		return append(dst, '0'), nil
	}

	val := new(big.Int).Set(n)
	mod := new(big.Int)
	thirtyTwo := big.NewInt(32)

	tmp := make([]byte, 0, 16)
	for val.Sign() > 0 {
		val.QuoRem(val, thirtyTwo, mod)
		tmp = append(tmp, encoderAlphabet[mod.Int64()])
	}
	for i, j := 0, len(tmp)-1; i < j; i, j = i+1, j-1 {
		tmp[i], tmp[j] = tmp[j], tmp[i]
	}

	for i := len(tmp); i < encoderPadLen && padded; i++ {
		dst = append(dst, '0')
	}
	return append(dst, tmp...), nil
}

// EncodeBigInt encodes a non-negative *big.Int into a Crockford Base32 string,
// supporting values beyond 64 bits. If padded is true the result is left-padded
// with '0' to 13 characters (values that already exceed 13 chars are not truncated).
func EncodeBigInt(n *big.Int, padded bool) (string, error) {
	out, err := AppendEncodeBigInt(nil, n, padded)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

// Decode decodes a Crockford Base32 string into a uint64.
// Returns an error on empty input, invalid characters, or values that overflow uint64.
func Decode(s string) (uint64, error) {
	if len(s) == 0 {
		return 0, errors.New("input string cannot be empty")
	}

	var result uint64
	for i := 0; i < len(s); i++ {
		c := s[i]
		v := encoderDecodeTable[c]
		if v < 0 {
			return 0, fmt.Errorf("invalid character in Base32 string: %q", string(c))
		}
		// Detect overflow: if result > (math.MaxUint64 - v) / 32, it overflows.
		if result > (^uint64(0)-uint64(v))/32 {
			return 0, fmt.Errorf("Base32 value exceeds uint64 range: %q", s)
		}
		result = result*32 + uint64(v)
	}
	return result, nil
}

// DecodeBigInt decodes a Crockford Base32 string into a *big.Int, preserving
// full precision for values beyond 64 bits.
func DecodeBigInt(s string) (*big.Int, error) {
	if len(s) == 0 {
		return nil, errors.New("input string cannot be empty")
	}

	result := new(big.Int)
	thirtyTwo := big.NewInt(32)
	tmp := new(big.Int)
	for i := 0; i < len(s); i++ {
		c := s[i]
		v := encoderDecodeTable[c]
		if v < 0 {
			return nil, fmt.Errorf("invalid character in Base32 string: %q", string(c))
		}
		result.Mul(result, thirtyTwo)
		tmp.SetInt64(int64(v))
		result.Add(result, tmp)
	}
	return result, nil
}

// SecureRandomLong returns a cryptographically secure unsigned 64-bit value.
// Panics only if the crypto/rand source fails, which on supported platforms
// indicates a serious system error.
func SecureRandomLong() uint64 {
	var buf [8]byte
	if _, err := rand.Read(buf[:]); err != nil {
		panic(fmt.Errorf("ashid: secure random source failed: %w", err))
	}
	return binary.BigEndian.Uint64(buf[:])
}

// IsValidBase32 reports whether s is a non-empty valid Crockford Base32 string
// (ignoring uint64 overflow — uses arbitrary precision for validation).
func IsValidBase32(s string) bool {
	if len(s) == 0 {
		return false
	}
	for i := 0; i < len(s); i++ {
		if encoderDecodeTable[s[i]] < 0 {
			return false
		}
	}
	return true
}
