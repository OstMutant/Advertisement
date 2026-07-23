# improvement-046: Advertisement list reorders/shifts under concurrent edits — offset pagination over an activity-sorted, mutable dataset has no stable-view guarantee

**Type:** improvement — UX/architecture, needs a product decision before implementation.
**Not ready for execution** — this issue captures a design discussion, not an agreed fix. See
"Needs decision" below before any code changes.
**Module:** `marketplace-app` (`AdvertisementsView`, `AdvertisementQueryBlock`,
`AdvertisementQueryConfig`), `advertisement-spring-boot-starter` (`AdvertisementRepository`),
`query-lib` (`PaginationSqlBuilder`) — cross-cutting, the same root cause likely applies to
`UserView`'s grid too (same offset-pagination shape via the same `PaginationSqlBuilder`).
**Priority:** medium — not a data-correctness bug (nothing is lost or corrupted), a UX/predictability
issue that gets worse as concurrent usage grows. Worth deciding on now while the pattern is easy to
change, before more views/features build on the same pagination shape.
**When:** independent, no blockers — but explicitly **do not implement anything from this issue
without a follow-up product discussion** confirming which option (see below) is chosen.

## Problem

Verified against current code: `AdvertisementQueryConfig.advertisementSortProcessor()` defaults
the advertisement list to `Sort.by(Sort.Order.desc(updatedAt), Sort.Order.desc(createdAt))`
("most recently active first"), and pagination is classic SQL `LIMIT`/`OFFSET` via
`PaginationSqlBuilder.pageLimit()` (`query-lib`). `AdvertisementsView` is the general
advertisement-management list — used both by regular users managing their own ads and by
admins/moderators managing everyone's (`AccessEvaluator.canOperate()` gates edit/delete, not
visibility) — i.e. closer to a **work queue** than a pure public content feed.

This combination has two distinct, layered problems:

**1. Editing your own row moves it under you.** After `Save`, the user returns to a list where
their row jumped from (say) page 3 to page 1, because `updatedAt` just changed. This is
**intentional and arguably correct product behavior** ("what changed should surface"), not a bug
— but the *transition* is jarring: the user loses their place with no explanation.

**2. Offset pagination has no stability guarantee at all once *anyone* mutates the sorted column
— not just the current user.** This is the deeper issue, independent of which field is sorted on
(`updatedAt`, `createdAt`, anything). `OFFSET 40` means "give me whatever is currently in
positions 40-49," not "give me the same 10 rows I saw a moment ago." If other users create,
edit, or delete rows while the current user is paginating/editing, `OFFSET`'s meaning silently
shifts underneath them — rows can repeat across pages, get skipped entirely, or a page can look
like an arbitrary shuffle of what was there before. This happens even sorting by immutable
`createdAt` if rows are being deleted/inserted concurrently; it is not specific to `updatedAt`.

**Not in scope for this issue:** the earlier idea of just switching the default sort away from
`updatedAt` to something more "stable" was considered and explicitly rejected — it treats the
symptom (jarring reorder) while giving up real product value (surfacing recently-active listings),
and does not fix problem 2 at all (offset pagination is unstable under concurrent mutation
regardless of sort key).

## Context established

- This is `AdvertisementsView`, not a "Мої оголошення"-only screen — any authenticated user sees
  the same paginated grid; `AccessEvaluator.canOperate()` only gates whether the *action* buttons
  (edit/delete) are enabled per row, not which rows are visible. Functionally the screen behaves
  like an internal management/work-queue tool more than a public consumer feed, even though it's
  not exclusively an admin screen.
- Given that framing, a **stable working-set** model (don't let the view shift mid-session; show
  an explicit "N new/changed" indicator instead of silently reordering) is a more defensible
  default than a feed-style "always live" model — mirrors how Jira/GitHub PR lists/most CRMs treat
  an open list as a fixed result set until the user explicitly refreshes.

## Options discussed (ranked by cost, not by preference — no option is decided yet)

