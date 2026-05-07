package ashid

import (
	"strings"
	"testing"
)

func TestEncoder_Encode_Zero(t *testing.T) {
	if got := EncoderBase32Crockford.Encode(0, false); got != "0" {
		t.Errorf("Encode(0) = %q, want %q", got, "0")
	}
}

func TestEncoder_Encode_SmallNumbers(t *testing.T) {
	cases := []struct {
		in   uint64
		want string
	}{
		{1, "1"},
		{31, "z"},
		{32, "10"},
	}
	for _, c := range cases {
		if got := EncoderBase32Crockford.Encode(c.in, false); got != c.want {
			t.Errorf("Encode(%d) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestEncoder_Encode_LargeNumbers(t *testing.T) {
	cases := []struct {
		in   uint64
		want string
	}{
		{1000, "z8"},
		{123456, "3rj0"},
	}
	for _, c := range cases {
		if got := EncoderBase32Crockford.Encode(c.in, false); got != c.want {
			t.Errorf("Encode(%d) = %q, want %q", c.in, got, c.want)
		}
	}
}

func TestEncoder_Encode_Padded(t *testing.T) {
	got := EncoderBase32Crockford.Encode(123, true)
	if len(got) != 13 {
		t.Errorf("padded length = %d, want 13", len(got))
	}
	if got != "000000000003v" {
		t.Errorf("padded = %q, want %q", got, "000000000003v")
	}
}

func TestEncoder_Encode_NoPadByDefault(t *testing.T) {
	got := EncoderBase32Crockford.Encode(123, false)
	if len(got) >= 13 {
		t.Errorf("unpadded length = %d, want < 13", len(got))
	}
	if got != "3v" {
		t.Errorf("unpadded = %q, want %q", got, "3v")
	}
}

func TestEncoder_Encode_PaddedZero(t *testing.T) {
	got := EncoderBase32Crockford.Encode(0, true)
	if got != "0000000000000" {
		t.Errorf("padded zero = %q, want %q", got, "0000000000000")
	}
}

func TestEncoder_Encode_Max64Bit(t *testing.T) {
	// 2^64 - 1 should encode to "fzzzzzzzzzzzz"
	got := EncoderBase32Crockford.Encode(^uint64(0), false)
	if got != "fzzzzzzzzzzzz" {
		t.Errorf("Encode(max uint64) = %q, want %q", got, "fzzzzzzzzzzzz")
	}
}

func TestEncoder_Decode_Zero(t *testing.T) {
	got, err := EncoderBase32Crockford.Decode("0")
	if err != nil {
		t.Fatal(err)
	}
	if got != 0 {
		t.Errorf("Decode(\"0\") = %d, want 0", got)
	}
}

func TestEncoder_Decode_SmallNumbers(t *testing.T) {
	cases := []struct {
		in   string
		want uint64
	}{
		{"1", 1},
		{"z", 31},
		{"10", 32},
	}
	for _, c := range cases {
		got, err := EncoderBase32Crockford.Decode(c.in)
		if err != nil {
			t.Fatalf("Decode(%q) returned error: %v", c.in, err)
		}
		if got != c.want {
			t.Errorf("Decode(%q) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestEncoder_Decode_LargeNumbers(t *testing.T) {
	cases := []struct {
		in   string
		want uint64
	}{
		{"z8", 1000},
		{"3rj0", 123456},
	}
	for _, c := range cases {
		got, err := EncoderBase32Crockford.Decode(c.in)
		if err != nil {
			t.Fatalf("Decode(%q): %v", c.in, err)
		}
		if got != c.want {
			t.Errorf("Decode(%q) = %d, want %d", c.in, got, c.want)
		}
	}
}

func TestEncoder_Decode_CaseInsensitive(t *testing.T) {
	a, err := EncoderBase32Crockford.Decode("ABC")
	if err != nil {
		t.Fatal(err)
	}
	b, err := EncoderBase32Crockford.Decode("abc")
	if err != nil {
		t.Fatal(err)
	}
	if a != b {
		t.Errorf("ABC=%d != abc=%d", a, b)
	}

	c, err := EncoderBase32Crockford.Decode("XyZ")
	if err != nil {
		t.Fatal(err)
	}
	d, err := EncoderBase32Crockford.Decode("xyz")
	if err != nil {
		t.Fatal(err)
	}
	if c != d {
		t.Errorf("XyZ=%d != xyz=%d", c, d)
	}
}

func TestEncoder_Decode_LookalikeOToZero(t *testing.T) {
	for _, s := range []string{"O", "o", "0"} {
		got, err := EncoderBase32Crockford.Decode(s)
		if err != nil {
			t.Fatalf("Decode(%q): %v", s, err)
		}
		if got != 0 {
			t.Errorf("Decode(%q) = %d, want 0", s, got)
		}
	}
}

func TestEncoder_Decode_LookalikeIAndLToOne(t *testing.T) {
	for _, s := range []string{"I", "i", "L", "l", "1"} {
		got, err := EncoderBase32Crockford.Decode(s)
		if err != nil {
			t.Fatalf("Decode(%q): %v", s, err)
		}
		if got != 1 {
			t.Errorf("Decode(%q) = %d, want 1", s, got)
		}
	}
}

func TestEncoder_Decode_LookalikeUToV(t *testing.T) {
	// U/u maps to same as V/v which is 27
	for _, s := range []string{"U", "u", "V", "v"} {
		got, err := EncoderBase32Crockford.Decode(s)
		if err != nil {
			t.Fatalf("Decode(%q): %v", s, err)
		}
		if got != 27 {
			t.Errorf("Decode(%q) = %d, want 27", s, got)
		}
	}
}

func TestEncoder_Decode_EmptyString(t *testing.T) {
	if _, err := EncoderBase32Crockford.Decode(""); err == nil {
		t.Error("Decode(\"\") should return error")
	}
}

func TestEncoder_Decode_InvalidCharacters(t *testing.T) {
	for _, s := range []string{"abc-def", "abc def", "abc_def"} {
		if _, err := EncoderBase32Crockford.Decode(s); err == nil {
			t.Errorf("Decode(%q) should return error", s)
		}
	}
}

func TestEncoder_Roundtrip(t *testing.T) {
	cases := []uint64{0, 1, 31, 32, 100, 1000, 123456, 1_700_000_000_000}
	for _, n := range cases {
		encoded := EncoderBase32Crockford.Encode(n, false)
		decoded, err := EncoderBase32Crockford.Decode(encoded)
		if err != nil {
			t.Fatalf("Decode(%q): %v", encoded, err)
		}
		if decoded != n {
			t.Errorf("roundtrip %d -> %q -> %d", n, encoded, decoded)
		}
	}
}

func TestEncoder_Roundtrip_Padded(t *testing.T) {
	n := uint64(12345)
	encoded := EncoderBase32Crockford.Encode(n, true)
	decoded, err := EncoderBase32Crockford.Decode(encoded)
	if err != nil {
		t.Fatal(err)
	}
	if decoded != n {
		t.Errorf("padded roundtrip %d -> %q -> %d", n, encoded, decoded)
	}
}

func TestEncoder_Roundtrip_Max64Bit(t *testing.T) {
	cases := []uint64{
		1 << 53,
		1 << 60,
		^uint64(0),                        // max uint64
		18_446_744_073_709_551_615 - 1000, // close to max
		9_223_372_036_854_775_808,         // 2^63
	}
	for _, n := range cases {
		encoded := EncoderBase32Crockford.Encode(n, false)
		decoded, err := EncoderBase32Crockford.Decode(encoded)
		if err != nil {
			t.Fatalf("Decode(%q): %v", encoded, err)
		}
		if decoded != n {
			t.Errorf("64-bit roundtrip %d -> %q -> %d", n, encoded, decoded)
		}
	}
}

func TestEncoder_SecureRandomLong_Differs(t *testing.T) {
	seen := make(map[uint64]struct{}, 100)
	for i := 0; i < 100; i++ {
		seen[EncoderBase32Crockford.SecureRandomLong()] = struct{}{}
	}
	if len(seen) < 90 {
		t.Errorf("expected mostly unique random values, got %d unique in 100", len(seen))
	}
}

func TestEncoder_SecureRandomLong_FullEntropy(t *testing.T) {
	// With 64-bit entropy ~50% of values should exceed 2^53
	threshold := uint64(1) << 53
	seen := false
	for i := 0; i < 100; i++ {
		if EncoderBase32Crockford.SecureRandomLong() > threshold {
			seen = true
			break
		}
	}
	if !seen {
		t.Error("expected to see a 64-bit value > 2^53 within 100 samples")
	}
}

func TestEncoder_SecureRandomLong_ProducesFull13CharEncoding(t *testing.T) {
	saw13 := false
	for i := 0; i < 200; i++ {
		n := EncoderBase32Crockford.SecureRandomLong()
		if len(EncoderBase32Crockford.Encode(n, false)) == 13 {
			saw13 = true
			break
		}
	}
	if !saw13 {
		t.Error("expected to see 13-char encoded output")
	}
}

func TestEncoder_IsValid(t *testing.T) {
	valid := []string{"0", "1", "abc123", "1fvszawr42tve3gxvx9900", "OIL", "oil"}
	for _, s := range valid {
		if !EncoderBase32Crockford.IsValid(s) {
			t.Errorf("IsValid(%q) = false, want true", s)
		}
	}

	invalid := []string{"", "abc-def", "abc def", "abc_def"}
	for _, s := range invalid {
		if EncoderBase32Crockford.IsValid(s) {
			t.Errorf("IsValid(%q) = true, want false", s)
		}
	}
}

func TestEncoder_NoRecursion_CompilesIteratively(t *testing.T) {
	// Smoke test: encoding a 13-char value should not stack-overflow
	// (this is mostly a documentation test - if implementation uses iteration it just works)
	for i := 0; i < 10000; i++ {
		_ = EncoderBase32Crockford.Encode(^uint64(0), false)
	}
}

func TestEncoder_Encode_PaddedDoesNotTruncate(t *testing.T) {
	// A 13-char encoding should not be re-padded shorter
	got := EncoderBase32Crockford.Encode(^uint64(0), true)
	if got != "fzzzzzzzzzzzz" {
		t.Errorf("padded max = %q, want %q", got, "fzzzzzzzzzzzz")
	}
	if !strings.HasPrefix(got, "f") {
		t.Errorf("expected prefix f, got %q", got)
	}
}
