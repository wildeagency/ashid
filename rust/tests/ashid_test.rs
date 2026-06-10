use ashid::{new, new4, parse_ashid, Ashid};
use std::collections::HashSet;

// ---------- create ----------

#[test]
fn create_no_prefix_is_22_chars() {
    let id = Ashid::create(None, None, None).unwrap();
    assert_eq!(id.len(), 22);
    assert!(
        id.chars().next().unwrap().is_ascii_digit()
            || id.chars().next().unwrap().is_ascii_lowercase()
    );
}

#[test]
fn create_with_prefix_auto_adds_delimiter() {
    let id = Ashid::create(Some("user"), None, None).unwrap();
    assert!(id.starts_with("user_"));
}

#[test]
fn create_with_trailing_underscore_strips_and_re_adds() {
    let id = Ashid::create(Some("user_"), None, None).unwrap();
    assert!(id.starts_with("user_"));
    assert!(!id.starts_with("user__"));
}

#[test]
fn create_same_id_with_or_without_trailing_delimiter() {
    let id1 = Ashid::create(Some("user"), Some(1000), Some(0)).unwrap();
    let id2 = Ashid::create(Some("user_"), Some(1000), Some(0)).unwrap();
    assert_eq!(id1, id2);
}

#[test]
fn create_empty_string_is_no_prefix() {
    let id = Ashid::create(Some(""), None, None).unwrap();
    assert_eq!(id.len(), 22);
}

#[test]
fn create_alphanumeric_prefix() {
    let id = Ashid::create(Some("user1"), Some(1000), Some(0)).unwrap();
    assert!(id.starts_with("user1_"));
}

#[test]
fn create_strips_non_alphanumeric_from_prefix() {
    let id = Ashid::create(Some("a-b_c"), Some(1000), Some(0)).unwrap();
    assert!(id.starts_with("abc_"));
}

#[test]
fn create_strips_special_chars_from_prefix() {
    let id = Ashid::create(Some("user!@#$%"), Some(1000), Some(0)).unwrap();
    assert!(id.starts_with("user_"));
}

#[test]
fn create_returns_no_prefix_when_all_stripped() {
    let id = Ashid::create(Some("___"), Some(1000), Some(0)).unwrap();
    assert_eq!(id.len(), 22);
}

#[test]
fn create_max_timestamp_works() {
    let id = Ashid::create(None, Some(35_184_372_088_831), Some(0)).unwrap();
    assert!(!id.is_empty());
}

#[test]
fn create_above_max_timestamp_errors() {
    let res = Ashid::create(None, Some(35_184_372_088_832), Some(0));
    assert!(res.is_err());
}

#[test]
fn create_lowercases_uppercase_prefix() {
    let id = Ashid::create(Some("USER"), Some(1000), Some(0)).unwrap();
    assert!(id.starts_with("user_"));
    assert_eq!(Ashid::prefix(&id).unwrap(), "user_");
}

#[test]
fn create_generates_unique_ids() {
    let mut ids = HashSet::new();
    for _ in 0..1000 {
        ids.insert(Ashid::create(None, None, None).unwrap());
    }
    assert_eq!(ids.len(), 1000);
}

// ---------- format: fixed (no prefix) ----------

#[test]
fn no_prefix_zero_timestamp_padded_to_22() {
    let id = Ashid::create(None, Some(0), Some(0)).unwrap();
    assert_eq!(id, "0000000000000000000000");
    assert_eq!(id.len(), 22);
}

#[test]
fn no_prefix_current_timestamp_is_22_chars() {
    let id = Ashid::create(None, None, None).unwrap();
    assert_eq!(id.len(), 22);
}

// ---------- format: variable (with prefix) ----------

#[test]
fn prefix_timestamp_zero_random_zero_minimal() {
    let id = Ashid::create(Some("user"), Some(0), Some(0)).unwrap();
    assert_eq!(id, "user_0");
}

#[test]
fn prefix_timestamp_zero_random_one_omits_timestamp() {
    let id = Ashid::create(Some("user"), Some(0), Some(1)).unwrap();
    assert_eq!(id, "user_1");
}

#[test]
fn prefix_timestamp_zero_random_31_short() {
    let id = Ashid::create(Some("user"), Some(0), Some(31)).unwrap();
    assert_eq!(id, "user_z");
}

