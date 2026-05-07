package ashid

import "testing"

// Benchmarks compare the string-returning API (New/Create) against the
// allocation-free Append family. Run with:
//
//	go test -bench=. -benchmem
func BenchmarkNew_NoPrefix(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_ = New()
	}
}

func BenchmarkNew_WithPrefix(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_ = New("user")
	}
}

func BenchmarkNew4_NoPrefix(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_ = New4()
	}
}

func BenchmarkAppendNew_NoPrefix(b *testing.B) {
	b.ReportAllocs()
	var buf [MaxIDLen]byte
	for i := 0; i < b.N; i++ {
		_ = AppendNew(buf[:0])
	}
}

func BenchmarkAppendNew_WithPrefix(b *testing.B) {
	b.ReportAllocs()
	var buf [MaxIDLen]byte
	for i := 0; i < b.N; i++ {
		_ = AppendNew(buf[:0], "user")
	}
}

func BenchmarkAppendNew4_NoPrefix(b *testing.B) {
	b.ReportAllocs()
	var buf [MaxIDLen]byte
	for i := 0; i < b.N; i++ {
		_ = AppendNew4(buf[:0])
	}
}

// Deterministic Create / Append benchmarks isolate encoding cost from
// crypto/rand and time.Now() syscalls.
func BenchmarkCreate_Deterministic(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_, _ = Create("user", 1700000000000, 0xDEADBEEFCAFEBABE)
	}
}

func BenchmarkAppend_Deterministic(b *testing.B) {
	b.ReportAllocs()
	var buf [MaxIDLen]byte
	for i := 0; i < b.N; i++ {
		_, _ = Append(buf[:0], "user", 1700000000000, 0xDEADBEEFCAFEBABE)
	}
}

func BenchmarkEncode(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_ = Encode(0xDEADBEEFCAFEBABE, true)
	}
}

func BenchmarkAppendEncode(b *testing.B) {
	b.ReportAllocs()
	var buf [16]byte
	for i := 0; i < b.N; i++ {
		_ = AppendEncode(buf[:0], 0xDEADBEEFCAFEBABE, true)
	}
}

func BenchmarkParse(b *testing.B) {
	id := "user_1kbg1jmtt0000000000000"
	b.ReportAllocs()
	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		_, _, _, _ = Parse(id)
	}
}

func BenchmarkSecureRandomLong(b *testing.B) {
	b.ReportAllocs()
	for i := 0; i < b.N; i++ {
		_ = SecureRandomLong()
	}
}
