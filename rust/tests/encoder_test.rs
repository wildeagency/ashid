use ashid::EncoderBase32Crockford;

#[test]
fn encode_zero() {
    assert_eq!(EncoderBase32Crockford::encode(0, false), "0");
}

#[test]
fn encode_small_numbers() {
    assert_eq!(EncoderBase32Crockford::encode(1, false), "1");
    assert_eq!(EncoderBase32Crockford::encode(31, false), "z");
    assert_eq!(EncoderBase32Crockford::encode(32, false), "10");
}

#[test]
fn encode_large_numbers() {
    assert_eq!(EncoderBase32Crockford::encode(1000, false), "z8");
    assert_eq!(EncoderBase32Crockford::encode(123456, false), "3rj0");
}

#[test]
fn encode_padded() {
    let encoded = EncoderBase32Crockford::encode(123, true);
    assert_eq!(encoded.len(), 13);
    assert_eq!(encoded, "000000000003v");
}

#[test]
fn encode_unpadded_default() {
    let encoded = EncoderBase32Crockford::encode(123, false);
    assert!(encoded.len() < 13);
    assert_eq!(encoded, "3v");
}

#[test]
fn encode_zero_padded() {
    assert_eq!(EncoderBase32Crockford::encode(0, true), "0000000000000");
}

#[test]
fn encode_full_64bit_max() {
    // 2^64 - 1 = 18446744073709551615
    let encoded = EncoderBase32Crockford::encode(u64::MAX, false);
    assert_eq!(encoded, "fzzzzzzzzzzzz");
}

#[test]
fn encode_full_64bit_padded() {
    let encoded = EncoderBase32Crockford::encode(u64::MAX, true);
    assert_eq!(encoded.len(), 13);
    assert_eq!(encoded, "fzzzzzzzzzzzz");
}

#[test]
fn decode_zero() {
    assert_eq!(EncoderBase32Crockford::decode("0").unwrap(), 0);
}

#[test]
fn decode_small_numbers() {
    assert_eq!(EncoderBase32Crockford::decode("1").unwrap(), 1);
    assert_eq!(EncoderBase32Crockford::decode("z").unwrap(), 31);
    assert_eq!(EncoderBase32Crockford::decode("10").unwrap(), 32);
}

#[test]
fn decode_large_numbers() {
    assert_eq!(EncoderBase32Crockford::decode("z8").unwrap(), 1000);
    assert_eq!(EncoderBase32Crockford::decode("3rj0").unwrap(), 123456);
}

#[test]
fn decode_case_insensitive() {
    assert_eq!(
        EncoderBase32Crockford::decode("ABC").unwrap(),
        EncoderBase32Crockford::decode("abc").unwrap()
    );
    assert_eq!(
        EncoderBase32Crockford::decode("XyZ").unwrap(),
        EncoderBase32Crockford::decode("xyz").unwrap()
    );
}

#[test]
fn decode_lookalike_o_to_zero() {
    assert_eq!(EncoderBase32Crockford::decode("O").unwrap(), 0);
    assert_eq!(EncoderBase32Crockford::decode("o").unwrap(), 0);
    assert_eq!(EncoderBase32Crockford::decode("0").unwrap(), 0);
}

#[test]
fn decode_lookalike_i_l_to_one() {
    assert_eq!(EncoderBase32Crockford::decode("I").unwrap(), 1);
    assert_eq!(EncoderBase32Crockford::decode("i").unwrap(), 1);
    assert_eq!(EncoderBase32Crockford::decode("L").unwrap(), 1);
    assert_eq!(EncoderBase32Crockford::decode("l").unwrap(), 1);
    assert_eq!(EncoderBase32Crockford::decode("1").unwrap(), 1);
}

#[test]
fn decode_lookalike_u_to_v() {
    assert_eq!(EncoderBase32Crockford::decode("U").unwrap(), 27);
    assert_eq!(EncoderBase32Crockford::decode("u").unwrap(), 27);
    assert_eq!(EncoderBase32Crockford::decode("V").unwrap(), 27);
    assert_eq!(EncoderBase32Crockford::decode("v").unwrap(), 27);
}

#[test]
fn decode_empty_string_errors() {
    assert!(EncoderBase32Crockford::decode("").is_err());
}

#[test]
fn decode_invalid_chars_error() {
    assert!(EncoderBase32Crockford::decode("abc-def").is_err());
    assert!(EncoderBase32Crockford::decode("abc def").is_err());
    assert!(EncoderBase32Crockford::decode("abc_def").is_err());
}

#[test]
fn roundtrip_basic() {
    for &n in &[0u64, 1, 31, 32, 100, 1000, 123_456, 1_700_000_000_000] {
        let encoded = EncoderBase32Crockford::encode(n, false);
        let decoded = EncoderBase32Crockford::decode(&encoded).unwrap();
        assert_eq!(decoded, n);
    }
}

#[test]
fn roundtrip_padded() {
    let n = 12345u64;
    let encoded = EncoderBase32Crockford::encode(n, true);
    let decoded = EncoderBase32Crockford::decode(&encoded).unwrap();
    assert_eq!(decoded, n);
}

#[test]
fn roundtrip_full_64bit() {
    for &n in &[u64::MAX, u64::MAX - 1, 1u64 << 63, (1u64 << 53) + 1] {
        let encoded = EncoderBase32Crockford::encode(n, false);
        let decoded = EncoderBase32Crockford::decode(&encoded).unwrap();
        assert_eq!(decoded, n);
    }
}

#[test]
fn secure_random_long_generates_different_values() {
    let mut values = std::collections::HashSet::new();
    for _ in 0..100 {
        values.insert(EncoderBase32Crockford::secure_random_long());
    }
    assert!(
        values.len() > 90,
        "expected mostly unique values, got {}",
        values.len()
    );
}

#[test]
fn secure_random_long_full_64bit_entropy() {
    // With 64-bit entropy, ~50% of values should exceed 2^53
    let threshold = (1u64 << 53) - 1;
    let saw_beyond = (0..100).any(|_| EncoderBase32Crockford::secure_random_long() > threshold);
    assert!(
        saw_beyond,
        "expected to see at least one value beyond 53-bit safe range in 100 samples"
    );
}

#[test]
fn secure_random_long_produces_13_char_encoding() {
    let saw_13 = (0..100).any(|_| {
        let r = EncoderBase32Crockford::secure_random_long();
        EncoderBase32Crockford::encode(r, false).len() == 13
    });
    assert!(
        saw_13,
        "expected at least one value encoding to full 13 chars"
    );
}

#[test]
fn is_valid_accepts_correct_strings() {
    assert!(EncoderBase32Crockford::is_valid("0"));
    assert!(EncoderBase32Crockford::is_valid("1"));
    assert!(EncoderBase32Crockford::is_valid("abc123"));
    assert!(EncoderBase32Crockford::is_valid("1fvszawr42tve3gxvx9900"));
}

#[test]
fn is_valid_rejects_invalid_strings() {
    assert!(!EncoderBase32Crockford::is_valid(""));
    assert!(!EncoderBase32Crockford::is_valid("abc-def"));
    assert!(!EncoderBase32Crockford::is_valid("abc def"));
    assert!(!EncoderBase32Crockford::is_valid("abc_def"));
}

#[test]
fn is_valid_accepts_lookalikes() {
    assert!(EncoderBase32Crockford::is_valid("OIL")); // O→0, I→1, L→1
    assert!(EncoderBase32Crockford::is_valid("oil"));
}
