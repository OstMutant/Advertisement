# improvement-084: AuditableSnapshot DTOs — extract diff() boilerplate into two shared helpers

**Type:** improvement — DRY, found via manual review of `platform-commons` snapshot DTOs.
**Module:** `platform-commons` (`org.ost.platform.audit.api.AuditableSnapshot` and four
implementing snapshot DTOs).
**Priority:** low — pure refactor, no behavior change, small and low-risk.
**When:** anytime — independent, no blockers.

## Problem

`AuditableSnapshot.diff()` is implemented separately in four DTOs — `AdvertisementSnapshotDto`
(3 fields), `TaxonSnapshotDto` (4 fields), `UserSnapshotDto` (3 fields), `SettingsSnapshotDto`
(3 fields) — 13 field-diff blocks total, in **two** duplicated shapes (not one):

**Variation A — `String` fields (9 occurrences: `TaxonSnapshotDto` 4/4, `UserSnapshotDto` 3/3,
`AdvertisementSnapshotDto` 2/3 — `title`, `description`):**

```java
if (!Objects.equals(prevX, x()))
    changes.add(new FieldChange(Fields.x, prevX, x()));
```

**Variation B — primitive `int` fields (3 occurrences, all in `SettingsSnapshotDto`:
`adsPageSize`, `usersPageSize`, `timelinePageSize`) — a different shape, not a missed instance of
Variation A: no `Objects.equals` short-circuit available for primitives, so it needs an explicit
`prev == null` guard plus `String.valueOf(...)` conversion for the `FieldChange` payload:**

```java
if (prev == null || prev.adsPageSize() != adsPageSize())
    changes.add(new FieldChange(Fields.adsPageSize,
        prevAds == null ? null : String.valueOf(prevAds), String.valueOf(adsPageSize())));
```

`AuditableSnapshot` already has a static helper, `field(S snapshot, Function<S, T> getter)`, that
every one of these `diff()` methods already uses to resolve the previous value — the same
interface is the natural place for two more helpers (one per variation) that also do the
comparison and `FieldChange` construction.

**The one genuine exception, correctly left out of both helpers:**
`AdvertisementSnapshotDto.categoryIds` (the 13th field) is a `List<Long>` converted to a display
string via a local `idsToString()` helper before comparison — neither a plain `String` nor an
`int`. Leave its `diff()` branch hand-written as-is; it's a third, distinct shape occurring exactly
once, not worth a helper of its own.

## Suggested fix

Add two overloads to `AuditableSnapshot`, alongside the existing `field()` helper:

```java
static void diffField(List<ChangeEntry> changes, String key, String prev, String curr) {
    if (!Objects.equals(prev, curr)) changes.add(new ChangeEntry.FieldChange(key, prev, curr));
}

static void diffField(List<ChangeEntry> changes, String key, Integer prev, int curr) {
    if (prev == null || prev != curr)
        changes.add(new ChangeEntry.FieldChange(key, prev == null ? null : String.valueOf(prev), String.valueOf(curr)));
}
```

(Boxing `prev` to `Integer` rather than adding a separate `boolean prevPresent` parameter is the
cleaner signature — `null` already means "no previous snapshot" the same way `field()` already
returns `null` for a `String` previous value, so the two overloads read the same way at call
sites. `field(prev, SettingsSnapshotDto::adsPageSize)` returns `Integer` via autoboxing already,
so no call-site change needed beyond calling `diffField` instead of hand-writing the `if`.)

Each `diff()` body shrinks from an `if`/`add` pair per field to one call per field:

```java
// TaxonSnapshotDto.diff() after
List<ChangeEntry> changes = new ArrayList<>();
diffField(changes, Fields.nameEn,        field(prev, TaxonSnapshotDto::nameEn),        nameEn());
diffField(changes, Fields.descriptionEn, field(prev, TaxonSnapshotDto::descriptionEn), descriptionEn());
diffField(changes, Fields.nameUk,        field(prev, TaxonSnapshotDto::nameUk),        nameUk());
diffField(changes, Fields.descriptionUk, field(prev, TaxonSnapshotDto::descriptionUk), descriptionUk());
return changes;

// SettingsSnapshotDto.diff() after
List<ChangeEntry> changes = new ArrayList<>();
diffField(changes, Fields.adsPageSize,      field(prev, SettingsSnapshotDto::adsPageSize),      adsPageSize());
diffField(changes, Fields.usersPageSize,    field(prev, SettingsSnapshotDto::usersPageSize),    usersPageSize());
diffField(changes, Fields.timelinePageSize, field(prev, SettingsSnapshotDto::timelinePageSize), timelinePageSize());
return changes;
```

Apply to `TaxonSnapshotDto` (4 calls, Variation A), `UserSnapshotDto` (3 calls, Variation A),
`AdvertisementSnapshotDto` (2 calls, Variation A — `title`/`description`; `categoryIds` stays as
its own hand-written branch), `SettingsSnapshotDto` (3 calls, Variation B).

## Steps

1. Add both `diffField()` overloads to `AuditableSnapshot`.
2. Update `TaxonSnapshotDto.diff()`, `UserSnapshotDto.diff()`, `AdvertisementSnapshotDto.diff()`
   (only the two `String` fields), `SettingsSnapshotDto.diff()`.
3. Existing unit/integration tests covering these four DTOs' `diff()` behavior (snapshot diff
   tests in `integration-tests`) must stay green unmodified — this is a pure refactor, output
   shape is identical field-for-field.

## Related

- Self-identified during a manual duplication-review pass of `platform-commons` (not from any
  automated batch); initially scoped as 3 files/9 `String`-only occurrences, then corrected on a
  follow-up pass to include `SettingsSnapshotDto`'s parallel `int`-field duplication — the fix
  needs two overloads, not one, from the start.
- `Initialization<T>` (33 implementations in `marketplace-app`, 32 with boilerplate-only bodies)
  was also checked as a candidate finding in the same review pass and ruled out as a separate
  issue: its removal is already an explicit acceptance-criterion of
  [improvement-025](improvement-025-leaf-ui-components-plain-classes.md) ("`Initialization`
  interfaces checked for remaining users after the last batch; deleted if dead").
