package ashid

import (
	"math/big"
	"strings"
	"testing"
	"time"
)

func TestEncode_Zero(t *testing.T) {
	if got := Encode(0, false); got != "0" {
		t.Fatalf("Encode(0) = %q, want %q", got, "0")
	}
}

func TestEncode_Small(t *testing.T) {
	cases := map[uint64]string{
		1:  "1",
		31: "z",
		32: "10",
	}
	for n, want := range cases {
		if got := Encode(n, false); got != want {
			t.Errorf("Encode(%d) = %q, want %q", n, got, want)
		}
	}
}

func TestEncode_Large(t *testing.T) {
	if got := Encode(1000, false); got != "z8" {
		t.Errorf("Encode(1000) = %q, want %q", got, "z8")
	}
	if got := Encode(123456, false); got != "3rj0" {
		t.Errorf("Encode(123456) = %q, want %q", got, "3rj0")
	}
}

func TestEncode_Padded(t *testing.T) {
	got := Encode(123, true)
	if len(got) != 13 {
		t.Errorf("padded length = %d, want 13", len(got))
	}
	if got != "000000000003v" {
		t.Errorf("Encode(123, padded) = %q, want %q", got, "000000000003v")
	}
}

func TestEncode_UnpaddedDefault(t *testing.T) {
	got := Encode(123, false)
	if got != "3v" {
		t.Errorf("Encode(123) = %q, want %q", got, "3v")
	}
}

func TestEncode_TimestampLike(t *testing.T) {
	ts := uint64(time.Now().UnixMilli())
	got := Encode(ts, false)
	if got == "" {
		t.Fatal("expected non-empty encoding for timestamp")
	}
}

func TestEncode_FullUint64(t *testing.T) {
	got := Encode(^uint64(0), false)
	if got != "fzzzzzzzzzzzz" {
		t.Errorf("Encode(MaxUint64) = %q, want %q", got, "fzzzzzzzzzzzz")
	}
}

func TestDecode_Zero(t *testing.T) {
	v, err := Decode("0")
	if err != nil || v != 0 {
		t.Fatalf("Decode(\"0\") = (%d, %v), want (0, nil)", v, err)
	}
}

func TestDecode_Small(t *testing.T) {
	cases := map[string]uint64{
		"1":  1,
		"z":  31,
		"10": 32,
	}
	for s, want := range cases {
		v, err := Decode(s)
		if err != nil {
			t.Errorf("Decode(%q) error: %v", s, err)
			continue
		}
		if v != want {
			t.Errorf("Decode(%q) = %d, want %d", s, v, want)
		}
	}
}

func TestDecode_Large(t *testing.T) {
	v, _ := Decode("z8")
	if v != 1000 {
		t.Errorf("Decode(\"z8\") = %d, want 1000", v)
	}
	v, _ = Decode("3rj0")
	if v != 123456 {
		t.Errorf("Decode(\"3rj0\") = %d, want 123456", v)
	}
}

func TestDecode_CaseInsensitive(t *testing.T) {
	a, _ := Decode("ABC")
	b, _ := Decode("abc")
	if a != b {
		t.Errorf("case-insensitive decode mismatch: %d vs %d", a, b)
	}
	a, _ = Decode("XyZ")
	b, _ = Decode("xyz")
	if a != b {
		t.Errorf("case-insensitive decode mismatch: %d vs %d", a, b)
	}
}

func TestDecode_LookalikeOToZero(t *testing.T) {
	cases := []string{"O", "o", "0"}
	for _, s := range cases {
		v, err := Decode(s)
		if err != nil || v != 0 {
			t.Errorf("Decode(%q) = (%d, %v), want (0, nil)", s, v, err)
		}
	}
}

func TestDecode_LookalikeILToOne(t *testing.T) {
	cases := []string{"I", "i", "L", "l", "1"}
	for _, s := range cases {
		v, err := Decode(s)
		if err != nil || v != 1 {
			t.Errorf("Decode(%q) = (%d, %v), want (1, nil)", s, v, err)
		}
	}
}

func TestDecode_LookalikeUToV(t *testing.T) {
	for _, s := range []string{"u", "U", "v", "V"} {
		v, err := Decode(s)
		if err != nil || v != 27 {
			t.Errorf("Decode(%q) = (%d, %v), want (27, nil)", s, v, err)
		}
	}
}

func TestDecode_EmptyError(t *testing.T) {
	if _, err := Decode(""); err == nil {
		t.Fatal("expected error for empty input")
	}
}

func TestDecode_InvalidChars(t *testing.T) {
	for _, s := range []string{"abc-def", "abc def", "abc_def"} {
		if _, err := Decode(s); err == nil {
			t.Errorf("expected error decoding %q", s)
		}
	}
}

func TestRoundtrip_Int(t *testing.T) {
	cases := []uint64{0, 1, 31, 32, 100, 1000, 123456, uint64(time.Now().UnixMilli())}
	for _, n := range cases {
		v, err := Decode(Encode(n, false))
		if err != nil {
			t.Errorf("decode error for %d: %v", n, err)
			continue
		}
		if v != n {
			t.Errorf("roundtrip mismatch: %d != %d", v, n)
		}
	}
}

func TestRoundtrip_Padded(t *testing.T) {
	n := uint64(12345)
	encoded := Encode(n, true)
	v, err := Decode(encoded)
	if err != nil {
		t.Fatalf("decode error: %v", err)
	}
	if v != n {
		t.Errorf("padded roundtrip mismatch: %d != %d", v, n)
	}
}

