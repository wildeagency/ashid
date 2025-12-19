# Contributing to ashid

Thank you for your interest in contributing to ashid! This project aims to be the reference implementation for time-sortable unique identifiers with type prefixes.

## Code of Conduct

Be respectful, constructive, and professional. We're all here to build something useful.

## Design Philosophy

Before contributing, please understand our core principles:

1. **Simplicity First**: The code should be the clearest example of how to implement this pattern
2. **Zero Dependencies**: Keep it simple, no external deps in core library
3. **Performance**: Fast enough, but prioritize correctness and clarity
4. **Developer Experience**: Every decision optimizes for developer happiness
5. **Documentation as Marketing**: Make people "get it" immediately

## How to Contribute

### Reporting Bugs

Open an issue with:
- Clear description of the problem
- Minimal reproduction case
- Expected vs actual behavior
- Environment details (Node version, OS, etc.)

### Suggesting Features

Open an issue with:
- Use case description
- Proposed API/interface
- Why this aligns with ashid's goals
- Alternatives considered

### Code Contributions

1. **Fork the repository**

2. **Create a feature branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**
   - Follow existing code style
   - Add tests for new functionality
   - Update documentation as needed

4. **Run tests**
   ```bash
   # TypeScript
   cd typescript
   pnpm test
   pnpm typecheck

   # Kotlin
   cd kotlin
   ./gradlew test
   ```

5. **Commit with clear messages**
   ```
   Add feature: brief description

   Detailed explanation of what changed and why.
   ```

6. **Push and create Pull Request**
   ```bash
   git push origin feature/your-feature-name
   ```

## Code Style

### TypeScript

- Use TypeScript strict mode
- Prefer explicit types over inference in public APIs
- Document all public methods with JSDoc
- Use meaningful variable names
- Keep functions focused and small

### Kotlin

- Follow Kotlin conventions
- Use object for singletons
- Prefer data classes for DTOs
- Document public APIs with KDoc

### Java

- Follow Java conventions
- Use static methods for utilities
- Document with Javadoc

## Testing

- All new code must have tests
- Aim for 100% coverage of new code
- Test edge cases and error conditions
- Use descriptive test names

Example:
```typescript
describe('Ashid.create', () => {
  it('should create Ashid with prefix', () => {
    const id = Ashid.create('user_');
    expect(id).toMatch(/^user_[0-9]/);
  });
});
```

## Documentation

- Update README.md for user-facing changes
- Update API documentation for new methods
- Add examples for new features
- Keep docs simple and clear

## What We're Looking For

### High Priority
- Bug fixes
- Performance improvements
- Better error messages
- Improved documentation
- Additional language implementations

### Medium Priority
- New utility functions (if they align with core goals)
- Better examples
- Tooling improvements (CLI, etc.)

### Low Priority
- Features that add complexity
- Dependencies
- Features that stray from core mission

## What We're NOT Looking For

- Breaking changes without strong justification
- Features that violate zero-dependency principle
- Complex abstractions
- Features that prioritize machines over humans

## Multi-Language Consistency

All language implementations should:
- Have identical core functionality
- Follow language-specific conventions
- Maintain feature parity
- Share the same test cases (conceptually)

When adding a feature to one language:
1. Consider impact on other languages
2. Implement in all languages if possible
3. Document language-specific differences

## Release Process

1. All tests must pass
2. Documentation must be updated
3. Version numbers follow semantic versioning
4. CHANGELOG.md must be updated

## Questions?

Open an issue with the `question` label.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.

## Recognition

Contributors will be recognized in:
- README.md contributors section
- Release notes
- Project documentation

Thank you for helping make ashid better!
