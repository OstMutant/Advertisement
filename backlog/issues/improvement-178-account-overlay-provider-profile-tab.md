# improvement-178: `AccountOverlay` — Name/Settings/Provider Profile tabs, `ProviderProfileSaveService`, permission model

**Type:** improvement — new UI overlay + orchestrator save-service, carved out of `improvement-124`
Batch 124-C
**Module:** `marketplace-app` (new `AccountOverlay`, `AccessEvaluator` additions, `HeaderBar`/Users
grid repoint), `marketplace-orchestrator` (new `ProviderProfileSaveService`)
**Priority:** 🟡 high — **resequenced (2026-08-28), explicit user request: this issue now goes
first**, `improvement-175` is gated on it instead. Gates `improvement-124` Batch 124-D.
**When:** independent, no blockers. Once `ProviderProfileSaveService` (step 2 below) is real,
`improvement-175`'s stale-id fix (and its rejected shared-sanitizer question) gets re-evaluated
against it before that issue is implemented.

## Current state

`provider-profile-spring-boot-starter` shipped backend-only (`improvement-124` Batch 124-B) — real,
tested code (`ProviderProfileService`, `ProviderProfilePort`), but no UI calls
`ProviderProfilePort.save()` anywhere in `marketplace-app` or `marketplace-orchestrator` today —
confirmed directly, no production caller exists. The domain has been sitting built-ahead-of-its-UI
since 2026-08-01.

Separately, a plain `USER` cannot self-edit their own name today — a real, pre-existing gap this
batch also closes.

`ADR-067` (`marketplace-app/DECISIONS.md`) removed the old 2-tab Content+Activity pairing
(`buildTabbedContent()`/`buildContentWithActivity()`/`ActivityTabParams`) entirely — every domain
now uses a single content area plus a `.{domain}-history-button` icon button (CSS class name only —
described here as "activity button", not "history", per user preference) that opens the shared
`EntityActivityOverlay` as a nested overlay. Any technical plan referencing the old 2-tab machinery
is stale.

**Correction (2026-08-28) — this issue's own original text was stale too, caught during planning.**
The earlier draft said the Provider Profile tab needs a `ProviderProfileActivityFieldsHookImpl`
"per `ADR-065`". Verified directly: `ADR-065` in `marketplace-app/DECISIONS.md` is about the F-02
city dictionary, unrelated. More importantly, `platform-commons/CLAUDE.md` already states
`AuditActivityFieldsHook` **does not exist** — that whole per-domain Hook pattern was removed;
field-name-to-label mapping now lives entirely in `marketplace-app`'s own
`AuditTimelineRowRenderer.labelFor()`. Confirmed directly by reading that class: a
`LABELED_ENTITY_TYPES` `EnumSet` (`ADVERTISEMENT, TAXON, USER, USER_SETTINGS` — no
`PROVIDER_PROFILE`) gates whether `labelFor()`'s `switch` even runs; the `switch` itself has no
`PROVIDER_PROFILE` case, so today a provider-profile activity entry would show its raw
`Fields.*` constant name untranslated. See the corrected Approach step 1b below — no Hook
implementation needed at all.

## Why change

Closes three real gaps in one coherent piece of work: (1) provider profiles have no way to be
created or edited by an actual user, so the backend module built in Batch B has zero real-world
effect; (2) a plain `USER` cannot self-edit their own display name; (3) moderator/admin viewing
another user's account currently has no field-level readonly enforcement.

## Expected benefit

A single `AccountOverlay` (replacing `SettingsOverlay`) covering Name + Settings + Provider Profile
for both self-service and admin/moderator viewing of another user, with narrower moderator
permissions than full edit — the concrete UI surface that makes `provider-profile-spring-boot-starter`
actually reachable by a real user for the first time.

## Approach

Grounded directly in the real, current code (`UserOverlay`/`UserFormOverlayModeHandler`/
`AbstractEntityOverlay`/`AccessEvaluator`/`AdvertisementSaveService`/`AuditTimelineRowRenderer`
all read in full before writing this) — not re-derived from the stale original Batch 124-C text.

