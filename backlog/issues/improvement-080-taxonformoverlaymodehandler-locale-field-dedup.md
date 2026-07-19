# improvement-080: TaxonFormOverlayModeHandler — collapse repeated EN/UK locale field wiring

**Type:** improvement — structural duplication cleanup, found via manual UI review.
**Module:** `marketplace-app` (`ui/.../TaxonFormOverlayModeHandler.java`).
**Priority:** medium — real, confirmed duplication (~60-70 lines across 5 methods), but touches
the most delicate part of this file (binder validation) — do this one carefully, last among the
structural items in this batch.
**When:** after improvement-078/079 — biggest structural change of the batch, do it once the
lower-risk items are green.

## Problem

`TaxonFormOverlayModeHandler` declares four separate fields —
`nameEnField`/`descriptionEnField`/`nameUkField`/`descriptionUkField` (`.java:77-80`) — and repeats
near-identical wiring for all four across five methods:
- `activate()` (`.java:95-118`, `.java:136-143`) — 4× `.configure(...)` blocks + 4×
  `setValueChangeMode(EAGER)`/`addValueChangeListener(...)`, ~32 lines.
- `buildBinder()` (`.java:295-310`) — 4× `binder.getBinder().forField(...)` blocks
  (asRequired + StringLengthValidator + bind), ~16 lines.
- `discardChanges()` (`.java:190-199`) — 4-field copy lambda, ~4 lines.
- `handleRestoreFromActivity()` (`.java:238-252`) and `loadRestored()` (`.java:254-264`) — same
  4-field copy pattern repeated twice more, ~4 lines each.

Total: ~60-70 duplicated lines, all differing only in locale-specific field/getter/setter/i18n-key,
never in structure.

## Suggested fix

Replace the four separately-declared fields with a small `Map<String, LocaleFieldPair>` (or
similar record pairing a name field + description field) keyed by locale code, and loop over it
for value-change wiring, binder registration, and field-copying in each of the five methods. Keep
the constructor-injection shape Spring needs — this is about collapsing the four repeated *uses*,
not necessarily how the fields are injected.

**Risk note:** this is the most delicate item in the batch — `buildBinder()`'s validation wiring
(`asRequired` + `StringLengthValidator` + i18n message keys) must produce byte-for-byte the same
validation behavior after the refactor. Run the full Playwright e2e suite after this change
specifically, not just at the end of the batch, and manually verify taxon create/edit validation
error messages still show correctly for both locales.

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076
  through improvement-079 and improvement-081 through improvement-083 for the rest.

## Related finding (2026-07-19, pattern-focused code review): the same EN/UK hardcoding lives server-side too

`TaxonService` (taxon-spring-boot-starter) repeats the exact same locale-pair hardcoding this
issue targets in the UI handler — an OCP gap, not just duplication:

- `buildSnapshotFromData()` reads only `Locale.ENGLISH` and `Locale.forLanguageTag("uk")`;
- `buildSnapshotFromTranslations()` string-matches only `"en"`/`"uk"`;
- yet `validateTranslations()` iterates `properties.supportedLocales()` — config-driven.

Adding a third locale via `TaxonProperties` would validate fine but **silently vanish from audit
snapshots** (`TaxonSnapshotDto` itself has fixed `nameEn/descEn/nameUk/descUk` fields, so a truly
config-driven snapshot needs a DTO-shape decision, not just a loop).

When implementing the map-keyed-by-locale refactor here, extend the same shape to
`TaxonService`'s two snapshot builders in the same PR — or, if the `TaxonSnapshotDto` schema
question makes that too big, at minimum leave a comment in both builders pointing at the fixed
DTO shape as the real constraint. Do not silently ship a UI-side-only fix that leaves the
server-side pair as the last hardcoded copy.
