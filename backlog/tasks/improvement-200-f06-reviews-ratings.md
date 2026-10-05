# improvement-200: F-06 — reviews & ratings

**Type:** feature — new product capability (private/roadmap.md Phase 3, F-06)
**Module:** new `feedback-spring-boot-starter` (`feedback` + `feedback_comment` +
`feedback_aggregate` tables, generic `EntityRef`-based attachment, `FeedbackPort` in
platform-commons — fully self-contained, no Hook, mirrors the existing
`contact-spring-boot-starter` precedent), `marketplace-app` (feedback list, threaded comment UI,
star rating UI, feedback form overlay, moderation admin UI)
**Priority:** 🟡 Top — active roadmap item, next unblocked item in `private/roadmap.md`'s Phase 2→3
order now that `improvement-199` (F-05) shipped 2026-09-29; user-requested Top placement, 2026-09-22
**When:** unblocked — depends only on F-04 (provider profile) and the advertisement domain, both
already shipped (`improvement-124`)

## Current state

Phase 1 done (2026-09-30): `feedback-spring-boot-starter` exists — `feedback` +
`feedback_aggregate` tables, `FeedbackPort`, and the UI (rating + text list, submission form) is
wired into `ProviderProfileCatalogViewModeHandler`'s public catalog overlay. Phases 2-5 (see
below) are not started — no `feedback_comment`/comment-tree table or UI, no moderation, no
advertisement-side wiring, no rate limiting yet.

## Why change

Per `private/roadmap.md`, reviews are the #1 trust signal and this project's stated "moat" vs
the Facebook group it's migrating users from — persistent reputation is the single strongest
reason to use the platform over the group, and the substance behind future PRO/badge
monetization (F-09).

## Expected benefit

A measurable trust signal (reviews/week, % entities with ≥10 reviews, contact-reveal uplift on
reviewed profiles) and a real competitive differentiator the FB group structurally cannot offer
(comment-thread reputation evaporates; a review — and its discussion tree — stays).

## Approach

