/**
 * Ashid - Basic Usage Examples (TypeScript)
 *
 * Run with: npx ts-node basic-usage.ts
 * Or: npm install && npm run example
 */

import { Ashid, ashid, parseAshid, EncoderBase32Crockford } from 'ashid';

// ============================================
// 1. Creating Ashids
// ============================================

// Simple creation without prefix
const simpleId = ashid();
console.log('Simple ID:', simpleId);
// Output: 1fvszawr42tve3gxvx9900 (22 chars)

// With a single-letter prefix (no delimiter)
const userId = ashid('u');
console.log('User ID:', userId);
// Output: u1fvszawr42tve3gxvx9900 (23 chars)

// With a multi-letter prefix (no delimiter)
const orderId = ashid('ord');
console.log('Order ID:', orderId);
// Output: ord1fvszawr42tve3gxvx900 (25 chars)

// With underscore delimiter (variable length)
const productId = ashid('product_');
console.log('Product ID:', productId);
// Output: product_1fvszawr...

// ============================================
// 2. Parsing Ashids
// ============================================

// Parse into components
const [prefix, timestamp, random] = parseAshid(userId);
console.log('\nParsed components:');
console.log('  Prefix:', prefix);
console.log('  Timestamp (encoded):', timestamp);
console.log('  Random (encoded):', random);

// Extract individual components
console.log('\nExtracted values:');
console.log('  Prefix:', Ashid.prefix(userId));
console.log('  Timestamp (ms):', Ashid.timestamp(userId));
console.log('  Random:', Ashid.random(userId));
console.log('  Created at:', new Date(Ashid.timestamp(userId)));

// ============================================
// 3. Time Sortability
// ============================================

console.log('\n--- Time Sortability ---');

// Create IDs with different timestamps
const ids: string[] = [];
for (let i = 0; i < 5; i++) {
  // Add small delay to ensure different timestamps
  const id = Ashid.create('msg', Date.now() + i);
  ids.push(id);
}

// Lexicographic sort = chronological sort!
const sorted = [...ids].sort();
console.log('Original order:', ids.join(', '));
console.log('Sorted order:  ', sorted.join(', '));
console.log('Are they equal?', JSON.stringify(ids) === JSON.stringify(sorted));

// ============================================
// 4. Validation
// ============================================

console.log('\n--- Validation ---');

console.log('Valid ashid:', Ashid.isValid(userId)); // true
console.log('Empty string:', Ashid.isValid('')); // false
console.log('Random string:', Ashid.isValid('hello')); // false
console.log('UUID format:', Ashid.isValid('550e8400-e29b-41d4-a716-446655440000')); // false

// ============================================
// 5. Normalization (Case & Lookalikes)
// ============================================

console.log('\n--- Normalization ---');

// Ashid handles case-insensitivity and lookalike characters
const mixedCase = userId.toUpperCase();
console.log('Original:', userId);
console.log('Uppercase:', mixedCase);
console.log('Normalized:', Ashid.normalize(mixedCase));

// Lookalike characters are mapped
const withLookalikes = userId.replace(/0/g, 'O').replace(/1/g, 'l');
console.log('With lookalikes:', withLookalikes);
console.log('Normalized:', Ashid.normalize(withLookalikes));

// ============================================
// 6. Using the Encoder Directly
// ============================================

console.log('\n--- Encoder ---');

// Encode a number
const encoded = EncoderBase32Crockford.encode(1234567890);
console.log('Encoded 1234567890:', encoded);

// Decode back
const decoded = EncoderBase32Crockford.decode(encoded);
console.log('Decoded back:', decoded);

// With padding (13 chars)
const padded = EncoderBase32Crockford.encode(42, true);
console.log('Padded 42:', padded);

// ============================================
// 7. Entity-Specific IDs Pattern
// ============================================

console.log('\n--- Entity IDs Pattern ---');

// Define a type-safe ID generator
function createEntityId(type: 'user' | 'order' | 'product' | 'invoice'): string {
  const prefixes = {
    user: 'u',
    order: 'ord_',
    product: 'prod_',
    invoice: 'inv_'
  };
  return ashid(prefixes[type]);
}

console.log('User:', createEntityId('user'));
console.log('Order:', createEntityId('order'));
console.log('Product:', createEntityId('product'));
console.log('Invoice:', createEntityId('invoice'));

// ============================================
// 8. Double-Click Selection Demo
// ============================================

console.log('\n--- Double-Click Selection ---');
console.log('Try double-clicking these IDs in your terminal:');
console.log('  Ashid:  ', userId);
console.log('  UUID:   ', '550e8400-e29b-41d4-a716-446655440000');
console.log('Notice how Ashid selects entirely, UUID breaks at hyphens!');
