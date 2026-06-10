package ashid

import (
	"math/big"
	"regexp"
	"sort"
	"strings"
	"testing"
	"time"
)

func nowMsForTest() int64 { return time.Now().UnixMilli() }

func mustCreate(t *testing.T, prefix string, ts int64, r uint64) string {
	t.Helper()
	id, err := Create(prefix, ts, r)
	if err != nil {
		t.Fatalf("Create(%q, %d, %d) error: %v", prefix, ts, r, err)
	}
	return id
}

// ---- Create ----

func TestCreate_NoPrefixFixed22(t *testing.T) {
	id := New()
	if len(id) != 22 {
		t.Errorf("New() length = %d, want 22", len(id))
	}
	if id[0] < '0' || id[0] > '9' {
		t.Errorf("expected leading digit, got %q", id[:1])
	}
}

func TestCreate_PrefixAddsDelimiter(t *testing.T) {
	id := New("user")
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("expected user_ prefix, got %q", id)
	}
}

func TestCreate_TrailingUnderscoreIgnored(t *testing.T) {
	id := New("user_")
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("expected user_ prefix, got %q", id)
	}
}

func TestCreate_SameWithOrWithoutTrailingDelim(t *testing.T) {
	a := mustCreate(t, "user", 1000, 0)
	b := mustCreate(t, "user_", 1000, 0)
	if a != b {
		t.Errorf("expected matching IDs, got %q vs %q", a, b)
	}
}

func TestCreate_EmptyPrefixMeansNone(t *testing.T) {
	id, err := Create("", nowMsForTest(), SecureRandomLong())
	if err != nil {
		t.Fatalf("Create error: %v", err)
	}
	if len(id) != 22 {
		t.Errorf("expected 22 chars, got %d (%q)", len(id), id)
	}
}

func TestCreate_AlphanumericPrefix(t *testing.T) {
	id := mustCreate(t, "user1", 1000, 0)
	if !strings.HasPrefix(id, "user1_") {
		t.Errorf("expected user1_ prefix, got %q", id)
	}
}

func TestCreate_StripsNonAlphanumeric(t *testing.T) {
	id := mustCreate(t, "a-b_c", 1000, 0)
	if !strings.HasPrefix(id, "abc_") {
		t.Errorf("expected abc_ prefix, got %q", id)
	}
}

func TestCreate_StripsSpecialChars(t *testing.T) {
	id := mustCreate(t, "user!@#$%", 1000, 0)
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("expected user_ prefix, got %q", id)
	}
}

func TestCreate_AllStrippedNoPrefix(t *testing.T) {
	id := mustCreate(t, "___", 1000, 0)
	if len(id) != 22 {
		t.Errorf("expected fixed 22 chars when prefix all stripped, got %d (%q)", len(id), id)
	}
}

func TestCreate_NegativeTimestampError(t *testing.T) {
	if _, err := Create("", -1, 0); err == nil {
		t.Fatal("expected error for negative timestamp")
	}
}

func TestCreate_MaxTimestampOK(t *testing.T) {
	if _, err := Create("", MaxTimestamp, 0); err != nil {
		t.Errorf("max timestamp should be allowed, got error: %v", err)
	}
}

func TestCreate_AboveMaxError(t *testing.T) {
	if _, err := Create("", MaxTimestamp+1, 0); err == nil {
		t.Fatal("expected error for timestamp above max")
	}
}

func TestCreate_UniqueIDs(t *testing.T) {
	seen := make(map[string]struct{}, 1000)
	for i := 0; i < 1000; i++ {
		seen[New()] = struct{}{}
	}
	if len(seen) != 1000 {
		t.Errorf("expected 1000 unique IDs, got %d", len(seen))
	}
}

func TestCreate_UppercasePrefixLowercased(t *testing.T) {
	id := mustCreate(t, "USER", 1000, 0)
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("expected user_ prefix (lowercased), got %q", id)
	}
	p, err := Prefix(id)
	if err != nil {
		t.Fatalf("Prefix error: %v", err)
	}
	if p != "user_" {
		t.Errorf("Prefix = %q, want %q", p, "user_")
	}
}

// ---- Fixed format ----

func TestFixed_TimestampZeroPaddedTo22(t *testing.T) {
	id := mustCreate(t, "", 0, 0)
	if id != "0000000000000000000000" {
		t.Errorf("got %q, want %q", id, "0000000000000000000000")
	}
}

func TestFixed_CurrentTimestamp22Chars(t *testing.T) {
	if l := len(New()); l != 22 {
		t.Errorf("expected length 22, got %d", l)
	}
}

// ---- Variable format ----

func TestVariable_ZeroTsZeroRandomMinimal(t *testing.T) {
	id := mustCreate(t, "user", 0, 0)
	if id != "user_0" {
		t.Errorf("got %q, want %q", id, "user_0")
	}
}

func TestVariable_ZeroTsRandom1(t *testing.T) {
	id := mustCreate(t, "user", 0, 1)
	if id != "user_1" {
		t.Errorf("got %q, want %q", id, "user_1")
	}
}

