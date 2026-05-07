use std::time::{SystemTime, UNIX_EPOCH};

use crate::encoder::EncoderBase32Crockford;
use crate::error::AshidError;
use crate::MAX_TIMESTAMP;

const RANDOM_ENCODED_LENGTH: usize = 13;
const TIMESTAMP_ENCODED_LENGTH: usize = 9;
const STANDARD_BASE_LENGTH: usize = 22;
const ASHID4_BASE_LENGTH: usize = 26;

/// Time-sortable unique identifier with optional type prefix.
///
/// Format: `[prefix_][timestamp][random]`
///
/// - With prefix: variable-length base ID (timestamp omitted when 0)
/// - Without prefix: fixed 22-char base (9 timestamp + 13 random) for v1,
///   or 26-char base (13 + 13) for v4.
pub struct Ashid;

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn normalize_prefix(prefix: Option<&str>) -> Option<String> {
    let raw = prefix?;
    if raw.is_empty() {
        return None;
    }
    let cleaned: String = raw
        .chars()
        .filter(|c| c.is_ascii_alphanumeric())
        .map(|c| c.to_ascii_lowercase())
        .collect();
    if cleaned.is_empty() {
        None
    } else {
        Some(cleaned + "_")
    }
}

fn pad_left(s: &str, target_len: usize, pad: char) -> String {
    if s.len() >= target_len {
        s.to_string()
    } else {
        let mut out = String::with_capacity(target_len);
        for _ in 0..(target_len - s.len()) {
            out.push(pad);
        }
        out.push_str(s);
        out
    }
}

impl Ashid {
    /// Create a new Ashid with optional prefix, timestamp, and random value.
    /// Defaults to current time and a secure random `u64`.
    pub fn create(
        prefix: Option<&str>,
        time: Option<u64>,
        random_long: Option<u64>,
    ) -> Result<String, AshidError> {
        let normalized_prefix = normalize_prefix(prefix);

        let time = time.unwrap_or_else(now_ms);
        if time > MAX_TIMESTAMP {
            return Err(AshidError::InvalidTimestamp(format!(
                "Ashid timestamp must not exceed {} (Dec 12, 3084)",
                MAX_TIMESTAMP
            )));
        }

        let random = random_long.unwrap_or_else(EncoderBase32Crockford::secure_random_long);

        let base_id = if normalized_prefix.is_some() {
            if time > 0 {
                let random_encoded = EncoderBase32Crockford::encode(random, true);
                EncoderBase32Crockford::encode(time, false) + &random_encoded
            } else {
                EncoderBase32Crockford::encode(random, false)
            }
        } else {
            let time_encoded = pad_left(
                &EncoderBase32Crockford::encode(time, false),
                TIMESTAMP_ENCODED_LENGTH,
                '0',
            );
            let random_encoded = EncoderBase32Crockford::encode(random, true);
            time_encoded + &random_encoded
        };

        Ok(normalized_prefix.unwrap_or_default() + &base_id)
    }

    /// Create a UUID-v4-style Ashid: two padded random `u64`s, 26-char base.
    pub fn create4(
        prefix: Option<&str>,
        random1: Option<u64>,
        random2: Option<u64>,
    ) -> Result<String, AshidError> {
        let normalized_prefix = normalize_prefix(prefix);
        let r1 = random1.unwrap_or_else(EncoderBase32Crockford::secure_random_long);
        let r2 = random2.unwrap_or_else(EncoderBase32Crockford::secure_random_long);

        let encoded1 = EncoderBase32Crockford::encode(r1, true);
        let encoded2 = EncoderBase32Crockford::encode(r2, true);
        let base_id = encoded1 + &encoded2;

        Ok(normalized_prefix.unwrap_or_default() + &base_id)
    }

