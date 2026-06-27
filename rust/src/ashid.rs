use std::time::{SystemTime, UNIX_EPOCH};

use crate::encoder::EncoderBase32Crockford;
use crate::error::{AshidError, InvalidIdReason};
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
            return Err(AshidError::InvalidTimestamp(time));
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
            let raw = EncoderBase32Crockford::encode(time, false);
            let time_encoded = format!("{:0>width$}", raw, width = TIMESTAMP_ENCODED_LENGTH);
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
            return Err(AshidError::InvalidId(InvalidIdReason::Empty));
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

        // If no delimiter was found, the whole string is the base — even if it
        // happens to be all-alpha (e.g. an ashid4 whose random component encodes
        // to letters only).
        if !has_delimiter {
            prefix_length = 0;
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
            return Err(AshidError::InvalidId(InvalidIdReason::MissingBase));
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
            return Err(AshidError::InvalidId(InvalidIdReason::WrongBaseLength(
                base_id.len(),
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
        let Ok((prefix, ts, rand)) = Self::parse(id) else {
            return false;
        };
        if !prefix.is_empty() && !is_valid_prefix(&prefix) {
            return false;
        }
        EncoderBase32Crockford::decode(&ts).is_ok() && EncoderBase32Crockford::decode(&rand).is_ok()
    }

    /// Convert an Ashid to its UUID-shaped representation.
    ///
    /// An Ashid encodes 128 bits of information (a 64-bit timestamp or first
    /// random component plus a 64-bit random component); this method emits those
    /// 128 bits as a standard 36-character UUID string with dashes (8-4-4-4-12).
    ///
    /// Round-trips losslessly with [`Ashid::from_uuid`]. The resulting UUID is
    /// shape-compatible with RFC 4122 but the version/variant bits reflect the
    /// underlying Ashid bytes, not RFC 4122 conventions, unless the Ashid was
    /// originally created from a v1/v4/v7 UUID.
    pub fn to_uuid(id: &str) -> Result<String, AshidError> {
        let (_, ts, rand) = Self::parse(id)?;
        let high = EncoderBase32Crockford::decode(&ts)?;
        let low = EncoderBase32Crockford::decode(&rand)?;
        Ok(format!(
            "{:08x}-{:04x}-{:04x}-{:04x}-{:012x}",
            (high >> 32) as u32,
            ((high >> 16) & 0xffff) as u16,
            (high & 0xffff) as u16,
            (low >> 48) as u16,
            low & 0xffff_ffff_ffff,
        ))
    }

    /// Convert a UUID string into an Ashid.
    ///
    /// Splits the 128-bit UUID into two 64-bit halves: the high half becomes the
    /// timestamp (or first random component, see below) and the low half becomes
    /// the random component.
    ///
    /// - If the high half fits in 45 bits (≤ `MAX_TIMESTAMP`), the result is a
    ///   standard 22-char Ashid base.
    /// - Otherwise — for UUIDv4 (random) and UUIDv7 (whose version bits force
    ///   the high half above 2^45) — the result is a 26-char ashid4 base.
    ///
    /// Round-trip through [`Ashid::to_uuid`] is byte-identical in both cases.
    pub fn from_uuid(uuid: &str, prefix: Option<&str>) -> Result<String, AshidError> {
        let hex: String = uuid
            .chars()
            .filter(|c| *c != '-')
            .flat_map(|c| c.to_lowercase())
            .collect();
        if hex.len() != 32 || !hex.chars().all(|c| c.is_ascii_hexdigit()) {
            return Err(AshidError::InvalidUuid(uuid.to_string()));
        }
        let high = u64::from_str_radix(&hex[..16], 16)
            .map_err(|_| AshidError::InvalidUuid(uuid.to_string()))?;
        let low = u64::from_str_radix(&hex[16..32], 16)
            .map_err(|_| AshidError::InvalidUuid(uuid.to_string()))?;

        if high <= MAX_TIMESTAMP {
            Self::create(prefix, Some(high), Some(low))
        } else {
            Self::create4(prefix, Some(high), Some(low))
        }
    }

    /// Normalize an Ashid: lowercase the prefix, canonicalize lookalike chars
    /// (`I`/`L` → `1`, `O` → `0`, `U` → `V`), convert dash delimiter to underscore.
    ///
    /// Type-1 inputs round-trip to type-1 shape; full-entropy ashid4 inputs
    /// round-trip to ashid4 shape (the first long naturally fills 13 chars).
    /// An ashid4 with a small first long collapses to type-1 shape — the two
    /// longs survive, only the string shape changes.
    pub fn normalize(id: &str) -> Result<String, AshidError> {
        let (prefix, encoded_first, encoded_second) = Self::parse(id)?;
        let long1 = EncoderBase32Crockford::decode(&encoded_first)?;
        let long2 = EncoderBase32Crockford::decode(&encoded_second)?;

        let normalized_prefix: Option<String> = if prefix.is_empty() {
            None
        } else {
            Some(prefix.to_ascii_lowercase())
        };
        Ok(build_base(
            normalized_prefix.as_deref(),
            long1,
            long2,
            false,
        ))
    }
}

/// Encode two `u64` components into the canonical base ID form.
///
/// Mirrors the TypeScript 1.7.0 `buildBase` with three shapes:
///   - Minimal form (prefix + `!padded` + `n1 == 0`): encoded1 is omitted
///     entirely and encoded2 is unpadded -> `prefix_<encoded2>`. Parse
///     recognises this via the `base_id.len() <= RANDOM_ENCODED_LENGTH`
///     branch.
///   - Type-1 with prefix (prefix + `!padded` + `n1 != 0`): encoded1 is
///     unpadded, encoded2 is 13-char padded (the parse anchor).
///   - No prefix or `padded`: encoded2 is 13-char padded; encoded1 is
///     padded to 9 chars (standard 22-char base) if it fits, else 13
///     (ashid4 26-char base).
fn build_base(prefix: Option<&str>, n1: u64, n2: u64, padded: bool) -> String {
    let normalized_prefix = normalize_prefix(prefix);
    let has_prefix = normalized_prefix.is_some();

    if has_prefix && !padded && n1 == 0 {
        return normalized_prefix.unwrap_or_default() + &EncoderBase32Crockford::encode(n2, false);
    }

    let encoded1 = if has_prefix || padded {
        EncoderBase32Crockford::encode(n1, padded)
    } else {
        let raw = EncoderBase32Crockford::encode(n1, false);
        let pad_to = if raw.len() <= TIMESTAMP_ENCODED_LENGTH {
            TIMESTAMP_ENCODED_LENGTH
        } else {
            RANDOM_ENCODED_LENGTH
        };
        format!("{:0>width$}", raw, width = pad_to)
    };
    let encoded2 = EncoderBase32Crockford::encode(n2, true);
    normalized_prefix.unwrap_or_default() + &encoded1 + &encoded2
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
