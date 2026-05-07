package ashid

import (
	"sort"
	"strings"
	"testing"
	"time"
)

func nowMs() int64 {
	return time.Now().UnixMilli()
}

// ==================== CREATE TESTS ====================

func TestCreate_NoPrefix_Fixed22Chars(t *testing.T) {
	id := New()
	if len(id) != 22 {
		t.Errorf("len = %d, want 22", len(id))
	}
	if !isDigit(id[0]) {
		t.Errorf("first char = %q, want digit", id[0])
	}
}

func TestCreate_WithPrefix_AddsDelimiter(t *testing.T) {
	id := New("user")
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("got %q, want prefix user_", id)
	}
}

func TestCreate_WithTrailingUnderscore_Ignored(t *testing.T) {
	id := New("user_")
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("got %q, want prefix user_", id)
	}
}

func TestCreate_SameIDWithOrWithoutTrailingDelim(t *testing.T) {
	id1, err := Ashid.Create("user", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	id2, err := Ashid.Create("user_", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if id1 != id2 {
		t.Errorf("%q != %q", id1, id2)
	}
}

func TestCreate_TrailingDash_ConvertedToUnderscore(t *testing.T) {
	id, err := Ashid.Create("user-", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("got %q, want prefix user_", id)
	}
}

func TestCreate_EmptyStringMeansNoPrefix(t *testing.T) {
	id := New("")
	if !isDigit(id[0]) {
		t.Errorf("first char = %q, want digit", id[0])
	}
	if len(id) != 22 {
		t.Errorf("len = %d, want 22", len(id))
	}
}

func TestCreate_AlphanumericPrefix(t *testing.T) {
	id, err := Ashid.Create("user1", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(id, "user1_") {
		t.Errorf("got %q, want prefix user1_", id)
	}
}

func TestCreate_StripsNonAlphanumericFromPrefix(t *testing.T) {
	id, err := Ashid.Create("a-b_c", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(id, "abc_") {
		t.Errorf("got %q, want prefix abc_", id)
	}
}

func TestCreate_StripsSpecialCharsFromPrefix(t *testing.T) {
	id, err := Ashid.Create("user!@#$%", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("got %q, want prefix user_", id)
	}
}

func TestCreate_NoPrefixWhenAllStripped(t *testing.T) {
	id, err := Ashid.Create("___", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(id) != 22 {
		t.Errorf("len = %d, want 22", len(id))
	}
}

func TestCreate_NegativeTimestampRaises(t *testing.T) {
	if _, err := Ashid.Create("", -1, 0); err == nil {
		t.Error("expected error for negative timestamp")
	} else if !strings.Contains(err.Error(), "non-negative") {
		t.Errorf("error = %v, expected to contain 'non-negative'", err)
	}
}

func TestCreate_MaxTimestampOK(t *testing.T) {
	if _, err := Ashid.Create("", 35184372088831, 0); err != nil {
		t.Errorf("max timestamp should not error, got %v", err)
	}
}

func TestCreate_AboveMaxTimestampRaises(t *testing.T) {
	if _, err := Ashid.Create("", 35184372088832, 0); err == nil {
		t.Error("expected error above max timestamp")
	} else if !strings.Contains(err.Error(), "must not exceed") {
		t.Errorf("error = %v, expected to contain 'must not exceed'", err)
	}
}

func TestCreate_UniqueIDs(t *testing.T) {
	ids := make(map[string]struct{}, 1000)
	for i := 0; i < 1000; i++ {
		ids[New()] = struct{}{}
	}
	if len(ids) != 1000 {
		t.Errorf("got %d unique, want 1000", len(ids))
	}
}

func TestCreate_UppercasePrefixLowercased(t *testing.T) {
	id, err := Ashid.Create("USER", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("got %q, want prefix user_", id)
	}
	prefix, err := Ashid.Prefix(id)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "user_" {
		t.Errorf("prefix = %q, want user_", prefix)
	}
}

// ==================== FIXED VS VARIABLE FORMAT ====================

func TestFixed_TimestampZeroPaddedTo22(t *testing.T) {
	id, err := Ashid.Create("", 0, 0)
	if err != nil {
		t.Fatal(err)
	}
	if id != "0000000000000000000000" {
		t.Errorf("id = %q, want %q", id, "0000000000000000000000")
	}
	if len(id) != 22 {
		t.Errorf("len = %d, want 22", len(id))
	}
}

func TestVariable_ZeroTimestampZeroRandom(t *testing.T) {
	id, err := Ashid.Create("user", 0, 0)
	if err != nil {
		t.Fatal(err)
	}
	if id != "user_0" {
		t.Errorf("id = %q, want user_0", id)
	}
}

func TestVariable_ZeroTimestampRandom1(t *testing.T) {
	id, err := Ashid.Create("user", 0, 1)
	if err != nil {
		t.Fatal(err)
	}
	if id != "user_1" {
		t.Errorf("id = %q, want user_1", id)
	}
}

func TestVariable_ZeroTimestampRandom31(t *testing.T) {
	id, err := Ashid.Create("user", 0, 31)
	if err != nil {
		t.Fatal(err)
	}
	if id != "user_z" {
		t.Errorf("id = %q, want user_z", id)
	}
}

func TestVariable_Timestamp1Random0(t *testing.T) {
	id, err := Ashid.Create("user", 1, 0)
	if err != nil {
		t.Fatal(err)
	}
	if id != "user_10000000000000" {
		t.Errorf("id = %q, want user_10000000000000", id)
	}
	if len(id) != 19 {
		t.Errorf("len = %d, want 19", len(id))
	}
}

func TestVariable_CurrentTimestampLength(t *testing.T) {
	id, err := Ashid.Create("user", nowMs(), 0)
	if err != nil {
		t.Fatal(err)
	}
	if len(id) != 27 {
		t.Errorf("len = %d, want 27", len(id))
	}
}

func TestVariable_SingleLetterPrefix(t *testing.T) {
	id, err := Ashid.Create("u", 0, 0)
	if err != nil {
		t.Fatal(err)
	}
	if id != "u_0" {
		t.Errorf("id = %q, want u_0", id)
	}
}

// ==================== PARSE TESTS ====================

func TestParse_NoPrefix(t *testing.T) {
	prefix, ts, rand, err := Ashid.Parse("0000000000000000000000")
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "" {
		t.Errorf("prefix = %q, want empty", prefix)
	}
	if ts != "000000000" {
		t.Errorf("ts = %q, want 000000000", ts)
	}
	if rand != "0000000000000" {
		t.Errorf("rand = %q, want 0000000000000", rand)
	}
}

func TestParse_DelimiterPrefix(t *testing.T) {
	prefix, ts, rand, err := Ashid.Parse("user_1kbg1jmtt0000000000000")
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "user_" {
		t.Errorf("prefix = %q, want user_", prefix)
	}
	if ts != "1kbg1jmtt" {
		t.Errorf("ts = %q, want 1kbg1jmtt", ts)
	}
	if rand != "0000000000000" {
		t.Errorf("rand = %q, want 0000000000000", rand)
	}
}

func TestParse_UnderscorePrefixZeroTimestamp(t *testing.T) {
	prefix, ts, rand, err := Ashid.Parse("user_c1s")
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "user_" {
		t.Errorf("prefix = %q, want user_", prefix)
	}
	if ts != "0" {
		t.Errorf("ts = %q, want 0", ts)
	}
	if rand != "c1s" {
		t.Errorf("rand = %q, want c1s", rand)
	}
}

func TestParse_EmptyString(t *testing.T) {
	if _, _, _, err := Ashid.Parse(""); err == nil {
		t.Error("expected error on empty string")
	}
}

func TestParse_PrefixOnly(t *testing.T) {
	if _, _, _, err := Ashid.Parse("user_"); err == nil {
		t.Error("expected error on prefix-only string")
	} else if !strings.Contains(err.Error(), "must have a base ID") {
		t.Errorf("error = %v, expected to mention 'must have a base ID'", err)
	}
}

func TestParse_NonDelimitedTreatedAsNoPrefix(t *testing.T) {
	prefix, ts, rand, err := Ashid.Parse("u1234567890123456789ab")
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "" {
		t.Errorf("prefix = %q, want empty", prefix)
	}
	if ts != "u12345678" {
		t.Errorf("ts = %q, want u12345678", ts)
	}
	if rand != "90123456789ab" {
		t.Errorf("rand = %q, want 90123456789ab", rand)
	}
}

func TestParse_WrongLengthWithoutDelimiter(t *testing.T) {
	if _, _, _, err := Ashid.Parse("abc123"); err == nil {
		t.Error("expected error for wrong length")
	} else if !strings.Contains(err.Error(), "must be 22 or 26 characters") {
		t.Errorf("error = %v, expected to mention '22 or 26 characters'", err)
	}
}

func TestParse_DashAsUnderscore(t *testing.T) {
	prefix, ts, rand, err := Ashid.Parse("user-1kbg1jmtt0000000000000")
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "user_" {
		t.Errorf("prefix = %q, want user_", prefix)
	}
	if ts != "1kbg1jmtt" {
		t.Errorf("ts = %q, want 1kbg1jmtt", ts)
	}
	if rand != "0000000000000" {
		t.Errorf("rand = %q, want 0000000000000", rand)
	}
}

func TestParse_DashAndUnderscoreSameTimestamp(t *testing.T) {
	a, err := Ashid.Timestamp("user-1kbg1jmtt0000000000000")
	if err != nil {
		t.Fatal(err)
	}
	b, err := Ashid.Timestamp("user_1kbg1jmtt0000000000000")
	if err != nil {
		t.Fatal(err)
	}
	if a != b {
		t.Errorf("%d != %d", a, b)
	}
}

// ==================== EXTRACTION TESTS ====================

func TestPrefix_Empty(t *testing.T) {
	id, err := Ashid.Create("", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	prefix, err := Ashid.Prefix(id)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "" {
		t.Errorf("prefix = %q, want empty", prefix)
	}
}

func TestPrefix_WithDelimiter(t *testing.T) {
	id, err := Ashid.Create("user", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	prefix, err := Ashid.Prefix(id)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "user_" {
		t.Errorf("prefix = %q, want user_", prefix)
	}
}

func TestPrefix_SingleLetter(t *testing.T) {
	id, err := Ashid.Create("u", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	prefix, err := Ashid.Prefix(id)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "u_" {
		t.Errorf("prefix = %q, want u_", prefix)
	}
}

func TestTimestamp_Fixed(t *testing.T) {
	const ts = int64(1609459200000)
	id, err := Ashid.Create("", ts, 0)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Timestamp(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != ts {
		t.Errorf("got %d, want %d", got, ts)
	}
}

func TestTimestamp_Variable(t *testing.T) {
	const ts = int64(1609459200000)
	id, err := Ashid.Create("user", ts, 0)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Timestamp(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != ts {
		t.Errorf("got %d, want %d", got, ts)
	}
}

func TestTimestamp_ZeroFixed(t *testing.T) {
	id, err := Ashid.Create("", 0, 12345)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Timestamp(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != 0 {
		t.Errorf("got %d, want 0", got)
	}
}

func TestTimestamp_ZeroVariable(t *testing.T) {
	id, err := Ashid.Create("user", 0, 12345)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Timestamp(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != 0 {
		t.Errorf("got %d, want 0", got)
	}
}

func TestRandom_Fixed(t *testing.T) {
	const r = uint64(123456789)
	id, err := Ashid.Create("", nowMs(), r)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Random(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != r {
		t.Errorf("got %d, want %d", got, r)
	}
}

func TestRandom_Variable(t *testing.T) {
	const r = uint64(123456789)
	id, err := Ashid.Create("user", nowMs(), r)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Random(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != r {
		t.Errorf("got %d, want %d", got, r)
	}
}

func TestRandom_ZeroFixed(t *testing.T) {
	id, err := Ashid.Create("", 1000, 0)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Random(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != 0 {
		t.Errorf("got %d, want 0", got)
	}
}

func TestRandom_VariableTimestampZero(t *testing.T) {
	id, err := Ashid.Create("user", 0, 12345)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Random(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != 12345 {
		t.Errorf("got %d, want 12345", got)
	}
}

func TestRandom_FullEntropy64Bit(t *testing.T) {
	const r = uint64(18446744073709551615) // 2^64 - 1
	id, err := Ashid.Create("user", nowMs(), r)
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Random(id)
	if err != nil {
		t.Fatal(err)
	}
	if got != r {
		t.Errorf("got %d, want %d", got, r)
	}
}

// ==================== NORMALIZE TESTS ====================

func TestNormalize_LowercasesUppercase(t *testing.T) {
	original, err := Ashid.Create("user", 1609459200000, 0)
	if err != nil {
		t.Fatal(err)
	}
	uppercased := strings.ToUpper(original)
	normalized, err := Ashid.Normalize(uppercased)
	if err != nil {
		t.Fatal(err)
	}
	if normalized != original {
		t.Errorf("got %q, want %q", normalized, original)
	}
}

func TestNormalize_AmbiguousCharsRoundTrip(t *testing.T) {
	original, err := Ashid.Create("user", 1609459200000, 1111)
	if err != nil {
		t.Fatal(err)
	}
	prefix, ts, rand, err := Ashid.Parse(original)
	if err != nil {
		t.Fatal(err)
	}
	modifiedTs := strings.ReplaceAll(strings.ReplaceAll(ts, "1", "I"), "0", "O")
	modifiedRand := strings.ReplaceAll(strings.ReplaceAll(rand, "1", "L"), "0", "O")
	modified := prefix + modifiedTs + modifiedRand
	normalized, err := Ashid.Normalize(modified)
	if err != nil {
		t.Fatal(err)
	}
	gotTs, err := Ashid.Timestamp(normalized)
	if err != nil {
		t.Fatal(err)
	}
	gotRand, err := Ashid.Random(normalized)
	if err != nil {
		t.Fatal(err)
	}
	if gotTs != 1609459200000 {
		t.Errorf("ts = %d, want 1609459200000", gotTs)
	}
	if gotRand != 1111 {
		t.Errorf("rand = %d, want 1111", gotRand)
	}
}

func TestNormalize_VariableFormat(t *testing.T) {
	original, err := Ashid.Create("user", 1609459200000, 12345)
	if err != nil {
		t.Fatal(err)
	}
	normalized, err := Ashid.Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatal(err)
	}
	if normalized != original {
		t.Errorf("got %q, want %q", normalized, original)
	}
}

func TestNormalize_FixedFormat(t *testing.T) {
	original, err := Ashid.Create("", 1609459200000, 12345)
	if err != nil {
		t.Fatal(err)
	}
	normalized, err := Ashid.Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatal(err)
	}
	if normalized != original {
		t.Errorf("got %q, want %q", normalized, original)
	}
}

func TestNormalize_DashToUnderscore(t *testing.T) {
	withDash := "user-1kbg1jmtt0000000000000"
	normalized, err := Ashid.Normalize(withDash)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(normalized, "user_") {
		t.Errorf("got %q, want prefix user_", normalized)
	}
	prefix, err := Ashid.Prefix(normalized)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "user_" {
		t.Errorf("prefix = %q, want user_", prefix)
	}
}

func TestNormalize_DashAndUnderscoreSame(t *testing.T) {
	withDash := "user-1kbg1jmtt0000000000000"
	withUnderscore := "user_1kbg1jmtt0000000000000"
	a, err := Ashid.Normalize(withDash)
	if err != nil {
		t.Fatal(err)
	}
	b, err := Ashid.Normalize(withUnderscore)
	if err != nil {
		t.Fatal(err)
	}
	if a != b {
		t.Errorf("%q != %q", a, b)
	}
}

func TestNormalize_Preserve64BitEntropy(t *testing.T) {
	const r = uint64(18446744073709551615)
	original, err := Ashid.Create("user", 1609459200000, r)
	if err != nil {
		t.Fatal(err)
	}
	normalized, err := Ashid.Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatal(err)
	}
	got, err := Ashid.Random(normalized)
	if err != nil {
		t.Fatal(err)
	}
	if got != r {
		t.Errorf("got %d, want %d", got, r)
	}
}

// ==================== ISVALID TESTS ====================

func TestIsValid_Correct(t *testing.T) {
	if !Ashid.IsValid(New()) {
		t.Error("New() should be valid")
	}
	if !Ashid.IsValid(New("u")) {
		t.Error("New(\"u\") should be valid")
	}
	if !Ashid.IsValid(New("user")) {
		t.Error("New(\"user\") should be valid")
	}
}

func TestIsValid_TimestampZero(t *testing.T) {
	for _, prefix := range []string{"", "u", "user"} {
		id, err := Ashid.Create(prefix, 0, 0)
		if err != nil {
			t.Fatal(err)
		}
		if !Ashid.IsValid(id) {
			t.Errorf("IsValid(%q) = false, want true", id)
		}
	}
}

func TestIsValid_EmptyString(t *testing.T) {
	if Ashid.IsValid("") {
		t.Error("IsValid(\"\") should be false")
	}
}

func TestIsValid_InvalidBase32(t *testing.T) {
	if Ashid.IsValid("user_invalid!@#") {
		t.Error("invalid chars should be rejected")
	}
}

func TestIsValid_WrongLengthWithoutDelimiter(t *testing.T) {
	if Ashid.IsValid("abc123") {
		t.Error("wrong length without delimiter should be invalid")
	}
}

func TestIsValid_22CharBaseNoDelimiter(t *testing.T) {
	if !Ashid.IsValid("0000000000000000000000") {
		t.Error("22-char base should be valid")
	}
}

func TestIsValid_VariableShortBase(t *testing.T) {
	if !Ashid.IsValid("user_0") {
		t.Error("user_0 should be valid")
	}
}

// ==================== TIME-SORTABILITY ====================

func TestSortable_NoPrefix(t *testing.T) {
	base := nowMs()
	id1, _ := Ashid.Create("", base, 0)
	id2, _ := Ashid.Create("", base+1000, 0)
	id3, _ := Ashid.Create("", base+2000, 0)

	got := []string{id3, id1, id2}
	sort.Strings(got)
	want := []string{id1, id2, id3}
	for i := range got {
		if got[i] != want[i] {
			t.Errorf("at %d: %q, want %q", i, got[i], want[i])
		}
	}
}

func TestSortable_WithPrefix(t *testing.T) {
	base := nowMs()
	id1, _ := Ashid.Create("user", base, 0)
	id2, _ := Ashid.Create("user", base+1000, 0)
	id3, _ := Ashid.Create("user", base+2000, 0)

	got := []string{id3, id1, id2}
	sort.Strings(got)
	want := []string{id1, id2, id3}
	for i := range got {
		if got[i] != want[i] {
			t.Errorf("at %d: %q, want %q", i, got[i], want[i])
		}
	}
}

func TestSortable_SameTimestampDifferentRandom(t *testing.T) {
	ts := nowMs()
	id1, _ := Ashid.Create("", ts, 1000)
	id2, _ := Ashid.Create("", ts, 2000)

	got := []string{id2, id1}
	sort.Strings(got)
	if got[0] != id1 || got[1] != id2 {
		t.Errorf("order wrong: %v", got)
	}
}

// ==================== CONVENIENCE FUNCTIONS ====================

func TestNew_Convenience(t *testing.T) {
	id := New()
	if !Ashid.IsValid(id) {
		t.Error("New() should be valid")
	}
}

func TestNew_WithPrefix(t *testing.T) {
	id := New("user")
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("got %q, want prefix user_", id)
	}
}

// ==================== DOUBLE-CLICK SELECTABILITY ====================

func TestSelectability_NoHyphensOrSpaces(t *testing.T) {
	id := New("user")
	if strings.Contains(id, "-") {
		t.Errorf("id contains -: %q", id)
	}
	if strings.Contains(id, " ") {
		t.Errorf("id contains space: %q", id)
	}
}

// ==================== ASHID4 (UUID v4 equivalent) ====================

func TestCreate4_NoPrefix(t *testing.T) {
	id := New4()
	if len(id) != 26 {
		t.Errorf("len = %d, want 26", len(id))
	}
	if !Ashid.IsValid(id) {
		t.Errorf("New4() should be valid: %q", id)
	}
}

func TestCreate4_WithPrefix(t *testing.T) {
	id := New4("tok")
	if !strings.HasPrefix(id, "tok_") {
		t.Errorf("got %q, want prefix tok_", id)
	}
	if len(id) != 30 {
		t.Errorf("len = %d, want 30", len(id))
	}
}

func TestCreate4_TrailingUnderscoreIgnored(t *testing.T) {
	id := New4("tok_")
	if len(id) != 30 {
		t.Errorf("len = %d, want 30", len(id))
	}
	if !strings.HasPrefix(id, "tok_") {
		t.Errorf("got %q, want prefix tok_", id)
	}
}

func TestCreate4_LowercasesPrefix(t *testing.T) {
	id := New4("TOKEN")
	if !strings.HasPrefix(id, "token_") {
		t.Errorf("got %q, want prefix token_", id)
	}
}

func TestCreate4_AlphanumericPrefix(t *testing.T) {
	id := New4("tok1")
	if !strings.HasPrefix(id, "tok1_") {
		t.Errorf("got %q, want prefix tok1_", id)
	}
}

func TestCreate4_StripsSpecialChars(t *testing.T) {
	id := New4("tok!@#")
	if !strings.HasPrefix(id, "tok_") {
		t.Errorf("got %q, want prefix tok_", id)
	}
}

func TestCreate4_Deterministic(t *testing.T) {
	id1, err := Ashid.Create4("tok", 1000, 2000)
	if err != nil {
		t.Fatal(err)
	}
	id2, err := Ashid.Create4("tok", 1000, 2000)
	if err != nil {
		t.Fatal(err)
	}
	if id1 != id2 {
		t.Errorf("%q != %q", id1, id2)
	}
}

func TestCreate4_UniqueIDs(t *testing.T) {
	ids := make(map[string]struct{}, 1000)
	for i := 0; i < 1000; i++ {
		ids[New4()] = struct{}{}
	}
	if len(ids) != 1000 {
		t.Errorf("got %d unique, want 1000", len(ids))
	}
}

func TestCreate4_ParseableAsValid(t *testing.T) {
	id := New4("tok")
	if !Ashid.IsValid(id) {
		t.Errorf("New4(tok) should be valid: %q", id)
	}
	prefix, _, _, err := Ashid.Parse(id)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "tok_" {
		t.Errorf("prefix = %q, want tok_", prefix)
	}
}

func TestCreate4_ZeroPaddedConsistentLength(t *testing.T) {
	id, err := Ashid.Create4("", 1, 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(id) != 26 {
		t.Errorf("len = %d, want 26", len(id))
	}
	if !strings.HasPrefix(id, "000000000000") {
		t.Errorf("expected leading zeros: %q", id)
	}
}

func TestCreate4_FullEntropy(t *testing.T) {
	saw := false
	for i := 0; i < 200; i++ {
		id := New4()
		_, e1, e2, err := Ashid.Parse(id)
		if err != nil {
			t.Fatal(err)
		}
		if len(strings.TrimLeft(e1, "0")) > 11 || len(strings.TrimLeft(e2, "0")) > 11 {
			saw = true
			break
		}
	}
	if !saw {
		t.Error("expected to see 64-bit values in ashid4 components")
	}
}

func TestCreate4_MaxValue(t *testing.T) {
	max := ^uint64(0)
	id, err := Ashid.Create4("tok", max, max)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(id, "tok_") {
		t.Errorf("got %q, want prefix tok_", id)
	}
	if len(id) != 30 {
		t.Errorf("len = %d, want 30", len(id))
	}
	prefix, e1, e2, err := Ashid.Parse(id)
	if err != nil {
		t.Fatal(err)
	}
	if prefix != "tok_" {
		t.Errorf("prefix = %q, want tok_", prefix)
	}
	if len(e1) != 13 || len(e2) != 13 {
		t.Errorf("encoded lengths = %d,%d, want 13,13", len(e1), len(e2))
	}
}

func TestCreate4_PreservesLargeValues(t *testing.T) {
	r1 := ^uint64(0) - 1000
	r2 := ^uint64(0) / 2
	id, err := Ashid.Create4("tok", r1, r2)
	if err != nil {
		t.Fatal(err)
	}
	_, e1, e2, err := Ashid.Parse(id)
	if err != nil {
		t.Fatal(err)
	}
	d1, err := EncoderBase32Crockford.Decode(e1)
	if err != nil {
		t.Fatal(err)
	}
	d2, err := EncoderBase32Crockford.Decode(e2)
	if err != nil {
		t.Fatal(err)
	}
	if d1 != r1 {
		t.Errorf("d1 = %d, want %d", d1, r1)
	}
	if d2 != r2 {
		t.Errorf("d2 = %d, want %d", d2, r2)
	}
}

// ==================== HELPERS ====================

func isDigit(b byte) bool {
	return b >= '0' && b <= '9'
}
