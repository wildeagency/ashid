//! Ashid — time-sortable unique identifiers with optional type prefixes.
//!
//! Format: `[prefix_][timestamp][random]`
//!
//! - Crockford Base32 alphabet (`0-9`, `a-z` excluding `i`, `l`, `o`, `u`)
//! - Without prefix: fixed 22-char base (9 timestamp + 13 random)
//! - With prefix: variable-length base ID
//! - `MAX_TIMESTAMP`: 35184372088831 (Dec 12, 3084)
//!
//! ```
//! use ashid::{Ashid, new, new4};
//!
//! let id = new(None);
//! assert!(Ashid::is_valid(&id));
//! ```

mod ashid;
mod encoder;
mod error;

pub use crate::ashid::{new, new4, parse_ashid, Ashid};
pub use encoder::EncoderBase32Crockford;
pub use error::{AshidError, InvalidIdReason};

/// Maximum supported timestamp (Dec 12, 3084).
pub const MAX_TIMESTAMP: u64 = 35_184_372_088_831;
