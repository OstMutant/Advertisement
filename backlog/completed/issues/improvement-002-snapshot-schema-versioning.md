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

## Scope correction (2026-07-28) — "MasterProfileSnapshotDto (F-04)" no longer applies

This issue text (item 2 of the Solution list) predates improvement-123 being superseded by
improvement-124 (F-04 is now Provider Profile, `ActorProfileSnapshotDto`/`EntityType
.ACTOR_PROFILE`, not "Master Profile"). Neither class exists in the codebase yet — F-04 is the
*next* item in the Top-priority chain after this one, not implemented yet. This issue therefore
covers only the 4 `AuditableSnapshot` implementations that exist today
(`AdvertisementSnapshotDto`, `TaxonSnapshotDto`, `UserSnapshotDto`, `SettingsSnapshotDto`).
`improvement-124`'s own execution plan must add `@SchemaVersion(1)` to its new
`ActorProfileSnapshotDto` when it's implemented, following the pattern this issue establishes —
noted there as a follow-up, not done here.

## Final design (2026-07-28) — real bound field everywhere, no reflection, no tree-parsing

Went through two intermediate designs before landing here — reflection-based `@SchemaVersion`
annotation + `JsonNode` tree-parsing with legacy-shape fallbacks, then a shared
`SchemaVersionCheck` tree-reading helper — both reverted as unnecessary complexity for an app that
has never run in production (no real legacy data in an old shape to protect). Full rationale:
`platform-commons/DECISIONS.md` ADR-024.

### 1. `platform-commons` — real `schemaVersion` record component on all 4 snapshot DTOs

`AuditableSnapshot.schemaVersion()` is a plain abstract interface method (no default, no
`@SchemaVersion` annotation — that annotation was removed entirely). Each of
`AdvertisementSnapshotDto`/`TaxonSnapshotDto`/`UserSnapshotDto`/`SettingsSnapshotDto` gets:
- `int schemaVersion` as its new **last** canonical record component.
- `public static final int SCHEMA_VERSION = 1;`.
- A second, non-canonical constructor matching the DTO's *old* parameter list, delegating to the
  canonical one with `SCHEMA_VERSION` — e.g. `AdvertisementSnapshotDto(String title, ...,  Long
  attachmentSnapshotId) { this(title, ..., attachmentSnapshotId, SCHEMA_VERSION); }` — so every
  existing call site (`AdvertisementSaveService`, `UserService`, `TaxonService`,
  `SettingsSnapshotDto.from()`, every `*SnapshotDtoTest`) keeps compiling unchanged.

### 2. `audit-spring-boot-starter` — no read-side change

`AuditLogRepository.ProjectionMapper.parseSnapshot()` stays exactly as it was —
`objectMapper.readValue(json, AuditableSnapshot.class)`. No comparison here: the polymorphic
`audit_log` path has no single "current expected version" to check a deserialized instance against
without reintroducing either reflection or a switch over the four known DTOs. The field is still
stamped on write (real data now, not a computed default), available for manual inspection or a
future migration tool.

### 3. `user-spring-boot-starter` — `UserSettingsDto`'s own `schemaVersion`

- `UserSettingsDto` (Lombok `@Value`/`@Builder`, not a record — `@Builder.Default` already covers
  "old callers don't need to change" here, no delegating constructor needed): add `public static
  final int SCHEMA_VERSION = 1;` and a `@Builder.Default int schemaVersion = SCHEMA_VERSION;`
  field, separate from the existing `version` field (optimistic-locking, untouched).
- `UserSettingsRepository.load()`: `UserSettingsDto settings = mapper.readValue(json,
  UserSettingsDto.class);` then `if (settings.getSchemaVersion() != UserSettingsDto.SCHEMA_VERSION)
  log.warn(...)` — one extra `if` on the already-deserialized object, no separate parse.

### 4. `attachment-spring-boot-starter` — `schemaVersion` on `AttachmentMediaChange` itself

No envelope wrapper — `changes_summary` stays a bare JSON array (no wire-format change). Instead
`AttachmentMediaChange(List<String> before, List<String> after)` gets the same treatment as the
four `AuditableSnapshot` DTOs: `int schemaVersion` as a new 3rd component, `SCHEMA_VERSION = 1`
constant, and a delegating 2-arg constructor covering both real call sites
(`AttachmentSnapshotService`). `AttachmentSnapshotRepository.findChangesById()`: unchanged
`objectMapper.readValue(json, new TypeReference<List<AttachmentMediaChange>>(){})`, then checks the
first element's `schemaVersion()` against `AttachmentMediaChange.SCHEMA_VERSION`, logs on mismatch.

### 5. Tests (`integration-tests`)

- `AuditLogRepositoryTest`: one test confirming `insertRowWithSnapshot()`'s JSON (no
  `schemaVersion` key at all — the shape every row written before this feature actually has)
  still deserializes correctly via the unchanged `parseSnapshot()`.
- `UserSettingsRepositoryTest`: one test confirming a freshly-created user (whose `settings`
  column value comes from the Liquibase default literal, which also has no `schemaVersion` key)
  still loads correct values via `@Builder.Default`.
- `AttachmentSnapshotRepositoryTest`: one round-trip test via `insert()` + `findChangesById()`
  confirming `schemaVersion` survives the round trip.

### 6. Docs

- `platform-commons/DECISIONS.md` ADR-024 — the real-bound-field pattern, the two reverted
  intermediate designs and why, why detection-only (no auto-migration), and the three JSON-blob
  locations it covers.

### 7. Verify

`scripts/unit-tests.sh`, `scripts/integration-tests.sh --sandbox`, full Playwright `e2e --full
--ux` (existing audit/settings/attachment flows must keep working unchanged — this issue only adds
a parallel version field/comparison, never changes what gets returned to callers).

## Resolution (2026-07-28)

Implemented per the final design above. Verified: `unit-tests.sh` (77/77), `integration-tests.sh
--sandbox` (133/133, incl. 4 new schema-version tests). Full Playwright `e2e --full --ux` surfaced
an unrelated, pre-existing timing fragility — `runSubmitLoginFlow` (`playwright/e2e/_flows/
auth.flow.js`) polled for post-login UI elements with an 8s timeout, but `LoginDialog.handleLogin()`
does a full `ui.getPage().reload()` on success (session-fixation re-init), not an in-place push
update; under sandbox load a full reload occasionally took longer than 8s. Root-caused via 3
repeated full suite runs (no server-side exceptions in any run, different unrelated failure each
time, zero UI files in this issue's own diff) plus a `pw-runner` container restart that didn't fix
it — confirmed the timeout itself was the issue, not a regression. Fixed by waiting for
`networkidle` before polling and raising the timeout to 15s, matching the convention already used
elsewhere in the suite for heavier operations. Final Playwright run: 50/50 passed.
