# Changelog

All notable changes to ashid will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

No unreleased changes.

## [1.0.3] - 2024-12-19

### Changed
- Restructured README: root for project overview, typescript/ for npm API docs
- Updated homepage URL to point to typescript subdirectory

### Added
- Badges (npm version, downloads, bundle size, license, TypeScript)
- Comparison table with uuid, nanoid, cuid2, ulid
- Real-world use cases section
- Full API reference documentation
- "Inspired By" section
- CONTRIBUTING.md

## [1.0.2] - 2024-12-XX

### Changed
- Renamed from NiceId to ashid
- Factory function is now `ashid()` (was `niceid()`)
- Class is now `Ashid` (was `NiceId`) - note: class uses PascalCase
- Package name changed to `ashid`

## [1.0.0] - 2024-11-28

### Changed
- Renamed from NUID to NiceId (now ashid)
- Prefix validation: now allows any length, letters only with optional trailing underscore
- Removed 3-character prefix limit
- Removed built-in prefix constants (project-specific, define your own)

### Added
- Support for trailing underscore in prefixes (e.g., `user_`, `tx_`)

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
