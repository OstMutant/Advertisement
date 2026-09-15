# improvement-126: Timeline row header repeats the entity name already shown in the row body

**Type:** bug — UX finding from a direct code review of the Timeline feed
**Module:** marketplace-app
**Priority:** high — user-requested, found during improvement-125's UX follow-up
**When:** independent, no blockers
**Status:** Done 2026-07-28 — header name-duplication removed; Phase 2 (actor+timestamp right-aligned
as one group, in both Timeline and the per-entity Activity tab) also shipped and verified with full
Playwright `e2e --full --ux` (50/50) plus direct screenshot confirmation — a first geometry-only
assertion pass had falsely gone green while the Activity tab was still visually broken (measured
against an inner container that didn't span the full row width), so the fix was only trusted after
looking at the actual rendered screenshot.

## Problem

Every Timeline row's compact header line (e.g. `+ Created / ADVERTISEMENT / Seed Advertisement 07`)
shows the entity's display name via `nameSpan()`
(`AuditTimelineRowRenderer.buildRow()`, line 71). The row's body — the expanded field list shown
immediately below, always visible, not behind a click — already includes that same name as one of
its fields (e.g. `Title: Seed Advertisement 07`), since `expandWithChanges()` always renders every
field. **Decided: the body stays exactly as it is today — untouched.** The name only needs to come
out of the compact header line; it's still immediately visible in the body right below.

## Fix

1. `AuditTimelineRowRenderer.buildRow()` (line 70-71) — drop `nameSpan(...)` from the
   `row.add(...)` call. Row header becomes just `actionSpan(...)`, `typeSpan(...)`,
   `timeSpan(...)`.
2. Remove the now-unused `nameSpan()` method (line 96) and the `displayNames` field from
   `RowContext` (`AuditTimelineRowRenderer` record, currently `Map<Long, String> displayNames`).
3. `AuditTimelineListRenderer.buildRowContext()` (lines 41-64) — remove the `displayNames` map
   construction (lines 43, 48-53) and its use in the `RowContext` constructor call (line 63), since
   nothing consumes it anymore. This also removes the now-unnecessary
   `auditDomainHook.resolveDisplayName(snapshot)` call inside the loop — confirm no other caller in
   this class needs `resolveDisplayName` before deleting the call (a quick grep first).
4. Do not touch `expandWithChanges()`, `allFields()`, or any `AuditableSnapshot` implementation —
   the body/diff list is explicitly out of scope, stays as-is.

## Testing

- Existing Playwright timeline assertions that check row header text for the entity's title/name
  (if any use `assertTimelineHasRows(..., titleText: ...)` matching against the header specifically,
  not the body) need to be re-pointed to check the body/diff content instead, since the header no
  longer carries the name. Audit `assertTimelineHasRows`'s actual selector before changing tests —
  confirm exactly what it currently asserts against.
- No new spec files.

## Phase 2 (found 2026-07-28) — right-align actor + timestamp, correct order

**Problem A — Activity tab (`.entity-activity-meta`):** the meta row (version, action, actor,
timestamp) sits inside `.entity-activity-row`, which is `flex-direction: column; align-items:
flex-start`. `.entity-activity-meta` itself has no explicit width, so it only shrinks to fit its
content — a `margin-left: auto` on the actor span only pushed actor+timestamp to the edge of that
narrow shrink-to-fit box, not the actual visible right edge of the card. Confirmed by reading an
actual Playwright screenshot directly, not by trusting the passing geometry test alone — the test
compared the actor/timestamp against `.entity-activity-meta` itself, which was exactly the too-narrow
container, so the assertion technically passed while the visual result was still wrong.

**Fix A:** `entity-activity.css` — add `width: 100%` to `.entity-activity-meta` so the flex
container spans the full row width, letting `margin-left: auto` on the actor span reach the card's
real right edge. (Applied.) Order in this tab was already correct — version, action, actor,
timestamp, actor immediately before timestamp — only the width was wrong.

**Problem B — Timeline (`.activity-feed-row`):** `AuditTimelineRowRenderer.buildRow()` adds
elements in this order: `actionSpan`, `typeSpan`, `timeSpan`, then conditionally the editor badge
last. `margin-left: auto` was applied to the editor badge (the last element), so the editor ends
up flush right — but `timeSpan` sits *before* it in DOM order, so the timestamp is left of the
editor, not the rightmost element. Desired order: actor (editor) immediately before the timestamp,
with the **timestamp** as the true rightmost element.

**Fix B:**
1. `AuditTimelineRowRenderer.buildRow()` — reorder so the editor badge (if present) is added
   *before* `timeSpan(...)`, not after.
2. `activity-feed.css` — move `margin-left: auto` off `.activity-feed-editor` and onto
   `.activity-feed-time` instead, so the timestamp is what anchors the group to the right edge
   (whether or not an editor badge is present for a given row).
3. Re-verify with an actual screenshot after deploying, not just the geometry test — the Activity
   tab bug above proved a passing geometry assertion can still hide a visually wrong result when
   the container being measured against is itself the bug.

## Related

- `[[feedback_audit_diff_always_full_fields]]` (memory) — background on why the body already
  contains the name (full-field dump), which is why removing it from the header loses no
  information.
- [improvement-127](improvement-127-entitytype-localization-taxon-color.md) — EntityType
  localization + TAXON color gap, carved out of this same investigation, not being worked on now.
