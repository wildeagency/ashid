use std::fmt;

/// Errors produced by Ashid encoding, parsing, and validation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum AshidError {
    /// Timestamp is negative (impossible with `u64`) or exceeds `MAX_TIMESTAMP`.
    InvalidTimestamp(String),
    /// Ashid string is malformed (empty, wrong length, missing base ID).
    InvalidId(String),
    /// Prefix violates the alphanumeric + trailing-`_` rule.
    InvalidPrefix(String),
    /// Base32 decoder hit a character outside the Crockford alphabet.
    InvalidChar(char),
}

impl fmt::Display for AshidError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            AshidError::InvalidTimestamp(msg) => write!(f, "invalid timestamp: {}", msg),
            AshidError::InvalidId(msg) => write!(f, "invalid ashid: {}", msg),
            AshidError::InvalidPrefix(msg) => write!(f, "invalid prefix: {}", msg),
            AshidError::InvalidChar(ch) => {
                write!(f, "invalid character in Base32 string: '{}'", ch)
            }
        }
    }
}

impl std::error::Error for AshidError {}