#[test]
fn prefix_timestamp_one_random_zero_includes_timestamp() {
    let id = Ashid::create(Some("user"), Some(1), Some(0)).unwrap();
    assert_eq!(id, "user_10000000000000");
    assert_eq!(id.len(), 19);
}

#[test]
fn prefix_single_letter_gets_delimiter() {
    let id = Ashid::create(Some("u"), Some(0), Some(0)).unwrap();
    assert_eq!(id, "u_0");
}

// ---------- parse ----------

#[test]
fn parse_no_prefix_22_char_base() {
    let (prefix, ts, rand) = Ashid::parse("0000000000000000000000").unwrap();
    assert_eq!(prefix, "");
    assert_eq!(ts, "000000000");
    assert_eq!(rand, "0000000000000");
}

#[test]
fn parse_with_delimiter_prefix() {
    let (prefix, ts, rand) = Ashid::parse("user_1kbg1jmtt0000000000000").unwrap();
    assert_eq!(prefix, "user_");
    assert_eq!(ts, "1kbg1jmtt");
    assert_eq!(rand, "0000000000000");
}

#[test]
fn parse_underscore_prefix_with_timestamp_zero() {
    let (prefix, ts, rand) = Ashid::parse("user_c1s").unwrap();
    assert_eq!(prefix, "user_");
    assert_eq!(ts, "0");
    assert_eq!(rand, "c1s");
}

#[test]
fn parse_empty_string_errors() {
    assert!(Ashid::parse("").is_err());
}

#[test]
fn parse_prefix_only_errors() {
    assert!(Ashid::parse("user_").is_err());
}

#[test]
fn parse_non_delimited_22_char_id_no_prefix() {
    let (prefix, ts, rand) = Ashid::parse("u1234567890123456789ab").unwrap();
    assert_eq!(prefix, "");
    assert_eq!(ts, "u12345678");
    assert_eq!(rand, "90123456789ab");
}

#[test]
fn parse_wrong_length_no_delimiter_errors() {
    assert!(Ashid::parse("abc123").is_err());
}

#[test]
fn parse_dash_normalizes_to_underscore() {
    let (prefix, ts, rand) = Ashid::parse("user-1kbg1jmtt0000000000000").unwrap();
    assert_eq!(prefix, "user_");
    assert_eq!(ts, "1kbg1jmtt");
    assert_eq!(rand, "0000000000000");
}

#[test]
fn parse_dash_and_underscore_yield_same_timestamp() {
    let dash_ts = Ashid::timestamp("user-1kbg1jmtt0000000000000").unwrap();
    let under_ts = Ashid::timestamp("user_1kbg1jmtt0000000000000").unwrap();
    assert_eq!(dash_ts, under_ts);
}

// ---------- prefix() ----------

#[test]
fn prefix_empty_when_none() {
    let id = Ashid::create(None, Some(1000), Some(0)).unwrap();
    assert_eq!(Ashid::prefix(&id).unwrap(), "");
}

#[test]
fn prefix_with_delimiter() {
    let id = Ashid::create(Some("user"), Some(1000), Some(0)).unwrap();
    assert_eq!(Ashid::prefix(&id).unwrap(), "user_");
}

#[test]
fn prefix_single_letter_with_delimiter() {
    let id = Ashid::create(Some("u"), Some(1000), Some(0)).unwrap();
    assert_eq!(Ashid::prefix(&id).unwrap(), "u_");
}

// ---------- timestamp() ----------

#[test]
fn timestamp_extract_fixed_format() {
    let ts = 1_609_459_200_000u64;
    let id = Ashid::create(None, Some(ts), Some(0)).unwrap();
    assert_eq!(Ashid::timestamp(&id).unwrap(), ts);
}

#[test]
fn timestamp_extract_variable_format() {
    let ts = 1_609_459_200_000u64;
    let id = Ashid::create(Some("user"), Some(ts), Some(0)).unwrap();
    assert_eq!(Ashid::timestamp(&id).unwrap(), ts);
}

#[test]
fn timestamp_zero_fixed_format() {
    let id = Ashid::create(None, Some(0), Some(12345)).unwrap();
    assert_eq!(Ashid::timestamp(&id).unwrap(), 0);
}

#[test]
fn timestamp_zero_variable_format() {
    let id = Ashid::create(Some("user"), Some(0), Some(12345)).unwrap();
    assert_eq!(Ashid::timestamp(&id).unwrap(), 0);
}

// ---------- random() ----------

