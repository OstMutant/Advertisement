# improvement-002: Snapshot schema versioning

**Type:** improvement — deferred, design complete
**Module:** audit-spring-boot-starter + platform-commons
**Priority:** medium — implement before first AuditableSnapshot field rename in production
**When:** Wave 3 — must land before the first new snapshot-bearing domain ships

## Problem

Field renames and type changes in `AuditableSnapshot` implementations cause silent data loss.
`FAIL_ON_UNKNOWN_PROPERTIES = false` handles additions/removals but not renames or type changes —
old stored JSON silently deserializes to wrong values or nulls.

## Designed solution

1. Add `@SchemaVersion(int value default 1)` annotation to `platform-commons/audit.api`
2. Apply annotation to all `AuditableSnapshot` implementations
3. Add `default int schemaVersion() { return 1; }` to `AuditableSnapshot` interface
   so Jackson serializes `"schemaVersion": 1` into every stored snapshot
4. `SnapshotCodec.decode()` reads `schemaVersion` from JSON tree, compares with
   `@SchemaVersion` on target class, logs warning on mismatch

## Backward compatibility

- Existing DB rows without `schemaVersion` field → treat as version 0, log warning
- Dev environment: deploy with `--reset` to wipe and re-seed
- Production: requires data migration script — defer until first production deployment

## Implementation trigger

Before the first `AuditableSnapshot` field rename or type change.

## Related surface (2026-07-19, edge-case review): the settings JSONB blob has the same forward-compat gap

`UserSettingsDto` is serialized whole into `user_information.settings` (JSONB) and read back via
`UserSettingsRepository.load()` (Jackson `readValue`). The read path is robust against *malformed*
JSON (catch → `defaultSettings()`), but not against *schema evolution*: renaming or removing a
`UserSettingsDto` field silently drops the old stored value on the next read (Jackson applies
defaults for the new shape), with no versioning or migration — exactly the class of problem this
issue solves for `AuditableSnapshot` snapshots. Whatever versioning/migration mechanism this issue
adopts for snapshots should either cover the settings blob too, or the settings blob should get an
explicit `version` field (it already carries `UserSettingsDto.version` for optimistic locking, see
`marketplace-app/DECISIONS.md` ADR-044 — that counter could double as the schema-version anchor).
Decide together so there is one story for "a JSONB-persisted DTO's shape changed", not two.
