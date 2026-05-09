use std::fmt;

use crate::MAX_TIMESTAMP;

/// Errors produced by Ashid encoding, parsing, and validation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AshidError {
    /// Timestamp exceeds `MAX_TIMESTAMP` (Dec 12, 3084). Carries the offending value.
    InvalidTimestamp(u64),
    /// Ashid string is malformed. The variant identifies which structural rule failed.
    InvalidId(InvalidIdReason),
    /// Base32 decoder hit a character outside the Crockford alphabet.
    InvalidChar(char),
    /// `from_uuid` received a string that is not 32 or 36 hex characters.
    InvalidUuid(String),
}

/// Specific structural failure encountered while parsing an Ashid.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InvalidIdReason {
    /// Input string was empty.
    Empty,
    /// Prefix had a delimiter but no base ID followed it.
    MissingBase,
    /// Base ID without a delimiter must be 22 or 26 characters; carries the actual length.
    WrongBaseLength(usize),
}

impl fmt::Display for AshidError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AshidError::InvalidTimestamp(value) => write!(
                f,
                "Ashid timestamp must not exceed {} (Dec 12, 3084), got {}",
                MAX_TIMESTAMP, value
            ),
            AshidError::InvalidId(reason) => match reason {
                InvalidIdReason::Empty => write!(f, "invalid ashid: cannot be empty"),
                InvalidIdReason::MissingBase => write!(f, "invalid ashid: must have a base ID"),
                InvalidIdReason::WrongBaseLength(got) => write!(
                    f,
                    "invalid ashid: base ID must be 22 or 26 characters without delimiter (got {})",
                    got
                ),
            },
            AshidError::InvalidChar(ch) => {
                write!(f, "invalid character in Base32 string: '{}'", ch)
            }
            AshidError::InvalidUuid(s) => {
                write!(f, "invalid UUID: must be 32 or 36 hex characters (got \"{}\")", s)
            }
        }
    }
}

impl std::error::Error for AshidError {}