**1a. `AccountOverlay` structure — reuses the existing OverlaySession pattern, no new overlay
mechanism needed.** `AbstractEntityOverlay<H extends AbstractFormOverlayModeHandler<?>>` already
holds exactly one `currentFormHandler` at a time and switches it via `switchTo()` — `UserOverlay`'s
own `Mode {VIEW, EDIT}` enum + `OverlaySession` record + `switchTo()`'s `switch` expression is
the exact shape to extend, not a new pattern:
```java
private enum Section {NAME, SETTINGS, PROVIDER_PROFILE}
private record OverlaySession(Section section, Long targetUserId, boolean enteredFromView) { ... }
```
A `Tabs` component (Vaadin's own, no precedent for it *inside* an overlay yet — this is genuinely
new for the overlay layer, first real use) sits in the header/content area, its
`addSelectedChangeListener` calling the same `switchTo()` pattern `switchToEdit()` already
demonstrates in `UserOverlay` — `currentFormHandler = null` first (existing rule), then a `switch
(session.section())` builds whichever mode handler is now active. Each section keeps its own
independent save/discard state (already how `AbstractEntityOverlay.handleSave()`/`hasUnsavedChanges()`
work per-`currentFormHandler` today) — **open question, not assumed:** whether switching tabs with
unsaved changes in the current one should trigger the same discard-confirmation flow `doCancel()`
uses, or block the switch outright. Decide this concretely during implementation, don't silently
pick one.

**1b. Name section** — near-directly reuses `UserFormOverlayModeHandler`'s existing shape
(`UserProfileService.save()`, the same field/binder pattern) — this already closes the "plain USER
can't self-edit name" gap once `AccountOverlay` is reachable by a plain user, not `UserOverlay`.
**Settings section** — reuses `SettingsFormModeHandler` as-is. **Provider Profile section** — new
`ProviderProfileFormOverlayModeHandler`, calling the new `ProviderProfileSaveService` (1c below).
Each section gets its own `.{section}-history-button` (class name; called "activity button" in
conversation) opening `EntityActivityOverlay` for its own `EntityRef` — `EntityRef(EntityType.USER,
...)` / `EntityRef(EntityType.USER_SETTINGS, ...)` / `EntityRef(EntityType.PROVIDER_PROFILE, ...)`,
same 3-argument `.build()` call `UserFormOverlayModeHandler.buildHistoryButton()` already shows.

**1c. Provider Profile activity labels — correction from the original plan, verified against real
code.** No Hook needed (see "Correction" above). Two small, direct edits to
`AuditTimelineRowRenderer.java`: add `EntityType.PROVIDER_PROFILE` to the `LABELED_ENTITY_TYPES`
`EnumSet`, and add a `case PROVIDER_PROFILE -> switch (rawFieldKey) { ... }` branch to `labelFor()`
mapping `ProviderProfileSnapshotDto.Fields.kind/about/categoryIds/cityTaxonId` (confirmed exact
field set by reading that DTO) to 4 new `I18nKey.CHANGES_FIELD_*` constants, same shape as the
existing `ADVERTISEMENT`/`TAXON`/`USER`/`USER_SETTINGS` branches.

**2. `ProviderProfileSaveService`** — new, in `marketplace-orchestrator`
(`org.ost.orchestrator.services`), mirroring `AdvertisementSaveService`'s exact shape (read in
full): a `TransactionTemplate`-bounded `save(ProviderProfileSaveDto dto, Long actorId, ...)` — 2
direct ports (`ProviderProfilePort` + `AuditPort`, both via `ComponentFactory`) + the existing
`TaxonAssignmentWriteService` collaborator for the category-assignment write. Move that write out
of `ProviderProfileService.save()`/`.delete()` in the starter — currently called directly from
there — the same way `AdvertisementService.delete()`'s own cascade already moved out. No
attachment-snapshot step (providers have no attachments, unlike `AdvertisementSaveService`'s
gallery-commit logic). Audit capture via `auditPortFactory.ifAvailable(p ->
p.captureCreation/captureUpdate(...))`, same `isNew`/`before`/`after`-snapshot pattern
`AdvertisementSaveService.save()` already uses.

