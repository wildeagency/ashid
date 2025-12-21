/**
 * Ashid - Basic Usage Examples (Kotlin)
 *
 * Run with: kotlinc -cp ashid.jar BasicUsage.kt -include-runtime -d example.jar && java -jar example.jar
 * Or with Gradle: gradle run
 */

package examples

import agency.wilde.ashid.Ashid
import agency.wilde.ashid.EncoderBase32Crockford
import agency.wilde.ashid.ashid
import java.util.Date

fun main() {
    // ============================================
    // 1. Creating Ashids
    // ============================================

    // Simple creation without prefix
    val simpleId = ashid()
    println("Simple ID: $simpleId")
    // Output: 1fvszawr42tve3gxvx9900 (22 chars)

    // With a single-letter prefix (no delimiter)
    val userId = ashid("u")
    println("User ID: $userId")
    // Output: u1fvszawr42tve3gxvx9900 (23 chars)

    // With a multi-letter prefix (no delimiter)
    val orderId = ashid("ord")
    println("Order ID: $orderId")
    // Output: ord1fvszawr42tve3gxvx900 (25 chars)

    // With underscore delimiter (variable length)
    val productId = ashid("product_")
    println("Product ID: $productId")
    // Output: product_1fvszawr...

    // ============================================
    // 2. Parsing Ashids
    // ============================================

    // Parse into components
    val (prefix, timestamp, random) = Ashid.parse(userId)
    println("\nParsed components:")
    println("  Prefix: $prefix")
    println("  Timestamp (encoded): $timestamp")
    println("  Random (encoded): $random")

    // Extract individual components
    println("\nExtracted values:")
    println("  Prefix: ${Ashid.prefix(userId)}")
    println("  Timestamp (ms): ${Ashid.timestamp(userId)}")
    println("  Random: ${Ashid.random(userId)}")
    println("  Created at: ${Date(Ashid.timestamp(userId))}")

    // ============================================
    // 3. Time Sortability
    // ============================================

    println("\n--- Time Sortability ---")

    // Create IDs with different timestamps
    val ids = mutableListOf<String>()
    repeat(5) { i ->
        val id = Ashid.create("msg", System.currentTimeMillis() + i)
        ids.add(id)
    }

    // Lexicographic sort = chronological sort!
    val sorted = ids.sorted()
    println("Original order: ${ids.joinToString(", ")}")
    println("Sorted order:   ${sorted.joinToString(", ")}")
    println("Are they equal? ${ids == sorted}")

    // ============================================
    // 4. Validation
    // ============================================

    println("\n--- Validation ---")

    println("Valid ashid: ${Ashid.isValid(userId)}") // true
    println("Empty string: ${Ashid.isValid("")}") // false
    println("Random string: ${Ashid.isValid("hello")}") // false
    println("UUID format: ${Ashid.isValid("550e8400-e29b-41d4-a716-446655440000")}") // false

    // ============================================
    // 5. Normalization (Case & Lookalikes)
    // ============================================

    println("\n--- Normalization ---")

    // Ashid handles case-insensitivity and lookalike characters
    val mixedCase = userId.uppercase()
    println("Original: $userId")
    println("Uppercase: $mixedCase")
    println("Normalized: ${Ashid.normalize(mixedCase)}")

    // Lookalike characters are mapped
    val withLookalikes = userId.replace('0', 'O').replace('1', 'l')
    println("With lookalikes: $withLookalikes")
    println("Normalized: ${Ashid.normalize(withLookalikes)}")

    // ============================================
    // 6. Using the Encoder Directly
    // ============================================

    println("\n--- Encoder ---")

    // Encode a number
    val encoded = EncoderBase32Crockford.encode(1234567890)
    println("Encoded 1234567890: $encoded")

    // Decode back
    val decoded = EncoderBase32Crockford.decode(encoded)
    println("Decoded back: $decoded")

    // With padding (13 chars)
    val padded = EncoderBase32Crockford.encode(42, padded = true)
    println("Padded 42: $padded")

    // ============================================
    // 7. Entity-Specific IDs Pattern
    // ============================================

    println("\n--- Entity IDs Pattern ---")

    // Define a type-safe ID generator
    enum class EntityType(val prefix: String) {
        USER("u"),
        ORDER("ord_"),
        PRODUCT("prod_"),
        INVOICE("inv_")
    }

    fun createEntityId(type: EntityType): String = ashid(type.prefix)

    println("User: ${createEntityId(EntityType.USER)}")
    println("Order: ${createEntityId(EntityType.ORDER)}")
    println("Product: ${createEntityId(EntityType.PRODUCT)}")
    println("Invoice: ${createEntityId(EntityType.INVOICE)}")

    // ============================================
    // 8. Double-Click Selection Demo
    // ============================================

    println("\n--- Double-Click Selection ---")
    println("Try double-clicking these IDs in your terminal:")
    println("  Ashid:   $userId")
    println("  UUID:    550e8400-e29b-41d4-a716-446655440000")
    println("Notice how Ashid selects entirely, UUID breaks at hyphens!")
}