#[test]
fn random_extract_fixed_format() {
    let r = 123_456_789u64;
    let id = Ashid::create(None, Some(1_700_000_000_000), Some(r)).unwrap();
    assert_eq!(Ashid::random(&id).unwrap(), r);
}

#[test]
fn random_extract_variable_format() {
    let r = 123_456_789u64;
    let id = Ashid::create(Some("user"), Some(1_700_000_000_000), Some(r)).unwrap();
    assert_eq!(Ashid::random(&id).unwrap(), r);
}

#[test]
fn random_zero_fixed_format() {
    let id = Ashid::create(None, Some(1000), Some(0)).unwrap();
    assert_eq!(Ashid::random(&id).unwrap(), 0);
}

#[test]
fn random_variable_format_with_timestamp_zero() {
    let id = Ashid::create(Some("user"), Some(0), Some(12345)).unwrap();
    assert_eq!(Ashid::random(&id).unwrap(), 12345);
}

#[test]
fn random_64bit_max() {
    let r = u64::MAX;
    let id = Ashid::create(Some("user"), Some(1_700_000_000_000), Some(r)).unwrap();
    assert_eq!(Ashid::random(&id).unwrap(), r);
}

// ---------- normalize() ----------

#[test]
fn normalize_lowercases() {
    let original = Ashid::create(Some("user"), Some(1_609_459_200_000), Some(0)).unwrap();
    let upper = original.to_uppercase();
    let normalized = Ashid::normalize(&upper).unwrap();
    assert_eq!(normalized, original);
}

#[test]
fn normalize_converts_ambiguous_chars() {
    let original = Ashid::create(Some("user"), Some(1_609_459_200_000), Some(1111)).unwrap();
    let (prefix, ts, rand) = Ashid::parse(&original).unwrap();
    let modified_ts = ts.replace('1', "I").replace('0', "O");
    let modified_rand = rand.replace('1', "L").replace('0', "O");
    let modified = format!("{}{}{}", prefix, modified_ts, modified_rand);

    let normalized = Ashid::normalize(&modified).unwrap();
    assert_eq!(Ashid::timestamp(&normalized).unwrap(), 1_609_459_200_000);
    assert_eq!(Ashid::random(&normalized).unwrap(), 1111);
}

#[test]
fn normalize_round_trip_preserves_values() {
    let original = Ashid::create(Some("user"), Some(1_609_459_200_000), Some(12345)).unwrap();
    let normalized = Ashid::normalize(&original.to_uppercase()).unwrap();
    assert_eq!(Ashid::timestamp(&normalized).unwrap(), 1_609_459_200_000);
    assert_eq!(Ashid::random(&normalized).unwrap(), 12345);
}

#[test]
fn normalize_fixed_format() {
    let original = Ashid::create(None, Some(1_609_459_200_000), Some(12345)).unwrap();
    let normalized = Ashid::normalize(&original.to_uppercase()).unwrap();
    assert_eq!(normalized, original);
}

#[test]
fn normalize_dash_to_underscore() {
    let with_dash = "user-1kbg1jmtt0000000000000";
    let normalized = Ashid::normalize(with_dash).unwrap();
    assert!(normalized.starts_with("user_"));
    assert_eq!(Ashid::prefix(&normalized).unwrap(), "user_");
}

#[test]
fn normalize_dash_equals_underscore() {
    let with_dash = "user-1kbg1jmtt0000000000000";
    let with_under = "user_1kbg1jmtt0000000000000";
    assert_eq!(
        Ashid::normalize(with_dash).unwrap(),
        Ashid::normalize(with_under).unwrap()
    );
}

#[test]
fn normalize_preserves_64bit_entropy() {
    let r = u64::MAX;
    let original = Ashid::create(Some("user"), Some(1_609_459_200_000), Some(r)).unwrap();
    let normalized = Ashid::normalize(&original.to_uppercase()).unwrap();
    assert_eq!(Ashid::random(&normalized).unwrap(), r);
}

// ---------- normalize() — ashid4 round-trip ----------
//
// Regression coverage: prior to the unified normalize, the function routed
// through create() (timestamp + random) for every input, which encoded the
// first half unpadded. For ashid4 inputs with leading zeros at the front of
// the first long, those zeros got dropped — corrupting the value.

#[test]
fn normalize_ashid4_with_prefix_full_entropy_identity() {
    let original = Ashid::create4(
        Some("tok"),
        Some(0x112210f47de98115),
        Some(0x88f7bfa8ac471d31),
    )
    .unwrap();
    assert_eq!(Ashid::normalize(&original).unwrap(), original);
}