func TestVariable_ZeroTsRandom31(t *testing.T) {
	id := mustCreate(t, "user", 0, 31)
	if id != "user_z" {
		t.Errorf("got %q, want %q", id, "user_z")
	}
}

func TestVariable_Ts1Random0(t *testing.T) {
	id := mustCreate(t, "user", 1, 0)
	want := "user_10000000000000"
	if id != want {
		t.Errorf("got %q, want %q", id, want)
	}
	if len(id) != 19 {
		t.Errorf("expected length 19, got %d", len(id))
	}
}

func TestVariable_CurrentTimestamp27Chars(t *testing.T) {
	id := mustCreate(t, "user", nowMsForTest(), 0)
	if len(id) != 27 {
		t.Errorf("expected length 27, got %d (%q)", len(id), id)
	}
}

func TestVariable_SingleLetterPrefix(t *testing.T) {
	id := mustCreate(t, "u", 0, 0)
	if id != "u_0" {
		t.Errorf("got %q, want %q", id, "u_0")
	}
}

// ---- Parse ----

func TestParse_NoPrefix22Char(t *testing.T) {
	id := "0000000000000000000000"
	p, ts, r, err := Parse(id)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	if p != "" || ts != "000000000" || r != "0000000000000" {
		t.Errorf("Parse(%q) = (%q, %q, %q)", id, p, ts, r)
	}
}

func TestParse_WithDelimiterPrefix(t *testing.T) {
	id := "user_1kbg1jmtt0000000000000"
	p, ts, r, err := Parse(id)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	if p != "user_" || ts != "1kbg1jmtt" || r != "0000000000000" {
		t.Errorf("Parse(%q) = (%q, %q, %q)", id, p, ts, r)
	}
}

func TestParse_UnderscorePrefixZeroTimestamp(t *testing.T) {
	id := "user_c1s"
	p, ts, r, err := Parse(id)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	if p != "user_" || ts != "0" || r != "c1s" {
		t.Errorf("Parse(%q) = (%q, %q, %q)", id, p, ts, r)
	}
}

func TestParse_EmptyError(t *testing.T) {
	if _, _, _, err := Parse(""); err == nil {
		t.Fatal("expected error for empty input")
	}
}

func TestParse_PrefixOnlyError(t *testing.T) {
	if _, _, _, err := Parse("user_"); err == nil {
		t.Fatal("expected error for prefix-only input")
	}
}

func TestParse_NonDelimited22NoPrefix(t *testing.T) {
	id := "u1234567890123456789ab"
	p, ts, r, err := Parse(id)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	if p != "" || ts != "u12345678" || r != "90123456789ab" {
		t.Errorf("Parse(%q) = (%q, %q, %q)", id, p, ts, r)
	}
}

func TestParse_WrongLengthNoDelimiterError(t *testing.T) {
	if _, _, _, err := Parse("abc123"); err == nil {
		t.Fatal("expected error for wrong-length non-delimited input")
	}
}

func TestParse_DashNormalizedToUnderscore(t *testing.T) {
	id := "user-1kbg1jmtt0000000000000"
	p, ts, r, err := Parse(id)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	if p != "user_" || ts != "1kbg1jmtt" || r != "0000000000000" {
		t.Errorf("Parse(%q) = (%q, %q, %q)", id, p, ts, r)
	}
}

func TestParse_DashAndUnderscoreSameTimestamp(t *testing.T) {
	a, errA := Timestamp("user-1kbg1jmtt0000000000000")
	b, errB := Timestamp("user_1kbg1jmtt0000000000000")
	if errA != nil || errB != nil {
		t.Fatalf("Timestamp errors: %v / %v", errA, errB)
	}
	if a != b {
		t.Errorf("dash/underscore timestamps differ: %d vs %d", a, b)
	}
}

// ---- Prefix / Timestamp / Random ----

func TestPrefix_NoPrefixEmpty(t *testing.T) {
	id := mustCreate(t, "", 1000, 0)
	p, _ := Prefix(id)
	if p != "" {
		t.Errorf("Prefix(%q) = %q, want \"\"", id, p)
	}
}

func TestPrefix_WithPrefix(t *testing.T) {
	id := mustCreate(t, "user", 1000, 0)
	p, _ := Prefix(id)
	if p != "user_" {
		t.Errorf("Prefix(%q) = %q, want %q", id, p, "user_")
	}
}

func TestPrefix_SingleLetter(t *testing.T) {
	id := mustCreate(t, "u", 1000, 0)
	p, _ := Prefix(id)
	if p != "u_" {
		t.Errorf("Prefix(%q) = %q, want %q", id, p, "u_")
	}
}

func TestTimestamp_FixedFormat(t *testing.T) {
	ts := int64(1609459200000)
	id := mustCreate(t, "", ts, 0)
	got, _ := Timestamp(id)
	if got != ts {
		t.Errorf("Timestamp = %d, want %d", got, ts)
	}
}