### A. Post-save UX acknowledgment (cheapest, addresses problem 1 only)
Don't silently dump the user back into a list that may have reordered. After `Save`, show an
explicit confirmation with a choice: `[View]` / `[Back to list]` / `[Keep editing]` — psychologically
prepares the user for "the list may look different now" instead of surprising them.

### B. "Moved to top" toast on return to list (cheap, addresses problem 1 only)
Return to the list as today, but show a one-line explanation: *"Your listing moved to the top
because it was updated."* Doesn't fix problem 2 (other users' concurrent changes), but closes the
"what just happened?" confusion for the common case (the user's own edit).

### C. Separate `activityAt` from `updatedAt` (medium cost, product-semantics fix, doesn't address problem 2)
Split the currently-overloaded `updatedAt` (used both for audit *and* as the ranking signal) into
two fields: `updatedAt` stays a pure technical/audit timestamp; a new `activityAt` becomes the
explicit, product-owned ranking signal with deliberately curated bump rules (e.g. content edits
bump it, a moderator's typo fix or an automated system correction does not). This makes "why did
this move" an explicit, designed decision instead of an accidental side effect of whatever touches
`updatedAt`. Real cost: schema change, deciding the exact bump-rule list per action type, and
auditing every write path that currently touches `updatedAt` to classify it.

### D. Cursor-based (keyset) pagination (medium-high cost, partially addresses problem 2)
Replace `OFFSET` with a keyset cursor (e.g. `WHERE (updatedAt, id) < (:lastSeenUpdatedAt,
:lastSeenId)`). Eliminates duplicate/skipped rows when the dataset shrinks or grows during
pagination. **Does not** solve the "my own edit moved my row to the top of the list I'm still
looking at" annoyance (problem 1) — a moved row is still a moved row from the cursor's perspective.

### E. Frozen working-set / snapshot pagination (highest cost, most complete fix for problem 2)
When the list is first opened, fix the result set (e.g. a captured list of matching IDs, or a
literal snapshot timestamp all queries filter against). Pagination, navigation, and even editing
happen against that frozen set regardless of what other users do concurrently. Surface an explicit
*"18 new/changed listings — [Refresh]"* affordance instead of silently mutating the view. This is
how Jira's JQL result sets, GitHub's PR list, and most professional work-queue tools behave.
**Explicitly not recommended to build now** — real engineering cost (snapshot storage/expiry, or a
materialized ID-set per session) for a marketplace at this stage; revisit if/when this becomes a
recurring complaint or the admin/moderator workflow scales up.

## Needs decision

Before any implementation work starts on this issue, the following needs an explicit answer from
product ownership (not just an engineering call):

1. **What does "top of the list" mean for this screen** — most recently created, most recently
   active (current behavior), or should it eventually support real ranking? (Current: recently
   active, via `updatedAt DESC, createdAt DESC` — confirmed intentional, not accidental.)
2. **Is `AdvertisementsView` conceptually a work queue (stability > liveness) or a feed (liveness >
   stability)?** This session's working conclusion leaned "work queue," but that should be
   confirmed, not assumed, since it drives which of options A-E is worth pursuing.
3. **Scope for a first pass:** is options A+B (cheap, UX-only, ship quickly) sufficient for now, with
   C/D/E deferred as follow-ups if the problem keeps surfacing? Or is this worth a bigger design
   pass (C or E) up front?

## Related

- `AdvertisementQueryConfig.java` (default sort), `PaginationSqlBuilder.java` (`query-lib`, the
  shared OFFSET-based pagination helper — same shape used by `UserRepository`, so any fix chosen
  here likely generalizes to `UserView` too).
- `marketplace-app/DECISIONS.md` ADR-035 — prior art for "compute at read time instead of storing a
  denormalized column," a similar shape of tradeoff to option C's `activityAt` proposal (though
  ADR-035 is about a different field/domain).
- No existing ADR covers list/pagination stability — if a direction is chosen, it should get a new
  ADR entry in `marketplace-app/DECISIONS.md`, not just this issue file.