**3. Decision to make once step 2 lands:** `TaxonAssignmentWriteService`/
   `AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService` (`marketplace-orchestrator`)
   each have exactly one caller today, `AdvertisementSaveService` — built during `improvement-136`
   as shared/reusable on the assumption this batch would become a second consumer. Check directly
   (grep, not memory) whether `ProviderProfileSaveService` actually ends up calling
   `TaxonAssignmentWriteService`. If yes and the shared shape holds — keep it, done. If no — fold
   `TaxonAssignmentWriteService` back into `AdvertisementSaveService` as a private method rather
   than leaving a "shared" class with one permanent caller.
   `AttachmentSnapshotReaderService`/`AttachmentSoftDeleteService` have no ProviderProfile
   equivalent (providers don't have attachments) — decide those two separately, on their own
   merits, regardless of how the Taxon one resolves.

**4. `AccessEvaluator` additions** — new `canViewUserAccount(Long targetUserId)`/
`canEditUserAccount(Long targetUserId)`, built on the existing `canOperate(Long ownerUserId)`
(already `isAdmin || isModerator || isOwner(target)` — confirmed by reading `AccessEvaluator`
in full). **Open question, not assumed:** the original Batch 124-C text says "narrower moderator
edit permissions" / "field-level readonly for `MODERATOR` viewing another user" but never names
*which* fields — decide this concretely (e.g. does a moderator get to change `kind`/category
assignments on someone else's provider profile, or only view them?) before implementing the
readonly wiring, not invented here.

**5. Repoint + cleanup:** `HeaderBar`'s `settingsOverlay.openSettings()` call → the new
`AccountOverlay` equivalent (self-service entry point). `UserView`'s `onView`/`onEdit` callbacks
(currently `overlay.openForView(...)`/`overlay.openForEdit(...)` against `UserOverlay`) → repoint
to `AccountOverlay` for admin/moderator viewing another user. Delete `UserOverlay`/
`UserFormOverlayModeHandler` and their tests once fully repointed — confirmed no other caller of
either exists beyond `UserView` and `HeaderBar`.

## Testing strategy

Unit: `AccessEvaluatorTest` new cases × 3 roles × {self, other}. Integration tests green.
Playwright: update spec 03's existing `adminEn edits userEn name` test in place (repoint + extend),
run via `bash scripts/playwright.sh e2e --ux`. Must be fully green before `improvement-124` Batch
124-D starts — that batch builds the public surface on top of profiles only creatable through this
overlay.

**Playwright gap found during implementation (2026-08-29):** the Provider Profile tab — including
its View/Edit split added mid-implementation (empty state, Create/Edit button, category/city
persistence on re-edit, its own `.provider-profile-history-button`) — has zero Playwright coverage;
`grep` across `e2e/*.spec.js` and `e2e/_flows/*.js` finds no match for `provider.profile`/
`ProviderProfile` anywhere. Separately, re-verified `AccessEvaluator` directly: `canEditUserAccount`
is decided (self or admin only; a moderator viewing another user's account is fully read-only
across all three tabs, never partial) — this closes the "open question" in step 4 above — but that
read-only behavior itself is untested for any of the three tabs (Name/Settings/Provider Profile),
not just the new one.

**Plan, in three parts:**

**Part A — insert Provider Profile as spec `04`, renumber the rest.** Provider Profile earns its
own spec file (comparable in scope to Advertisement's own `04`), inserted at position `04` rather
than appended at the end, since spec files are read top-to-bottom as the domain tour of the app.
Renumber (git mv, preserving history):
- `04-marketplace-advertisement-flow.spec.js` → `05-marketplace-advertisement-flow.spec.js`
- `05-seed-filter-sort-pagination.spec.js` → `06-seed-filter-sort-pagination.spec.js`
- `06-marketplace-delete-flow.spec.js` → `07-marketplace-delete-flow.spec.js`
- `07-accessibility.spec.js` → `08-accessibility.spec.js`

Every real (non-generated) cross-reference to the old numbers gets updated in the same change —
found by direct grep, not assumed:
- Flow-file header `Input:` lines naming the old filenames: `advertisement.flow.js`,
  `attachment.flow.js`, `auth.flow.js`, `category.flow.js`, `city.flow.js`, `delete.flow.js`,
  `entity-activity.flow.js`, `filter.flow.js`, `settings.flow.js`, `seed.flow.js`,
  `timeline.flow.js`, `user-management.flow.js` — plus adding `04-provider-profile-flow.spec.js`
  to whichever of these its own new tests end up requiring (`audit.flow.js`/`entity-activity.flow.js`
  for history-button verification, `category.flow.js`/`city.flow.js` for the category/city combo
  boxes, `user-management.flow.js` for the moderator-readonly scenario).
- Each renamed spec file's own header `Usage:` line (self-reference to its own filename).
- `03-marketplace-promotion-flow.spec.js`'s own text: two docstring lines and one test name
  currently say "spec 04" (meaning today's Advertisement spec) — become "spec 05".
- `05-seed-filter-sort-pagination.spec.js` (renamed `06`)'s own docstring: "later specs (e.g. spec
  06)" (meaning today's Delete spec) — becomes "spec 07".
- `playwright/README.md`: "e2e suite (01–07)" → "(01–08)", "spec 05 seed" → "spec 06 seed".
- `.claude/rules/playwright.md`: "specs 01–06" → "01–07", "spec 05" → "spec 06" (three mentions),
  "spec 04 alone" → "spec 05 alone".
Not touched: `playwright/pw-report/**` (generated report output), `scripts/logs/playwright/run.log`
(generated), `docs/architecture/**` (generated by `/sync-docs`, not hand-edited),
`scripts/ci/DECISIONS.md` (append-only historical record of a past CI run against the
then-current filename — describes what was true then, not current state).

**Part B — new `04-provider-profile-flow.spec.js`.** Same header/docstring shape as
`05-marketplace-advertisement-flow.spec.js` (post-rename). Depends on spec `02` (TEST_USERS
registered) and spec `03` (Electronics/Vehicles categories, Lviv/Kyiv cities already seeded — no
new category/city creation needed in this spec). Tests, mirroring the Advertisement spec's own
create/edit/activity/history pattern and the Name/promotion spec's view-mode-badge-check pattern:
1. `userEn creates provider profile — empty state before, Create button, kind/about/category/city
   filled, view mode shows kind badge/about/category chips/city chip after save`.
2. `userEn edits provider profile — re-edit shows previously-saved kind/about/categories/city
   pre-filled, save records activity diff, history button opens EntityActivityOverlay with the
   create+update entries, outer breadcrumb link closes directly to list` — this is the regression
   test for the actual bug just fixed (categories silently empty on re-edit), structured the same
   way `05-marketplace-advertisement-flow.spec.js`'s edit tests already assert activity diffs and
   `03`'s `adminEn edits Electronics` test already asserts the outer-breadcrumb-closes-to-list
   behavior.
3. `moderatorEn views userEn's provider profile — read-only, no Edit/Create button, same read-only
   shape for Name and Settings tabs on the same account` — closes the coverage gap found above;
   one test exercising all three tabs' read-only state for a moderator viewing another user's
   account, entered via the Users grid (`runOpenUserViewDialogFlow`-style entry, not
   `openForSettings`).

**Part C — audit existing Users/Settings tests, not just rename their selectors.** Read
`02-marketplace-authentication-flow.spec.js`'s `runVerifySettingsAfterSignupFlow` usage,
`03-marketplace-promotion-flow.spec.js`'s `adminEn edits userEn name` test, and
`06-seed-filter-sort-pagination.spec.js` (post-rename)'s `adminEn changes page sizes` test against
the *current* `AccountOverlay` code (tab-switching behavior, breadcrumb shape, `enteredFromView`)
to confirm each still asserts real, current behavior rather than having been mechanically
selector-renamed (`.user-overlay`/`.settings-overlay` → `.account-overlay`, already done earlier
this session) without re-checking whether the assertions themselves still match what the merged
overlay actually does. Report findings before changing anything else.

Verify via `bash scripts/deploy-and-run.sh --reset` + `bash /app/playwright/run.sh e2e --full --ux`
once Parts A–C are implemented — `--full` is not required by Part B's own tests (they only need
spec `03`'s non-boundary Electronics/Vehicles/Lviv/Kyiv seeding), but is run anyway here since it
is the full regression pass this change's scope (a spec renumber touching every later file) calls
for, matching this project's "clean DB immediately before a verification run" rule.

## Related

- `improvement-124` — originally tracked this as Batch 124-C; carved out into this standalone issue
  2026-08-28 per explicit user request. Gates that issue's Batch 124-D.
- `improvement-175` — gates this issue (shared `save()` shape cleanup should land first).
- `marketplace-app/DECISIONS.md` `ADR-067` — removed the 2-tab Content+Activity pairing this
  batch's own plan must not assume still exists.
- `AuditTimelineRowRenderer.java` (`marketplace-app`) — the real, current home for
  entity-field-label mapping (step 1c) since the old per-domain `*ActivityFieldsHook` pattern
  this issue's original text incorrectly assumed was removed project-wide.
- `improvement-136` — the extraction that put `AdvertisementSaveService`/`TaxonAssignmentWriteService`
  in `marketplace-orchestrator`, the shape this issue's `ProviderProfileSaveService` mirrors.
- `improvement-147` — originally tracked the `TaxonAssignmentWriteService` single-caller decision
  before this batch had a concrete plan; the decision itself moved here (step 3 above).

## Follow-up fixes found during implementation and manual testing (2026-08-29)

- **Categories/about/city not pre-filled on re-edit** — `ProviderProfileSaveService.findById()`/
  `findByActorId()`/`buildCurrentSnapshot()` returned unenriched DTOs (no `categoryIds`/
  `categoryNames`/`cityName`). Fixed by wrapping each with
  `ProviderProfileDisplayEnrichmentService.enrichWithCategoryAndCity(...)`. Regression test: spec
  `04`'s test 2.
- **Moderator could bypass the View-mode Edit gate for Name and Settings** — `AccountOverlay.openForEdit()`
  (Users grid's Edit action) reaches `AccountNameFormModeHandler` directly, skipping
  `AccountNameViewModeHandler`'s own gate; Settings has no View mode to gate at all. Fixed by adding
  a `canEditUserAccount` re-check inside both form handlers (readonly fields + hidden Save/Discard).
  Regression test: spec `04`'s test 3.
- **Provider profile created for the wrong owner when an admin creates it via the Users grid** —
  `ProviderProfilePort.save()`/`ProviderProfileService.save()` used the acting user's id as the new
  row's `actor_id`, correct only for self-service. Fixed by threading an explicit `targetUserId`
  parameter through `ProviderProfilePort.save()` → `ProviderProfileService.buildEntity()` →
  `ProviderProfileSaveService.save()` → `ProviderProfileFormOverlayModeHandler.save()` (passes
  `params.getTargetUserId()`), separate from the acting-user id used for audit capture. Regression
  test: spec `04`'s test 4 (`adminEn creates ... userUk's provider profile via the Users grid`) and
  `ProviderProfileServiceTest.save_actorIdTakenFromTargetUser_whenCreatingNewProfile`.
- **No CSS for the Provider Profile view** — `.provider-profile-kind-badge`/`-category-chip`/
  `-city-chip` had no stylesheet at all (unlike Advertisement's equivalent chips/badge), rendering
  as plain unstyled text. Added `marketplace-app/src/main/frontend/themes/my-app/provider-profile-overlay.css`,
  imported from `styles.css`, mirroring `advertisement-card.css`'s chip/badge shape.
- **`UserDeleteService` bypassed `ProviderProfileSaveService.delete()` on account-deletion cascade**
  — found by `/review` after the above fixes landed. Deleting a user with a provider profile called
  `ProviderProfilePort.delete()` directly, skipping taxon-assignment cleanup and audit capture that
  `ProviderProfileSaveService.delete()` now exclusively owns (the advertisement cascade branch in
  the same class already routed through `AdvertisementSaveService.delete()` correctly). Fixed by
  routing the provider-profile cascade through `ProviderProfileSaveService` too, and removing the
  now-unused direct `ComponentFactory<ProviderProfilePort>` field. `UserDeleteServiceTest` updated
  to mock `ProviderProfileSaveService` instead of the raw port.
- **Still open, not yet fixed:** `.claude/rules/provider-profile-spring-boot-starter.md` still
  documents category assignments as written directly by the starter's own service via
  `TaxonPort.replaceAssignments()` — that responsibility moved to `ProviderProfileSaveService` as
  part of this issue. `ProviderProfileFormOverlayModeHandler` also has no defensive
  `canEditUserAccount()` re-check of its own (unlike its Name/Settings siblings) — currently safe
  since no direct-entry path bypasses `ProviderProfileViewModeHandler`'s gate, but worth hardening
  if such a path is ever added.