func TestTimestamp_VariableFormat(t *testing.T) {
	ts := int64(1609459200000)
	id := mustCreate(t, "user", ts, 0)
	got, _ := Timestamp(id)
	if got != ts {
		t.Errorf("Timestamp = %d, want %d", got, ts)
	}
}

func TestTimestamp_ZeroFixed(t *testing.T) {
	id := mustCreate(t, "", 0, 12345)
	got, _ := Timestamp(id)
	if got != 0 {
		t.Errorf("Timestamp = %d, want 0", got)
	}
}

func TestTimestamp_ZeroVariable(t *testing.T) {
	id := mustCreate(t, "user", 0, 12345)
	got, _ := Timestamp(id)
	if got != 0 {
		t.Errorf("Timestamp = %d, want 0", got)
	}
}

func TestRandom_FixedFormat(t *testing.T) {
	r := uint64(123456789)
	id := mustCreate(t, "", nowMsForTest(), r)
	got, _ := Random(id)
	if got.Uint64() != r {
		t.Errorf("Random = %s, want %d", got, r)
	}
}

func TestRandom_VariableFormat(t *testing.T) {
	r := uint64(123456789)
	id := mustCreate(t, "user", nowMsForTest(), r)
	got, _ := Random(id)
	if got.Uint64() != r {
		t.Errorf("Random = %s, want %d", got, r)
	}
}

func TestRandom_ZeroFixed(t *testing.T) {
	id := mustCreate(t, "", 1000, 0)
	got, _ := Random(id)
	if got.Sign() != 0 {
		t.Errorf("Random = %s, want 0", got)
	}
}

func TestRandom_WithZeroTimestampVariable(t *testing.T) {
	id := mustCreate(t, "user", 0, 12345)
	got, _ := Random(id)
	if got.Uint64() != 12345 {
		t.Errorf("Random = %s, want 12345", got)
	}
}

func TestRandom_64BitRoundtrip(t *testing.T) {
	r := ^uint64(0)
	id := mustCreate(t, "user", nowMsForTest(), r)
	got, _ := Random(id)
	if got.Uint64() != r {
		t.Errorf("64-bit roundtrip mismatch: %s vs %d", got, r)
	}
}

// ---- Normalize ----

func TestNormalize_LowercasesUppercase(t *testing.T) {
	original := mustCreate(t, "user", 1609459200000, 0)
	normalized, err := Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if normalized != original {
		t.Errorf("Normalize(upper) = %q, want %q", normalized, original)
	}
}

func TestNormalize_ConvertsAmbiguousChars(t *testing.T) {
	original := mustCreate(t, "user", 1609459200000, 1111)
	prefix, ts, rand, err := Parse(original)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	modifiedTS := strings.NewReplacer("1", "I", "0", "O").Replace(ts)
	modifiedRand := strings.NewReplacer("1", "L", "0", "O").Replace(rand)
	modified := prefix + modifiedTS + modifiedRand

	normalized, err := Normalize(modified)
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	gotTs, _ := Timestamp(normalized)
	gotRand, _ := Random(normalized)
	if gotTs != 1609459200000 {
		t.Errorf("Timestamp = %d, want 1609459200000", gotTs)
	}
	if gotRand.Uint64() != 1111 {
		t.Errorf("Random = %s, want 1111", gotRand)
	}
}

func TestNormalize_VariableLength(t *testing.T) {
	original := mustCreate(t, "user", 1609459200000, 12345)
	normalized, err := Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if normalized != original {
		t.Errorf("Normalize(upper) = %q, want %q", normalized, original)
	}
}

func TestNormalize_PreservesValuesRoundtrip(t *testing.T) {
	original := mustCreate(t, "user", 1609459200000, 12345)
	normalized, _ := Normalize(strings.ToUpper(original))
	gotTs, _ := Timestamp(normalized)
	gotRand, _ := Random(normalized)
	if gotTs != 1609459200000 || gotRand.Uint64() != 12345 {
		t.Errorf("roundtrip values lost: ts=%d rand=%s", gotTs, gotRand)
	}
}

func TestNormalize_FixedFormat(t *testing.T) {
	original := mustCreate(t, "", 1609459200000, 12345)
	normalized, err := Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if normalized != original {
		t.Errorf("Normalize(upper) = %q, want %q", normalized, original)
	}
}

func TestNormalize_DashToUnderscore(t *testing.T) {
	withDash := "user-1kbg1jmtt0000000000000"
	normalized, err := Normalize(withDash)
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if !strings.HasPrefix(normalized, "user_") {
		t.Errorf("expected user_ prefix, got %q", normalized)
	}
	p, _ := Prefix(normalized)
	if p != "user_" {
		t.Errorf("Prefix = %q, want user_", p)
	}
}

func TestNormalize_DashAndUnderscoreEqual(t *testing.T) {
	a, _ := Normalize("user-1kbg1jmtt0000000000000")
	b, _ := Normalize("user_1kbg1jmtt0000000000000")
	if a != b {
		t.Errorf("dash/underscore normalize differ: %q vs %q", a, b)
	}
}

