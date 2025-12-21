/**
 * Ashid - Basic Usage Examples (Java)
 *
 * Compile: javac -cp ashid.jar BasicUsage.java
 * Run: java -cp .:ashid.jar BasicUsage
 */

package examples;

import agency.wilde.ashid.Ashid;
import agency.wilde.ashid.EncoderBase32Crockford;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class BasicUsage {

    public static void main(String[] args) {
        // ============================================
        // 1. Creating Ashids
        // ============================================

        // Simple creation without prefix
        String simpleId = Ashid.create();
        System.out.println("Simple ID: " + simpleId);
        // Output: 1fvszawr42tve3gxvx9900 (22 chars)

        // With a single-letter prefix (no delimiter)
        String userId = Ashid.create("u");
        System.out.println("User ID: " + userId);
        // Output: u1fvszawr42tve3gxvx9900 (23 chars)

        // With a multi-letter prefix (no delimiter)
        String orderId = Ashid.create("ord");
        System.out.println("Order ID: " + orderId);
        // Output: ord1fvszawr42tve3gxvx900 (25 chars)

        // With underscore delimiter (variable length)
        String productId = Ashid.create("product_");
        System.out.println("Product ID: " + productId);
        // Output: product_1fvszawr...

        // ============================================
        // 2. Parsing Ashids
        // ============================================

        // Parse into components
        Ashid.ParseResult parsed = Ashid.parse(userId);
        System.out.println("\nParsed components:");
        System.out.println("  Prefix: " + parsed.getPrefix());
        System.out.println("  Timestamp (encoded): " + parsed.getEncodedTimestamp());
        System.out.println("  Random (encoded): " + parsed.getEncodedRandom());

        // Extract individual components
        System.out.println("\nExtracted values:");
        System.out.println("  Prefix: " + Ashid.prefix(userId));
        System.out.println("  Timestamp (ms): " + Ashid.timestamp(userId));
        System.out.println("  Random: " + Ashid.random(userId));
        System.out.println("  Created at: " + new Date(Ashid.timestamp(userId)));

        // ============================================
        // 3. Time Sortability
        // ============================================

        System.out.println("\n--- Time Sortability ---");

        // Create IDs with different timestamps
        List<String> ids = new ArrayList<>();
        long baseTime = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            String id = Ashid.create("msg", baseTime + i);
            ids.add(id);
        }

        // Lexicographic sort = chronological sort!
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        System.out.println("Original order: " + String.join(", ", ids));
        System.out.println("Sorted order:   " + String.join(", ", sorted));
        System.out.println("Are they equal? " + ids.equals(sorted));

        // ============================================
        // 4. Validation
        // ============================================

        System.out.println("\n--- Validation ---");

        System.out.println("Valid ashid: " + Ashid.isValid(userId)); // true
        System.out.println("Empty string: " + Ashid.isValid("")); // false
        System.out.println("Random string: " + Ashid.isValid("hello")); // false
        System.out.println("UUID format: " + Ashid.isValid("550e8400-e29b-41d4-a716-446655440000")); // false

        // ============================================
        // 5. Normalization (Case & Lookalikes)
        // ============================================

        System.out.println("\n--- Normalization ---");

        // Ashid handles case-insensitivity and lookalike characters
        String mixedCase = userId.toUpperCase();
        System.out.println("Original: " + userId);
        System.out.println("Uppercase: " + mixedCase);
        System.out.println("Normalized: " + Ashid.normalize(mixedCase));

        // Lookalike characters are mapped
        String withLookalikes = userId.replace('0', 'O').replace('1', 'l');
        System.out.println("With lookalikes: " + withLookalikes);
        System.out.println("Normalized: " + Ashid.normalize(withLookalikes));

        // ============================================
        // 6. Using the Encoder Directly
        // ============================================

        System.out.println("\n--- Encoder ---");

        // Encode a number
        String encoded = EncoderBase32Crockford.encode(1234567890);
        System.out.println("Encoded 1234567890: " + encoded);

        // Decode back
        long decoded = EncoderBase32Crockford.decode(encoded);
        System.out.println("Decoded back: " + decoded);

        // With padding (13 chars)
        String padded = EncoderBase32Crockford.encode(42, true);
        System.out.println("Padded 42: " + padded);

        // ============================================
        // 7. Entity-Specific IDs Pattern
        // ============================================

        System.out.println("\n--- Entity IDs Pattern ---");

        System.out.println("User: " + createEntityId(EntityType.USER));
        System.out.println("Order: " + createEntityId(EntityType.ORDER));
        System.out.println("Product: " + createEntityId(EntityType.PRODUCT));
        System.out.println("Invoice: " + createEntityId(EntityType.INVOICE));

        // ============================================
        // 8. Double-Click Selection Demo
        // ============================================

        System.out.println("\n--- Double-Click Selection ---");
        System.out.println("Try double-clicking these IDs in your terminal:");
        System.out.println("  Ashid:   " + userId);
        System.out.println("  UUID:    550e8400-e29b-41d4-a716-446655440000");
        System.out.println("Notice how Ashid selects entirely, UUID breaks at hyphens!");
    }

    // Define a type-safe ID generator
    enum EntityType {
        USER("u"),
        ORDER("ord_"),
        PRODUCT("prod_"),
        INVOICE("inv_");

        private final String prefix;

        EntityType(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }

    static String createEntityId(EntityType type) {
        return Ashid.create(type.getPrefix());
    }
}
