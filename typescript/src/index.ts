/**
 * Ashid - Ash Unique IDs
 *
 * Time-sortable unique identifiers with optional type prefixes.
 * Designed for easy double-click selection (no separators).
 *
 * @packageDocumentation
 *
 * @example
 * Basic usage:
 * ```typescript
 * import { ashid, ashid4 } from 'ashid';
 *
 * const id = ashid();              // "1fvszawr42tve3gxvx9900" (22 chars, time-sortable)
 * const userId = ashid('user_');   // "user_1fvszawr42tve3gxvx9900"
 * const token = ashid4('tok_');    // "tok_x7k9m2p4q8r1..." (30 chars, random like UUID v4)
 * ```
 *
 * @example
 * Advanced usage:
 * ```typescript
 * import { Ashid, parseAshid } from 'ashid';
 *
 * // Parse components
 * const [prefix, timestamp, random] = parseAshid('user_1fvszawr42tve3gxvx9900');
 *
 * // Extract timestamp
 * const timestamp = Ashid.timestamp('user_1fvszawr42tve3gxvx9900');
 *
 * // Validate
 * const isValid = Ashid.isValid('user_1fvszawr42tve3gxvx9900');
 * ```
 */

// Core Ashid class and utility functions
export { Ashid, ashid, ashid4, parseAshid } from './ashid';

// Encoder (for advanced usage and testing)
export { EncoderBase32Crockford } from './encoder';