func TestNormalize_Preserves64BitEntropy(t *testing.T) {
	r := ^uint64(0)
	original := mustCreate(t, "user", 1609459200000, r)
	normalized, _ := Normalize(strings.ToUpper(original))
	got, _ := Random(normalized)
	if got.Uint64() != r {
		t.Errorf("64-bit entropy lost: %s vs %d", got, r)
	}
}

// ---- Normalize: ashid4 round-trip ----
//
// Regression coverage: prior to the unified normalize, the function routed
// through Create (timestamp + random) for every input, which encoded the
// timestamp unpadded. For ashid4 inputs with leading zeros at the front of
// the first long, those zeros got dropped — corrupting the value.

func TestNormalize_Ashid4_WithPrefix_FullEntropyIdentity(t *testing.T) {
	original, err := Create4("tok", 0x112210f47de98115, 0x88f7bfa8ac471d31)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	normalized, err := Normalize(original)
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if normalized != original {
		t.Errorf("Normalize(ashid4) = %q, want identity %q", normalized, original)
	}
}

func TestNormalize_Ashid4_NoPrefix_FullEntropyIdentity(t *testing.T) {
	original, err := Create4("", 0x112210f47de98115, 0x88f7bfa8ac471d31)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	normalized, err := Normalize(original)
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if normalized != original {
		t.Errorf("Normalize(ashid4 no-prefix) = %q, want identity %q", normalized, original)
	}
}

func TestNormalize_Ashid4_UppercasedBackToCanonical(t *testing.T) {
	original, err := Create4("tok", 0xdeadbeefcafebabe, 0x0123456789abcdef)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	normalized, err := Normalize(strings.ToUpper(original))
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	if normalized != original {
		t.Errorf("Normalize(upper ashid4) = %q, want %q", normalized, original)
	}
}

func TestNormalize_Ashid4_SmallFirstLong_CollapsesToV1Shape(t *testing.T) {
	// A small first long means the canonical type-1 path truncates leading
	// zeros, so the shape collapses to v1. The values survive — that's the
	// point of normalize.
	r1 := uint64(1)
	r2 := uint64(0)
	original, err := Create4("tok", r1, r2)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	normalized, err := Normalize(original)
	if err != nil {
		t.Fatalf("Normalize error: %v", err)
	}
	gotTs, err := Timestamp(normalized)
	if err != nil {
		t.Fatalf("Timestamp error: %v", err)
	}
	gotRand, err := Random(normalized)
	if err != nil {
		t.Fatalf("Random error: %v", err)
	}
	if uint64(gotTs) != r1 {
		t.Errorf("first long after normalize = %d, want %d", gotTs, r1)
	}
	if gotRand.Uint64() != r2 {
		t.Errorf("second long after normalize = %s, want %d", gotRand, r2)
	}
}

func TestNormalize_Idempotent(t *testing.T) {
	v1 := mustCreate(t, "user", 1609459200000, 12345)
	once, _ := Normalize(v1)
	twice, _ := Normalize(once)
	if twice != once {
		t.Errorf("v1 idempotence broken: once=%q twice=%q", once, twice)
	}

	a4, err := Create4("tok", 0xdeadbeefcafebabe, 0x0123456789abcdef)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	a4Once, _ := Normalize(a4)
	a4Twice, _ := Normalize(a4Once)
	if a4Once != a4 {
		t.Errorf("ashid4 normalize identity broken: %q vs %q", a4Once, a4)
	}
	if a4Twice != a4Once {
		t.Errorf("ashid4 idempotence broken: once=%q twice=%q", a4Once, a4Twice)
	}
}

func TestNormalize_V1AndMatchingAshid4_CollapseToSameShape(t *testing.T) {
	// normalize routes through buildBase(padded=false), so a v1 and an ashid4
	// with the same two longs (when the first long is small enough to truncate)
	// produce the same canonical (v1) shape.
	var long1 int64 = 1609459200000
	long2 := uint64(12345)
	v1 := mustCreate(t, "tok", long1, long2)
	a4, err := Create4("tok", uint64(long1), long2)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	nV1, _ := Normalize(v1)
	nA4, _ := Normalize(a4)
	if nV1 != nA4 {
		t.Errorf("v1 vs ashid4 normalize diverge: %q vs %q", nV1, nA4)
	}
	if nV1 != v1 {
		t.Errorf("v1 normalize not byte-identical: got %q want %q", nV1, v1)
	}
}

// ---- IsValid ----

func TestIsValid_ValidIDs(t *testing.T) {
	if !IsValid(New()) || !IsValid(New("u")) || !IsValid(New("user")) {
		t.Error("expected New ids to validate")
	}
}