#[test]
fn normalize_ashid4_no_prefix_full_entropy_identity() {
    let original =
        Ashid::create4(None, Some(0x112210f47de98115), Some(0x88f7bfa8ac471d31)).unwrap();
    assert_eq!(Ashid::normalize(&original).unwrap(), original);
}

#[test]
fn normalize_ashid4_uppercased_back_to_canonical() {
    let original = Ashid::create4(
        Some("tok"),
        Some(0xdeadbeefcafebabe),
        Some(0x0123456789abcdef),
    )
    .unwrap();
    let upper = original.to_uppercase();
    assert_eq!(Ashid::normalize(&upper).unwrap(), original);
}

#[test]
fn normalize_ashid4_uppercased_no_prefix_back_to_canonical() {
    let original =
        Ashid::create4(None, Some(0xdeadbeefcafebabe), Some(0x0123456789abcdef)).unwrap();
    let upper = original.to_uppercase();
    assert_eq!(Ashid::normalize(&upper).unwrap(), original);
}

#[test]
fn normalize_ashid4_small_first_long_collapses_to_v1_shape() {
    let r1: u64 = 1;
    let r2: u64 = 0;
    let original = Ashid::create4(Some("tok"), Some(r1), Some(r2)).unwrap();
    let normalized = Ashid::normalize(&original).unwrap();
    assert_eq!(Ashid::timestamp(&normalized).unwrap(), r1);
    assert_eq!(Ashid::random(&normalized).unwrap(), r2);
}

#[test]
fn normalize_idempotent() {
    let v1 = Ashid::create(Some("user"), Some(1_609_459_200_000), Some(12345)).unwrap();
    let once = Ashid::normalize(&v1).unwrap();
    assert_eq!(Ashid::normalize(&once).unwrap(), once);

    let a4 = Ashid::create4(
        Some("tok"),
        Some(0xdeadbeefcafebabe),
        Some(0x0123456789abcdef),
    )
    .unwrap();
    let a4_once = Ashid::normalize(&a4).unwrap();
    assert_eq!(a4_once, a4);
    assert_eq!(Ashid::normalize(&a4_once).unwrap(), a4);
}

#[test]
fn normalize_v1_and_matching_ashid4_collapse_to_same_canonical() {
    let long1: u64 = 1_609_459_200_000;
    let long2: u64 = 12345;
    let v1 = Ashid::create(Some("tok"), Some(long1), Some(long2)).unwrap();
    let a4 = Ashid::create4(Some("tok"), Some(long1), Some(long2)).unwrap();
    assert_eq!(
        Ashid::normalize(&v1).unwrap(),
        Ashid::normalize(&a4).unwrap()
    );
    assert_eq!(Ashid::normalize(&v1).unwrap(), v1);
}

// ---------- is_valid ----------

#[test]
fn is_valid_correct_ashids() {
    assert!(Ashid::is_valid(&Ashid::create(None, None, None).unwrap()));
    assert!(Ashid::is_valid(
        &Ashid::create(Some("u"), None, None).unwrap()
    ));
    assert!(Ashid::is_valid(
        &Ashid::create(Some("user"), None, None).unwrap()
    ));
}

#[test]
fn is_valid_timestamp_zero() {
    assert!(Ashid::is_valid(
        &Ashid::create(None, Some(0), Some(0)).unwrap()
    ));
    assert!(Ashid::is_valid(
        &Ashid::create(Some("u"), Some(0), Some(0)).unwrap()
    ));
    assert!(Ashid::is_valid(
        &Ashid::create(Some("user"), Some(0), Some(0)).unwrap()
    ));
}

#[test]
fn is_valid_rejects_empty() {
    assert!(!Ashid::is_valid(""));
}

#[test]
fn is_valid_rejects_invalid_base32() {
    assert!(!Ashid::is_valid("user_invalid!@#"));
}

#[test]
fn is_valid_rejects_wrong_length_no_delimiter() {
    assert!(!Ashid::is_valid("abc123"));
}

#[test]
fn is_valid_22_char_base_no_delimiter() {
    assert!(Ashid::is_valid("0000000000000000000000"));
}

// ---------- time-sortability ----------

