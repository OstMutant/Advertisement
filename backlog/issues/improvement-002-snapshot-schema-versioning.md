# improvement-002: Snapshot schema versioning

**Type:** improvement — design complete
**Module:** audit-spring-boot-starter + platform-commons
**Priority:** high — implementing now, same batch as F-04 (improvement-123)
**When:** now. F-04 is the first new snapshot-bearing domain since this issue was filed —
implement here instead of deferring again.

## Problem

Field renames and type changes in `AuditableSnapshot` implementations cause silent data loss.
`FAIL_ON_UNKNOWN_PROPERTIES = false` handles additions/removals but not renames or type changes —
old stored JSON silently deserializes to wrong values or nulls, with no warning.

## Solution — detection + logging only, no migration

1. Add `@SchemaVersion(int value default 1)` annotation to `platform-commons/audit.api`.
2. Apply it to all `AuditableSnapshot` implementations: `AdvertisementSnapshotDto`,
   `TaxonSnapshotDto`, `UserSnapshotDto`, `SettingsSnapshotDto`, and the new
   `MasterProfileSnapshotDto` (F-04) — all five ship with `@SchemaVersion(1)` from day one.
3. Add `default int schemaVersion() { return 1; }` to `AuditableSnapshot` so Jackson serializes
   `"schemaVersion": 1` into every stored snapshot.
4. Wherever a snapshot is currently deserialized (`AuditLogRepository.parseSnapshot()`): read
   `schemaVersion` from the JSON tree, compare with the target class's `@SchemaVersion`. On a
   mismatch, log a warning and deserialize anyway with whatever Jackson produces (nulls for
   fields missing from the old JSON) — same runtime behavior as today, just no longer silent.
5. Existing DB rows with no `schemaVersion` key at all → treat as version 0, log a warning the
   same way.

**Explicitly out of scope: no migration script.** This issue does not convert an old-shape JSON
into the new shape before deserializing. If a real field rename or type change happens later with
real stored data to protect, writing that conversion is a separate, future task at that time —
this issue only makes the drift visible in logs instead of silent.

## Decided 2026-07-27: cover every JSON-persisted blob in the system, not just AuditableSnapshot

Three places store JSON in this codebase; all three get the same detection+logging treatment
(verified by grepping every Liquibase changelog for a `JSONB`/JSON-serialized column — these are
the only three):

1. **`audit_log.snapshot_data`** — the `AuditableSnapshot` implementations covered above
   (`AdvertisementSnapshotDto`, `TaxonSnapshotDto`, `UserSnapshotDto`, `SettingsSnapshotDto`,
   `MasterProfileSnapshotDto`). Straightforward: one polymorphic object per row, `@SchemaVersion`
   applies directly.
2. **`user_information.settings`** — `UserSettingsDto`, read via `UserSettingsRepository.load()`.
   Gets its own `int schemaVersion` field (Jackson default `1` via `@Builder.Default`), separate
   from the existing `version` field (that one is for optimistic locking — a different concern,
   not repurposed). Checked the same way: mismatch → log warning, deserialize anyway.
3. **`attachment_snapshot.changes_summary`** — `List<AttachmentMediaChange>`, read via
   `AttachmentSnapshotRepository.findChangesById()`. **Structurally different from the other two:**
   this column stores a raw JSON *array*, not a single object, so there is no object to hang a
   `schemaVersion` field on without changing the stored shape itself. Fixing this means wrapping
   the array in an envelope object (`{"schemaVersion": 1, "changes": [...]}`) at both the write
   site (`insert()`) and read site (`findChangesById()`) — a real wire-format change to this
   column's JSON shape, not just an additive field. Flagging this cost explicitly since it's a
   bigger change than the other two; still in scope per the decision to cover every blob, but
   budget extra time for it specifically.