func TestIsValid_ZeroTimestampValid(t *testing.T) {
	if !IsValid(mustCreate(t, "", 0, 0)) {
		t.Error("zero ts/random fixed should be valid")
	}
	if !IsValid(mustCreate(t, "u", 0, 0)) {
		t.Error("zero ts/random with single-letter prefix should be valid")
	}
	if !IsValid(mustCreate(t, "user", 0, 0)) {
		t.Error("zero ts/random with prefix should be valid")
	}
}

func TestIsValid_Empty(t *testing.T) {
	if IsValid("") {
		t.Error("empty should be invalid")
	}
}

func TestIsValid_InvalidBase32(t *testing.T) {
	if IsValid("user_invalid!@#") {
		t.Error("invalid chars should fail validation")
	}
}

func TestIsValid_WrongLengthNoDelim(t *testing.T) {
	if IsValid("abc123") {
		t.Error("wrong-length non-delimited should fail")
	}
}

func TestIsValid_22CharBase(t *testing.T) {
	if !IsValid("0000000000000000000000") {
		t.Error("all-zero 22-char base should be valid")
	}
}

// ---- Time sortability ----

func TestSortable_NoPrefix(t *testing.T) {
	base := nowMsForTest()
	ids := []string{
		mustCreate(t, "", base+2000, 0),
		mustCreate(t, "", base, 0),
		mustCreate(t, "", base+1000, 0),
	}
	sort.Strings(ids)
	if ids[0] >= ids[1] || ids[1] >= ids[2] {
		t.Errorf("sort order broken: %v", ids)
	}
}

func TestSortable_WithPrefix(t *testing.T) {
	base := nowMsForTest()
	ids := []string{
		mustCreate(t, "user", base+2000, 0),
		mustCreate(t, "user", base, 0),
		mustCreate(t, "user", base+1000, 0),
	}
	sort.Strings(ids)
	if ids[0] >= ids[1] || ids[1] >= ids[2] {
		t.Errorf("sort order broken: %v", ids)
	}
}

func TestSortable_ByRandomWhenTimestampEqual(t *testing.T) {
	ts := nowMsForTest()
	a := mustCreate(t, "", ts, 1000)
	b := mustCreate(t, "", ts, 2000)
	ids := []string{b, a}
	sort.Strings(ids)
	if ids[0] != a || ids[1] != b {
		t.Errorf("sort by random broken: %v", ids)
	}
}

// ---- Convenience functions ----

func TestNew_ProducesValid(t *testing.T) {
	if !IsValid(New()) {
		t.Error("New() produced invalid id")
	}
}

func TestNew_WithPrefix(t *testing.T) {
	id := New("user")
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("expected user_ prefix, got %q", id)
	}
}

// ---- Double-click selectability ----

var (
	noPrefixRe   = regexp.MustCompile(`^[a-z0-9]+$`)
	withPrefixRe = regexp.MustCompile(`^[a-z]+_[a-z0-9]+$`)
)

func TestDoubleClick_NoHyphens(t *testing.T) {
	if strings.Contains(New("user"), "-") {
		t.Error("id should not contain hyphens")
	}
}

func TestDoubleClick_NoSpaces(t *testing.T) {
	if strings.Contains(New("user"), " ") {
		t.Error("id should not contain spaces")
	}
}

func TestDoubleClick_AlphanumericAndUnderscoreOnly(t *testing.T) {
	if !noPrefixRe.MatchString(New()) {
		t.Errorf("New() failed alphanumeric pattern: %q", New())
	}
	if !withPrefixRe.MatchString(New("user")) {
		t.Errorf("New(\"user\") failed pattern: %q", New("user"))
	}
}

// ---- Ashid4 ----

func TestAshid4_NoPrefix26Chars(t *testing.T) {
	id := New4()
	if len(id) != 26 {
		t.Errorf("expected 26 chars, got %d", len(id))
	}
	if !IsValid(id) {
		t.Errorf("New4() id should be valid: %q", id)
	}
}

func TestAshid4_WithPrefix(t *testing.T) {
	id := New4("tok")
	if !strings.HasPrefix(id, "tok_") {
		t.Errorf("expected tok_ prefix, got %q", id)
	}
	if len(id) != 30 {
		t.Errorf("expected 30 chars, got %d", len(id))
	}
}

func TestAshid4_UniqueIDs(t *testing.T) {
	seen := make(map[string]struct{}, 1000)
	for i := 0; i < 1000; i++ {
		seen[New4()] = struct{}{}
	}
	if len(seen) != 1000 {
		t.Errorf("expected 1000 unique IDs, got %d", len(seen))
	}
}

func TestAshid4_Full64BitEntropy(t *testing.T) {
	saw := false
	for i := 0; i < 100; i++ {
		id := New4()
		_, e1, e2, err := Parse(id)
		if err != nil {
			t.Fatalf("Parse error: %v", err)
		}
		if len(strings.TrimLeft(e1, "0")) > 11 || len(strings.TrimLeft(e2, "0")) > 11 {
			saw = true
			break
		}
	}
	if !saw {
		t.Error("expected at least one full-entropy component in 100 samples")
	}
}