#[test]
fn sortable_no_prefix() {
    let base = 1_700_000_000_000u64;
    let id1 = Ashid::create(None, Some(base), Some(0)).unwrap();
    let id2 = Ashid::create(None, Some(base + 1000), Some(0)).unwrap();
    let id3 = Ashid::create(None, Some(base + 2000), Some(0)).unwrap();

    let mut v = vec![id3.clone(), id1.clone(), id2.clone()];
    v.sort();
    assert_eq!(v, vec![id1, id2, id3]);
}

#[test]
fn sortable_with_prefix() {
    let base = 1_700_000_000_000u64;
    let id1 = Ashid::create(Some("user"), Some(base), Some(0)).unwrap();
    let id2 = Ashid::create(Some("user"), Some(base + 1000), Some(0)).unwrap();
    let id3 = Ashid::create(Some("user"), Some(base + 2000), Some(0)).unwrap();

    let mut v = vec![id3.clone(), id1.clone(), id2.clone()];
    v.sort();
    assert_eq!(v, vec![id1, id2, id3]);
}

#[test]
fn sort_by_random_when_timestamp_same() {
    let ts = 1_700_000_000_000u64;
    let id1 = Ashid::create(None, Some(ts), Some(1000)).unwrap();
    let id2 = Ashid::create(None, Some(ts), Some(2000)).unwrap();
    let mut v = vec![id2.clone(), id1.clone()];
    v.sort();
    assert_eq!(v, vec![id1, id2]);
}

// ---------- convenience ----------

#[test]
fn new_creates_valid_ashid() {
    let id = new(None);
    assert!(Ashid::is_valid(&id));
}

#[test]
fn new_with_prefix() {
    let id = new(Some("user"));
    assert!(id.starts_with("user_"));
}

#[test]
fn parse_ashid_returns_tuple() {
    let (prefix, ts, rand) = parse_ashid("user_1kbg1jmtt0000000000000").unwrap();
    assert_eq!(prefix, "user_");
    assert_eq!(ts, "1kbg1jmtt");
    assert_eq!(rand, "0000000000000");
}

// ---------- double-click selectability ----------

#[test]
fn no_hyphens_in_output() {
    let id = new(Some("user"));
    assert!(!id.contains('-'));
}

#[test]
fn no_spaces_in_output() {
    let id = new(Some("user"));
    assert!(!id.contains(' '));
}

#[test]
fn output_only_alphanumeric_and_underscore() {
    let id1 = new(None);
    let id2 = new(Some("user"));
    assert!(id1
        .chars()
        .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit()));
    assert!(id2
        .chars()
        .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_'));
}

// ---------- ashid4 ----------

#[test]
fn ashid4_no_prefix_is_26_chars() {
    let id = new4(None);
    assert_eq!(id.len(), 26);
    assert!(Ashid::is_valid(&id));
}

#[test]
fn ashid4_with_prefix() {
    let id = new4(Some("tok"));
    assert!(id.starts_with("tok_"));
    assert_eq!(id.len(), 30);
}

#[test]
fn ashid4_unique() {
    let mut ids = HashSet::new();
    for _ in 0..1000 {
        ids.insert(new4(None));
    }
    assert_eq!(ids.len(), 1000);
}

#[test]
fn ashid4_uses_full_64bit_entropy() {
    let mut saw_full_entropy = false;
    for _ in 0..200 {
        let id = new4(None);
        let (_, c1, c2) = Ashid::parse(&id).unwrap();
        // strip leading zeros and check length > 11 (>2^55 → needs >11 chars)
        let stripped1 = c1.trim_start_matches('0');
        let stripped2 = c2.trim_start_matches('0');
        if stripped1.len() > 11 || stripped2.len() > 11 {
            saw_full_entropy = true;
            break;
        }
    }
    assert!(
        saw_full_entropy,
        "expected at least one ashid4 with full 64-bit entropy in 200 samples"
    );
}

#[test]
fn ashid4_explicit_random() {
    let id = Ashid::create4(Some("tok"), Some(123_456_789), Some(987_654_321)).unwrap();
    assert!(id.starts_with("tok_"));
}

#[test]
fn ashid4_preserves_64bit_in_roundtrip() {
    let r1 = u64::MAX;
    let r2 = 1u64 << 63;
    let id = Ashid::create4(Some("tok"), Some(r1), Some(r2)).unwrap();
    let (prefix, c1, c2) = Ashid::parse(&id).unwrap();
    assert_eq!(prefix, "tok_");
    assert_eq!(c1.len(), 13);
    assert_eq!(c2.len(), 13);
}

