# improvement-046: Advertisement list reorders/shifts under concurrent edits — offset pagination over an activity-sorted, mutable dataset has no stable-view guarantee

**Type:** improvement — UX/architecture.
**Status: Implemented (2026-07-25)** — option E (client-side variant) + banner, chosen and built.
See "Decision made and implemented" below. Original design-discussion content kept for history.
**Module:** `marketplace-app` (`AdvertisementOverlay`/`AdvertisementCardView`/`AdvertisementsView`,
`UserOverlay`/`UserView`, `TaxonOverlay`/`TaxonManagementView`), `advertisement-spring-boot-starter`
(`AdvertisementRepository`), `query-lib` (`PaginationSqlBuilder`) — the root cause applied
identically to `AdvertisementsView` and `UserView`; `TaxonManagementView` was also converted for
consistency of approach even though it has no pagination and so never had this symptom.
**Priority:** medium — not a data-correctness bug (nothing is lost or corrupted), a UX/predictability
issue that gets worse as concurrent usage grows.
**When:** done — see ADR-063 in `marketplace-app/DECISIONS.md` for the full implementation record.

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

## Current behavior — verified against code (2026-07-24)

Re-verified directly (not assumed from the earlier write-up) that the root cause is **identical**
across both grids this app has today, not just "likely applies":

**`AdvertisementsView` (`AdvertisementQueryConfig.advertisementSortProcessor()`):**
```java
Sort.by(Sort.Order.desc(AdvertisementInfoDto.Fields.updatedAt), Sort.Order.desc(AdvertisementInfoDto.Fields.createdAt))
```

**`UserView` (`UserQueryConfig.userSortProcessor()`):**
```java
Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("createdAt"))
```

Same two-key sort, same field semantics, same pagination shape (`query-lib`'s
`PaginationSqlBuilder` → plain SQL `LIMIT :limit OFFSET :offset`, no keyset/cursor anywhere in
this codebase yet).

**Exact edit→refresh timing (both domains, same shape):**
- Card/row click → `overlay.openForEdit(entity, onChanged)` where `onChanged` is always the
  owning view's `this::refresh` (`AdvertisementCardView`/`UserGridConfigurator` wiring).
- On save, `AbstractEntityOverlay.handleSave()` → `proceed()` calls `session.onSaved().run()`
  (i.e. `refresh()`) **unconditionally, before deciding whether to stay in EDIT mode or close** —
  confirmed in `AdvertisementOverlay.proceed()`: the grid behind the still-open overlay re-sorts
  itself in the background *while the user is still looking at the edit form*, not at the moment
  they close it.
- `refresh()` in both `AdvertisementsView` and `UserView` calls `getFiltered(filter,
  paginationBar.getCurrentPage(), ...)` — **the page number is preserved, never reset to 1.** This
  means the actual symptom is subtler than "your row jumps to the top in front of you": if the
  user was on page 3 and their edit re-sorts their row to page 1, page 3 doesn't show their row
  moved — it shows **a different row that has now shifted into that slot**, with no visual
  indication anything changed. The user's own edited item silently leaves their current view.
- Page size is per-user, stored in `UserSettingsDto` (`adsPageSize`/`usersPageSize`), wired via
  `SettingsPaginationBinding` — doesn't affect the reorder problem, just the page-size denominator.

**Visibility vs. edit-permission split (confirmed via `AccessEvaluator`):**
`canOperate(ownerUserId)` = `isAdmin() || isModerator() || isOwner()` — this gates **only whether
the Edit/Delete buttons render**, not which rows are in the result set at all. Every viewer
(including anonymous, for `AdvertisementsView`) queries and sees the exact same paginated,
identically-sorted list. This confirms the "work queue, not a personal list" framing: there is no
per-user filtered view where this problem would naturally not apply — one shared, globally-ordered
grid, mutated by anyone with edit rights, viewed by everyone.

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

## External research — how comparable systems handle this (2026-07-24)

Checked how this class of problem is actually solved elsewhere before deciding among A-E.
**No sixth option emerged — every real-world pattern found maps onto A-E already listed** — but
the research meaningfully changes how expensive E actually is, and confirms D unambiguously.