func TestAshid4_ExplicitRandomValues(t *testing.T) {
	id, err := Create4("tok", 123456789, 987654321)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	if !strings.HasPrefix(id, "tok_") {
		t.Errorf("expected tok_ prefix, got %q", id)
	}
}

func TestAshid4_64BitRoundtrip(t *testing.T) {
	r1 := ^uint64(0)
	r2 := uint64(1) << 63
	id, _ := Create4("tok", r1, r2)
	prefix, e1, e2, err := Parse(id)
	if err != nil {
		t.Fatalf("Parse error: %v", err)
	}
	if prefix != "tok_" {
		t.Errorf("prefix = %q, want tok_", prefix)
	}
	if len(e1) != 13 || len(e2) != 13 {
		t.Errorf("expected 13-char encoded components, got %d / %d", len(e1), len(e2))
	}
}

// ---- Create4 padding lockdown ----
//
// Create4 must always emit both halves 13-char padded, regardless of input
// magnitude. These pin the wire format so a refactor that drops padding
// (e.g. routing Create4 through an unpadded builder) fails loudly.
// Mirrors typescript/test/ashid.test.ts.

func TestCreate4_Padding_ZeroZero_WithPrefix(t *testing.T) {
	id, err := Create4("tok", 0, 0)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	const want = "tok_00000000000000000000000000"
	if id != want {
		t.Errorf("Create4(tok, 0, 0) = %q, want %q", id, want)
	}
}

func TestCreate4_Padding_ZeroZero_NoPrefix(t *testing.T) {
	id, err := Create4("", 0, 0)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	const want = "00000000000000000000000000"
	if id != want {
		t.Errorf("Create4(\"\", 0, 0) = %q, want %q", id, want)
	}
}

func TestCreate4_Padding_OneZero_WithPrefix(t *testing.T) {
	id, err := Create4("tok", 1, 0)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	const want = "tok_00000000000010000000000000"
	if id != want {
		t.Errorf("Create4(tok, 1, 0) = %q, want %q", id, want)
	}
}

func TestCreate4_Padding_CrockfordZ_WithPrefix(t *testing.T) {
	id, err := Create4("tok", 31, 0)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	const want = "tok_000000000000z0000000000000"
	if id != want {
		t.Errorf("Create4(tok, 31, 0) = %q, want %q", id, want)
	}
}

func TestCreate4_Padding_MaxU64_FirstHalf(t *testing.T) {
	id, err := Create4("tok", ^uint64(0), 0)
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	const want = "tok_fzzzzzzzzzzzz0000000000000"
	if id != want {
		t.Errorf("Create4(tok, max u64, 0) = %q, want %q", id, want)
	}
	if len(id) != 3+1+26 {
		t.Errorf("len = %d, want %d", len(id), 3+1+26)
	}
}

func TestCreate4_Padding_MaxU64_SecondHalf(t *testing.T) {
	id, err := Create4("tok", 0, ^uint64(0))
	if err != nil {
		t.Fatalf("Create4 error: %v", err)
	}
	const want = "tok_0000000000000fzzzzzzzzzzzz"
	if id != want {
		t.Errorf("Create4(tok, 0, max u64) = %q, want %q", id, want)
	}
	if len(id) != 3+1+26 {
		t.Errorf("len = %d, want %d", len(id), 3+1+26)
	}
}

func TestCreate4_Padding_NoPrefix_Always26Chars(t *testing.T) {
	samples := [][2]uint64{
		{0, 0},
		{1, 0},
		{0, 1},
		{31, 31},
		{^uint64(0), 0},
		{0, ^uint64(0)},
	}
	for _, s := range samples {
		id, err := Create4("", s[0], s[1])
		if err != nil {
			t.Fatalf("Create4(%d, %d) error: %v", s[0], s[1], err)
		}
		if len(id) != 26 {
			t.Errorf("Create4(\"\", %d, %d) len = %d, want 26", s[0], s[1], len(id))
		}
	}
}

func TestCreate4_Padding_WithPrefix_LengthInvariant(t *testing.T) {
	samples := [][2]uint64{
		{0, 0},
		{1, 0},
		{^uint64(0), ^uint64(0)},
	}
	for _, s := range samples {
		id, err := Create4("tok", s[0], s[1])
		if err != nil {
			t.Fatalf("Create4(%d, %d) error: %v", s[0], s[1], err)
		}
		if len(id) != 3+1+26 {
			t.Errorf("Create4(tok, %d, %d) len = %d, want %d", s[0], s[1], len(id), 3+1+26)
		}
	}
}

// ---- Encoder big.Int integration ----

// ---- Append API ----

func TestAppendNew_MatchesNew(t *testing.T) {
	// Same shape, different value (random differs each call).
	a := New("user")
	var buf [MaxIDLen]byte
	b := string(AppendNew(buf[:0], "user"))
	if !strings.HasPrefix(a, "user_") || !strings.HasPrefix(b, "user_") {
		t.Fatalf("missing prefix: %q / %q", a, b)
	}
	if len(a) != len(b) {
		t.Errorf("length mismatch: %q vs %q", a, b)
	}
	if !IsValid(b) {
		t.Errorf("AppendNew result invalid: %q", b)
	}
}