// ---------- create4 padding lockdown ----------
//
// create4 must always emit both halves 13-char padded, regardless of input
// magnitude. These pin the wire format so a refactor that drops padding
// (e.g. routing create4 through an unpadded builder) fails loudly. Mirrors
// typescript/test/ashid.test.ts.

#[test]
fn create4_padding_zero_zero_with_prefix() {
    let id = Ashid::create4(Some("tok"), Some(0), Some(0)).unwrap();
    assert_eq!(id, "tok_00000000000000000000000000");
}

#[test]
fn create4_padding_zero_zero_no_prefix() {
    let id = Ashid::create4(None, Some(0), Some(0)).unwrap();
    assert_eq!(id, "00000000000000000000000000");
}

#[test]
fn create4_padding_one_zero_with_prefix() {
    let id = Ashid::create4(Some("tok"), Some(1), Some(0)).unwrap();
    assert_eq!(id, "tok_00000000000010000000000000");
}

#[test]
fn create4_padding_crockford_z_with_prefix() {
    let id = Ashid::create4(Some("tok"), Some(31), Some(0)).unwrap();
    assert_eq!(id, "tok_000000000000z0000000000000");
}

#[test]
fn create4_padding_max_u64_first_half() {
    let id = Ashid::create4(Some("tok"), Some(u64::MAX), Some(0)).unwrap();
    assert_eq!(id, "tok_fzzzzzzzzzzzz0000000000000");
    assert_eq!(id.len(), 3 + 1 + 26);
}

#[test]
fn create4_padding_max_u64_second_half() {
    let id = Ashid::create4(Some("tok"), Some(0), Some(u64::MAX)).unwrap();
    assert_eq!(id, "tok_0000000000000fzzzzzzzzzzzz");
    assert_eq!(id.len(), 3 + 1 + 26);
}

#[test]
fn create4_padding_no_prefix_always_26_chars() {
    let samples: &[(u64, u64)] = &[
        (0, 0),
        (1, 0),
        (0, 1),
        (31, 31),
        (u64::MAX, 0),
        (0, u64::MAX),
    ];
    for &(r1, r2) in samples {
        let id = Ashid::create4(None, Some(r1), Some(r2)).unwrap();
        assert_eq!(id.len(), 26, "create4(None, {}, {})", r1, r2);
    }
}

#[test]
fn create4_padding_with_prefix_length_invariant() {
    let samples: &[(u64, u64)] = &[(0, 0), (1, 0), (u64::MAX, u64::MAX)];
    for &(r1, r2) in samples {
        let id = Ashid::create4(Some("tok"), Some(r1), Some(r2)).unwrap();
        assert_eq!(id.len(), 3 + 1 + 26, "create4(tok, {}, {})", r1, r2);
    }
}

// ---------- error types ----------

#[test]
fn error_invalid_id_for_empty() {
    use ashid::AshidError;
    match Ashid::parse("") {
        Err(AshidError::InvalidId(_)) => {}
        other => panic!("expected InvalidId, got {:?}", other),
    }
}

#[test]
fn error_invalid_timestamp_above_max() {
    use ashid::AshidError;
    match Ashid::create(None, Some(35_184_372_088_832), Some(0)) {
        Err(AshidError::InvalidTimestamp(_)) => {}
        other => panic!("expected InvalidTimestamp, got {:?}", other),
    }
}

#[test]
fn error_invalid_char_for_bad_decode() {
    use ashid::AshidError;
    match ashid::EncoderBase32Crockford::decode("abc-def") {
        Err(AshidError::InvalidChar(_)) => {}
        other => panic!("expected InvalidChar, got {:?}", other),
    }
}

// ---------- UUID round-trip ----------

#[test]
fn to_uuid_format() {
    let id = Ashid::create(Some("user"), Some(1733140800000), Some(8234567890123456789)).unwrap();
    let uuid = Ashid::to_uuid(&id).unwrap();
    assert_eq!(uuid.len(), 36);
    let parts: Vec<&str> = uuid.split('-').collect();
    assert_eq!(parts.len(), 5);
    assert_eq!(
        parts.iter().map(|p| p.len()).collect::<Vec<_>>(),
        vec![8, 4, 4, 4, 12]
    );
    assert!(uuid.chars().all(|c| c == '-' || c.is_ascii_hexdigit()));
}