- **Keyset/cursor pagination (option D) is the unambiguous, unanimous industry recommendation**
  for problem 2 specifically. Every source checked ([Sequin](https://blog.sequinstream.com/keyset-cursors-not-offsets-for-postgres-pagination/),
  [Stacksync](https://www.stacksync.com/blog/keyset-cursors-postgres-pagination-fast-accurate-scalable),
  [GetKnit](https://www.getknit.dev/blog/how-to-preserve-api-pagination-stability),
  [Leapcell](https://leapcell.io/blog/efficient-data-pagination-keyset-vs-offset)) makes the same
  point: `OFFSET` is only safe for a genuinely static dataset; any live table needs a keyset
  cursor anchored on a monotonic key (`(updatedAt, id)` tie-break, matching this codebase's
  existing two-key sort exactly) to avoid skipped/duplicated rows under concurrent
  insert/update/delete. This is not a matter of taste — treat "eventually do D" as settled
  regardless of which other option ships first.
- **Real-time dashboards (Power BI / Microsoft Fabric) use a lightweight "Live vs. Paused" toggle
  with a data-freshness indicator**, not a heavyweight server-side snapshot: the currently-rendered
  view simply stops silently swapping rows while the user is looking at it; a small, explicit
  "stale — new data available" affordance invites a deliberate refresh. This is functionally
  **the same UX contract as option E** (Jira/GitHub-style frozen result set) but suggests E's
  *implementation* doesn't have to mean real snapshot storage — the freeze can live **client-side,
  for the duration of one open view**, not server-side/session-persisted. Concretely for this
  codebase: `refresh()` could keep rendering the *already-fetched* page's rows as-is and only
  re-query on an explicit user action (page change, filter change, or clicking a "N updated —
  refresh" banner), instead of re-querying on every `onChanged` callback. That's a much smaller
  change than "materialized ID-set per session" — worth re-costing E with this framing before
  ruling it out as "highest cost."
- No comparable system found silently reorders a work-queue-style grid out from under an actively
  editing user without *some* explicit signal (toast, banner, or a frozen-until-refresh view) —
  reinforces that shipping *nothing* (today's behavior) is the one option not actually validated
  by prior art anywhere.

## Decision made and implemented (2026-07-25)

Product-confirmed answers to the three open questions below: `AdvertisementsView` and `UserView`
are work queues (stability > liveness); "top of the list" stays recently-active
(`updatedAt DESC, createdAt DESC`, unchanged); **option E, client-side variant**, was chosen —
not the heavyweight server-side snapshot originally costed, but the cheaper framing surfaced by
external research (dashboard "live/paused" pattern): keep rendering the already-fetched page as-is,
only re-query on an explicit action. Plus a lightweight "N changes — Refresh" banner (cheap
`count()`-only check on overlay close, no polling) so the user isn't left with silently stale data
indefinitely.

**What shipped, in one line:** editing a row no longer refetches the whole page — the overlay's
`onUpdated` callback splices just that one row/card, in place, with fresh data, into the currently-
rendered list. CREATE/DELETE still trigger a full `refresh()` (row count genuinely changes there).
Taxon was converted too, for consistency of approach, even though it has no pagination and so
never exhibited the "wrong page" symptom — no banner there (no count to compare without paging).

Full technical record, including a real bug found and fixed mid-implementation (Taxon's CREATE
overlay briefly auto-closed after save, breaking 3 Playwright tests that expect it to stay open):
**`marketplace-app/DECISIONS.md` ADR-063**.

**Verification:** `bash scripts/unit-tests.sh marketplace-app` — 73/73 (incl. ArchUnit boundary
checks). Full Playwright `e2e --full --ux` — 50/50 on the post-fix re-run.

**Deliberately deferred, not part of this pass:** option D (keyset/cursor pagination) — still the
correct eventual fix for the *deeper*, unrelated instability (other users' concurrent inserts/
deletes shifting unrelated rows across pages), which the client-side splice does not address.
Tracked here as a known follow-up, not a separate issue, since it targets the same root cause
described in "Problem" above.

## Related

- `AdvertisementQueryConfig.java` (default sort), `PaginationSqlBuilder.java` (`query-lib`, the
  shared OFFSET-based pagination helper — same shape used by `UserRepository`, so any fix chosen
  here likely generalizes to `UserView` too).
- `marketplace-app/DECISIONS.md` ADR-035 — prior art for "compute at read time instead of storing a
  denormalized column," a similar shape of tradeoff to option C's `activityAt` proposal (though
  ADR-035 is about a different field/domain).
- No existing ADR covers list/pagination stability — if a direction is chosen, it should get a new
  ADR entry in `marketplace-app/DECISIONS.md`, not just this issue file.
