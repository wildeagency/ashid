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
 * import { ashid } from 'ashid';
 *
 * const id = ashid();              // "1fvszawr42tve3gxvx9900"
 * const userId = ashid('user_');   // "user_1fvszawr42tve3gxvx9900"
 * const shortId = ashid('u');      // "u1fvszawr42tve3gxvx9900"
 * ```
 *
 * @example
 * Advanced usage:
 * ```typescript
 * import { Ashid, parseAshid } from 'ashid';
 *
 * // Parse components
 * const { prefix, baseId } = parseAshid('user_1fvszawr42tve3gxvx9900');
 *
 * // Extract timestamp
 * const timestamp = Ashid.timestamp('user_1fvszawr42tve3gxvx9900');
 *
 * // Validate
 * const isValid = Ashid.isValid('user_1fvszawr42tve3gxvx9900');
 * ```
 */

// Core Ashid class and utility functions
export { Ashid, ashid, parseAshid } from './ashid';

// Encoder (for advanced usage and testing)
export { EncoderBase32Crockford } from './encoder';