#[test]
fn to_uuid_is_prefix_agnostic() {
    let with_prefix =
        Ashid::create(Some("user"), Some(1733140800000), Some(8234567890123456789)).unwrap();
    let no_prefix = Ashid::create(None, Some(1733140800000), Some(8234567890123456789)).unwrap();
    assert_eq!(
        Ashid::to_uuid(&with_prefix).unwrap(),
        Ashid::to_uuid(&no_prefix).unwrap()
    );
}

#[test]
fn roundtrip_standard_form_through_uuid() {
    let original =
        Ashid::create(Some("user"), Some(1733140800000), Some(8234567890123456789)).unwrap();
    let uuid = Ashid::to_uuid(&original).unwrap();
    let restored = Ashid::from_uuid(&uuid, Some("user")).unwrap();
    assert_eq!(original, restored);
}

#[test]
fn roundtrip_long_form_through_uuid() {
    let original = Ashid::create4(Some("tok"), Some(u64::MAX), Some(1u64 << 63)).unwrap();
    let uuid = Ashid::to_uuid(&original).unwrap();
    let restored = Ashid::from_uuid(&uuid, Some("tok")).unwrap();
    assert_eq!(original, restored);
}

#[test]
fn uuid1_shaped_routes_to_22_char() {
    let uuid = "00000123-abcd-ef01-2345-6789abcdef01";
    let id = Ashid::from_uuid(uuid, None).unwrap();
    assert_eq!(id.len(), 22);
    assert_eq!(Ashid::to_uuid(&id).unwrap(), uuid);
}

#[test]
fn uuidv4_routes_to_26_char() {
    let uuid = "550e8400-e29b-41d4-a716-446655440000";
    let id = Ashid::from_uuid(uuid, None).unwrap();
    assert_eq!(id.len(), 26);
    assert_eq!(Ashid::to_uuid(&id).unwrap(), uuid);
}

#[test]
fn uuidv7_routes_to_26_char() {
    let uuid = "019e008b-edc4-7265-8312-f6a278b46b11";
    let id = Ashid::from_uuid(uuid, None).unwrap();
    assert_eq!(id.len(), 26);
    assert_eq!(Ashid::to_uuid(&id).unwrap(), uuid);
}

#[test]
fn roundtrips_canonical_uuids_from_post() {
    for uuid in [
        "550e8400-e29b-41d4-a716-446655440000",
        "7c9e6679-7425-40de-944b-e07fc1f90ae7",
        "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    ] {
        let id = Ashid::from_uuid(uuid, None).unwrap();
        assert_eq!(Ashid::to_uuid(&id).unwrap(), uuid);
    }
}

#[test]
fn accepts_undashed_input() {
    let dashed = "550e8400-e29b-41d4-a716-446655440000";
    let undashed = "550e8400e29b41d4a716446655440000";
    assert_eq!(
        Ashid::from_uuid(dashed, None).unwrap(),
        Ashid::from_uuid(undashed, None).unwrap()
    );
}

#[test]
fn preserves_prefix_through_from_uuid() {
    let id = Ashid::from_uuid("550e8400-e29b-41d4-a716-446655440000", Some("user")).unwrap();
    assert!(id.starts_with("user_"));
    let uuid = Ashid::to_uuid(&id).unwrap();
    assert_eq!(Ashid::from_uuid(&uuid, Some("user")).unwrap(), id);
}

#[test]
fn handles_all_zero_uuid() {
    let uuid = "00000000-0000-0000-0000-000000000000";
    let id = Ashid::from_uuid(uuid, None).unwrap();
    assert_eq!(Ashid::to_uuid(&id).unwrap(), uuid);
}

#[test]
fn handles_all_ff_uuid() {
    let uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";
    let id = Ashid::from_uuid(uuid, None).unwrap();
    assert_eq!(id.len(), 26);
    assert_eq!(Ashid::to_uuid(&id).unwrap(), uuid);
}

#[test]
fn invalid_uuid_returns_error() {
    use ashid::AshidError;
    for bad in [
        "not-a-uuid",
        "550e8400e29b41d4a71644665544000",
        "550e8400-e29b-41d4-a716-44665544000g",
    ] {
        match Ashid::from_uuid(bad, None) {
            Err(AshidError::InvalidUuid(_)) => {}
            other => panic!("expected InvalidUuid for {:?}, got {:?}", bad, other),
        }
    }
}
