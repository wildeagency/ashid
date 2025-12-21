# Migration Guide: From UUIDs to Ashid

This guide helps you migrate from UUIDs (or other ID formats) to Ashid in your applications.

## Why Migrate?

Before migrating, understand the benefits:

1. **Better DX**: Double-click to select, no hyphens breaking copy/paste
2. **Sortable**: Chronological ordering without extra columns
3. **Shorter**: 22 chars vs 36 chars (39% reduction)
4. **Type prefixes**: Know what you're looking at: `user_abc` vs `550e8400-...`
5. **Human-friendly**: Case-insensitive, lookalike character mapping

## Migration Strategies

### Strategy 1: New Tables Only (Recommended Start)

The safest approach—use Ashid for new tables while existing tables keep UUIDs.

```typescript
// New entities use Ashid
const newUser = {
  id: ashid('user_'),  // user_1fvszawr42tve3gxvx
  // ...
};

// Existing entities keep UUID
const existingOrder = {
  id: '550e8400-e29b-41d4-a716-446655440000',
  // ...
};
```

**Pros**: Zero risk to existing data, gradual adoption
**Cons**: Inconsistent ID formats across your system

### Strategy 2: Dual-Write with Shadow Column

Add an Ashid column alongside your UUID column, populate both, then switch.

```sql
-- Step 1: Add new column
ALTER TABLE users ADD COLUMN ashid VARCHAR(26);

-- Step 2: Populate for existing rows (application code)
-- For each row: UPDATE users SET ashid = generated_ashid WHERE id = uuid;

-- Step 3: After verification, switch primary key
-- (This varies by database)
```

**Pros**: Reversible, allows verification before switch
**Cons**: More complex, temporary data duplication

### Strategy 3: Full Migration

Convert all existing UUIDs to Ashids in a single migration.

```typescript
// Generate Ashid from existing UUID's timestamp (if UUID v1)
// or use current time for UUID v4
function migrateUuidToAshid(uuid: string, prefix: string): string {
  // For UUID v4: Use current timestamp (loses original creation time)
  return ashid(prefix);

  // For UUID v1: Extract and preserve timestamp
  // const timestamp = extractUuidV1Timestamp(uuid);
  // return Ashid.create(prefix, timestamp);
}
```

**Pros**: Clean, consistent system
**Cons**: Requires updating all foreign keys, risky for large systems

## Code Migration Examples

### TypeScript/JavaScript

**Before (UUID)**:
```typescript
import { v4 as uuid } from 'uuid';

function createUser(name: string) {
  return {
    id: uuid(),
    name,
    createdAt: new Date(),
  };
}
```

**After (Ashid)**:
```typescript
import { ashid } from 'ashid';

function createUser(name: string) {
  return {
    id: ashid('user_'),
    name,
    // createdAt is now encoded in the ID!
  };
}

// Extract timestamp when needed
import { Ashid } from 'ashid';
const createdAt = new Date(Ashid.timestamp(user.id));
```

### Kotlin

**Before (UUID)**:
```kotlin
import java.util.UUID

data class User(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)
```

**After (Ashid)**:
```kotlin
import agency.wilde.ashid.ashid

data class User(
    val id: String = ashid("user_"),
    val name: String
)
```

### Java

**Before (UUID)**:
```java
import java.util.UUID;

public class User {
    private String id = UUID.randomUUID().toString();
    private String name;
}
```

**After (Ashid)**:
```java
import agency.wilde.ashid.Ashid;

public class User {
    private String id = Ashid.create("user_");
    private String name;
}
```

## Database Considerations

### Column Type

Ashid is always a string. Use appropriate column types:

| Database | Column Type |
|----------|-------------|
| PostgreSQL | `VARCHAR(26)` or `TEXT` |
| MySQL | `VARCHAR(26)` |
| SQLite | `TEXT` |
| MongoDB | `String` |

**Note**: 26 chars allows for 3-char prefix + underscore + 22-char base. Adjust if using longer prefixes.

### Indexing

Ashids are time-sortable, which benefits B-tree indexes:

```sql
-- Ashids naturally cluster by creation time
CREATE INDEX idx_users_id ON users(id);

-- Range queries work efficiently
SELECT * FROM users WHERE id > 'user_1fvszawr' AND id < 'user_1fvt0000';
```

### Sorting

With UUIDs, you needed a separate `created_at` column for chronological ordering:

```sql
-- Before: UUID requires created_at for sorting
SELECT * FROM orders ORDER BY created_at DESC;

-- After: Ashid is inherently sorted
SELECT * FROM orders ORDER BY id DESC;
```

## API Migration

### URL Changes

```
Before: /api/users/550e8400-e29b-41d4-a716-446655440000
After:  /api/users/user_1fvszawr42tve3gxvx
```

**Breaking change warning**: If you have public APIs, this changes your URL structure. Consider:

1. Supporting both formats during transition
2. Using redirects from old to new format
3. Versioning your API (v1 uses UUID, v2 uses Ashid)

### Validation Changes

```typescript
// Before: UUID validation
const UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// After: Ashid validation
import { Ashid } from 'ashid';
if (!Ashid.isValid(id)) {
  throw new Error('Invalid ID');
}
```

## Foreign Key Migration

The trickiest part of migration is updating foreign keys:

```sql
-- 1. Add new column to child table
ALTER TABLE orders ADD COLUMN user_ashid VARCHAR(26);

-- 2. Update child table with new IDs (requires lookup)
UPDATE orders o
SET user_ashid = u.ashid
FROM users u
WHERE o.user_id = u.uuid_id;

-- 3. After verification, drop old column and rename
ALTER TABLE orders DROP COLUMN user_id;
ALTER TABLE orders RENAME COLUMN user_ashid TO user_id;
```

## Rollback Plan

Always have a rollback plan:

1. **Keep backup**: Snapshot your database before migration
2. **Shadow columns**: Keep UUID columns until migration is verified
3. **Feature flags**: Use flags to switch between ID formats in code
4. **Bi-directional mapping**: Maintain a UUID↔Ashid lookup table

## Checklist

- [ ] Identify all tables/collections using UUIDs
- [ ] Map all foreign key relationships
- [ ] Update model/entity classes
- [ ] Update validation logic
- [ ] Update API routes and documentation
- [ ] Update database schemas
- [ ] Migrate existing data
- [ ] Update tests
- [ ] Update logging to use new format
- [ ] Communicate changes to API consumers
- [ ] Plan rollback procedure
- [ ] Monitor for issues post-migration

## Common Pitfalls

1. **Forgetting foreign keys**: Ensure all references are updated together
2. **Case sensitivity**: Some databases are case-sensitive. Ashid normalizes to lowercase.
3. **String comparison**: Ashids sort correctly as strings; no special handling needed
4. **Timestamp precision**: Ashid uses milliseconds, same as most UUID v1 implementations
5. **Log searching**: Update any log parsing that relies on UUID format

## Timeline Recommendation

| Phase | Duration | Activities |
|-------|----------|------------|
| Planning | 1 sprint | Audit codebase, identify dependencies |
| Preparation | 1 sprint | Add Ashid library, update models |
| Dual-write | 2-4 sprints | Write both formats, verify consistency |
| Switch | 1 sprint | Change primary key, update consumers |
| Cleanup | 1 sprint | Remove UUID columns, update docs |

Adjust based on your system's complexity and risk tolerance.
