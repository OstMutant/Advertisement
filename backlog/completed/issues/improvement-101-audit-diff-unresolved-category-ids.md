# improvement-101: Audit diffs render unresolved category ids as bare numbers ("Category: 4, 5, 6") with no hint

**Type:** improvement — UX of audit rendering + one open verification question. Found via UX
review over the 2026-07-19 e2e screenshot set.
**Module:** `marketplace-app` (`services/advertisement/AdvertisementEnrichService`, audit
rendering i18n)
**Priority:** medium — every diff involving a since-deleted (or otherwise unresolvable) category
shows meaningless numbers to the user
**When:** Batch F (UI dedup & polish, PR 2 — diff-rendering cosmetics, alongside improvement-014)

## Problem

`AdvertisementEnrichService.idsToNames()` falls back to `String.valueOf(id)` when a category id
is missing from the resolved name map (`nameById.getOrDefault(id, String.valueOf(id))`), and
`resolveCategories()` skips resolution entirely when the map comes back empty. The 2026-07-19
e2e screenshots show the result live: Activity-tab diffs rendering `Category: 4, 5, 6, 7, 8, 9,
10, 11, 12, 13` — bare numbers that mean nothing to a user.

Two sub-items:

1. **UX (certain):** the fallback should be self-describing — e.g. `#4 (removed)` via a localized
   `I18nKey` template — so a diff referencing a category that no longer resolves reads as "a
   removed category", not as noise. `TaxonPort.findByIds()` resolves active taxons; ids of
   hard-purged or otherwise unresolvable taxons legitimately reach the fallback.
2. **Verification (open):** in the screenshots, *seed categories that should exist* ("Boundary-01
   …10", visible resolved on the list cards in the same run) still rendered as raw ids in the
   edit-overlay Activity tab. Before styling the fallback, verify live whether Activity-tab
   resolution actually works for existing categories (the code path exists —
   `enrichActivityItems()` → `resolveCategories()` — and improvement-058 verified the Timeline
   tab; the Activity tab was not explicitly verified). Plausible benign explanation: the
   snapshots referenced ids from a previous seed run wiped by the CI reset — in that case only
   sub-item 1 applies. If resolution is actually broken for live ids in the Activity tab, that's
   a bug to fix in the same PR.

## Suggested fix

- Add an `I18nKey` for the unresolved-category fallback and use it in `idsToNames()`.
- Add one Playwright assertion to the existing activity-diff checks: after assigning a *named*
  category and editing, the Activity tab diff must contain the category *name*, not its id —
  this pins sub-item 2's answer permanently.

## Related

- `backlog/completed/issues/improvement-058-taxon-assignment-audit-trail-missing.md` /
  `marketplace-app/DECISIONS.md` ADR-019 — the read-time resolution design this issue polishes.
- `backlog/issues/improvement-008-deleted-category-strikethrough.md` — the view-overlay analog
  (deleted categories shown struck-through); keep the two visual treatments consistent.