Full spec: `private/features/F-06-reviews-ratings.md`. Summary:
- New `feedback-spring-boot-starter`, mirroring the project's existing modular-starter pattern
  (own Liquibase changelog, `FeedbackPort` in platform-commons): `feedback` table (`author_id`,
  `entity_type`, `entity_id` — generic `EntityRef` target, the same `entity_type`+`entity_id`
  convention `contact-spring-boot-starter`'s `contact_info`/`contact_view` tables already use),
  `rating`, sanitized text, `created_at`, moderation status; `feedback_comment` table
  (`feedback_id` real FK, `parent_comment_id` self-FK, `author_id`, sanitized text, `created_at`,
  moderation status) — unbounded discussion tree, read via Postgres `WITH RECURSIVE`.
- `feedback_aggregate` table (own schema, one row per `(entity_type, entity_id)`: `avg_rating`,
  `review_count`) — lives entirely inside this starter, exactly like `contact_info` does today.
  **No column is added to `provider_profile` or `advertisement`, no Hook, no cross-starter
  write** — a deliberate simplification over an earlier draft of this plan that would have
  denormalized onto those two starters' own tables.
- Rules: one feedback entry per author per `(entity_type, entity_id)` (unique constraint),
  registered users only, edit window (~48h). Any registered user may reply to a feedback entry or
  to another reply — no per-entry response cap.
- Validation: feedback/comment text length validated server-side via named `@Size(max=...)`
  constants on the save DTOs (raw-input cap + visible-text cap via `html-sanitizer-lib`),
  following `AdvertisementSaveDto`'s existing pattern; client-side field `maxLength` mirrors it.
- Moderation: visible by default (NEW), flag/report → hidden pending admin decision, applies at
  both feedback and comment level.
- Orphan cleanup: `feedback.entity_type`/`entity_id` has no real FK (targets whichever table
  matches the `EntityType`), so deleting a provider profile or advertisement can orphan its
  feedback/comments/aggregate row — handled by the existing cleanup-service scheduled-job pattern.
- Deleted-author display: reuse `UiLabelHook.markDeleted(String)` (already used by the audit UI)
  for an author who has deleted their account.
- UI: paginated feedback list + expandable/collapsible comment tree (Reddit/HN-style) on the
  entity's own detail view only (provider profile and advertisement overlay), placed directly
  under the existing main-info block — right after `contactRevealPanelFactory.build(...)`, before
  `metaPanelFactory.build(...)`, in both `AdvertisementViewOverlayModeHandler.buildPrimaryContent()`
  and `ProviderProfileCatalogViewModeHandler.buildPrimaryContent()`. No rating stars on
  cards/search results. Feedback form overlay, inline reply composer.
- Audit: only moderation actions (flag→hidden, admin approve/reject/delete) go through the
  existing audit starter — plain feedback/comment creation is not audited (append-only content,
  already self-describing via its own `author_id`/`created_at`).
- Anti-fraud minimum: registered-only, one-per-entity, rate limit entries/comments per day per
  author, admin delete with audit trail.
- Test plan: extend the existing Playwright suite (leave feedback, reply to tree, edit-window
  enforcement, flag/moderate, duplicate-entry rejection) — see spec's own Test plan section.

## Phases

1. ✅ **Done 2026-09-30** — Core feedback, provider profile only (`feedback` +
   `feedback_aggregate` tables, `FeedbackPort`, `FeedbackAccessService` in
   `marketplace-orchestrator`, `FeedbackPanel` UI wired into
   `ProviderProfileCatalogViewModeHandler.buildPrimaryContent()`). Covered by 15 new
   integration-tests (`FeedbackRepositoryTest`/`FeedbackServiceTest`) and a Playwright
   test.step in `04-provider-profile-flow.spec.js` (anonymous-visitor empty state + logged-in
   submission, list/header refresh).
   **Pending refinements (agreed 2026-09-30):**
   - ✅ (2026-09-30) `FeedbackPanel.buildForm()`'s rating field is currently a `RadioButtonGroup<Integer>` with
     star-text item labels — works functionally (click a star label = assign that rating) but
     renders as a visible radio-button list, not a clean star row. Replace with a new
     `StarRatingField` component (`marketplace-app/ui/views/components/fields/`, alongside
     `UiTextArea`) — 5 clickable `VaadinIcon.STAR`/`STAR_O` icons, click sets the value and fills
     stars 1..N, no visible radio circles.
   - ✅ (2026-09-30) `FeedbackPanel.buildHeader()`'s two `Span`s (section label + `"{count} reviews, avg
     {rating}"`) have no CSS at all — render jammed together with no spacing (e.g. "Reviews1
     reviews, avg 5.0"). Same root cause as the card-separation finding in `improvement-203` item
     2 (no dedicated stylesheet for `.feedback-*`/`.contact-reveal-*` classes). Fix: new
     `marketplace-app/src/main/frontend/themes/my-app/feedback-panel.css` with a proper flex/gap
     layout for `.feedback-header`/`.feedback-list`/`.feedback-entry`; restyle the header as
     filled stars (rounded avg) + exact numeric average + count in parentheses (e.g. "★★★★★ 4.8
     (12 reviews)"), matching the `"★".repeat(n)` pattern `buildEntryRow` already uses; adjust the
     `feedback.aggregate.count` i18n message shape to fit (count text separate from the
     stars/number, not one combined sentence).
   - ✅ (2026-10-01) `FeedbackPanel.buildEntryRow()` renders every entry as static text with no "Edit" action —
     the own-author's entry needs an Edit button that opens `buildForm()` pre-filled with the
     existing `rating`/`feedbackText`, submits via `save()` with the existing feedback `id` (not
     `null`), and is hidden/disabled once the 48h edit window (`FeedbackService`'s existing
     server-side check) has closed. Extend the existing `04-provider-profile-flow.spec.js`
     test.step (not a new spec/scenario) to also cover editing.
   - ✅ (2026-10-01) UI copy currently says "review"/"відгук" throughout (`feedback.section.label`,
     `feedback.aggregate.count`, `feedback.empty`, `feedback.form.button.submit`,
     `feedback.form.field.text`, `feedback.notification.saved`) — user-requested wording change
     (2026-10-01) to "Feedback"/"Фідбек" instead. Update
     `marketplace-app/src/main/resources/i18n/messages_en.properties` (lines 497-503) and
     `messages_uk.properties` (lines 498-504); `feedback.form.field.rating` ("Rating"/"Оцінка")
     is unaffected, it never said "review". No code/class/table renaming — property values only.
2. ✅ **Done 2026-10-01** Extend to advertisements (`entity_type=ADVERTISEMENT`, UI in
   `AdvertisementViewOverlayModeHandler`).
3. ✅ **Done 2026-10-03** Comment tree + schema redesign (`feedback_content`/`feedback`/
   `feedback_rating`/`feedback_comment`/`feedback_comment_reaction`, `WITH RECURSIVE`,
   expand/collapse UI, inline reply/edit/delete, reactions, any registered user, unbounded depth)
   — see "Implementation Plan — Phase 3" below for full scope. Status:
   - ✅ Backend (`feedback-spring-boot-starter`, `platform-commons`, `marketplace-orchestrator`) —
     compiles clean, browser-verified via real deploy + `e2e --ux` run (2026-10-02).
   - ✅ UI (`CommentTreePanel`, `FeedbackPanel` inline-edit) — rounds 1 and 2 of UX fixes (see
     below) browser-verified (real deploy + Playwright-driven Chromium, screenshots captured).
   - ✅ `integration-tests` — `FeedbackRepositoryTest` (12/12) and `FeedbackServiceTest` (14/14)
     passing.
   - ✅ Playwright (`04-provider-profile-flow.spec.js:802`) — **root cause found (2026-10-02), not a
     product bug.** `expect(.comment-node with nested-reply text).toHaveCount(1)` at line 802 checks
     *before* the "Show replies" toggle is clicked, while `childrenContainer.setVisible(false)` is
     still in effect. Per Vaadin Flow's own documented behavior ("if you set a component invisible...
     the corresponding element is created in the DOM only when it becomes visible"), the DB row, the
     recursive SQL query, and the server-side component tree are all confirmed correct (verified
     directly: `feedback_comment` row exists with the right `parent_comment_id`; the same
     `WITH RECURSIVE` query re-run manually returns both rows; the `.comment-toggle` correctly shows
     "1" replies) — the nested node's DOM simply never reaches the browser while its container is
     hidden, confirmed via the failing run's own `trace.zip` (the `toHaveCount` check polled 14 times
     across the full 5s timeout, 0 every time — not a slow-render race). **Fix (not yet applied):**
     delete line 802 — lines 804-807 already correctly re-check for the same node *after* clicking
     the toggle (container visible), which is the only point Vaadin actually syncs that DOM to the
     client.
4. ✅ **Done 2026-10-05** Moderation (flag/report on feedback + comment, admin UI, audit for
   moderation actions only) — see "Implementation Plan — Phase 4" and the two "Phase 4 follow-up"
   rounds below for full scope and status.
5. ⬜ Anti-fraud + hardening (rate limiting, orphan cleanup, comment nesting depth cap — see
   "Comment nesting depth cap" below). Orphan cleanup specifically needs a design decision before
   sizing (no existing entity-existence-based cleanup mechanism anywhere in the codebase to reuse,
   despite the spec's own claim otherwise) — deferred, tracked as
   `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` entry 25, not sized here.
   Also found while scoping this phase: zero existing test
   coverage anywhere (`FeedbackServiceTest`, Playwright) for `FeedbackSaveDto.TEXT_MAX_LENGTH`/
   `FeedbackCommentSaveDto.TEXT_MAX_LENGTH` (2000 visible chars) or the raw
   `TEXT_RAW_MAX_LENGTH` (20,000) caps — neither the client-side `maxLength` field behavior nor the
   server-side `@Size` rejection is verified today. Add as part of this phase's own hardening scope:
   an integration test asserting `saveComment`/`save` reject text beyond `TEXT_RAW_MAX_LENGTH` via
   the DTO's `@Size` validation, and a Playwright check that the feedback/comment text field's
   client-side `maxLength` attribute matches the constant.
6. ⬜ **`FeedbackPanel` as its own separate sibling card**, not flat-stacked inside the same card
   as `ContactRevealPanel`/description/meta — already found and fully specified in
   `improvement-203` item 2 (`ContactRevealPanel`/`FeedbackPanel`... no visual card-per-section
   separation), deliberately deferred there until every phase of this task is implemented first.
   Now that Phases 1-3 are substantially done, this is unblocked — see that entry for the full
   wanted layout and every call site affected (`ProviderProfileCatalogViewModeHandler`,
   `AdvertisementViewOverlayModeHandler`, `ProviderProfileViewModeHandler`).

## Phase 3 follow-up — UX investigation & fixes (2026-10-02)

Real usage (manual, by the project owner) of the Phase 3 feedback/comment-tree UI surfaced three
UX bugs the "compiles clean" status above had missed — nothing here was caught by integration
tests or the Playwright script, only by actually opening the feature:

1. **Add-feedback form never hides.** `FeedbackPanel.configure()` adds `buildForm(entityRef)`
   unconditionally once (`if (access.isLoggedIn() && !hasOwnEntry) add(buildForm(entityRef))`), but
   `refresh(entityRef)` (called after a successful save) only rebuilds the header and the list —
   nothing re-checks `hasOwnEntry` against the now-added form, so the form (and the ability to
   submit a second entry) stays on screen until the next full page load (logout/login). Required:
   an explicit "Add feedback" button, hidden once the viewer already has an entry; clicking it
   reveals the form; a successful save immediately hides both the form and the button (no
   relogin needed to see the correct state).
2. **Edit icon for your own feedback entry sits below the review text**, appended at the end of
   `viewContainer` in `FeedbackPanel.buildEntryRow()`, instead of next to the author name.
3. **Comment reply/edit/delete/reaction icons sit in a separate row below the comment text**
   (`CommentTreePanel.buildActionsRow()`, appended after `textContainer`), and the reply composer
   is nested inside that same actions `Div` instead of appearing directly under the specific
   comment it replies to. Standard threaded-comment layout (GitHub/Reddit-style) keeps
   author+actions on one header line (actions right-aligned) with content below, and opens the
   reply box directly under that comment, not in a shared block at the bottom.

**Fix plan (not yet implemented, pending approval):**

- `FeedbackPanel.java`: new `addButton`/`formContainer` fields; `configure()` calls a new
  `buildAddControls(entityRef)` instead of adding `buildForm()` directly; new
  `updateAddControlsVisibility()` (`addButton.setVisible(access.isLoggedIn() && !hasOwnEntry)`)
  called both on initial build and after `buildForm()`'s submit handler's `refresh(entityRef)` (which
  also calls `formContainer.removeAll()`). New i18n key `FEEDBACK_FORM_BUTTON_ADD`
  (`feedback.form.button.add` = "Add feedback"/"Додати фідбек") in `I18nKey.java` +
  `messages_en.properties`/`messages_uk.properties`. New CSS `.feedback-add-button`.
- `FeedbackPanel.buildEntryRow()`: wrap `author` + the edit `UiIconButton` in a new
  `Div.feedback-entry-header` (flex, `justify-content: space-between`) instead of appending the
  edit button inside `viewContainer`.
- `CommentTreePanel.buildNode()`: wrap `author` + the actions row (from `buildActionsRow()`) in a
  new `Div.comment-header` (flex, `justify-content: space-between`). Add a dedicated
  `Div.comment-reply-form-slot` directly under `textContainer`; change the reply `UiIconButton`'s
  click listener (currently toggling its own embedded `Div`) to add/remove `buildReplyComposer(...)`
  from this slot instead, so the composer renders directly under the comment it belongs to.
- Visual polish (user-requested, 2026-10-02): slightly increase breathing-room padding/margin
  around `.feedback-entry`/`.comment-node` (both headers and content), and add a subtle horizontal
  divider line between sibling feedback entries and between sibling comment nodes for clearer
  visual separation — exact CSS values to be picked during implementation, not prescribed here.

Out of scope for this follow-up: the nested-reply Playwright failure above (separate
investigation), Phase 4/5 (moderation, rate limiting).

## Phase 3 follow-up round 2 — real-usage UX feedback (2026-10-02)

After the round-1 fixes above landed and were browser-verified, further hands-on use of the
feature surfaced 9 more concrete UX requests (a 10th, requiring nested comments to also carry a
star rating, was raised and explicitly declined by the user — comments stay text-only, rating
stays first-level-feedback-only, no change needed there). None of these are implemented yet —
pending approval.

1. **"Add feedback" button placement** — currently added to the panel body below the header/list
   (`FeedbackPanel.configure()`'s `buildAddControls()`), not in the same row as the "Feedback"
   section title. Move it into `headerContainer`, right-aligned.
2. **"Add feedback" button must fully toggle, not one-shot-reveal.** Today's click listener only
   ever reveals the form once (`addButton.setVisible(false); formContainer.add(buildForm(...))`) —
   there's no way to close it again short of submitting. Required: click 1 → empty form appears;
   click 2 (form still open) → form closes (user changed their mind), button stays visible/clickable;
   click 3 → form reopens, **empty again** (a fresh `buildForm()` call already guarantees this,
   since it builds brand-new field instances each time).
3. **Feedback-entry Edit icon needs the same toggle+discard behavior.** Today `editButton` only
   ever opens edit mode (`viewContainer.setVisible(false); editContainer.setVisible(true)`), no way
   to close without saving. Clicking Edit again while already editing must discard any typed
   changes (reset `editRatingField`/`editTextField` back to `entry.rating()`/`entry.feedbackText()`)
   and revert to view mode.
4. **Edit/Add form layout — actions move from below the field to the right, vertically, with a
   left border.** Today `editContainer.add(editRatingField, editTextField, saveButton)` stacks
   everything in one column (note: `editContainer` currently has no CSS class at all applied in
   Java — `.feedback-entry-edit`'s existing CSS rule is dead code). Split into two child `Div`s:
   fields (rating + text, narrower now) on the left, a vertical bordered actions column on the
   right containing **two** icon buttons — Apply (save, existing behavior) and a new **Discard**
   icon (new i18n key `FEEDBACK_FORM_BUTTON_DISCARD` = `feedback.form.button.discard` =
   "Discard"/"Відмінити", same reset-and-close behavior as item 3's toggle-close). Apply the same
   fields-left/actions-right-with-border split to `CommentTreePanel`'s `.comment-inline-form`
   (both `showInlineEdit()` and `buildReplyComposer()`), adding the same Discard icon there too
   (reuses `FEEDBACK_FORM_BUTTON_DISCARD`) — "apply this everywhere, not just the feedback form."
5. **Consistent icon-button style everywhere** — the existing small `UiIconButton` pattern already
   used for comment reply/edit/delete/reactions is the look to standardize on; nothing in this
   round introduces a new full-text `Button` where an icon button already fits (the add-button
   itself stays a labeled `Button` since it has no natural icon-only reading — only the per-row
   edit/discard/apply/toggle controls move to icons).
6. ~~Comments requiring a star rating~~ — explicitly declined by the user (2026-10-02): rating
   stays first-level-feedback-only, comments stay text-only. No change.
7. **Comment-header action layout** — currently one flat `.comment-actions` row holds
   reply/edit/delete icons AND the 👍/👎 reaction buttons together. Split into two groups within
   the same row: a reactions group (closer to center) and a controls group (reply/edit/delete,
   pushed to the far right), separated by a vertical divider line.
8. **Comment delete needs confirmation** — today `deleteButton.addClickListener(_ ->
   feedbackAccessService.deleteComment(comment.id()); reload());` deletes immediately, no undo.
   Wrap it with the existing `ConfirmActionDialog` (`org.ost.marketplace.ui.views.components.dialogs`,
   constructor `(title, message, confirmLabel, cancelLabel, onConfirm)`) — same reusable pattern
   `ProviderProfileDeleteUtil.confirmAndDelete()` already uses for provider-profile deletion. New
   i18n keys for the dialog's title/message/confirm/cancel text (feedback-comment-specific, not
   reusing the provider-profile ones).
9. **"Show N replies"/"Hide replies" becomes an icon button, moved into the header's controls
   group** (alongside reply/edit/delete, same row) instead of being a separate full-text `Button`
   appended after `childrenContainer` at the bottom of the node. Icon toggles between
   `VaadinIcon.CHEVRON_DOWN`/`VaadinIcon.CHEVRON_UP`; the reply count stays visible as a small text
   badge next to the icon (reusing the existing count-badge visual style `.comment-reaction-count`
   already has). **Default state flips from collapsed to expanded** — `childrenContainer` starts
   `setVisible(true)` (not `false`), the icon reflects "currently expanded, click to collapse."
10. **Reply icon change + real i18n key.** The reply `UiIconButton` currently reuses
    `FEEDBACK_FORM_BUTTON_SUBMIT` ("Leave feedback"/"Залишити фідбек") as its label/tooltip — wrong
    meaning, confusing. New dedicated key `FEEDBACK_COMMENT_BUTTON_REPLY`
    (`feedback.comment.button.reply` = "Reply"/"Відповісти"). Icon changes from `VaadinIcon.REPLY`
    to `VaadinIcon.COMMENT_O` (a clearer speech-bubble "reply" affordance) — confirmed to exist in
    the pinned Vaadin icon set.

**Fix plan (not yet implemented, pending approval):**

- New i18n keys in `I18nKey.java` + both `messages_*.properties`:
  - `FEEDBACK_FORM_BUTTON_DISCARD` (`feedback.form.button.discard`) = "Discard"/"Відмінити"
  - `FEEDBACK_COMMENT_BUTTON_REPLY` (`feedback.comment.button.reply`) = "Reply"/"Відповісти"
  - `FEEDBACK_COMMENT_CONFIRM_DELETE_TITLE` (`feedback.comment.confirm.delete.title`) =
    "Delete comment?"/"Видалити коментар?"
  - `FEEDBACK_COMMENT_CONFIRM_DELETE_TEXT` (`feedback.comment.confirm.delete.text`) = "This will
    permanently remove this comment. This action cannot be undone."/"Це остаточно видалить цей
    коментар. Цю дію не можна скасувати."
  - `FEEDBACK_COMMENT_CONFIRM_DELETE_BUTTON` (`feedback.comment.confirm.delete.button`) =
    "Delete"/"Видалити"
  - `FEEDBACK_COMMENT_CONFIRM_CANCEL_BUTTON` (`feedback.comment.confirm.cancel.button`) =
    "Cancel"/"Скасувати"
  (naming mirrors the existing `PROVIDERS_CATALOG_CONFIRM_*`/`providers.catalog.confirm.*`
  convention `ProviderProfileDeleteUtil` already uses)
- `FeedbackPanel.java`: `addButton` moves into `headerContainer` (re-appended every
  `refreshHeader()` rebuild, since that method's own `removeAll()` would otherwise drop it);
  `refresh(entityRef)` becomes the single place that re-attaches `addButton` and calls
  `updateAddControlsVisibility()` after both `refreshHeader()`/`refreshList()` finish (so
  `hasOwnEntry` is current). `addButton`'s click listener becomes a true toggle
  (`formContainer.getComponentCount() == 0 ? add(buildForm(...)) : removeAll()`) instead of a
  one-shot reveal. `buildEntryRow()` reordered so `editRatingField`/`editTextField` are declared
  before `editButton` (needed so its toggle-close listener can reset them); `editButton` becomes a
  toggle (open vs. discard-and-close); `editContainer` restructured into two children —
  `.feedback-entry-edit-fields` (rating + text) and `.feedback-entry-edit-actions` (Apply +
  new Discard icon, vertical, bordered) — `editContainer` itself finally gets the
  `.feedback-entry-edit` class its own CSS rule already assumed.
- `CommentTreePanel.java`: `buildActionsRow()` split into a reactions sub-`Div` and a controls
  sub-`Div` (reply/edit/delete/toggle) with a divider between them; reply icon/key changed per
  item 10; delete wrapped in `ConfirmActionDialog`; the show/hide `Button` replaced by a
  `UiIconButton` (chevron + count badge) moved into the controls sub-`Div`, staying conditional
  (`if (!children.isEmpty())`, same as today — an empty chevron with nothing to toggle would be
  confusing) with `childrenContainer` defaulting to visible when it does exist;
  `showInlineEdit()`/`buildReplyComposer()` both
  restructured into fields/actions split matching `FeedbackPanel`'s new edit layout, both gaining a
  Discard icon alongside their existing save icon, and both gaining the same toggle-close-on-second-
  click behavior as item 3 (re-clicking the edit/reply icon that opened a currently-open composer
  closes it without saving).
- `feedback-panel.css`: `.feedback-add-button` loses `margin-top`, gains `margin-left: auto`;
  `.feedback-entry-edit`/`.comment-inline-form` become `display: flex` row splits with new
  `*-fields`/`*-actions` (bordered, vertical) companion classes; `.comment-actions` split into
  `.comment-actions-reactions`/`.comment-actions-controls`/`.comment-actions-divider`; the toggle's
  new icon-button joins the existing `.comment-reply-icon, .comment-edit-icon, .comment-delete-icon`
  sizing selector group.
- Playwright (`04-provider-profile-flow.spec.js`): every selector touching `.feedback-entry-edit`'s
  internal structure, `.comment-inline-form`'s internal structure, the reply icon's old
  `FEEDBACK_FORM_BUTTON_SUBMIT`-derived label, the toggle's old full-text `Button` selector, and the
  delete-icon click (now needs a confirm-dialog click first) will all need updating to match —
  same class of fix as round 1's selector updates, scoped larger this time.

**Status: ✅ Implemented and browser-verified (2026-10-03).** All 9 items landed
(`FeedbackPanel.java`, `CommentTreePanel.java`, `feedback-panel.css`, 6 new i18n keys), `mvn`
compile green, real deploy + full `e2e --ux` run **51 passed, 0 failed, 13 skipped**. Two real
bugs found and fixed only during this verification pass, both in the Playwright spec itself, not
product code:
- Comment delete order: the test deleted the nested leaf reply before the parent, so by the time
  the parent was deleted it had 0 remaining children and was hard-deleted instead of tombstoned
  as the test expected — reordered to delete the parent (tombstone) first, then the now-leaf
  nested reply (hard-delete) after.
- A `commentTree.locator('.comment-node').filter({ hasText: ... })` check for the nested reply
  (added to verify it survives the parent's tombstoning) hit a Playwright strict-mode violation —
  it matched both the nested reply's own `.comment-node` and its tombstoned parent `.comment-node`
  (the parent structurally contains the child's text too) — scoped to the already-defined
  `childrenContainerAfterEdit` instead.

## Phase 3 follow-up round 3 — real-usage UX feedback after round 2 (2026-10-02)

Further hands-on use of round 2 surfaced 7 more items. Not implemented yet — full scope below,
pending approval to dispatch.

1. **Discard must not close the form, only reset values.** Today's `Discard` button (both
   `FeedbackPanel.buildEntryRow()`'s edit form and `CommentTreePanel`'s edit/reply composers)
   resets the fields *and* closes the form (same effect as clicking the trigger icon a second
   time). Wanted: `Discard` only resets fields to their saved/empty state and **leaves the form
   open** — the user can immediately start typing again. Closing (discard-and-hide) stays the job
   of clicking the *trigger* icon (Edit/Reply) a second time — already correct, keep as-is.
2. **"Add feedback" trigger must be an icon button**, not the current text `Button`.
3. **The feedback-add form's own submit control is a text button stacked below the fields**
   (`buildForm()`'s `Button submit` + `form.add(ratingLabel, ratingField, textField, submit)`) —
   move it to a right-side icon button with the same fields-left/actions-right-with-border split
   round 2 already gave the per-entry edit form.
4. **A feedback entry needs its own Reply toggle, next to Edit.** Today `CommentTreePanel`
   unconditionally renders a top-level "write a comment" composer
   (`add(buildReplyComposer(null))` in `configure()`) — always visible, no trigger. Wanted: a new
   icon button in `FeedbackPanel.buildEntryRow()`'s header (next to Edit, reply-before-edit
   ordering to match the comment header's own reply-then-edit-then-delete order) that
   shows/hides it, symmetric to every other reply/edit toggle in this feature.
5. **First-level comments must render visually nested under the feedback, not as peers.** Today
   `byParent.getOrDefault(0L, List.of()).forEach(comment -> add(buildNode(comment, byParent, 0)))`
   starts top-level comments at depth `0` (no indent) — change the starting depth to `1` so they
   get `buildNode`'s existing `margin-left: 20px` and read as "replies to the feedback," not
   siblings of it.
6. **+ 7. Scroll position jumps on every comment save/delete** (both report the same symptom —
   the view ends up somewhere else after the action, instead of staying where the user was
   looking). **Root cause investigated and confirmed, not a guess:** `CommentTreePanel.reload()`
   calls `configure()`, which does a full `removeAll()` + rebuild of the entire comment tree on
   every single save/delete/reaction — this changes the total content height inside the shared
   scrollable container (`.overlay__content`, built by `OverlayLayout.java`, `overflow-y: auto` —
   confirmed via `advertisement-overlay.css`), so the same raw `scrollTop` pixel offset after
   rebuild no longer points at the comment the user was looking at. This is a **different**
   mechanism from the scroll handling `BaseOverlay.java` already has (that one locks/restores the
   *background page's* `window` scroll for the open/close of an overlay itself — unrelated to a
   scrollable panel's own internal content changing height while already open).

**Fix plan (not yet implemented, pending approval):**

- `FeedbackPanel.java`:
  - `addButton` field type `Button` → `UiIconButton`; `buildAddControls()` constructs it with
    `VaadinIcon.PLUS.create()` instead of a plain labeled `Button`.
  - `buildForm()`: `Button submit` → `UiIconButton` (`VaadinIcon.CHECK.create()`, same as the
    entry-edit form's save icon); wrap `ratingLabel`/`ratingField`/`textField` in a new
    `Div.feedback-form-fields`, `submit` alone in a new `Div.feedback-form-actions` (same
    bordered/vertical treatment as `.feedback-entry-edit-fields`/`.feedback-entry-edit-actions` —
    share the CSS rule via a combined selector, not a duplicated block).
  - `buildEntryRow(EntityRef, FeedbackDto)` gains a third parameter,
    `CommentTreePanel commentTreePanel`; a new `UiIconButton` (`FEEDBACK_COMMENT_BUTTON_REPLY`,
    `VaadinIcon.COMMENT_O.create()`, class `feedback-entry-reply-icon`) added to `header` before
    the existing edit icon, visible whenever `access.isLoggedIn()` (not gated by ownership, same
    rule as comment-level reply), click calls `commentTreePanel.toggleTopLevelComposer()`.
  - `refreshList()`: builds the `CommentTreePanel` instance once per entry into a local variable
    (`commentTreePanelFactory.build(...)`), passes it into `buildEntryRow(...)`, then adds it to
    `listContainer` — instead of today's inline `commentTreePanelFactory.build(...)` call directly
    inside `add(...)`.
  - `buildEntryRow()`'s `discardButton` listener: drop the `viewContainer.setVisible(true);
    editContainer.setVisible(false);` lines — only reset `editRatingField`/`editTextField` values,
    leave the form open. The existing `editButton`'s own toggle-close branch (clicking Edit again)
    keeps its current reset-and-close behavior unchanged.
- `CommentTreePanel.java`:
  - `configure()`: `buildNode(comment, byParent, 0)` → `buildNode(comment, byParent, 1)`; replace
    the unconditional `add(buildReplyComposer(null))` with a new `private Div topLevelReplySlot`
    field (class `comment-reply-form-slot`, same as the per-node one), added empty.
  - New `public void toggleTopLevelComposer()`: same toggle-slot pattern already used for
    per-comment reply (`if (topLevelReplySlot.getComponentCount() == 0) ... else
    topLevelReplySlot.removeAll()`), calling `buildReplyComposer(null)` — called from
    `FeedbackPanel`'s new reply icon.
  - `buildReplyComposer(Long parentCommentId)` loses its `Runnable onDiscard` parameter (every
    composer gets a Discard icon now, symmetric with item 1 — there is no longer a "top-level,
    no discard" special case). Its `discardButton` listener becomes `_ ->
    replyField.clear()` (reset only, same as item 1 — composer stays open). Update both call
    sites (`buildReplyComposer(comment.id())` for the per-node reply toggle,
    `buildReplyComposer(null)` for `toggleTopLevelComposer()`).
  - `buildEditComposer()`'s `discardButton` listener: drop `editFormSlot.removeAll();
    textContainer.setVisible(true);` — only `editField.setValue(comment.commentText())`. The
    `editButton`'s own toggle-close branch (clicking Edit again) already handles the close case.
  - `reload()`: wrap the existing `configure(new Parameters(feedbackId))` call with a scrollTop
    save/restore around it, scoped to the nearest `.overlay__content` ancestor:
    ```java
    private void reload() {
        getElement().executeJs(
                "var sc = $0.closest('.overlay__content');" +
                "$0.__savedScrollTop = sc ? sc.scrollTop : null;",
                getElement());
        configure(new Parameters(feedbackId));
        getElement().executeJs(
                "if ($0.__savedScrollTop != null) {" +
                "  var sc = $0.closest('.overlay__content');" +
                "  if (sc) sc.scrollTop = $0.__savedScrollTop;" +
                "}",
                getElement());
    }
    ```
    Needs real browser verification once implemented (Vaadin's documented ordering guarantee for
    `executeJs` calls relative to the component-tree diff they bracket is the basis for this
    design, not independently re-confirmed against this exact case yet) — not a guaranteed fix
    until seen working live, unlike items 1-5 above which are pure Java/CSS with no such
    uncertainty.
- `feedback-panel.css`: new `.feedback-form-fields`/`.feedback-form-actions` (share the existing
  `.feedback-entry-edit-fields`/`.feedback-entry-edit-actions` rule bodies via a combined
  selector, not a duplicate block).

**Status: ✅ Implemented and browser-verified (2026-10-03).** All 7 items landed, `mvn` compile
green, real deploy + full `e2e --ux` run **51 passed, 0 failed, 13 skipped**. Two test changes were
needed beyond the plan above: (1) the top-level "write a comment" composer is now gated behind the
new `.feedback-entry-reply-icon` toggle (item 4), so the existing comment-tree test needed a click
on it before the composer's fields become reachable; (2) a new assertion was added specifically to
get real evidence for item 6+7 (not just trust the `executeJs` ordering claim) — scrolls
`.overlay__content` to a non-zero position before a comment-edit save, asserts it's within 5px of
the same position after the full tree rebuild. **This assertion passed on a real browser run** —
the scrollTop-preservation fix is confirmed working live, not just theoretically sound.
- Playwright (`04-provider-profile-flow.spec.js`): the feedback-submit flow (`.feedback-form-submit`
  stays the same class, only its underlying element type changes, so likely no selector change
  needed there) and the comment-tree flow will need a new step for the feedback-entry-level Reply
  toggle and the depth-1 indentation change; exact selector updates to be determined once the
  Java/CSS side is implemented, not prescribed here.

**Out of scope / not addressed by this round:** `FeedbackPanel.refresh()` (the feedback-entry-level
save/edit path, separate from comment `reload()`) was not reported as jumping and is not touched
here, though it shares the same full-rebuild-inside-`.overlay__content` shape and could have the
same latent issue — flag for a future round if actually observed, not preemptively fixed.

## Phase 3 follow-up round 4 — real-usage UX feedback after round 3 (2026-10-02)

Further hands-on use of round 3 surfaced 5 more items. Not implemented yet — full scope below,
pending approval to dispatch (already approved by the user to proceed straight to Haiku dispatch
for this round).

1. **Reply composers don't sit at the indent level their resulting comment will land on.** The
   top-level "write a comment" composer (`topLevelReplySlot`) renders at the feedback's own left
   edge, but the comment it creates will render at depth 1 (`margin-left: 20px`, per round 3's
   `buildNode(comment, byParent, 1)`) — a visible jump once saved. Same for a per-node
   `replyFormSlot`: it sits flush with its parent `node` (whatever `margin-left` that parent has
   from its own depth), but the reply it creates lands one level deeper. Fix: give
   `topLevelReplySlot` an explicit `margin-left: 20px` (matching depth 1), and give each node's own
   `replyFormSlot` `margin-left: ((depth + 1) * 20) + "px"` (matching the exact formula
   `buildNode` already uses for a real child one level deeper, so the composer visually
   pre-occupies the slot its result will land in).
2. **Deleting a reply bottom-up can leave a dangling tombstone with no children at all.**
   `FeedbackService.deleteComment()` tombstones a comment if it currently has replies, hard-deletes
   it otherwise — but it never re-checks the comment's own *parent* afterward. If a user deletes a
   comment that has one reply (parent tombstones, correctly preserving the thread), then separately
   deletes that one remaining reply (now a leaf, hard-deleted) — the parent is left permanently
   tombstoned with zero children, pointless to keep (nothing left for it to thread together) and
   with no path to remove it since a tombstoned comment with no replies offers no delete action of
   its own in the UI. Fix: after a hard-delete, walk up the parent chain and hard-delete any
   ancestor that is itself tombstoned (`commentText() == null`) and now has zero remaining
   children — repeating up the chain, since removing one dangling tombstone can orphan its own
   parent the same way.
3. **The feedback entry's reply icon renders in the horizontal middle of its header, not the right
   edge**, because `buildEntryRow()`'s header (`display: flex; justify-content: space-between`)
   currently gets 1-3 flat children (`author`, optionally `replyButton`, optionally `editButton`) —
   with exactly 3 children, `space-between` spaces the middle one evenly rather than grouping the
   two action icons together on the right the way `CommentTreePanel`'s own `.comment-header`
   already correctly does (exactly 2 children: author, one actions `Div`). Fix: wrap
   `replyButton`/`editButton` in a single new `Div.feedback-entry-header-actions`, so the header
   always has exactly 2 top-level children regardless of how many icons it contains.
4. **The comment reply icon (`VaadinIcon.COMMENT_O`) looks visually clipped/cramped** inside its
   `1.6rem` button box, unlike the other icons (`EDIT`/`TRASH`/`CHEVRON_*`) at the same size.
   Best-effort fix (not independently confirmed to look right without a live screenshot check
   after implementing — flag this specifically for a visual re-check): give the icon itself an
   explicit smaller size independent of the button's own box via `Icon.setSize("1.1rem")` on the
   `VaadinIcon.COMMENT_O.create()` instance before passing it to `UiIconButton`'s constructor, in
   both places `COMMENT_O` is used (`CommentTreePanel`'s `comment-reply-icon`, `FeedbackPanel`'s
   `feedback-entry-reply-icon`).
5. **Show/hide needs a default flip back to collapsed, plus a recursive expand-all cascade.** Round
   3 made every level's `comment-toggle-icon`/`comment-children` default to expanded; this round
   reverses that back to collapsed by default (the implicit, un-toggled depth-1 comments directly
   under a feedback entry were never gated by a toggle at all and stay always-visible regardless —
   this item is purely about the toggle-gated `comment-children` containers at every depth).
   Additionally: expanding a node must force-show its *entire* descendant subtree in one action
   (every nested `comment-children` below it, regardless of each one's own prior collapsed/expanded
   state), not just its own direct children — collapsing only needs to hide its own
   `comment-children` (descendants are already visually hidden for free as a result, no explicit
   action needed on them).

**Fix plan (not yet implemented, pending Haiku dispatch):**

- `CommentTreePanel.java`:
  - `configure()`: after `topLevelReplySlot` is created, add
    `topLevelReplySlot.getStyle().set("margin-left", "20px");`.
  - `buildNode(..., depth)`: add
    `replyFormSlot.getStyle().set("margin-left", ((depth + 1) * 20) + "px");` right after
    `replyFormSlot` is created.
  - `buildActionsRow()`'s toggle block: revert to collapsed-by-default —
    `childrenContainer.setVisible(false);` before constructing the toggle button; construct the
    toggle with `getValue(FEEDBACK_COMMENT_SHOW_REPLIES, childrenCount)` /
    `VaadinIcon.CHEVRON_DOWN.create()` initially (matching the new default-collapsed state,
    reversed from round 3's default-expanded `HIDE_REPLIES`/`CHEVRON_UP`); in the click listener,
    when `nowVisible` is true (expanding), additionally call a new
    `forceExpandDescendants(childrenContainer)` to cascade the expand down.
  - New private static method:
    ```java
    private static void forceExpandDescendants(com.vaadin.flow.component.Component root) {
        root.getChildren().forEach(child -> {
            if (child.hasClassName("comment-children")) {
                child.setVisible(true);
            }
            forceExpandDescendants(child);
        });
    }
    ```
    (Chevron-icon-direction mismatch on a deeply-nested toggle after a cascaded force-expand is a
    known, accepted minor cosmetic limitation of this simpler approach — the icon may still read
    "collapsed" even though its content is now visible; not fixed in this round, visibility
    correctness is what matters.)
  - `VaadinIcon.COMMENT_O.create()` (the `comment-reply-icon` button) — capture into a local
    `Icon` variable, call `.setSize("1.1rem")` on it before constructing the `UiIconButton`.
- `FeedbackPanel.java`:
  - `buildEntryRow()`: wrap the existing `replyButton`/`editButton` additions in a new
    `Div.feedback-entry-header-actions` instead of adding them directly to `header`.
  - Same `VaadinIcon.COMMENT_O.create()` → `Icon.setSize("1.1rem")` treatment for
    `feedback-entry-reply-icon`.
- `feedback-panel.css`: new `.feedback-entry-header-actions { display: flex; align-items: center;
  gap: 4px; }`.
- `FeedbackService.java` (`feedback-spring-boot-starter`):
  - `deleteComment()`: after the existing hard-delete branch, call a new private
    `pruneDanglingTombstones(existing.parentCommentId())`:
    ```java
    private void pruneDanglingTombstones(Long parentCommentId) {
        Long current = parentCommentId;
        while (current != null) {
            FeedbackCommentView parent = repository.findCommentViewById(current).orElse(null);
            if (parent == null || parent.commentText() != null || repository.countChildren(current) > 0) {
                return;
            }
            repository.deleteCommentHard(current, parent.contentId());
            current = parent.parentCommentId();
        }
    }
    ```
    (`repository.countChildren`/`deleteCommentHard`/`findCommentViewById` already exist, used
    unchanged from the existing `deleteComment()` method right above.)
- Test coverage (per standing project rule — every new edge case covered, not just happy paths):
  - `integration-tests` (`FeedbackServiceTest`): new case — build a 3-level chain (feedback →
    comment A → reply B), delete B first (A tombstones, has 0 children after but *is* tombstoned
    so hard-delete fires immediately per the pruning logic — confirm A is actually gone, not left
    dangling), and a second case deleting in the original bottom-up order the bug report described
    (delete the *child* reply first while A still exists untombstoned, confirm nothing is
    incorrectly pruned prematurely while A is still a real, non-tombstoned comment).
  - Playwright (`04-provider-profile-flow.spec.js`): extend the existing comment-tree test.step —
    assert the reply composer's own indent (item 1) via its rendered horizontal position/CSS
    `margin-left`, assert the feedback entry's reply icon is right-aligned (item 3, e.g. via
    `assertRightAligned` — already used elsewhere in this suite per `_helpers.js`), assert
    `comment-children` is hidden by default on a freshly-loaded tree and that expanding a
    multi-level chain shows every descendant level at once (item 5), and a full
    create-chain-then-delete-bottom-up-to-dangling-tombstone scenario matching item 2's backend
    fix end-to-end (not just the integration-test version — this is specifically what the user
    reported noticing from the UI).

**Status: ✅ Implemented and browser-verified (2026-10-03).** All 5 items landed, `mvn` compile
green, `FeedbackServiceTest` 15/15 (new `deleteComment_lastChildOfTombstonedParent_prunesParentToo`
passing), real deploy + full `e2e --ux` run **51 passed, 0 failed, 13 skipped**. Test coverage
landed as: a right-alignment check for `.feedback-entry-header-actions` (item 3), a
collapsed-by-default + explicit re-expand check on the existing nested-reply flow (item 5, scoped
to the existing 2-level chain already in the test rather than a new dedicated 3-level cascade
test — a true multi-level cascade scenario is not yet covered, noted below), and one new assertion
at the very end of the existing delete flow confirming the tombstoned parent is fully gone after
its last child is removed (item 2, end-to-end through the real UI). Item 1 (composer indentation)
and item 4 (icon size) landed in code but have no dedicated automated assertion — both are
visual/cosmetic and were not independently re-confirmed against a live screenshot.

**One additional real product bug found only during this verification pass, not in the original
plan:** `buildNode()` only called `buildActionsRow()` — the method containing both the
collapsed-by-default `childrenContainer.setVisible(false)` and the toggle button construction —
when `!tombstoned`. A tombstoned comment that still has children (the exact shape item 2's fix
produces, and the exact shape the existing test's own tombstone step exercises) therefore never
got its children collapsed and had no toggle button at all to control them, leaving its subtree
stuck permanently visible with no way to hide it. Fixed by calling `buildActionsRow()`
unconditionally and moving the `tombstoned` check inside it — the toggle (and the
`childrenContainer` default-collapsed behavior) now builds regardless of tombstone state, while
reactions/reply/edit/delete still correctly stay hidden for a tombstoned comment (nothing to
react to, reply to, edit, or delete-again on already-deleted text).

**Known gap, not addressed this round:** a genuine 3+-level cascade test for item 5 (expanding a
grandparent should force-show a great-grandchild's content even though the great-grandchild's own
toggle was never individually clicked) is not covered by an automated test — the existing test
only has 2 real nesting levels, insufficient to distinguish "cascade works" from "simple one-level
toggle works." Flag for a future round if this specific interaction is ever reported broken.

## Phase 3 follow-up round 5 — real-usage UX feedback after round 4 (2026-10-03)

Further hands-on use of round 4 surfaced 2 more items. Not implemented yet at time of writing —
full scope below.

1. **Expand/collapse state must persist across `reload()`, not reset to collapsed every time.**
   Round 4 made `comment-children` default to collapsed on every fresh build — correct for the
   very first page load, but `CommentTreePanel.configure()` does a full `removeAll()` + rebuild on
   *every* reply/edit/delete/reaction anywhere in the tree, and since each rebuild starts every
   node fresh with no memory of prior state, a node the user had deliberately expanded collapses
   again the moment any action (even an unrelated one elsewhere in the tree) triggers a reload.
   Wanted: collapsed-by-default applies only to a node that has never been touched; once the user
   expands a specific node, it stays expanded through any subsequent reload, and operations affect
   only the specific element being acted on — no jumping, no unrelated resets.
2. **The reply icon looks visually undersized/thin next to edit/delete/toggle**, confirmed via an
   actual Playwright screenshot (not guessed) — `provider-catalog-comment-tree-top-level-reply.png`
   shows the `comment-reply-icon`/`feedback-entry-reply-icon` rendering smaller than its sibling
   icons. Root cause found directly in the round-4 diff: both were given an explicit
   `Icon.setSize("1.1rem")` call (round 4's own attempted fix for a *different*, never-confirmed
   "looks clipped" complaint) while every sibling icon (`EDIT`, `TRASH`, `CHEVRON_*`, `CLOSE_SMALL`,
   `CHECK`) uses its unmodified default size — the explicit shrink is what created the size
   mismatch the user is now reporting.

**Fix plan:**

- `CommentTreePanel.java`:
  - New instance field `private final Set<Long> expandedCommentIds = new HashSet<>();` — not
    reset in `configure()`, so it survives every `reload()` call for the lifetime of this
    `CommentTreePanel` instance (one instance per feedback entry's comment tree; see the
    known-limitation note below for the one case this doesn't cover).
  - `buildNode()`/`buildActionsRow()`: the toggle's and `childrenContainer`'s *initial* state at
    build time now reads `boolean expanded = expandedCommentIds.contains(comment.id());` instead
    of always starting `false` — `childrenContainer.setVisible(expanded)`, and the toggle's initial
    label/icon (`SHOW_REPLIES`/`CHEVRON_DOWN` vs `HIDE_REPLIES`/`CHEVRON_UP`) picked accordingly.
  - Toggle's click listener: on expand, `expandedCommentIds.add(comment.id())` plus a new
    `collectDescendantIds(comment.id(), byParent, expandedCommentIds)` (walks the *data*, i.e.
    `byParent`, not the component tree — recursively adds every descendant comment's own id so a
    rebuild keeps the whole cascaded-open subtree open, not just the one node directly clicked) —
    needs `buildActionsRow()` to receive `byParent` as a new parameter, threaded through from
    `buildNode()`. The existing `forceExpandDescendants(Component)` component-tree walk stays
    unchanged, still doing the *immediate* visual cascade within the current render (an instant,
    no-reload client-side action) — `collectDescendantIds` is a separate, additional step for
    *persisting* that same cascade across a *future* reload, not a replacement. On collapse, only
    this node's own id is removed (`expandedCommentIds.remove(comment.id())`) — a later re-expand
    starts fresh rather than remembering a deeply stale nested-expand state indefinitely.
  - New private static helper:
    ```java
    private static void collectDescendantIds(Long commentId, Map<Long, List<FeedbackCommentDto>> byParent, Set<Long> out) {
        for (FeedbackCommentDto child : byParent.getOrDefault(commentId, List.of())) {
            out.add(child.id());
            collectDescendantIds(child.id(), byParent, out);
        }
    }
    ```
  - Remove `replyIcon.setSize("1.1rem");` (and the now-unnecessary local `Icon replyIcon` variable
    can stay or collapse back to inline `VaadinIcon.COMMENT_O.create()` directly in the
    `UiIconButton` constructor) — reverts to the same default sizing every sibling icon already
    uses.
- `FeedbackPanel.java`: remove the matching `replyIcon.setSize("1.1rem");` call for
  `feedback-entry-reply-icon`, same reasoning.

**Known limitation, disclosed not fixed:** `expandedCommentIds` lives on the `CommentTreePanel`
instance itself. `FeedbackPanel.refreshList()` calls `commentTreePanelFactory.build(...)` fresh
for *every* feedback entry whenever the feedback list itself refreshes (adding a new feedback
entry, or editing an existing one) — this creates brand-new `CommentTreePanel` instances for
*every* entry on the page, discarding all expand-state for all of them, not just the one entry
being edited. This is a real, not-yet-reported gap sharing the same root shape as the just-fixed
issue, but one level up (`FeedbackPanel`'s own rebuild instead of `CommentTreePanel`'s) — out of
scope for this round since it wasn't what was reported; flag for a future round if it surfaces.

**Status: ✅ Implemented and browser-verified (2026-10-03).** Both items landed in
`CommentTreePanel.java`/`FeedbackPanel.java`, `mvn` compile green, real deploy + full `e2e --ux`
run **51 passed, 0 failed, 13 skipped**. The existing comment-tree Playwright test's own
collapse/re-expand workaround (added in round 4 to tolerate the since-fixed reset-on-reload
behavior) was removed and replaced with a direct assertion that the expanded node's
`comment-children` container stays visible through the subsequent reaction/edit/delete actions —
confirming the persistence fix live, not just compiling.

## Implementation Plan — Phase 1 (2026-09-29, ✅ done 2026-09-30 — backend + UI wiring both landed)

Scope: provider-profile-only feedback (rating + text), no comment tree (Phase 3), no UI wiring
into `marketplace-app` yet (separate follow-up once this backend slice builds and tests green).
Template throughout: `contact-spring-boot-starter` (own self-contained `entity_type`+`entity_id`
starter, no FK to the owning domain, `*Port` resolved generically) — same shape as this module.

**New module `feedback-spring-boot-starter`:**
- `pom.xml` — copy `contact-spring-boot-starter/pom.xml` structure: parent `advertisement-parent`;
  deps `platform-commons`, `query-lib`, `html-sanitizer-lib`, `spring-boot-starter`,
  `spring-boot-starter-data-jdbc`, `spring-boot-liquibase`, `spring-boot-starter-validation`,
  `postgresql` (runtime), `lombok`.
- `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  — `org.ost.feedback.config.FeedbackAutoConfiguration`
- `src/main/resources/db/feedback-changelog/feedback-changelog-master.xml` — includes
  `changes/01-feedback-schema.xml`
- `src/main/resources/db/feedback-changelog/changes/01-feedback-schema.xml` — Liquibase, every
  column with a `remarks` attribute (project convention):
  - `feedback` table: `id BIGSERIAL PK`, `author_id BIGINT NOT NULL` (no FK, references
    `user_information(id)`), `entity_type VARCHAR(30) NOT NULL`, `entity_id BIGINT NOT NULL` (no
    FK — generic `EntityRef`, same convention as `contact_info`), `rating SMALLINT NOT NULL`,
    `feedback_text VARCHAR(20000)` (sanitized HTML via `html-sanitizer-lib`), `moderation_status
    VARCHAR(20) NOT NULL DEFAULT 'NEW'`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT
    NOW()`, `updated_at TIMESTAMP WITH TIME ZONE`, `version BIGINT NOT NULL DEFAULT 0`. Unique
    index `(author_id, entity_type, entity_id)`; plain index `(entity_type, entity_id)`.
  - `feedback_aggregate` table: `id BIGSERIAL PK`, `entity_type VARCHAR(30) NOT NULL`, `entity_id
    BIGINT NOT NULL` (no FK), `avg_rating NUMERIC(2,1) NOT NULL DEFAULT 0`, `review_count INTEGER
    NOT NULL DEFAULT 0`, `updated_at TIMESTAMP WITH TIME ZONE`. Unique index
    `(entity_type, entity_id)`.
- `org.ost.feedback.entity.Feedback` / `FeedbackAggregate` — `@Value @Builder @FieldNameConstants
  @Table(...)`, `@Id`/`@CreatedDate`/`@LastModifiedDate`/`@Version`, mirrors `ContactInfo` entity
  shape exactly.
- `org.ost.feedback.repository.FeedbackCrudRepository extends CrudRepository<Feedback, Long>`;
  `FeedbackAggregateCrudRepository extends CrudRepository<FeedbackAggregate, Long>`.
- `org.ost.feedback.repository.FeedbackRepository` — `@Repository` + `JdbcClient`, mirrors
  `ContactRepository`: `findByAuthorAndEntity(authorId, entityType, entityId)`,
  `findByEntity(entityType, entityId, pageable)` (paginated list for the future UI step),
  `save(Feedback)` delegates to `FeedbackCrudRepository` (lets `DuplicateKeyException` propagate
  naturally on the unique index — same convention `UserService.register()` already uses, no
  custom exception type), `upsertAggregate(entityType, entityId)` — recomputes
  `AVG(rating)`/`COUNT(*)` from `feedback` and upserts the one `feedback_aggregate` row in the
  same transaction as the feedback write (`INSERT ... ON CONFLICT (entity_type, entity_id) DO
  UPDATE`).
- `org.ost.feedback.services.FeedbackService` — `@Service @Transactional`: `save(FeedbackSaveDto)`
  sanitizes `feedbackText` via `HtmlSanitizer.sanitize(text, FeedbackSaveDto.TEXT_MAX_LENGTH)`,
  enforces the 48h edit window (reject update once `createdAt` is older than 48h), calls
  `repository.save(...)` then `repository.upsertAggregate(...)` in the same `@Transactional`
  method; `findForEntity(entityType, entityId, pageable)`; `getAggregate(entityType, entityId)`.
- `org.ost.feedback.spi.FeedbackPortImpl` — pure delegation to `FeedbackService`, mirrors
  `ContactPortImpl` exactly (one line per method, `@Transactional`/`@Transactional(readOnly =
  true)` split the same way).
- `org.ost.feedback.config.FeedbackAutoConfiguration` — mirrors `ContactAutoConfiguration`:
  `@AutoConfiguration(afterName = ".../LiquibaseAutoConfiguration")`, `@ConditionalOnClass
  (DataSource.class)`, `@ComponentScan({"org.ost.feedback.spi", "org.ost.feedback.services",
  "org.ost.feedback.repository"})`, `@EnableJdbcRepositories(basePackages =
  "org.ost.feedback.repository")`, `feedbackLiquibase` bean, `ComponentFactory<FeedbackPort>` bean.

**`platform-commons` additions:**
- `org.ost.platform.feedback.spi.FeedbackPort` — purpose Javadoc (no `Port:`/`Hook:` prefix per
  convention), methods: `find(EntityType, Long, Pageable)`→`Page<FeedbackDto>`,
  `save(FeedbackSaveDto)`→`FeedbackDto`, `getAggregate(EntityType, Long)`→`FeedbackAggregateDto`.
- `org.ost.platform.feedback.dto.FeedbackDto`, `FeedbackSaveDto` (`@NonNull entityType/entityId`,
  `@NotNull @Min(1) @Max(5) rating`, `@NotBlank @Size(max = TEXT_MAX_LENGTH) feedbackText`,
  named `TEXT_MAX_LENGTH`/`TEXT_RAW_MAX_LENGTH` constants — `AdvertisementSaveDto`'s
  `TITLE_MAX_LENGTH` pattern), `FeedbackAggregateDto` (`entityType`, `entityId`, `avgRating`,
  `reviewCount`). All `@FieldNameConstants`.

**Root `/app/pom.xml`:** add `<module>feedback-spring-boot-starter</module>` (after
`contact-spring-boot-starter`, same position as every other starter in the existing list).

**Verification (per `/autopilot` step 4):** `bash scripts/ci.sh --sonar` first, then
`scripts/sync-docs`, then `DECISIONS.md` (see below), then full `bash scripts/ci.sh`. New unit
tests for `FeedbackService` (sanitization, edit-window rejection, duplicate rejection) and an
`integration-tests` repository test for `FeedbackRepository` (mirrors the existing
`AbstractPostgresIntegrationTest` pattern used for `ContactRepository`).

**ADR:** `/record-decision platform-commons — reuse EntityRef for a new domain + self-contained
per-starter aggregate table over a cross-starter Hook`, referencing this task and the
`contact-spring-boot-starter` precedent it follows.

**Explicitly out of scope for this run:** `feedback_comment` table/tree (Phase 3), moderation
(Phase 4), rate limiting (Phase 5). UI wiring, originally deferred to a separate follow-up, was
completed the same day (2026-09-30) — see the Phases section above.

## Architecture-map verification (2026-09-30)

Checked whether new modules (`feedback-spring-boot-starter`, and `contact-spring-boot-starter` as
a comparison point since it's also recent) are correctly, dynamically picked up by
`docs/architecture/scripts/generate-architecture-model.sh` across every diagram.

**Confirmed fully dynamic, no gap:** Module Dependencies, Bounded Contexts domain grouping
(`<architecture.boundedContext>` pom.xml property), Database ERD (`db/*/changes/*.xml` discovery
off the `<modules>` list), and the per-module Entities/Key Services/Contracts page (`@Table`/
`*Service.java`/`implements *Port` scans) — verified directly against the real generated
`docs/architecture/data/architecture-model.json`, `feedback-spring-boot-starter`'s data is present
and correct in all four.

**Found 2 real gaps, both also affecting `contact-spring-boot-starter` (pre-existing, not
feedback-specific):**
1. ✅ (2026-09-30) `SPI_SUBSYSTEM_ORDER`/`SPI_SUBSYSTEM_LABEL` in `generate-architecture-model.sh`
   (~line 841) was a hardcoded bash array, not derived from real source — `FeedbackPort`/
   `ContactPort` existed in the generated JSON's `spiMap.details`/`nodes` but never rendered on any
   SPI Map tab or in the markdown export, since every render path iterated this fixed list.
   Confirmed precedent: `apikey` had to be added by hand the same way when that module was
   introduced. **Fixed:** subsystem set is now discovered live from
   `platform-commons/src/main/java/org/ost/platform/*/spi/*.java` package names
   (`SPI_SUBSYSTEM_DISCOVERED`); the original 8 entries became `SPI_SUBSYSTEM_PREFERRED_ORDER`
   (still controls display order/gets a curated `SPI_SUBSYSTEM_LABEL` override), any newly
   discovered subsystem is auto-appended with a generated fallback label via
   `spi_subsystem_label_for()` (`Title-Case + " Subsystem"`) when no override exists. Verified:
   regenerated `architecture-model.json` now has `subsystemOrder: [..., "contact", "feedback"]`
   with labels `"Contact Subsystem"`/`"Feedback Subsystem"`, and the HTML page renders both tabs.
2. ✅ (2026-09-30) Root `CLAUDE.md` never had `contact-spring-boot-starter`/
   `feedback-spring-boot-starter` added to: the "Module Layout" ASCII tree (root cause of the
   generated Module page showing an empty description for `feedback-spring-boot-starter` —
   `MODULE_DESCRIPTION` parses exactly this tree, nothing else), the `platform-commons` Package
   Layout bullet list (`contact.*`/`feedback.*` missing next to `core.*`/`audit.*`/etc.), and the
   "Architectural Decisions Log" file list (`contact-spring-boot-starter/DECISIONS.md` exists with
   real content but wasn't listed; also fixed a pre-existing "three modules" miscount in that same
   note paragraph — 5 modules were already named there before this change, now 6 with
   `feedback-spring-boot-starter` added). **Fixed:** hand-added the missing lines (deliberately
   hand-maintained canonical content per "one fact, one canonical home", not generator-derivable).
   Verified: regenerated JSON now shows real one-line descriptions for both modules instead of
   `""`; `feedback-spring-boot-starter/DECISIONS.md` auto-generated as a pointer file in the same
   run (no cross-referencing ADRs yet, as expected).

## CI verification & new-module registration gaps (2026-09-30)

Ran `bash scripts/ci.sh --foreground` (full pipeline) to verify the two Phase 1 UI refinements
(`StarRatingField`, `feedback-panel.css`) plus the architecture-map fixes above. Found and fixed 3
more instances of the same root-cause pattern already seen above: a place that lists sibling
starters/modules by name, where `contact-spring-boot-starter` was added by hand but
`feedback-spring-boot-starter` was missed.

1. ✅ `integration-tests/src/main/java/org/ost/integrationtests/support/Level3ScenarioTest.java` —
   fixed `@SpringBootTest(classes = {...})` missing `FeedbackAutoConfiguration.class` (had
   `ContactAutoConfiguration.class`). Broke `ApplicationContext` bootstrap for all 8 Level-3 REST
   API scenario tests (`NoSuchBeanDefinitionException` for `ComponentFactory<FeedbackPort>`, since
   `OrchestratorAutoConfiguration`'s `FeedbackAccessService` needs it). Verified via
   `integration-tests/run.sh --sandbox AdvertisementAuthorizationScenarioTest` — passed.
2. ✅ `integration-tests/run.sh`'s staleness-check (`STARTER_MODULES`) — was derived from "modules
   with no own `src/test/java`", which wrongly excluded `marketplace-orchestrator`/
   `marketplace-rest-api` (both have their own tests AND are real compile dependencies of this
   module's Level 3 tests) — a source change in either never triggered a host `~/.m2` reinstall.
   Confirmed directly: `marketplace-orchestrator`'s installed jar was 3 weeks stale
   (`TaxonCatalogService` referencing a since-renamed `TaxonFilterDto`), causing
   `ClassNotFoundException` independent of the fix above. Fixed: module list now derived directly
   from `integration-tests/pom.xml`'s own `<dependency>` entries (self-maintaining). Also updated
   `.claude/rules/integration-tests.md`'s matching description. Verified via a second
   `run.sh --sandbox` invocation — auto-reinstalled `marketplace-orchestrator`, test passed.
3. ✅ `scripts/deploy-and-run/reset-clean.sql` (used by `--reset-only-db` before every e2e run) —
   `TRUNCATE TABLE` listed `contact_view`/`contact_info` but not `feedback`/`feedback_aggregate`.
   Caused a real E2E failure in this same CI run: leftover feedback data from earlier manual
   testing made `04-provider-profile-flow.spec.js`'s "adminEn leaves a review" test find a non-empty
   panel where it expected `.feedback-empty`. Fixed: added both tables to the `TRUNCATE` list.

**Full CI run result (`ci-20260930T193848Z-585158-41632295`, 27m52s):** build/lint/shellcheck/
unit/integration/archunit_metrics/docs all ✅. **e2e: 60/64 passed**, 1 failed (root-caused to
finding 3 above, now fixed but not yet re-verified with a fresh full run), 3 didn't run (serial
spec dependency on the failed one). **sonar: failed only on the pre-existing Quality Gate**
(New Coverage 63.7%<80% + the known `HeaderBar.java` S1450 — both already tracked in
`improvement-203`) — no new test failures in the sonar stage itself once findings 1-2 above were
fixed (it had failed on the same `integration` cascade before those fixes).

**Re-verified (2026-09-30, `ci-20260930T200934Z-617685-2219019323`, 28m10s):** confirms finding
3's fix — **e2e: 64/64 passed**, including the previously-failing "adminEn leaves a review"
test.step. build/lint/shellcheck/unit/integration/archunit_metrics/docs all ✅ again. sonar still
❌, same pre-existing Quality Gate only (not a new failure) — expected, not investigated further.

## Implementation Plan — Phase 3 (2026-10-01, comment tree + schema redesign)

Scope: comment tree (reply to a feedback entry or to another comment, unbounded depth, any
registered user), plus a schema redesign of the existing `feedback` table worked out interactively
with the user — splitting the "text+moderation" concern and the "rating" concern each into their
own table, both reused by the new comment tree, plus a reactions table for comments. No comment
moderation UI yet (Phase 4) — the `moderation_status` column exists on the content table but is
not yet filtered/acted on, same as `feedback`'s own column today.

**Decided (2026-10-01, user confirmed):**
1. Comments get the same 48h edit window as top-level feedback entries.
2. Author-name display is added for both `feedback_comment` rows **and** the existing top-level
   feedback entry row (`FeedbackPanel.buildEntryRow()` currently shows no author at all — a
   pre-existing Phase 1/2 gap, fixed in this same pass).
3. Tables get a `feedback_` prefix so module ownership is visible from the name alone.
4. `rating` is split out of `feedback` into its own `feedback_rating` table (1:1).
5. The "text + moderation + edit-tracking" shape is identical for a top-level feedback entry and a
   reply, so it's factored into one shared `feedback_content` table, referenced by FK from both
   `feedback` and the new `feedback_comment`. `author_id` is **not** in `feedback_content` — it's
   duplicated directly onto `feedback` (needed for the existing `(author_id, entity_type,
   entity_id)` unique constraint, which can't span two tables) and onto `feedback_comment` (needed
   for ownership/edit-window checks), since each wrapper already needs to know its own author
   regardless of the shared content table.
6. Comment reactions (👍/👎, extensible to more types later) get their own `feedback_comment_reaction`
   table, counted **live** via `COUNT() ... GROUP BY` at read time — no denormalized aggregate
   table for reactions, since they're always read in the bounded context of one already-paginated
   comment tree. `feedback_aggregate` (entity-level avg rating) is **kept** as a denormalized table,
   unlike reactions, because it's designed to also serve future bulk/list contexts (many entity
   cards on one search-results page) where N live aggregate queries would be worse than reading one
   ready row per entity — and reworking it now would mean undoing already-shipped Phase 1/2 code
   for a use case (list/card display) that doesn't exist yet.
7. Comment deletion (own comment, author-initiated) follows the industry-standard tombstone
   pattern confirmed via web research (Reddit/Disqus-style threaded comment systems — see sources
   below): a leaf comment (no replies) is hard-deleted outright; a comment that has replies is
   soft-deleted — its `feedback_content.content_text` is set to `NULL` (the tombstone signal, no
   extra status column needed) and the UI renders a "[deleted]" placeholder in its place, with its
   row kept so `parent_comment_id` stays valid for its children. Delete is gated by the same 48h
   window as edit — once the window closes, neither the edit nor the delete icon is available
   anymore, same `content.createdAt` check both actions already share. Recursive cleanup of
   a tombstone once its last remaining child is itself removed is **not** implemented in this pass
   (noted as a possible future refinement, not required for v1).
   Sources: [How would you model posts, comments, and threaded chat replies in a relational database?](https://github.com/orgs/community/discussions/167352),
   [Designing a Scalable Database Schema for Reddit-like Comments](https://www.lldcoding.com/designing-a-scalable-database-schema-for-reddit-like-comments-part-1),
   [The Anatomy of Reddit-Style Comments — A Weekend Engineering Dive](https://vikkrraant.medium.com/the-anatomy-of-reddit-style-comments-a-weekend-engineering-dive-6b52cd80139d).
8. UI pattern for both the feedback entry and the comment tree is icon-driven inline editing, not a
   persistent separate form: the standalone "add feedback" form only renders when the current user
   has **no** existing entry yet for this entity; once they have one, the form disappears and a
   small edit icon on their own entry row toggles that row into an inline-editable state (rating +
   text editable in place, small save icon) instead of jumping to/populating a separate form. The
   comment tree uses the same idea: small reply/edit/delete icons positioned directly on each node;
   clicking reply or edit drops an inline text field at that exact position with a small save
   button, rather than a separately-styled always-present form.
9. Hovering a reaction icon shows who reacted (resolved display names), via a Vaadin `Tooltip`.
   Reactions are already registered-users-only by construction (the reaction buttons are only
   shown/enabled when `access.isLoggedIn()`, and `FeedbackCommentReactionSaveDto.userId` is
   `@NonNull` — no anonymous reaction row can exist).

**Final schema (6 tables total in this starter) — edited in place into the existing
`01-feedback-schema.xml` changeset** (no new changeset file — pre-production, and that changeset
already carries `<validCheckSum>ANY</validCheckSum>` for exactly this situation):

1. **`feedback_content`** (new) — shared text atom for both a feedback entry's own text and a
   comment's text: `id BIGSERIAL PK`, `content_text VARCHAR(20000)` (sanitized HTML),
   `moderation_status VARCHAR(20) NOT NULL DEFAULT 'NEW'`, `created_at TIMESTAMP WITH TIME ZONE NOT
   NULL DEFAULT NOW()`, `updated_at TIMESTAMP WITH TIME ZONE`, `version BIGINT NOT NULL DEFAULT 0`.
2. **`feedback`** (restructured) — drops `rating`/`feedback_text`/`moderation_status`/`created_at`/
   `updated_at`/`version` (all moved to `feedback_content`/`feedback_rating`); keeps `id`,
   `author_id`, `entity_type`, `entity_id`; adds `content_id BIGINT NOT NULL` (FK →
   `feedback_content`). Keeps its existing unique index `(author_id, entity_type, entity_id)` and
   plain index `(entity_type, entity_id)`. Becomes an immutable link row after creation — nothing
   on it ever changes once written (edits touch `feedback_content`/`feedback_rating` instead), so
   no `@Version`/timestamps of its own.
3. **`feedback_rating`** (new) — `id BIGSERIAL PK`, `feedback_id BIGINT NOT NULL` (FK → `feedback`,
   unique — 1:1), `rating SMALLINT NOT NULL`, `created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT
   NOW()`, `updated_at TIMESTAMP WITH TIME ZONE`.
4. **`feedback_comment`** (new) — `id BIGSERIAL PK`, `content_id BIGINT NOT NULL` (FK →
   `feedback_content`), `author_id BIGINT NOT NULL` (no FK, same convention as `feedback.author_id`),
   `feedback_id BIGINT NOT NULL` (real FK → `feedback` — which thread this reply belongs to),
   `parent_comment_id BIGINT` nullable (self-FK → `feedback_comment`, NULL = direct reply to the
   feedback entry itself). Index `(feedback_id)`; index `(parent_comment_id)`.
5. **`feedback_comment_reaction`** (new) — `id BIGSERIAL PK`, `feedback_comment_id BIGINT NOT NULL`
   (FK → `feedback_comment`), `user_id BIGINT NOT NULL` (no FK), `reaction_type VARCHAR(20) NOT
   NULL` (`UP`/`DOWN` today, extensible to more values later with no schema change), `created_at
   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`. Unique index `(feedback_comment_id, user_id)`
   — one active reaction per user per comment; changing or removing a reaction updates/deletes that
   same row rather than inserting a new one.
6. **`feedback_aggregate`** — unchanged structurally; its upsert source query changes to read
   `rating` via a join against `feedback_rating` instead of a column on `feedback` directly.

**`feedback-spring-boot-starter` entity/repository changes:**
- `org.ost.feedback.entity.FeedbackContent` — `@Value @Builder @FieldNameConstants
  @Table("feedback_content")`, `@Id`/`@CreatedDate`/`@LastModifiedDate`/`@Version`.
- `org.ost.feedback.entity.Feedback` — slimmed to `id`, `contentId`, `authorId`, `entityType`,
  `entityId` only; drops `@CreatedDate`/`@LastModifiedDate`/`@Version` (no longer mutated after
  creation).
- `org.ost.feedback.entity.FeedbackRating` — `id`, `feedbackId`, `rating`, `createdAt`,
  `updatedAt`; upserted directly via `JdbcClient` (`INSERT ... ON CONFLICT (feedback_id) DO
  UPDATE`), same pattern `upsertAggregate` already uses — no `CrudRepository`/`@Version` needed
  since it's always driven by the owning `feedback` row's own save, never independently contended.
- `org.ost.feedback.entity.FeedbackComment` — `id`, `contentId`, `authorId`, `feedbackId`,
  `parentCommentId`.
- `org.ost.feedback.entity.FeedbackCommentReaction` — `id`, `feedbackCommentId`, `userId`,
  `reactionType`, `createdAt`.
- `FeedbackContentCrudRepository`, `FeedbackCommentCrudRepository` — trivial `CrudRepository`s.
  (`feedback`/`feedback_rating`/`feedback_comment_reaction` keep using bespoke `JdbcClient` SQL in
  `FeedbackRepository`, no CRUD repository needed for them given the upsert/link-row shapes above.)
- `FeedbackRepository` (same class, extended) — package-private read records `FeedbackView`
  (feedback+content+rating join — same fields as `FeedbackDto`) and `FeedbackCommentView`
  (comment+content join only — deliberately **without** reaction data, which the service layer
  folds in separately from `findReactionsByComments`, so this view is narrower than the external
  `FeedbackCommentDto`), both internal to this repository, replacing the old direct-entity
  `ROW_MAPPER`s for reads:
  - `findByAuthorAndEntity(authorId, entityType, entityId) -> Optional<FeedbackView>` — joins
    `feedback f JOIN feedback_content c ON c.id = f.content_id JOIN feedback_rating r ON
    r.feedback_id = f.id`.
  - `findByEntity(entityType, entityId, pageable) -> List<FeedbackView>` — same join, `ORDER BY
    c.created_at DESC` + pagination (unchanged behavior from the reader's perspective).
  - `findViewById(Long id) -> Optional<FeedbackView>` — same join as above, by `feedback.id`; used
    by `FeedbackService.save()`'s edit path to load the existing row for the 48h-window check.
  - `saveContent(FeedbackContent) -> FeedbackContent` / `saveLink(Feedback) -> Feedback` — thin
    `CrudRepository` delegations (content used on both create and edit; link only on create).
  - `upsertRating(feedbackId, rating)` — `JdbcClient` upsert as described above.
  - `upsertAggregate(entityType, entityId)` — same shape as today, source query now joins
    `feedback_rating`.
  - `findCommentsByFeedback(feedbackId) -> List<FeedbackCommentView>` — `WITH RECURSIVE` over
    `feedback_comment` (building a `path BIGINT[]` column for `ORDER BY path`, giving a correct
    depth-first sibling-ordered traversal with no separate "depth" bookkeeping), joined with
    `feedback_content` for text/moderation/timestamps. Reactions are deliberately **not** joined
    into this query (see next bullet) — keeps the recursive CTE itself simple.
  - `findReactionsByComments(List<Long> commentIds) -> List<FeedbackCommentReactionView>`
    (`feedbackCommentId`, `userId`, `reactionType`) — one plain `WHERE feedback_comment_id IN
    (:ids)` query, grouped in Java (`FeedbackService`) into `Map<Long, Map<String, List<Long>>>`
    (comment id → reaction type → reactor ids) rather than attempting to express that grouping in
    SQL — counts and "who reacted" both fall out of this same map (`.size()` / the list itself),
    and the viewer's own `myReaction` is computed by checking which list (if any) contains
    `viewerId`.
  - `findCommentViewById(Long id) -> Optional<FeedbackCommentView>` — comment+content join by
    `feedback_comment.id`; used by `FeedbackService.saveComment()`'s edit path and `deleteComment()`
    to load the existing row (48h-window check, and `contentId` for `tombstoneComment`).
  - `saveCommentLink(FeedbackComment) -> FeedbackComment` — create-only (identity of a reply never
    changes on edit).
  - `findReactionType(feedbackCommentId, userId) -> Optional<String>` — the one existing reaction
    row for that pair, if any; used by `FeedbackService.saveReaction()` to decide toggle-off vs.
    upsert.
  - `upsertReaction(feedbackCommentId, userId, reactionType)` / `deleteReaction(feedbackCommentId,
    userId)` — `JdbcClient` upsert/delete against `feedback_comment_reaction`.
  - `countChildren(commentId) -> int` — `SELECT COUNT(*) FROM feedback_comment WHERE
    parent_comment_id = :commentId`, decides hard-delete vs. tombstone.
  - `deleteCommentHard(commentId)` — deletes the `feedback_comment` row, its own `feedback_content`
    row, and any `feedback_comment_reaction` rows referencing it (no FK `ON DELETE CASCADE`
    configured, so all three deletes run explicitly in the same `@Transactional` service method).
  - `tombstoneComment(contentId)` — `UPDATE feedback_content SET content_text = NULL WHERE id =
    :contentId` — the comment row and its reactions stay untouched.
- `FeedbackService`:
  - `save(FeedbackSaveDto)` — create: sanitize text, `saveContent` (new row), `saveLink` (new row
    with the fresh `content_id`), `upsertRating`, `upsertAggregate`. Edit (`dto.id()` present):
    load the existing `FeedbackView` by id, enforce the 48h window off `content.createdAt`,
    sanitize text, `saveContent` (update, same `content_id`, optimistic-lock via its own
    `@Version`), `upsertRating`, `upsertAggregate`. `DuplicateKeyException` on `feedback`'s own
    unique index still propagates naturally on create (unchanged behavior).
  - `findCommentsByFeedback(feedbackId, viewerId)` — maps `FeedbackCommentView` → `FeedbackCommentDto`,
    folding in the `Map<Long, Map<String, List<Long>>>` from `findReactionsByComments` (one extra
    query per tree load, not per comment) plus the derived `myReaction` for `viewerId`.
  - `saveComment(FeedbackCommentSaveDto)` — create: sanitize, `saveContent` (new), `saveCommentLink`
    (new). Edit (`dto.id()` present): load existing view by id, enforce the 48h window off
    `content.createdAt`, sanitize, `saveContent` (update) — `feedbackId`/`parentCommentId`/
    `authorId` never change on edit.
  - `saveReaction(FeedbackCommentReactionSaveDto)` — toggle semantics: looks up the user's existing
    reaction on that comment; same type → `deleteReaction` (un-vote); different/absent → `upsertReaction`.
  - `deleteComment(commentId)` — loads the existing comment view, enforces the same 48h window off
    `content.createdAt` as edit, then `countChildren(commentId)` decides `deleteCommentHard` (zero
    children) vs. `tombstoneComment` (one or more children).
- `FeedbackPortImpl` — pure delegation for all of the above, unchanged pattern.

**`platform-commons` additions:**
- `FeedbackDto`/`FeedbackSaveDto` — **unchanged shape** (still `rating`+`feedbackText` as one flat
  read model) — the content/rating split is an internal persistence detail of this starter, not
  something the rest of the platform needs to see.
- `org.ost.platform.feedback.dto.FeedbackCommentDto` — `id`, `feedbackId`, `parentCommentId`
  (nullable), `authorId`, `commentText`, `moderationStatus`, `createdAt`, `updatedAt`, `version`,
  `Map<String, List<Long>> reactorIdsByType` (keyed by reaction-type code, open-ended for future
  reaction types — the reactor id list carries both "who" and "how many" via its own size, so no
  separate counts field), `String myReaction` (nullable — the viewer's own current reaction, for
  highlighting the active button; always `null` for an anonymous/unspecified viewer).
- `org.ost.platform.feedback.dto.FeedbackCommentSaveDto` — `id` (nullable — edit support),
  `@NonNull feedbackId`, `parentCommentId` (nullable), `@NonNull authorId`, `@NotBlank @Size(max =
  TEXT_RAW_MAX_LENGTH) commentText`, `version`. Own `TEXT_MAX_LENGTH`/`TEXT_RAW_MAX_LENGTH`
  constants (same 2000/20000 values as `FeedbackSaveDto`, named separately per convention).
- `org.ost.platform.feedback.dto.FeedbackCommentReactionSaveDto` — `@NonNull feedbackCommentId`,
  `@NonNull userId`, `@NotNull reactionType`.
- `org.ost.platform.feedback.model.FeedbackReactionType` — enum, `UP`/`DOWN` today.
- `FeedbackPort` additions: `List<FeedbackCommentDto> findCommentsByFeedback(@NonNull Long
  feedbackId, Long viewerId)` (`viewerId` intentionally not `@NonNull` — anonymous visitors may
  read the tree), `FeedbackCommentDto saveComment(@NonNull FeedbackCommentSaveDto dto)`, `void
  saveReaction(@NonNull FeedbackCommentReactionSaveDto dto)`, `void deleteComment(@NonNull Long
  commentId)`.

**`marketplace-orchestrator` additions:**
- `FeedbackAccessService`: pass-through `findCommentsByFeedback(feedbackId, viewerId)` /
  `saveComment(dto)` / `saveReaction(dto)` / `deleteComment(commentId)`, plus
  `resolveAuthorNames(Set<Long> authorIds)` →
  delegates to the existing `UserActorNameService.resolveNames(...)` (already handles the
  deleted-account strikethrough via `UiLabelHook`) — a plain collaborator field, doesn't count
  against the ≤2-domain-port rule since it's a `services.*` collaborator, not a raw
  `ComponentFactory<XPort>`.

**`marketplace-app` additions — icon-driven inline editing, no persistent side form:**
- `FeedbackPanel.configure()`: after fetching the entity's entries, check whether one of them
  belongs to `access.getCurrentUserId()`. Render the standalone `buildForm()` ("add feedback")
  **only** when the user is logged in and has no existing entry yet. When they already have one,
  the form is omitted entirely — their own row (see next bullet) is the only way to change it.
- `FeedbackPanel.buildEntryRow()`: resolve author names for the whole page of entries in one batch
  (in `refreshList`, via `feedbackAccessService.resolveAuthorNames`) and display the name/deleted
  label on each row. Replace the old "Edit button scrolls to the separate form" behavior: the
  owning user's row (within the 48h window) gets a small `UiIconButton` (`VaadinIcon.EDIT`,
  `.feedback-entry-edit-icon`) that toggles the row into an inline-editable state in place — the
  static stars/text replaced by the same `StarRatingField`/`UiTextArea` pair pre-filled with the
  current values, plus a small save icon button (`VaadinIcon.CHECK`) right there; clicking it
  calls `feedbackAccessService.save(...)` then `refresh(entityRef)`.
- New `org.ost.marketplace.ui.views.components.CommentTreePanel` (`Configurable<CommentTreePanel,
  Parameters>`, `Parameters{@NonNull Long feedbackId}`, prototype scope, same shape as
  `FeedbackPanel`): on `configure()`, loads the flat comment list (passing the current viewer id,
  or `null` when logged out), then resolves display names in **one** combined
  `feedbackAccessService.resolveAuthorNames(...)` batch call over the union of every comment's
  `authorId` **and** every id appearing in any comment's `reactorIdsByType` (so the reactor-name
  tooltips need no extra round trip), groups into `Map<Long,
  List<FeedbackCommentDto>>` keyed by `parentCommentId` (null key = top-level), then recursively
  renders starting from the null-keyed list. Each node: author name (or `UiLabelHook`-marked
  deleted label, or the `"[deleted]"` placeholder from `FEEDBACK_COMMENT_DELETED_TEXT` when
  `commentText == null`, with no icons/reactions shown on such a node), sanitized text
  (`innerHTML`), a per-node indent (`getStyle().set("margin-left", depth*20+"px")`, depth tracked
  as a recursion parameter, never persisted), and a small row of `UiIconButton`s instead of any
  persistent form:
  - `VaadinIcon.REPLY` (`.comment-reply-icon`, visible when `access.isLoggedIn()`) — toggles an
    inline `UiTextArea` + small save icon directly below that node; submit calls
    `feedbackAccessService.saveComment(...)` with `parentCommentId` = this node's id.
  - `VaadinIcon.EDIT` (`.comment-edit-icon`, visible only for the node's own author within the 48h
    window) — toggles the node's own text display into the same inline `UiTextArea` + save-icon
    pattern, pre-filled with the current `commentText`; submit calls `saveComment(...)` with this
    node's `id`.
  - `VaadinIcon.TRASH` (`.comment-delete-icon`, same visibility rule as edit) — calls
    `feedbackAccessService.deleteComment(id)` directly (no confirmation dialog in this pass) then
    reloads the panel.
  - 👍/👎 reaction icon buttons showing each `reactorIdsByType` entry's count (`.size()`), the
    active one highlighted via `myReaction` (click calls `feedbackAccessService.saveReaction(...)`,
    only enabled when logged in and the node isn't a tombstone). Each button carries a Vaadin
    `Tooltip.forComponent(button).setText(...)` listing the resolved display names of that type's
    reactors (comma-joined), built from the same batched author-name resolution below — no
    tooltip when the count is zero.
  - Only when the node has children: a "Show N replies"/"Hide replies" toggle flipping
    `setVisible()` on that node's children container.
  Any save/reaction/delete action reloads the whole panel via `configure()` again (same
  full-refresh pattern `FeedbackPanel` already uses) — no incremental DOM patching.
- `FeedbackPanel.buildEntryRow()` also appends `commentTreePanelFactory.build(new
  CommentTreePanel.Parameters(entry.id()))` after the (now inline-editable) entry row.
- `marketplace-app/src/main/frontend/themes/my-app/feedback-panel.css`: extend with
  `.comment-tree-panel`/`.comment-node`/`.comment-author`/`.comment-text`/`.comment-reply-icon`/
  `.comment-edit-icon`/`.comment-delete-icon`/`.comment-inline-form`/`.comment-toggle`/
  `.comment-reaction-button`/`.comment-reaction-count`/`.comment-deleted-text` rules (same file,
  not a new stylesheet — same feature area); `.feedback-entry-author`/`.feedback-entry-edit-icon`
  added alongside the existing `.feedback-entry-*` classes.
- New `I18nKey` entries (`FEEDBACK_COMMENT_FIELD_TEXT`, `FEEDBACK_COMMENT_SHOW_REPLIES`,
  `FEEDBACK_COMMENT_HIDE_REPLIES`, `FEEDBACK_COMMENT_DELETED_TEXT`) + matching `feedback.comment.*`
  keys in both message bundles, directly after the existing `feedback.*` block. Icon buttons
  (reply/edit/delete/reactions) carry no separate text key — Vaadin icon + existing
  `UiIconButton`/`Button` tooltip conventions are enough, same as the existing `.user-history-button`
  icon-button precedent.

**Other required touches (same root-cause pattern already seen twice in Phase 1's own
CI-verification section — a new table missed in a sibling list):**
- `scripts/deploy-and-run/reset-clean.sql` — add `feedback_content`, `feedback_rating`,
  `feedback_comment`, `feedback_comment_reaction` to the existing `TRUNCATE TABLE` list alongside
  `feedback`/`feedback_aggregate`.

**Tests:**
- `integration-tests`: extend `FeedbackRepositoryTest`/`FeedbackServiceTest` (not new files) —
  create/edit round-trip across the `feedback`/`feedback_content`/`feedback_rating` split, edit-
  window enforcement on both feedback and comments, `findCommentsByFeedback` tree ordering across
  3+ nesting levels with reaction counts attached, reaction toggle (vote/change/un-vote), delete a
  leaf comment (hard-deleted, row gone) vs. delete a comment with replies (tombstoned —
  `commentText == null`, row and children still present), delete rejected past the 48h window.
- Playwright: extend the existing `04-provider-profile-flow.spec.js` feedback test.step — edit an
  existing feedback entry inline (no separate form visible once an entry exists), reply to a
  feedback entry, reply to that reply (nested), edit a comment within its window, react 👍/👎 on a
  comment and toggle it off, delete a leaf comment and a comment with replies (verify the
  "[deleted]" placeholder on the latter), verify tree order and expand/collapse.

**Verification:** `bash scripts/ci.sh` (full pipeline).

**ADR:** `/record-decision feedback-spring-boot-starter — split feedback into feedback_content/
feedback/feedback_rating, feedback_comment reuses feedback_content, reactions counted live while
the entity-level rating aggregate stays denormalized, comment deletion follows the tombstone-if-
has-replies/hard-delete-if-leaf pattern gated by the same 48h edit window`.

## Phase 3 follow-up round 6 — real-usage UX feedback after round 5 (2026-10-03)

Further hands-on use of round 5 surfaced 6 more items. Not implemented yet — full scope below,
pending approval.

1. **Typed, unsaved reply/edit text gets wiped by an unrelated reload() elsewhere in the tree.**
   `expandedCommentIds` (round 5) only persists collapse/expand state across a `reload()` rebuild —
   an *open composer* (`replyFormSlot`/`editFormSlot` with a user mid-typing) has no equivalent
   memory at all. If the user opens a reply form, starts typing, and *any* other action anywhere in
   the same tree triggers a `reload()` (a reaction click on a different comment, another edit save,
   etc.), the whole tree rebuilds from scratch and the in-progress, unsaved composer — along with
   whatever text was typed into it — disappears entirely, same root shape as round 5's issue but
   for form content instead of expand state.
2. **Opening a reply/edit composer itself causes a visible jump.** Unlike save/delete/reaction,
   clicking the reply or edit icon is a *pure client-side* action — `replyFormSlot.add(...)`/
   `editFormSlot.add(...)` directly, no `reload()` call at all — so it is not wrapped by any of the
   existing `reload()`-scoped scrollTop save/restore. Inserting new content shifts the page's total
   height with nothing compensating for it.
3. **`comment-reply-icon` (comments) still looks clipped; `feedback-entry-reply-icon` (feedback) is
   fine with the same `VaadinIcon.COMMENT_O` glyph.** Root cause: `.comment-reply-icon` is one of
   the classes forced into a `1.6rem × 1.6rem` box by the shared sizing rule (`.comment-reply-icon,
   .comment-edit-icon, .comment-delete-icon, .comment-toggle-icon, .comment-discard-icon`) —
   `COMMENT_O`'s own proportions don't fit that box cleanly and get cut off, while
   `.feedback-entry-reply-icon` has no such forced-box rule at all, so it renders at its natural
   size without clipping. `VaadinIcon.EDIT`/`TRASH`/`CHEVRON_*` share that exact same `1.6rem` box
   with zero clipping complaints ever raised against them — the box itself isn't the problem,
   `COMMENT_O`'s specific proportions inside it are.
4. **Confirming a delete also causes a scroll jump**, despite `reload()` already being wrapped.
   **Root cause investigated and found in code (2026-10-03), not guessed:**
   `ConfirmActionDialog`'s confirm button —
   ```java
   confirmButton.addClickListener(_ -> {
       try {
           onConfirm.run();   // runs feedbackAccessService.deleteComment(...) + reload()
       } finally {
           close();           // only runs AFTER onConfirm.run() fully completes
       }
   });
   ```
   — runs the entire `onConfirm.run()` body (the delete call *and* `reload()`'s whole
   save-scroll/rebuild/restore-scroll cycle) **while the dialog is still open**, and only calls
   `close()` afterward, in `finally`. `BaseDialog extends com.vaadin.flow.component.dialog.Dialog`
   directly (confirmed by reading `BaseDialog.java`) — `close()` is Vaadin Flow's own unmodified
   `Dialog.close()`, not custom code, and removes the modal `vaadin-dialog-overlay` from the
   render tree as its own separate DOM change, known to happen after this project's `onConfirm`
   callback, not before or during it.

   **What this means:** `reload()`'s scrollTop restore is **not the last thing to touch the DOM** —
   the dialog's own close-triggered overlay removal happens strictly afterward, as a second,
   separate layout change nothing currently compensates for. A likely fix (not implemented, per
   explicit instruction to investigate only) would reorder `ConfirmActionDialog` to call `close()`
   *before* invoking `onConfirm`, so the dialog is already gone from the DOM by the time
   `reload()`'s own save/rebuild/restore cycle runs — making the scroll-restore genuinely the last
   DOM-affecting step, matching how the reply/edit-composer case (item 2) already works cleanly
   with no dialog involved at all.

   **Confirmed directly:** the execution-order fact above (read from the real source of both
   `ConfirmActionDialog.java` and `BaseDialog.java`).
   **Not independently confirmed:** whether Vaadin's own modal-overlay removal actually produces a
   *measurable* scroll/layout shift on close (this is standard, well-documented behavior for modal
   overlay web components in general, but this specific claim about Vaadin's own implementation
   was not verified via a live browser trace before writing this — the execution-order bug is the
   solid, code-level finding; the overlay-removal-causes-shift mechanism is the explanation that
   fits it, not independently re-verified).
5. **Confirmed by the user, 2026-10-03.** The reply/"Add feedback"/"add a top-level comment"
   trigger is removed from the header entirely and instead lives *inside* the exact slot where its
   form will render (`formContainer` for "Add feedback", `replyFormSlot` for a per-comment reply,
   and — since `feedback-entry-reply-icon` calling `commentTreePanel.toggleTopLevelComposer()`
   across components no longer makes sense once the trigger moves next to `topLevelReplySlot`
   itself — the "add a top-level comment" trigger also moves entirely into `CommentTreePanel`,
   removing `feedback-entry-reply-icon`/`toggleTopLevelComposer()` from `FeedbackPanel` altogether).
   Each such slot toggles between exactly two states: a trigger button, or the open form. The form
   gains a third action button alongside Save/Discard — **Close** — which discards any draft and
   switches the slot back to showing the trigger button (Discard alone no longer closes anything,
   per round 5 — only Close does). Edit's own icon/behavior is untouched, stays in the header
   exactly as today.
6. **Same scroll-jump as item 4, specifically on delete** — tracked as the same open question as
   item 4, not a separate root cause until investigation says otherwise.

**Fix plan:**

- Item 1 (draft persistence): new instance fields on `CommentTreePanel` —
  `Map<Long, String> draftReplyText` (key: parent comment id being replied to, `0L` for the
  top-level composer) and `Map<Long, String> draftEditText` (key: the comment id being edited),
  plus `Set<Long> openReplyComposerFor`/`Set<Long> openEditComposerFor` recording which composers
  are currently open. Each `UiTextArea` in `buildReplyComposer()`/`buildEditComposer()` gets a
  `.addValueChangeListener` writing into the matching draft map on every keystroke (not just on
  save), so the *latest* typed value is always available to a mid-typing rebuild. `buildNode()`
  checks the open-sets when constructing `replyFormSlot`/`editFormSlot` and, if open, immediately
  populates the composer with its draft text instead of leaving the slot empty. Save and discard
  both clear the corresponding draft entry and remove the id from the open-set (a saved/discarded
  draft has nothing left to restore).
- Item 2 (composer-open jump): wrap the reply/edit icon's own click listener with the same
  scrollTop save/restore `reload()` already uses, scoped to `.overlay__content` — extract that
  save/restore pair into a small reusable private helper (e.g. `preserveScroll(Runnable action)`)
  used by both `reload()` and the composer-open toggles, instead of duplicating the two
  `executeJs` calls a third time inline.
- Item 3 (icon clipping): change `.comment-reply-icon`'s icon from `VaadinIcon.COMMENT_O` to
  `VaadinIcon.REPLY` (the icon this project used before round 3's icon change — proven to render
  without clipping in this exact `1.6rem` box, zero historical complaints against it specifically)
  — keep the already-correct `FEEDBACK_COMMENT_BUTTON_REPLY` i18n tooltip, only the glyph changes.
  `.feedback-entry-reply-icon` stays on `COMMENT_O` (user-confirmed fine there).
- Items 4 and 6 (delete scroll jump): investigate first — reproduce with a real Playwright trace
  capturing DOM/scroll state immediately around a delete-confirm action, before proposing a fix;
  explicitly not folded into item 2's generic `preserveScroll()` wrap until the actual mechanism is
  confirmed (wrapping blind risks masking the real cause the same way a retry-loop would).
- Item 5 (icon relocation): **blocked on user confirmation of the interpretation above** before any
  code changes — `FeedbackPanel.buildAddControls()`/`buildEntryRow()`'s `headerActions` placement
  and `CommentTreePanel.buildActionsRow()`'s reply-button placement would both need rework if
  confirmed.

**Status: ✅ Implemented and browser-verified (2026-10-03).** Items 1, 2, 3, 5 landed
(`CommentTreePanel.java`, `FeedbackPanel.java`, `feedback-panel.css`, `04-provider-profile-flow.spec.js`),
`mvn` compile green, real deploy + full `e2e --ux` run **51 passed, 0 failed, 13 skipped**.

Two real bugs were found during verification (not in the original Haiku pass) and fixed in a
follow-up pass before the run above:
- `FeedbackPanel.refresh()` referenced a removed `addButton` field (compile error);
  `updateAddControlsVisibility()` called `buildAddTrigger(null)` (latent NPE); `formContainer` never
  cleared once the viewer already had an own entry. Fixed: dropped the stray line, threaded the real
  `EntityRef` through, added an explicit branch to clear the container when
  `!loggedIn || hasOwnEntry`.
- `CommentTreePanel.buildActionsRow()` still had the old header-level reply-icon block left in
  place alongside the new slot-based trigger (item 5's own spec said to remove it) — both opened the
  same composer. Fixed: removed the duplicate block; also added the missing `comment-reply-icon`
  CSS class to the new slot-based trigger buttons (`buildReplyTrigger`/`buildTopLevelReplyTrigger`),
  which had been constructed without it.

**Items 4/6 (delete-confirm / edit-save scroll jump) — investigated and fixed, separately from the
items above.** Root cause confirmed via live Playwright trace inspection (`trace.zip`, not
guessed): `preserveScroll()`'s restore JS fired correctly (confirmed scrollTop restored to the
test's own set value immediately after the triggering click), but the browser later clamped it back
down during the subsequent `expect().toContainText()` wait, because `.overlay__content`'s available
scroll range (`scrollHeight - clientHeight`) genuinely shrank afterward (148px → 36px) — some part of
Vaadin's client-side settle for this heavier round-6 tree (more content per node) completes later
than the point our restore JS ran. Two hypotheses were tested and **disproven** by direct evidence
before the real fix:
- Not a `--trace=off` artifact — reran specs 01-04 with tracing fully disabled, same failure,
  same 112px diff.
- Not "restore runs too early" in the simple sense — wrapping the restore in a double
  `requestAnimationFrame` had zero measurable effect (identical 112px diff, identical trace
  timeline).
- `BaseOverlay.java`'s own scroll save/restore (used for the overlay's open/close) does not apply
  here — it locks the *background page's* `window.scrollY` while the overlay is open and the
  background never re-renders; it never faces a scrollable container whose own content shrinks
  while visible, so there was no existing pattern to reuse directly.

**Fix:** `preserveScroll()` rewritten to a "sticky" restore — reapplies the saved `scrollTop` for a
500ms window after the action via both a `MutationObserver` (reacts to any further DOM mutation on
`.overlay__content`) and a `requestAnimationFrame` loop, instead of a single one-shot restore.
Confirmed working live: the previously-failing edit-save scroll assertion now passes in the same
full `e2e --ux` run reported above.

## Phase 3 follow-up round 7 — real-usage UX feedback after round 6 (2026-10-04)

Further hands-on use surfaced 2 more items. Not implemented yet — pending approval.

1. **A newly-added reply to a nested comment is saved but stays hidden.** `expandedCommentIds`
   (round 5) only persists a toggle's state once the user has actually clicked it — a comment that
   never had any children before (so never had a toggle button at all, since `buildActionsRow()`
   only renders the toggle `if (childrenCount > 0)`) gets its first child added, `reload()` rebuilds
   with `childrenCount` now 1, a toggle now exists, but its initial state still reads
   `expandedCommentIds.contains(comment.id())` = false (never added) — so the brand-new reply the
   user just wrote is immediately hidden behind a collapsed toggle, requiring an extra, unexpected
   click to see what was just saved. Wanted: a comment the user just replied to (or edited into
   having its first child) opens automatically — no separate click needed right after adding.
2. **Feedback-level comments have no collapse at all, inconsistent with every nested level.** Every
   individual comment node already gets a show/hide toggle for its own children once it has at
   least one reply (`buildActionsRow()`'s toggle block) — but the *top-level* list of direct replies
   to a feedback entry (`CommentTreePanel.configure()`'s `byParent.getOrDefault(0L, ...)` loop) has
   no equivalent toggle at all; it always renders, unconditionally. Wanted: the same show/hide
   mechanism at this level too, for consistency.

**Fix plan:**

- Item 1: in `CommentTreePanel.buildReplyComposer(Long parentCommentId)`'s save-button click
  listener, right before calling `reload()`, add the (possibly-new) parent to the expanded set so
  the branch the user just added to opens automatically on rebuild:
  - nested reply (`parentCommentId != null`): `expandedCommentIds.add(parentCommentId);`
  - top-level reply (`parentCommentId == null`): once item 2's own top-level container/toggle
    exists, add its own sentinel key (`0L`, matching the existing `draftReplyText`/
    `openReplyComposerFor` top-level convention) to the same set the new top-level toggle reads from.
  `buildEditComposer()`'s save handler is unaffected — editing never changes `childrenCount` or a
  node's own collapse state, only this node's own text.
- Item 2: wrap `configure()`'s top-level comment loop in a new `Div topLevelCommentsContainer`
  (`comment-children`-equivalent styling, no left margin since top-level comments already start at
  depth 1), add a new small header row above it inside `CommentTreePanel` itself (not cross-wired
  into `FeedbackPanel`, avoiding the kind of cross-component trigger coupling round 6 already had to
  remove) — a `UiIconButton` (chevron, same `comment-toggle-icon` sizing/class) + a count badge
  (`comment-toggle-count`, total top-level comment count), reusing the existing
  `FEEDBACK_COMMENT_SHOW_REPLIES`/`FEEDBACK_COMMENT_HIDE_REPLIES` i18n keys (no new key needed —
  "show/hide replies" reads fine for a feedback entry's own direct replies too). State persisted the
  same way as every other level: `expandedCommentIds.contains(0L)` decides the initial
  visible/collapsed state and the toggle's own click listener adds/removes `0L` the same way a
  per-node toggle does (default collapsed on first-ever load, same convention as every other level
  since round 4). Needs real browser verification once implemented — a brand-new container/toggle
  at the panel root, not yet proven live.

**Status: ✅ Implemented and browser-verified (2026-10-04).** Both items landed in
`CommentTreePanel.java`, `mvn` compile green, real deploy + full `e2e --ux` run **51 passed,
0 failed, 13 skipped**. One real test-selector break was found and fixed during verification, not
product code: the new top-level `topLevelCommentsContainer` (item 2) put `.comment-node` one level
deeper than before, breaking three direct-child (`'> .comment-node'`) locators in
`04-provider-profile-flow.spec.js` — changed to descendant locators (`'.comment-node'`). A second,
expected test update: the existing "children hidden by default right after adding a reply"
assertion now correctly expects `toBeVisible()` instead (item 1's own auto-expand behavior, exactly
as specified), with the now-redundant manual expand-click removed from that step.

**Follow-up (2026-10-04, user-requested relocation):** item 2's toggle was placed as its own
standalone row above the top-level comment list, inside `CommentTreePanel` itself. User wants it
moved next to the feedback entry's own Edit icon instead, matching how every comment-level toggle
already sits in that node's own header controls group, not as a separate row.

**Fix plan (not yet implemented, pending approval):**

- `CommentTreePanel.java`:
  - `topLevelCommentsContainer` becomes an instance field (currently a local var in `configure()`)
    so a public method can reach it later; add `private int topLevelCommentCount;`, set from
    `topLevelComments.size()` in `configure()`.
  - Remove the standalone `topLevelToggleRow` button-building block from `configure()` entirely —
    keep only building `topLevelCommentsContainer` itself and its initial visibility.
  - New `public void toggleTopLevelComments()` — same show/hide + `expandedCommentIds`
    add/remove(0L) + `forceExpandDescendants` logic the removed button's click listener had.
  - New `public boolean isTopLevelCommentsExpanded()` → `expandedCommentIds.contains(0L)`.
  - New `public int getTopLevelCommentCount()` → `topLevelCommentCount`.
- `FeedbackPanel.java` `buildEntryRow(...)`: after the existing `editable` block, append a new
  `UiIconButton` to `headerActions` (only when `commentTreePanel.getTopLevelCommentCount() > 0`) —
  same `comment-toggle-icon` class, chevron icon, `FEEDBACK_COMMENT_SHOW_REPLIES`/
  `FEEDBACK_COMMENT_HIDE_REPLIES` labels as the removed button had; its click listener calls
  `commentTreePanel.toggleTopLevelComments()` then updates its own icon/title/aria-label from
  `commentTreePanel.isTopLevelCommentsExpanded()`. `commentTreePanel` is already available in this
  method's own parameter list and already fully configured by this point (built via the factory
  earlier in `refreshList()`), so its state is current.
- `feedback-panel.css`: remove the now-dead `.comment-top-level-toggle-row` rule.
- Playwright: the comment-tree test step's existing assertions around the top-level toggle (if any
  were added) move from a `CommentTreePanel`-internal selector to `.feedback-entry-header-actions`'s
  own button — check the current test for any such selector and update to match.

**Status: ✅ Implemented and browser-verified (2026-10-04).** Toggle moved from a standalone row
inside `CommentTreePanel` to `FeedbackPanel.buildEntryRow()`'s `.feedback-entry-header-actions`,
next to Edit — new public `toggleTopLevelComments()`/`isTopLevelCommentsExpanded()`/
`getTopLevelCommentCount()` on `CommentTreePanel` back it. `mvn` compile green, real deploy + full
`e2e --ux` run **51 passed, 0 failed, 13 skipped**, no test changes needed (no existing selector
targeted the old standalone row).

**Real bug found by the user, 2026-10-04 (not caught by the Playwright run above — that suite never
adds a FIRST comment to a previously-commentless entry and checks the toggle appears live):** the
toggle only appears after a full page refresh, not immediately after adding the first comment.
Root cause: `CommentTreePanel.reload()` only rebuilds itself; `FeedbackPanel`'s header row (holding
the toggle button) was built once in `buildEntryRow()` reading `commentTreePanel
.getTopLevelCommentCount()` at that one point in time — it has no way to learn the count changed
on a later `CommentTreePanel`-only reload.

**Fix plan (not yet implemented, pending approval):**
- `CommentTreePanel.java`: new `private Runnable onChanged;` field +
  `public void setOnChanged(Runnable onChanged)` setter. At the end of `configure(Parameters p)`
  (right before `return this;`), call `if (onChanged != null) onChanged.run();`.
- `FeedbackPanel.java` `buildEntryRow(...)`: extract the existing
  `if (commentTreePanel.getTopLevelCommentCount() > 0) { ... headerActions.add(toggleButton); }`
  block into a new private method `refreshCommentToggle(Div headerActions, CommentTreePanel
  commentTreePanel)` — first removes any existing `.comment-toggle-icon` child from
  `headerActions`, then re-adds a freshly-built one if the count is still > 0 (so it's safe to call
  repeatedly, not just once). `buildEntryRow(...)` calls it once during initial build (replacing
  the old inline block) and wires `commentTreePanel.setOnChanged(() -> refreshCommentToggle
  (headerActions, commentTreePanel))` right after, so every future `CommentTreePanel`-only reload
  re-syncs just the toggle button, not the whole row.

**Status: ✅ Implemented and browser-verified (2026-10-04).** This fix landed together with two
other user-reported items in the same Haiku pass:
- **Reply composer no longer "jumps."** Opening a reply composer (top-level or nested) now expands
  the surrounding comment list immediately (`expandedCommentIds`/`topLevelCommentsContainer`
  updated on open, not just on save) — the full context is visible from the start, so saving no
  longer reveals a previously-hidden list out from under the composer.
- **Discard vs. Close split correctly per composer type.** `CommentTreePanel.buildReplyComposer()`
  now has Save+Close only (no Discard — nothing to discard back to for a brand-new reply);
  `buildEditComposer()` now has Save+Discard only (no Close — the Edit trigger icon's own
  toggle-close already handles exiting edit mode).

`mvn` compile green, real deploy + full `e2e --ux` run **51 passed, 0 failed, 13 skipped**, no
regressions.

## Implementation Plan — Phase 4 (2026-10-04, moderation)

Scope: flag/report on a feedback entry or a comment (any logged-in non-owner), a new admin-only
"Moderation" tab listing hidden items with Approve/Reject, audit entries for moderation actions
only. Researched against current (2026) content-moderation UX practice before designing (sources:
getstream.io moderation-dashboard docs, several GitHub moderation-queue issue threads, Higher
Logic's moderation-queue support docs) — oldest-first queue, item shows content+author+age, hide
is reversible (Approve) while delete is a separate, confirmed, irreversible action, each
transition gets an audited actor+timestamp. No new table needed — `feedback_content.moderation_status`
(`NEW`/`HIDDEN`, already shipped in Phase 3) already covers both feedback and comment rows, since
both already go through this shared table.

**`platform-commons` additions:**
- `org.ost.platform.core.model.EntityType`: add `FEEDBACK`, `FEEDBACK_COMMENT` (needed so a
  moderation action can be audited via the existing `EntityRef`-keyed `AuditPort`, same as every
  other domain's own create/update/delete already is).
- `org.ost.platform.feedback.dto.FeedbackSnapshotDto` (new) — `implements AuditableSnapshot`,
  mirrors `AdvertisementSnapshotDto`'s shape: a `moderationStatus` field, `diff(AuditableSnapshot
  previous)` comparing just that one field (`NEW`↔`HIDDEN`). `FeedbackCommentSnapshotDto` (new) —
  same shape for comments.
- `FeedbackPort` additions: `void flagFeedback(@NonNull Long feedbackId)`, `void
  flagComment(@NonNull Long commentId)`, `void approveFeedback(@NonNull Long feedbackId)`, `void
  approveComment(@NonNull Long commentId)`, `void rejectFeedback(@NonNull Long feedbackId)` (hard
  delete, admin-only — see below), `void rejectComment(@NonNull Long commentId)` (hard delete,
  reuses the existing `deleteCommentHard`/`pruneDanglingTombstones` machinery), `List<FeedbackDto>
  findHiddenFeedback(Pageable)`, `List<FeedbackCommentDto> findHiddenComments(Pageable)`.

**`feedback-spring-boot-starter` additions:**
- `FeedbackRepository`: `updateModerationStatus(contentId, FeedbackModerationStatus)` (one shared
  `UPDATE feedback_content SET moderation_status = :status WHERE id = :contentId`, reused by both
  flag and approve on either a feedback or a comment's own `content_id`); `findHiddenFeedback
  (pageable)` / `findHiddenComments(pageable)` (same join shape as the existing `findByEntity`/
  `findCommentsByFeedback` queries, `WHERE c.moderation_status = 'HIDDEN'`, oldest-first per the
  researched convention — `ORDER BY c.created_at ASC`, the opposite of every other read path in
  this starter which is newest-first); `deleteFeedbackHard(feedbackId, contentId)` (new — no
  existing method deletes a whole feedback entry today; cascades explicitly in one
  `@Transactional` service method: every `feedback_comment` row for this `feedback_id` and each
  one's own `feedback_content`/`feedback_comment_reaction` rows first — reusing the comment
  starter's own existing per-comment hard-delete SQL in a loop — then `feedback_rating`,
  `feedback_content`, `feedback` itself, then `upsertAggregate` to recompute the now-one-fewer
  count).
- `FeedbackService`: `flagFeedback`/`flagComment` → `updateModerationStatus(..., HIDDEN)`;
  `approveFeedback`/`approveComment` → `updateModerationStatus(..., NEW)`; `rejectFeedback` →
  `deleteFeedbackHard` + `upsertAggregate`; `rejectComment` → existing `deleteComment`'s hard-delete
  branch, callable directly regardless of whether the comment currently has children (admin
  override — a moderator rejecting an abusive comment shouldn't be blocked by the same
  "tombstone if it has replies" rule a regular author's own self-delete follows); `findHiddenFeedback`/
  `findHiddenComments` → thin pass-throughs mapping to the existing `FeedbackDto`/`FeedbackCommentDto`
  shapes (no new external DTO needed for the read side).
- `FeedbackPortImpl`: pure delegation for all of the above, unchanged pattern.

**`marketplace-orchestrator` additions (`FeedbackAccessService`):**
- Pass-through `flagFeedback`/`flagComment`/`approveFeedback`/`approveComment`.
- `rejectFeedback(Long feedbackId)` / `rejectComment(Long commentId)`: load the snapshot
  (`FeedbackSnapshotDto`/`FeedbackCommentSnapshotDto`) before deleting, call
  `feedbackPortFactory.ifAvailable(p -> p.rejectFeedback(feedbackId))`, then
  `auditPortFactory.ifAvailable(p -> p.captureDeletion(feedbackId, snapshot, actorId))` — same
  `ComponentFactory<AuditPort>`-via-`.ifAvailable(...)` pattern `AdvertisementSaveService` already
  uses, `EntityType.FEEDBACK`/`FEEDBACK_COMMENT` plus the new snapshot DTOs make this a direct
  mirror, no new audit-side code needed. Same `captureUpdate(...)` shape for approve/flag (before/
  after snapshot = `moderationStatus` field only).
- Still within the ≤2-domain-port-per-class rule (`FeedbackPort` + `AuditPort`, same as
  `AdvertisementSaveService`'s own `AdvertisementPort`+`AuditPort` pair).

**`marketplace-app` additions:**
- `FeedbackPanel.buildEntryRow()` / `CommentTreePanel.buildActionsRow()`: new `UiIconButton`
  (`VaadinIcon.FLAG`, new i18n key `FEEDBACK_BUTTON_REPORT`/`feedback.button.report` =
  "Report"/"Поскаржитись"), visible when `access.isLoggedIn() && !ownEntry` (mirrors the existing
  `editable`/`ownComment` boolean gate already computed in both methods, just inverted +
  login-gated instead of ownership-gated), wrapped in the existing `ConfirmActionDialog` pattern
  (new i18n keys `FEEDBACK_CONFIRM_REPORT_TITLE`/`_TEXT`/`_BUTTON`/`_CANCEL_BUTTON`, mirroring the
  existing `FEEDBACK_COMMENT_CONFIRM_DELETE_*` naming), `onConfirm` calls
  `feedbackAccessService.flagFeedback(...)`/`flagComment(...)` then `refresh(entityRef)`/`reload()`
  — the flagged item then simply disappears from this viewer's own list on the next refresh (no
  client-side "hidden, pending review" placeholder needed — an already-hidden item was never
  queried back from `findForEntity`/`findCommentsByFeedback`, both of which should gain a `WHERE
  moderation_status = 'NEW'` filter as part of this phase, since today they return every row
  regardless of status — this is the actual visibility-enforcement half of "flag → hidden",
  currently entirely missing).
- New `org.ost.marketplace.ui.views.main.tabs.moderation` package (mirrors `tabs.providers`/
  `tabs.users`): `ModerationView` — two `Grid`s (feedback entries, comments; mirrors
  `UserView`'s/`AdvertisementsView`'s own Grid-building structure), each row: author name
  (resolved same as everywhere else via `UserActorNameService`), text snippet, created date, a
  context link/button opening the owning entity's overlay (reuse the existing deep-link
  navigation), Approve icon button, Reject icon button (wrapped in `ConfirmActionDialog` — this one
  is irreversible). No filter/sort bar needed for v1 (queues are expected to stay small — "start
  permissive, tighten with volume" per the spec) — a plain oldest-first list is enough, matching
  the researched convention directly.
- `MainView.java`: new conditionally-added tab (same `if (access.isModeratorOrAdmin()) { ... }`
  guard shape already wrapping `usersTab`/`timelineTab`), new i18n key `MAIN_TAB_MODERATION`.

**Test plan:**
- `integration-tests` (`FeedbackServiceTest`): flag hides an entry from `findForEntity`/
  `findCommentsByFeedback` but it still exists in `findHiddenFeedback`/`findHiddenComments`;
  approve reverses it; reject hard-deletes (feedback case: cascades through its whole comment
  tree, verify zero rows remain in all four tables for that `feedback_id`); a non-owner can flag,
  the owner's own flag attempt on their own entry is rejected (service-level check, not just a
  UI-level hidden button — defense in depth, same reasoning the edit-window check already gets).
- Playwright (extend the existing `04-provider-profile-flow.spec.js` comment-tree test.step, not a
  new spec file per this project's own "prefer updating existing tests" convention): userUk
  reports adminEn's feedback entry and a comment → both disappear from userUk's own view →
  adminEn (logged in separately, not the moderator) still can't see them either → moderatorEn (or
  adminEn-as-admin) opens the new Moderation tab → sees both queued, oldest-first → Approve the
  feedback entry → reappears in the public view → Reject the comment → confirm dialog → gone
  permanently, confirmed via the existing tree not containing it anymore.

**Out of scope for this phase (per the spec's own Phase 5):** rate limiting, orphan cleanup on
entity deletion (already exists as a separate standing cleanup-service pattern, unrelated to
moderation specifically).

**Status (backend layer): ✅ Implemented and test-verified (2026-10-04).** `platform-commons`,
`feedback-spring-boot-starter`, `marketplace-orchestrator` all landed — `mvn` compile green, full
`--unit --integration` run **292 passed, 0 failed**, including 8 new moderation tests. UI layer
(`marketplace-app`) not yet started.

Real bugs found and fixed during implementation/verification, not in the original plan:
- Two exhaustive-`switch` compile breaks elsewhere in the codebase caused by adding
  `EntityType.FEEDBACK`/`FEEDBACK_COMMENT` (`EntityExistenceService` had no case for them — added a
  no-op case, since neither is ever existence-checked through that service; `I18nKey.forEntityType`
  had no case either — added `ENTITY_TYPE_FEEDBACK`/`ENTITY_TYPE_FEEDBACK_COMMENT` i18n keys +
  translations).
- `findByEntity`/`findCommentsByFeedback` never actually filtered by `moderation_status` — flagging
  something hid nothing. Added `WHERE c.moderation_status = 'NEW'` to both (the comment-tree query's
  outer `SELECT` only, not the recursive CTE itself).
- `flagFeedback`/`flagComment` had no owner-check at all — any author could've had their own content
  stay fully visible regardless, since nothing stopped a self-flag from being silently accepted (or
  conversely, nothing *required* one — the gap was that nothing prevented an author from flagging
  their own entry, which isn't a real moderation signal). Added an `actorId` parameter + an
  `IllegalStateException` guard matching the existing edit-window check's error style.
- `rejectComment`/`rejectFeedback`'s cascade delete tried to delete a parent `feedback_comment` row
  before its own children, hitting the real `fk_feedback_comment_parent` FK (no cascade configured)
  the instant a rejected comment/feedback had any replies — confirmed via an actual
  `DataIntegrityViolationException` from a real Postgres in the first test run, not a guess. Fixed
  both with a children-first (post-order) recursive delete.
- The `moderation_status = 'NEW'` filter above (added earlier in this same pass) then broke
  `deleteFeedbackHard`'s OWN internal use of `findCommentsByFeedback` — a hidden comment inside a
  rejected feedback's tree would be silently skipped by the now-filtered query, leaving its row
  behind to violate the feedback-level FK a moment later. Added a separate, unfiltered
  `findAllCommentsByFeedbackIncludingHidden` for this cascade-only use, with a dedicated regression
  test (`rejectFeedback_cascadesEvenWhenTreeContainsHiddenComment`).

**Status (UI layer): ✅ Implemented and browser-verified (2026-10-04).** `marketplace-app` additions
landed: report button (`.feedback-report-icon`/`.comment-report-icon`, login+not-own-content gated)
on both `FeedbackPanel` and `CommentTreePanel`; new `ModerationView` (two grids, oldest-first,
Approve/Reject) and its conditional "Moderation" tab in `MainView`, gated on
`FeedbackAccessService.isAvailable()` and `access.canView()`. New Playwright test
(`04-provider-profile-flow.spec.js`) covering the full flow: report hides from the public view,
Moderation tab lists both, approve restores, reject permanently deletes. `mvn` compile green, real
deploy + full `e2e --ux` run **52 passed, 0 failed, 13 skipped**.

Real bugs found and fixed during this verification pass, not in the original plan:
- `ModerationView`'s own grids never refreshed after the view's initial construction — a
  `@UIScope` singleton's `@PostConstruct init()` runs once at session start, long before the user
  ever visits the tab; `MainView`'s tab-switch listener only ever called this for `ProvidersView`.
  Added the same `refreshOnTabSelect()` wrapper `ProvidersView` already has, wired into
  `MainView`'s listener.
- A confirmed, documented class of Vaadin Grid bug
  ([flow#16116](https://github.com/vaadin/flow/issues/16116)): `refresh()` unconditionally rebuilt
  *both* grids on every single action, so approving a feedback entry also regenerated every
  `addComponentColumn` button in the unrelated comment grid — detaching a button the test (and a
  real user double-clicking quickly) was about to click. Split into independent
  `refreshFeedbackGrid()`/`refreshCommentGrid()`, each action now only touches its own grid.

## Phase 4 follow-up — real-usage UX feedback (2026-10-05)

Hands-on use of the moderation feature surfaced 2 design problems plus 1 unrelated regression.
Not implemented yet — pending approval.

1. **Reject (hard-delete) shouldn't exist in the moderation UI.** Approve/restore is enough —
   there's no actual need for an admin-initiated permanent delete in v1.
2. **A flagged item should show as a "[censored]"-style placeholder in place, not disappear from
   the list entirely.** Today, flagging a feedback entry removes the whole entry — and since
   `CommentTreePanel` only renders alongside its own still-listed feedback entry, that also makes
   its entire comment thread vanish from the public view, not just the flagged entry itself. The
   same applies to flagging a single comment — it disappears instead of showing a placeholder, the
   same way an author's own tombstoned (`[deleted]`) comment already does today. Both
   `FeedbackDto`/`FeedbackCommentDto` already carry `moderationStatus` — no new DTO field needed,
   just different rendering + un-filtering the public read queries.
3. **Regression: editing a feedback entry re-collapses every comment tree on the page**, reverting
   the already-shipped "expand/draft state survives unrelated reloads" behavior. Root cause: a
   previously-disclosed, accepted limitation from an earlier round
   ("`FeedbackPanel.refreshList()` calls `commentTreePanelFactory.build(...)` fresh for every
   feedback entry whenever the feedback list itself refreshes... discarding all expand-state for
   all of them, not just the one entry being edited") — flagged then as out of scope, now reported
   as actually wanted.

**Fix plan (not yet implemented, pending approval):**

- `FeedbackRepository.java`: remove `AND c.moderation_status = 'NEW'` from `findByEntity`'s WHERE
  clause and remove `WHERE c.moderation_status = 'NEW'` from `findCommentsByFeedback`'s outer
  `SELECT` (added in the previous round specifically to hide flagged items — reversed here now
  that hiding is replaced by in-place censoring). `findHiddenFeedback`/`findHiddenComments` (the
  admin queue's own queries) are unaffected — those stay filtered to `HIDDEN` only, unrelated to
  this change.
- New i18n keys (mirroring `FEEDBACK_COMMENT_DELETED_TEXT`'s existing "[deleted]" pattern):
  `FEEDBACK_CENSORED_TEXT`/`feedback.censored` = "[censored]"/"[цензуровано]",
  `FEEDBACK_COMMENT_CENSORED_TEXT`/`feedback.comment.censored` = "[censored]"/"[цензуровано]".
- `FeedbackPanel.buildEntryRow()`: when `entry.moderationStatus() ==
  FeedbackModerationStatus.HIDDEN`, render the censored placeholder instead of the star rating +
  `feedbackText` (same minimal substitution `CommentTreePanel.renderCommentText()` already does
  for a tombstoned comment); hide the Report button for an already-censored entry (nothing useful
  to re-report).
- `CommentTreePanel.renderCommentText()`/`buildNode()`: add a second, independent check alongside
  the existing `tombstoned` one — `censored = comment.moderationStatus() ==
  FeedbackModerationStatus.HIDDEN` (a censored comment still has real `commentText`, unlike a
  tombstoned one) — render the censored placeholder when true; hide that comment's own Report
  button too.
- `ModerationView.java`: remove the Reject button and `confirmRejectFeedback`/
  `confirmRejectComment` methods entirely from both grids — Approve-only. Leave
  `FeedbackAccessService.rejectFeedback`/`rejectComment` and the rest of the backend reject
  capability as-is (already built and tested, just no longer wired to this UI).
- `FeedbackPanel.java`: new `Map<Long, CommentTreePanel> commentTreePanelsByFeedbackId` field.
  `refreshList()` reuses an existing cached instance (calling `.configure(...)` again on it, which
  preserves its own `expandedCommentIds`/draft-text instance state) instead of always building a
  brand-new one via the factory — only builds+caches a new instance the first time a given feedback
  id is encountered. Prune cache entries whose feedback id is no longer present in the current
  `entries` list (keeps the map from growing unbounded across many edit/create cycles over a long
  session).
- Playwright: update the existing moderation test.step — a flagged item now stays visible with
  "[censored]" text instead of disappearing (`toContainText`/`toHaveText` on the censored string
  instead of `toHaveCount(0)`); remove the Reject-button interaction entirely (Approve-only now);
  add a step confirming a feedback-entry edit doesn't collapse an already-expanded comment thread
  elsewhere in the same panel.

**Status: ✅ Implemented and browser-verified (2026-10-05).** All 3 items landed
(`FeedbackRepository.java`, `FeedbackPanel.java`, `CommentTreePanel.java`, `ModerationView.java`,
2 new i18n keys), `mvn` compile green, `FeedbackServiceTest` 24/24 (292/292 across the full
unit+integration suite), real deploy + full `e2e --ux` run **52 passed, 0 failed, 13 skipped**.
Three real bugs found and fixed only during this verification pass:
- Two integration tests asserted the now-superseded hide-on-flag behavior
  (`flagFeedback_hidesFromPublicListing_visibleInHiddenQueue`/
  `flagComment_hidesFromTree_visibleInHiddenQueue`, both asserting `isEmpty()` after flagging) —
  renamed/rewritten to `flagFeedback_staysListedWithHiddenStatus_alsoVisibleInHiddenQueue`/
  `flagComment_staysInTreeWithHiddenStatus_alsoVisibleInHiddenQueue`, asserting the item stays
  listed with `HIDDEN` status and is also visible in the admin queue.
- Playwright's `reportableCommentNode`/`reportableEntry` locators were built via
  `.filter({ hasText: 'Reportable comment/feedback text.' })`, which stopped matching the instant
  that exact text was replaced by "[censored]" — the two post-censoring assertions were rewritten
  to query through the stable `commentTree`/`feedbackPanel` locators directly instead of the
  now-stale filtered sub-locator.
- The test's own `page.reload()` (used to defeat an earlier, separately-fixed grid-staleness issue)
  resets `CommentTreePanel`'s in-memory `expandedCommentIds` to empty on the fresh session; per
  Vaadin's documented behavior, an invisible component's descendant DOM never reaches the client —
  so the final post-approval comment-text assertion failed with "element(s) not found" until a
  `.feedback-entry-header-actions .comment-toggle-icon` click was added right before it to
  re-expand the tree.

## Phase 4 follow-up round 2 — real-usage UX feedback after round 1 (2026-10-05)

Hands-on use of the censored-in-place UI surfaced 2 more layout problems. Not implemented yet —
pending approval.

1. **Reply button renders twice per comment.** `CommentTreePanel.buildActionsRow()` (lines
   272-288) adds a `replyButton` into the header's `.comment-actions-controls` group (next to
   thumbs-up/down, Edit, Delete) — but `buildNode()` (lines 204-211) already builds a dedicated
   `.comment-reply-form-slot` directly under the comment, containing its own reply trigger
   (`buildReplyTrigger(comment.id())`) where the actual reply composer opens. Both control the
   identical `openReplyComposerFor`/`replyFormSlot` state — functionally redundant, visually a
   duplicate "reply" arrow icon (confirmed via the `moderation-feedback-and-comment-created`
   screenshot from the round-1 Playwright run: two reply arrows stacked, one inline with the
   thumbs-up/down/edit/trash row, one alone directly below it). Wanted: keep only the bottom
   slot's own trigger — the one next to where the reply text is actually typed; remove the
   duplicate from the header row.
2. **The action row visibly narrows when a comment is deleted or censored.** Confirmed via two
   existing screenshots:
   - `provider-catalog-comment-tree-tombstoned` — a tombstoned (`[deleted]`, has children) comment's
     `.comment-actions` row has an empty `reactions` div and an empty `controls` div (every one of
     reply/report/edit/delete is gated by `!tombstoned`), yet `.comment-actions-divider` still
     renders unconditionally between them — a stray 1px line floating next to nothing.
   - `moderation-comment-reported-and-hidden` — once a comment is flagged, `canReport` becomes
     `false` (`comment.moderationStatus() == FeedbackModerationStatus.NEW` no longer holds), so the
     Report icon disappears from `controls` entirely, narrowing the row by one icon the instant the
     report succeeds.
   User chose the **"disable, don't hide"** option: the Report icon stays visible (not removed)
   once a comment/feedback entry is censored, just disabled — so reporting doesn't visibly change
   the row's width/contents. Combined with always suppressing the leftover divider when a side is
   genuinely empty (the tombstoned/no-children case), the row's visual weight stays stable across
   both triggers reported.

**Fix plan (not yet implemented, pending approval):**

- `CommentTreePanel.buildActionsRow()`: remove the `replyButton` block entirely (the
  `if (!tombstoned && access.isLoggedIn()) { ... controls.add(replyButton); }` lines) — the bottom
  `replyFormSlot` trigger (unchanged) becomes the only reply entry point per comment.
- `CommentTreePanel.buildActionsRow()`: split `canReport` into two checks — `canSeeReport`
  (`!tombstoned && access.isLoggedIn() && !comment.authorId().equals(access.getCurrentUserId())`,
  without the moderation-status condition) gates whether the Report button is added to `controls`
  at all; `canReport` (`canSeeReport && comment.moderationStatus() ==
  FeedbackModerationStatus.NEW`) now only controls `reportButton.setEnabled(canReport)` — the
  button renders whenever `canSeeReport`, just disabled once already `HIDDEN`.
- `FeedbackPanel.buildEntryRow()`: same split for the feedback-entry-level Report button —
  `canSeeReport`/`canReport` mirroring the comment-level change, button disabled (not removed)
  once `entry.moderationStatus() == FeedbackModerationStatus.HIDDEN`.
- `CommentTreePanel.buildActionsRow()`: before `actions.add(reactions, divider, controls)`, only
  add `divider` when both `reactions.getComponentCount() > 0` and `controls.getComponentCount() >
  0` — suppresses the stray floating line for a tombstoned comment with no children's-worth of
  controls.
- Playwright: update the moderation test.step — after reporting, assert the Report icon is present
  but disabled (not absent) on both the comment and the feedback entry; the duplicate-reply-icon
  removal needs no new assertion (no test currently asserts a *count* of reply icons).

**Status: ✅ Implemented and browser-verified (2026-10-05).** Both items landed in
`CommentTreePanel.java`/`FeedbackPanel.java` (duplicate header Reply button removed;
`canSeeReport`/`canReport` split so the Report icon stays visible-but-disabled instead of
disappearing; the stray `.comment-actions-divider` is now only added when both `reactions` and
`controls` have content), `mvn` compile green, real deploy + full `e2e --ux` run **52 passed, 0
failed, 13 skipped**. Two new Playwright assertions added to the existing moderation test.step
(Report icon visible+disabled on both the comment and the feedback entry after reporting) passed
on the real browser run. Visually confirmed via the `moderation-comment-reported-and-hidden`
screenshot: a single reply arrow remains (no duplicate), and the flag/report icon renders greyed
out rather than vanishing.

## Comment nesting depth cap — new requirement (2026-10-05)

User requested a hard cap on reply nesting: a feedback entry's comment tree may go **3 levels
deep, no more** (confirmed semantics: level 1 = a direct comment on the feedback entry itself,
level 2 = a reply to a level-1 comment, level 3 = a reply to a level-2 comment — a level-3 comment
gets no Reply affordance at all, since that would create a level-4 comment). Today the tree is
explicitly unbounded (`private/features/F-06-reviews-ratings.md`'s own "any registered user may
reply to a feedback entry or to another reply — no per-entry response cap" / "unbounded depth").
Not implemented yet — pending approval.

**Fix plan (UI hide + server-side enforcement — the server-side check is also explicitly wanted now
for future reuse by `marketplace-rest-api`, which bypasses the Vaadin UI entirely):**

- `CommentTreePanel.java`: new constant `private static final int MAX_DEPTH = 3;`. In
  `buildNode(comment, byParent, depth)`, the existing `replyFormSlot` construction (currently
  always adds either `buildReplyTrigger(comment.id())` or an open `buildReplyComposer(comment.id())`)
  only adds that content when `depth < MAX_DEPTH`; at `depth == MAX_DEPTH`, `replyFormSlot` stays
  empty (no trigger, no composer) — this comment cannot be replied to. The top-level "write a
  comment" trigger (`topLevelReplySlot`, replying directly to the feedback entry — always creates a
  level-1 comment) is unaffected, no change needed there.
- `FeedbackService.java` (`feedback-spring-boot-starter`) — `saveComment()`: when
  `dto.id() == null && dto.parentCommentId() != null` (a brand-new reply, not a top-level comment
  and not an edit), compute the parent's depth by walking the `parentCommentId` chain via the
  existing `repository.findCommentViewById(...)` (small loop, at most `MAX_DEPTH` round-trips — no
  new SQL/repository method needed) and reject (`IllegalStateException`, same style as the existing
  "Cannot flag your own feedback entry" check) if the parent's own depth already equals
  `MAX_DEPTH`. Editing an existing comment (`dto.id() != null`) reuses `existing.parentCommentId()`
  unchanged, so it never needs this check. No new UI-facing error message/i18n key for the Vaadin
  side — the Reply trigger is already hidden there, so this path is unreachable through normal UI
  use; the REST API surface (future, `marketplace-rest-api`) will be the first real caller able to
  hit this exception directly.
- Test coverage: `FeedbackServiceTest` — new case building a 3-level chain (feedback → comment A →
  reply B → reply C) and asserting `saveComment` with `parentCommentId = C.id()` throws
  `IllegalStateException`, plus a case confirming a reply to B (still depth 2) succeeds normally.
  Playwright (`04-provider-profile-flow.spec.js`): extend the existing nested-reply flow — assert
  a level-3 comment's `.comment-reply-form-slot` has no trigger/composer content.

## Feedback-entry list pagination — new requirement (2026-10-05)

`FeedbackPanel.refreshList()` always calls `feedbackAccessService.findForEntity(entityType,
entityId, 0, PAGE_SIZE)` — hardcoded page 0, `PAGE_SIZE = 20`, no way to reach entry 21+. The
Port/Service/repository chain already supports real pagination end-to-end
(`findForEntity(EntityType, Long, int page, int size)`); only the UI side is unpaginated. Wanted:
a pagination control under the feedback-entries list (not the comment trees — those stay
full-tree, no change there), reusing the project's existing `PaginationBar` component (already
used by `AdvertisementsView`/`UserView`/`ProvidersView`/`TimelineView`) rather than building a new
pager.

**Fix plan:**

- `FeedbackPanel.java`: new constructor-injected field `private final PaginationBar
  paginationBar;` (prototype-scoped bean, same direct-injection pattern the four existing Views
  already use — no `SettingsPaginationBinding` registration, since that ties page size to a
  per-user grid setting in `UserSettingsDto` that has no feedback-list equivalent and isn't being
  added here; `PaginationBar`'s own default page size is used as-is).
- `configure(Parameters p)`: call `paginationBar.resetToFirstPage()` right after the `!available`
  early-return check, before `refresh(entityRef)` — so reopening this panel for a different entity
  never starts on a stale page left over from a previously-viewed entity. Add `paginationBar` to
  the DOM between `listContainer` and `formContainer`: `add(headerContainer); add(listContainer);
  add(paginationBar); buildFormContainer(entityRef); add(formContainer);`. Also wire
  `paginationBar.setPageChangeListener(_ -> refreshList(entityRef));` here (captures `entityRef` in
  the closure, same as the rest of `configure()`'s listener wiring already does).
- `refreshHeader(EntityRef entityRef)`: after the existing `FeedbackAggregateDto aggregate = ...`
  fetch, add `paginationBar.setTotalCount(aggregate.reviewCount());` — `reviewCount` is already the
  exact total `findForEntity` paginates over, no new count query needed.
- `refreshList(EntityRef entityRef)`: change the hardcoded `0` to `paginationBar.getCurrentPage()`
  in the `findForEntity(...)` call.
- Always-visible bar, no show/hide toggle — matches `AdvertisementsView`'s own convention
  (`PaginationBar.setTotalCount`'s existing button-disable logic already makes prev/next inert on a
  single page, no separate visibility rule needed).
- Playwright (`04-provider-profile-flow.spec.js`): extend an existing feedback-list flow (or add a
  step) that creates more than `PAGE_SIZE` feedback entries on one entity and asserts page
  navigation — if this is expensive to seed per-test, scope it against spec 06's existing bulk-seed
  infrastructure rather than creating 21+ entries one at a time inside this flow.

## Related

- `private/features/F-06-reviews-ratings.md` — full feature spec (goal, user story, scope, tech
  notes, test plan, phases, KPI, risks).
- `private/roadmap.md` — Phase 3 sequencing (F-06 + F-07 after F-05/F-11a).
- `improvement-199` — F-05/F-11a, sequenced immediately before this per roadmap order.
- improvement-124 (completed) — F-04 provider profile, one of the two dependencies this feature
  builds on (advertisement domain is the other, both already shipped).