    /// Parse an Ashid into `(prefix, encoded_timestamp, encoded_random)`.
    /// Prefix retains its trailing `_` (empty string if no prefix).
    pub fn parse(id: &str) -> Result<(String, String, String), AshidError> {
        if id.is_empty() {
            return Err(AshidError::InvalidId("cannot be empty".into()));
        }

        let bytes = id.as_bytes();
        let mut prefix_length = 0usize;
        let mut has_delimiter = false;

        for &b in bytes.iter() {
            if b.is_ascii_alphabetic() {
                prefix_length += 1;
            } else if (b == b'_' || b == b'-') && prefix_length > 0 {
                prefix_length += 1;
                has_delimiter = true;
                break;
            } else {
                prefix_length = 0;
                break;
            }
        }

        let prefix = if has_delimiter {
            let raw = &id[..prefix_length];
            match raw.strip_suffix('-') {
                Some(stripped) => format!("{}_", stripped),
                None => raw.to_string(),
            }
        } else {
            String::new()
        };

        let base_id = &id[prefix_length..];
        if base_id.is_empty() {
            return Err(AshidError::InvalidId("must have a base ID".into()));
        }

        let (encoded_timestamp, encoded_random) = if has_delimiter {
            if base_id.len() <= RANDOM_ENCODED_LENGTH {
                ("0".to_string(), base_id.to_string())
            } else {
                let split = base_id.len() - RANDOM_ENCODED_LENGTH;
                (base_id[..split].to_string(), base_id[split..].to_string())
            }
        } else if base_id.len() == ASHID4_BASE_LENGTH {
            (
                base_id[..RANDOM_ENCODED_LENGTH].to_string(),
                base_id[RANDOM_ENCODED_LENGTH..].to_string(),
            )
        } else if base_id.len() == STANDARD_BASE_LENGTH {
            (
                base_id[..TIMESTAMP_ENCODED_LENGTH].to_string(),
                base_id[TIMESTAMP_ENCODED_LENGTH..].to_string(),
            )
        } else {
            return Err(AshidError::InvalidId(format!(
                "base ID must be {} or {} characters without delimiter (got {})",
                STANDARD_BASE_LENGTH,
                ASHID4_BASE_LENGTH,
                base_id.len()
            )));
        };

        Ok((prefix, encoded_timestamp, encoded_random))
    }

    /// Extract the prefix (with trailing `_`) from an Ashid; empty string if none.
    pub fn prefix(id: &str) -> Result<String, AshidError> {
        Ok(Self::parse(id)?.0)
    }

    /// Extract the timestamp (in ms) from an Ashid.
    pub fn timestamp(id: &str) -> Result<u64, AshidError> {
        let (_, ts, _) = Self::parse(id)?;
        EncoderBase32Crockford::decode(&ts)
    }

    /// Extract the random component from an Ashid (full 64-bit precision).
    pub fn random(id: &str) -> Result<u64, AshidError> {
        let (_, _, rand) = Self::parse(id)?;
        EncoderBase32Crockford::decode(&rand)
    }

    /// Validate whether a string is a well-formed Ashid.
    pub fn is_valid(id: &str) -> bool {
        if id.is_empty() {
            return false;
        }
        let (prefix, ts, rand) = match Self::parse(id) {
            Ok(parts) => parts,
            Err(_) => return false,
        };
        if !prefix.is_empty() && !is_valid_prefix(&prefix) {
            return false;
        }
        EncoderBase32Crockford::decode(&ts).is_ok() && EncoderBase32Crockford::decode(&rand).is_ok()
    }

    /// Normalize an Ashid: lowercase the prefix, canonicalize lookalike chars
    /// (`I`/`L` → `1`, `O` → `0`, `U` → `V`), convert dash delimiter to underscore.
    pub fn normalize(id: &str) -> Result<String, AshidError> {
        let (prefix, ts, rand) = Self::parse(id)?;
        let timestamp = EncoderBase32Crockford::decode(&ts)?;
        let random = EncoderBase32Crockford::decode(&rand)?;

        let normalized_prefix: Option<String> = if prefix.is_empty() {
            None
        } else {
            Some(prefix.to_ascii_lowercase())
        };
        Self::create(normalized_prefix.as_deref(), Some(timestamp), Some(random))
    }
}

fn is_valid_prefix(prefix: &str) -> bool {
    // Must end with '_' and have alphanumeric chars before that.
    let bytes = prefix.as_bytes();
    if bytes.is_empty() || bytes[bytes.len() - 1] != b'_' {
        return false;
    }
    let body = &bytes[..bytes.len() - 1];
    if body.is_empty() {
        return false;
    }
    body.iter().all(|b| b.is_ascii_alphanumeric())
}

/// Create a new Ashid with optional type prefix.
pub fn new(prefix: Option<&str>) -> String {
    Ashid::create(prefix, None, None).expect("default Ashid creation cannot fail")
}

/// Create a UUID-v4-style Ashid (two 64-bit random components).
pub fn new4(prefix: Option<&str>) -> String {
    Ashid::create4(prefix, None, None).expect("default Ashid creation cannot fail")
}

/// Parse an Ashid into `(prefix, encoded_timestamp, encoded_random)`.
pub fn parse_ashid(id: &str) -> Result<(String, String, String), AshidError> {
    Ashid::parse(id)
}
