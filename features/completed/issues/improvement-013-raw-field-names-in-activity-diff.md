# improvement-013: Activity diffs show raw camelCase field names while Timeline shows localized labels

**Status:** ✅ RESOLVED (2026-07-13) — the label mappings in `AdvertisementActivityFieldsHookImpl`/
`TaxonActivityFieldsHookImpl`/`UserSettingsActivityFieldsHookImpl` were already complete; the gap
was purely in the wiring. `AuditTimelineRowRenderer.buildEntityChangesDiv()` (used by the
ADVERTISEMENT enrich-hook Timeline branch and unconditionally by every overlay's Activity tab)
never called `labelHook.labelFor()`. Fixed by threading the resolved `AuditActivityFieldsHook`
through both call sites and applying it via a shared `applyLabel()` helper. See
`marketplace-app/DECISIONS.md` ADR-030. Playwright assertion in
`05-seed-filter-sort-pagination.spec.js` updated from a raw-field-tolerant regex to the actual
humanized label, proving the fix. Full e2e suite 48/48 green.

**Type:** improvement — UX consistency, found during screenshot review of full e2e run
**Module:** marketplace-app
**Priority:** medium — the i18n mapping infrastructure already exists, it is just not applied on one path
**When:** Wave 2 — quality hardening before public traffic

## Problem

Two rendering paths for the same change data disagree on field naming:

- **Timeline (USER-type rows)** — humanized: "Name:", "Email:", "Ads per page:". Works because
  `AuditTimelineRowRenderer.buildActivityFieldsList()` routes USER/USER_SETTINGS items through
  `AuditActivityFieldsHook` and `buildActivityChangesDiv()` maps every entry via
  `labelHook.labelFor(field)` (`AuditTimelineRowRenderer.java:118-121,130`).
- **Activity tab in overlays + Timeline ADVERTISEMENT/TAXON rows** — raw Java field names:
  `nameEn:`, `descriptionUk:`, `categoryIds:`, `adsPageSize:`, `media:`. These come from
  `AuditableSnapshot.diff()` field names rendered without any `labelFor` mapping: the
  enrich-hook branch calls `buildEntityChangesDiv(item.changes(), ...)` directly
  (`AuditTimelineRowRenderer.java:109-117`), and the overlay Activity panels render snapshot
  diffs the same way.

Confirmed on screenshots: `taxon-06-timeline-all-fields-in-diff` and every advertisement
activity screenshot show `nameEn` / `categoryIds`; `admin-signup-settings-activity` shows
`adsPageSize` — while `timeline-adminuk-promoted` shows the same settings fields properly
humanized ("Ads per page: 20").

Raw camelCase identifiers are developer jargon leaking into a user-facing screen, and the
inconsistency is visible within a single page (Timeline mixes both styles depending on row
type).

## Suggested fix

Route every `ChangeEntry.FieldChange` through the same label mapping before formatting,
regardless of which branch produced it:

1. In `buildActivityFieldsList()` / `buildEntityChangesDiv()`, resolve the
   `AuditActivityFieldsHook` for the entity type and apply `labelHook.labelFor(field)` to each
   entry — same as the existing `buildActivityChangesDiv()` does.
2. Add the missing label mappings for advertisement/taxon fields (`title`, `description`,
   `categoryIds`, `media`, `nameEn`, `nameUk`, `descriptionEn`, `descriptionUk`) to the
   respective `*ActivityFieldsHookImpl` classes, using `I18nKey` entries (no dynamic key
   building — explicit enum mapping per the i18n rules).
3. Overlay Activity panels (`AuditActivityPanel` path) get the fix for free if the mapping is
   applied inside the shared renderer.
