# Changelog

All notable changes to the NiceId project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

No unreleased changes.

## [1.0.0] - 2024-11-28

### Changed
- Renamed from NUID to NiceId
- Factory function is now `niceid()` (was `nuid()`)
- Class is now `NiceId` (was `NUID`)
- Package changed from `com.wildeagency.nuid` to `agency.wilde.niceid` (Kotlin)
- Prefix validation: now allows any length, letters only with optional trailing underscore
- Removed 3-character prefix limit
- Removed built-in prefix constants (project-specific, define your own)

### Added
- Support for trailing underscore in prefixes (e.g., `user_`, `tx_`)
- Backwards compatibility aliases for NUID/nuid (deprecated)

### Added
- Initial TypeScript/JavaScript implementation
- Initial Kotlin/JVM implementation
- Crockford Base32 encoding with lookalike character mapping
- Time-sortable ID generation
- Optional type prefixes
- Zero dependencies (TypeScript)
- Comprehensive test suites for both implementations
- Maven Central publishing configuration
- npm publishing configuration

### Features
- **Double-click selectable**: No separators, no special characters
- **Time-sortable**: Lexicographic sorting matches chronological ordering
- **Type-prefixed**: Self-documenting IDs (user_, tx_, etc.)
- **Crockford Base32**: Case-insensitive with lookalike mapping
- **Compact**: 22 characters base + prefix length
- **Cross-platform**: Works in TypeScript/JavaScript and Kotlin/Java

---

## Version Scheme

This project uses [Semantic Versioning](https://semver.org/):

- **MAJOR** version for incompatible API changes
- **MINOR** version for backwards-compatible functionality additions
- **PATCH** version for backwards-compatible bug fixes
