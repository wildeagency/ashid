use crate::error::AshidError;

const ALPHABET: &[u8; 32] = b"0123456789abcdefghjkmnpqrstvwxyz";
const PADDED_LENGTH: usize = 13;

/// Crockford Base32 encoder/decoder.
///
/// - Alphabet: `0123456789abcdefghjkmnpqrstvwxyz` (excludes `i`, `l`, `o`, `u`)
/// - Decode lookalikes: `I`/`L` → `1`, `O` → `0`, `U` → `V`
/// - Operates on `u64` (covers full 64-bit range in 13 chars)
pub struct EncoderBase32Crockford;

const fn build_decode_table() -> [i8; 256] {
    let mut t = [-1i8; 256];
    let mut i = 0u8;
    while i < 10 {
        t[(b'0' + i) as usize] = i as i8;
        i += 1;
    }
    t[b'a' as usize] = 10;
    t[b'A' as usize] = 10;
    t[b'b' as usize] = 11;
    t[b'B' as usize] = 11;
    t[b'c' as usize] = 12;
    t[b'C' as usize] = 12;
    t[b'd' as usize] = 13;
    t[b'D' as usize] = 13;
    t[b'e' as usize] = 14;
    t[b'E' as usize] = 14;
    t[b'f' as usize] = 15;
    t[b'F' as usize] = 15;
    t[b'g' as usize] = 16;
    t[b'G' as usize] = 16;
    t[b'h' as usize] = 17;
    t[b'H' as usize] = 17;
    t[b'j' as usize] = 18;
    t[b'J' as usize] = 18;
    t[b'k' as usize] = 19;
    t[b'K' as usize] = 19;
    t[b'm' as usize] = 20;
    t[b'M' as usize] = 20;
    t[b'n' as usize] = 21;
    t[b'N' as usize] = 21;
    t[b'p' as usize] = 22;
    t[b'P' as usize] = 22;
    t[b'q' as usize] = 23;
    t[b'Q' as usize] = 23;
    t[b'r' as usize] = 24;
    t[b'R' as usize] = 24;
    t[b's' as usize] = 25;
    t[b'S' as usize] = 25;
    t[b't' as usize] = 26;
    t[b'T' as usize] = 26;
    t[b'v' as usize] = 27;
    t[b'V' as usize] = 27;
    t[b'w' as usize] = 28;
    t[b'W' as usize] = 28;
    t[b'x' as usize] = 29;
    t[b'X' as usize] = 29;
    t[b'y' as usize] = 30;
    t[b'Y' as usize] = 30;
    t[b'z' as usize] = 31;
    t[b'Z' as usize] = 31;
    // Lookalikes
    t[b'o' as usize] = 0;
    t[b'O' as usize] = 0;
    t[b'i' as usize] = 1;
    t[b'I' as usize] = 1;
    t[b'l' as usize] = 1;
    t[b'L' as usize] = 1;
    t[b'u' as usize] = 27;
    t[b'U' as usize] = 27;
    t
}

const DECODE_TABLE: [i8; 256] = build_decode_table();

impl EncoderBase32Crockford {
    /// Encode a `u64` to Crockford Base32. When `padded`, the result is left-padded
    /// with `0` to 13 characters (enough to hold any `u64`).
    pub fn encode(n: u64, padded: bool) -> String {
        let mut buf = [b'0'; PADDED_LENGTH];
        let mut idx = PADDED_LENGTH;
        let mut value = n;

        // Always write at least one digit, even for n == 0 (writes '0' at the last slot).
        loop {
            idx -= 1;
            let rem = (value % 32) as usize;
            buf[idx] = ALPHABET[rem];
            value /= 32;
            if value == 0 {
                break;
            }
        }

        // Buffer is ASCII (alphabet bytes + leading '0' fills); from_utf8 is safe.
        if padded {
            String::from_utf8(buf.to_vec()).unwrap()
        } else {
            String::from_utf8(buf[idx..].to_vec()).unwrap()
        }
    }

    /// Decode a Crockford Base32 string to `u64`. Lookalikes (`I`/`L`/`O`/`U`)
    /// map to their canonical digits; matching is case-insensitive.
    pub fn decode(s: &str) -> Result<u64, AshidError> {
        if s.is_empty() {
            return Err(AshidError::InvalidId("input string cannot be empty".into()));
        }

        let mut result: u64 = 0;
        for ch in s.chars() {
            let code = ch as u32;
            let value = if code < 256 {
                DECODE_TABLE[code as usize]
            } else {
                -1
            };
            if value < 0 {
                return Err(AshidError::InvalidChar(ch));
            }
            // Wrapping arithmetic: matches TS/Kotlin behavior on >13-char inputs,
            // where the encoder silently loses precision rather than throwing.
            // For valid Ashid components (≤13 chars random, ≤9 chars timestamp v1)
            // the value always fits in u64.
            result = result.wrapping_mul(32).wrapping_add(value as u64);
        }
        Ok(result)
    }

    /// Generate a cryptographically secure random `u64` (full 64-bit entropy).
    pub fn secure_random_long() -> u64 {
        let mut buf = [0u8; 8];
        getrandom::getrandom(&mut buf).expect("OS RNG failure");
        u64::from_be_bytes(buf)
    }

    /// Validate whether a string is a well-formed Crockford Base32 number.
    pub fn is_valid(s: &str) -> bool {
        !s.is_empty() && Self::decode(s).is_ok()
    }
}