func TestAppendNew_NoPrefix(t *testing.T) {
	var buf [MaxIDLen]byte
	id := string(AppendNew(buf[:0]))
	if len(id) != 22 {
		t.Errorf("expected 22 chars, got %d (%q)", len(id), id)
	}
	if !IsValid(id) {
		t.Errorf("invalid id: %q", id)
	}
}

func TestAppendNew4_Shape(t *testing.T) {
	var buf [MaxIDLen]byte
	id := string(AppendNew4(buf[:0], "tok"))
	if !strings.HasPrefix(id, "tok_") || len(id) != 30 {
		t.Errorf("unexpected shape: %q (len=%d)", id, len(id))
	}
}

func TestAppend_DeterministicMatchesCreate(t *testing.T) {
	cases := []struct {
		prefix string
		ts     int64
		r      uint64
	}{
		{"", 0, 0},
		{"user", 0, 0},
		{"user", 1000, 0},
		{"user", 1, 0},
		{"USER", 1609459200000, 12345},
		{"a-b_c", 1000, 0},
		{"", 1609459200000, 12345},
	}
	for _, tc := range cases {
		want, err := Create(tc.prefix, tc.ts, tc.r)
		if err != nil {
			t.Errorf("Create(%q, %d, %d) error: %v", tc.prefix, tc.ts, tc.r, err)
			continue
		}
		var buf [MaxIDLen]byte
		got, err := Append(buf[:0], tc.prefix, tc.ts, tc.r)
		if err != nil {
			t.Errorf("Append error: %v", err)
			continue
		}
		if string(got) != want {
			t.Errorf("Append(%q, %d, %d) = %q, want %q", tc.prefix, tc.ts, tc.r, got, want)
		}
	}
}

func TestAppend4_DeterministicMatchesCreate4(t *testing.T) {
	cases := []struct {
		prefix string
		r1, r2 uint64
	}{
		{"", 0, 0},
		{"tok", 123456789, 987654321},
		{"tok", ^uint64(0), 1 << 63},
	}
	for _, tc := range cases {
		want, err := Create4(tc.prefix, tc.r1, tc.r2)
		if err != nil {
			t.Errorf("Create4 error: %v", err)
			continue
		}
		var buf [MaxIDLen]byte
		got, err := Append4(buf[:0], tc.prefix, tc.r1, tc.r2)
		if err != nil {
			t.Errorf("Append4 error: %v", err)
			continue
		}
		if string(got) != want {
			t.Errorf("Append4(%q, %d, %d) = %q, want %q", tc.prefix, tc.r1, tc.r2, got, want)
		}
	}
}

func TestAppend_PreservesExistingBuffer(t *testing.T) {
	dst := []byte("prefix:")
	out, err := Append(dst, "user", 1000, 0)
	if err != nil {
		t.Fatalf("Append error: %v", err)
	}
	if !strings.HasPrefix(string(out), "prefix:user_") {
		t.Errorf("expected 'prefix:user_' prefix, got %q", out)
	}
}

func TestAppend_ErrorOnBadTimestamp(t *testing.T) {
	if _, err := Append(nil, "", -1, 0); err == nil {
		t.Error("expected error for negative timestamp")
	}
	if _, err := Append(nil, "", MaxTimestamp+1, 0); err == nil {
		t.Error("expected error for timestamp above max")
	}
}

// ---- Encoder big.Int integration ----

func TestRandom_ReturnsBigInt(t *testing.T) {
	id := mustCreate(t, "user", nowMsForTest(), 12345)
	got, err := Random(id)
	if err != nil {
		t.Fatalf("Random error: %v", err)
	}
	// Make sure it's a *big.Int and equals 12345
	if got.Cmp(big.NewInt(12345)) != 0 {
		t.Errorf("Random = %s, want 12345", got)
	}
}

// ---- UUID round-trip ----