func TestEncodeBigInt_Zero(t *testing.T) {
	got, err := EncodeBigInt(big.NewInt(0), false)
	if err != nil || got != "0" {
		t.Fatalf("EncodeBigInt(0) = (%q, %v), want (%q, nil)", got, err, "0")
	}
}

func TestEncodeBigInt_Small(t *testing.T) {
	cases := map[int64]string{
		1:  "1",
		31: "z",
		32: "10",
	}
	for n, want := range cases {
		got, err := EncodeBigInt(big.NewInt(n), false)
		if err != nil {
			t.Errorf("EncodeBigInt(%d) error: %v", n, err)
			continue
		}
		if got != want {
			t.Errorf("EncodeBigInt(%d) = %q, want %q", n, got, want)
		}
	}
}

func TestEncodeBigInt_BeyondSafeInt(t *testing.T) {
	// 2^53 + 1 — beyond JS safe int but well within int64
	beyond := big.NewInt(1)
	beyond.Lsh(beyond, 53)
	beyond.Add(beyond, big.NewInt(1))
	got, err := EncodeBigInt(beyond, false)
	if err != nil {
		t.Fatalf("EncodeBigInt error: %v", err)
	}
	if len(got) <= 10 {
		t.Errorf("expected encoding length > 10, got %d (%q)", len(got), got)
	}
}

func TestEncodeBigInt_Full64Bit(t *testing.T) {
	max64 := new(big.Int).SetUint64(^uint64(0))
	got, err := EncodeBigInt(max64, false)
	if err != nil {
		t.Fatalf("EncodeBigInt error: %v", err)
	}
	if got != "fzzzzzzzzzzzz" {
		t.Errorf("EncodeBigInt(MaxUint64) = %q, want %q", got, "fzzzzzzzzzzzz")
	}
}

func TestEncodeBigInt_Padded(t *testing.T) {
	got, err := EncodeBigInt(big.NewInt(123), true)
	if err != nil {
		t.Fatalf("EncodeBigInt error: %v", err)
	}
	if got != "000000000003v" {
		t.Errorf("EncodeBigInt(123, padded) = %q, want %q", got, "000000000003v")
	}
}

func TestEncodeBigInt_NegativeError(t *testing.T) {
	if _, err := EncodeBigInt(big.NewInt(-1), false); err == nil {
		t.Fatal("expected error for negative input")
	}
}

func TestDecodeBigInt_FullUint64(t *testing.T) {
	max64 := new(big.Int).SetUint64(^uint64(0))
	encoded, _ := EncodeBigInt(max64, false)
	got, err := DecodeBigInt(encoded)
	if err != nil {
		t.Fatalf("DecodeBigInt error: %v", err)
	}
	if got.Cmp(max64) != 0 {
		t.Errorf("DecodeBigInt mismatch: %s != %s", got, max64)
	}
}

func TestDecodeBigInt_BeyondSafe(t *testing.T) {
	beyond := big.NewInt(1)
	beyond.Lsh(beyond, 53)
	beyond.Add(beyond, big.NewInt(1))
	encoded, _ := EncodeBigInt(beyond, false)
	got, _ := DecodeBigInt(encoded)
	if got.Cmp(beyond) != 0 {
		t.Errorf("roundtrip mismatch: %s != %s", got, beyond)
	}
}

func TestDecodeBigInt_CaseInsensitive(t *testing.T) {
	a, _ := DecodeBigInt("ABC")
	b, _ := DecodeBigInt("abc")
	if a.Cmp(b) != 0 {
		t.Errorf("case-insensitive decode mismatch: %s vs %s", a, b)
	}
}

func TestSecureRandomLong_Distinct(t *testing.T) {
	seen := make(map[uint64]struct{}, 100)
	for i := 0; i < 100; i++ {
		seen[SecureRandomLong()] = struct{}{}
	}
	if len(seen) < 90 {
		t.Errorf("expected mostly-unique random values; got %d distinct out of 100", len(seen))
	}
}

func TestSecureRandomLong_Full64BitEntropy(t *testing.T) {
	// ~50% should exceed 2^53 with uniform 64-bit distribution
	threshold := uint64(1) << 53
	saw := false
	for i := 0; i < 100; i++ {
		if SecureRandomLong() > threshold {
			saw = true
			break
		}
	}
	if !saw {
		t.Error("expected at least one value > 2^53 in 100 samples")
	}
}

func TestSecureRandomLong_Produces13Chars(t *testing.T) {
	saw := false
	for i := 0; i < 100; i++ {
		if len(Encode(SecureRandomLong(), false)) == 13 {
			saw = true
			break
		}
	}
	if !saw {
		t.Error("expected at least one 13-char encoding in 100 samples")
	}
}

func TestIsValidBase32(t *testing.T) {
	valid := []string{"0", "1", "abc123", "1fvszawr42tve3gxvx9900", "OIL", "oil"}
	for _, s := range valid {
		if !IsValidBase32(s) {
			t.Errorf("expected %q to be valid", s)
		}
	}
	invalid := []string{"", "abc-def", "abc def", "abc_def"}
	for _, s := range invalid {
		if IsValidBase32(s) {
			t.Errorf("expected %q to be invalid", s)
		}
	}
}

func TestEncode_NoLookalikeChars(t *testing.T) {
	// Encoded output must never contain i, l, o, or u — these only appear on decode.
	for n := uint64(0); n < 32; n++ {
		got := Encode(n, false)
		if strings.ContainsAny(got, "ilou") {
			t.Errorf("Encode(%d) = %q contains a lookalike char", n, got)
		}
	}
}