func TestToUuid_Format(t *testing.T) {
	id := mustCreate(t, "user", 1733140800000, 8234567890123456789)
	uuid, err := ToUuid(id)
	if err != nil {
		t.Fatalf("ToUuid: %v", err)
	}
	if len(uuid) != 36 {
		t.Errorf("expected 36 chars, got %d (%q)", len(uuid), uuid)
	}
	matched, _ := regexp.MatchString(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`, uuid)
	if !matched {
		t.Errorf("uuid not in canonical 8-4-4-4-12 form: %q", uuid)
	}
}

func TestToUuid_PrefixAgnostic(t *testing.T) {
	withPrefix := mustCreate(t, "user", 1733140800000, 8234567890123456789)
	noPrefix := mustCreate(t, "", 1733140800000, 8234567890123456789)
	a, _ := ToUuid(withPrefix)
	b, _ := ToUuid(noPrefix)
	if a != b {
		t.Errorf("toUuid not prefix-agnostic: %q vs %q", a, b)
	}
}

func TestRoundtrip_StandardForm(t *testing.T) {
	original := mustCreate(t, "user", 1733140800000, 8234567890123456789)
	uuid, _ := ToUuid(original)
	restored, err := FromUuid(uuid, "user")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if restored != original {
		t.Errorf("roundtrip mismatch: %q -> %q -> %q", original, uuid, restored)
	}
}

func TestRoundtrip_LongForm(t *testing.T) {
	original, err := Create4("tok", ^uint64(0), 1<<63)
	if err != nil {
		t.Fatalf("Create4: %v", err)
	}
	uuid, _ := ToUuid(original)
	restored, err := FromUuid(uuid, "tok")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if restored != original {
		t.Errorf("ashid4 roundtrip mismatch: %q -> %q -> %q", original, uuid, restored)
	}
}

func TestUuid1Shaped_RoutesTo22Char(t *testing.T) {
	uuid := "00000123-abcd-ef01-2345-6789abcdef01"
	id, err := FromUuid(uuid, "")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if len(id) != 22 {
		t.Errorf("expected 22-char id, got %d (%q)", len(id), id)
	}
	got, _ := ToUuid(id)
	if got != uuid {
		t.Errorf("roundtrip: %q -> %q", uuid, got)
	}
}

func TestUuidV4_RoutesTo26Char(t *testing.T) {
	uuid := "550e8400-e29b-41d4-a716-446655440000"
	id, err := FromUuid(uuid, "")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if len(id) != 26 {
		t.Errorf("expected 26-char id, got %d (%q)", len(id), id)
	}
	got, _ := ToUuid(id)
	if got != uuid {
		t.Errorf("roundtrip: %q -> %q", uuid, got)
	}
}

func TestUuidV7_RoutesTo26Char(t *testing.T) {
	uuid := "019e008b-edc4-7265-8312-f6a278b46b11"
	id, err := FromUuid(uuid, "")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if len(id) != 26 {
		t.Errorf("expected 26-char id, got %d (%q)", len(id), id)
	}
	got, _ := ToUuid(id)
	if got != uuid {
		t.Errorf("roundtrip: %q -> %q", uuid, got)
	}
}

func TestRoundtrip_CanonicalUuidsFromPost(t *testing.T) {
	for _, uuid := range []string{
		"550e8400-e29b-41d4-a716-446655440000",
		"7c9e6679-7425-40de-944b-e07fc1f90ae7",
		"f47ac10b-58cc-4372-a567-0e02b2c3d479",
	} {
		id, err := FromUuid(uuid, "")
		if err != nil {
			t.Fatalf("FromUuid(%q): %v", uuid, err)
		}
		got, _ := ToUuid(id)
		if got != uuid {
			t.Errorf("roundtrip %q -> %q", uuid, got)
		}
	}
}

func TestFromUuid_AcceptsUndashed(t *testing.T) {
	dashed := "550e8400-e29b-41d4-a716-446655440000"
	undashed := "550e8400e29b41d4a716446655440000"
	a, _ := FromUuid(dashed, "")
	b, _ := FromUuid(undashed, "")
	if a != b {
		t.Errorf("dashed/undashed mismatch: %q vs %q", a, b)
	}
}

func TestFromUuid_PreservesPrefix(t *testing.T) {
	id, err := FromUuid("550e8400-e29b-41d4-a716-446655440000", "user")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if !strings.HasPrefix(id, "user_") {
		t.Errorf("expected user_ prefix, got %q", id)
	}
	uuid, _ := ToUuid(id)
	restored, _ := FromUuid(uuid, "user")
	if restored != id {
		t.Errorf("prefix-roundtrip mismatch: %q -> %q -> %q", id, uuid, restored)
	}
}

func TestFromUuid_AllZero(t *testing.T) {
	uuid := "00000000-0000-0000-0000-000000000000"
	id, err := FromUuid(uuid, "")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	got, _ := ToUuid(id)
	if got != uuid {
		t.Errorf("zero-roundtrip: %q", got)
	}
}

func TestFromUuid_AllFF(t *testing.T) {
	uuid := "ffffffff-ffff-ffff-ffff-ffffffffffff"
	id, err := FromUuid(uuid, "")
	if err != nil {
		t.Fatalf("FromUuid: %v", err)
	}
	if len(id) != 26 {
		t.Errorf("expected 26-char id, got %d (%q)", len(id), id)
	}
	got, _ := ToUuid(id)
	if got != uuid {
		t.Errorf("ff-roundtrip: %q", got)
	}
}

func TestFromUuid_InvalidInput(t *testing.T) {
	for _, bad := range []string{
		"not-a-uuid",
		"550e8400e29b41d4a71644665544000",
		"550e8400-e29b-41d4-a716-44665544000g",
	} {
		if _, err := FromUuid(bad, ""); err == nil {
			t.Errorf("expected error for %q", bad)
		}
	}
}
