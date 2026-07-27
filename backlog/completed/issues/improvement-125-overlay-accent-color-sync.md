# improvement-125: Sync overlay view-card accent border with AdKind / Role

**Type:** bug — visual inconsistency
**Module:** marketplace-app
**Priority:** highest — user-requested top priority (2026-07-27)
**When:** independent, no blockers
**Status:** Done 2026-07-27 — Phase 1 (view-card border), Phase 2 (gallery card color + "Listing type" → "Advertisement kind" rename, EN and UK), and Phase 3 (view-mode header strip color, edit mode deliberately left untouched) all shipped. Verified with full Playwright `e2e --full --ux` — 50/50 passed, including the new computed-color assertions (not just class-name presence) for both AdKind and Role.

## Problem

`.overlay__view-card` (`advertisement-overlay.css:108`, used by `AdvertisementViewOverlayModeHandler`)
has a hardcoded `border-top: 3px solid var(--app-status-success-text) !important;` regardless of
the entity being viewed.

This is already inconsistent with the advertisement **card** view: `advertisement-card.css`
(lines 286-297) gives each `advertisement-card--{offer,request,product}` a left-accent border that
matches its `advertisement-ad-kind-badge--{offer,request,product}` color exactly. But when a user
opens the same advertisement in the view overlay, the top border stays green no matter which
`AdKind` it is — the list and detail views visually disagree.

Same gap on the user side, in a separate but identical class: `UserViewOverlayModeHandler` renders
its own `.user-view-card` (`user-overlay.css:3`, **not** the advertisement's `.overlay__view-card`
— a distinct class with the same hardcoded values, confirmed by direct read), which also carries a
fixed `border-top: 3px solid var(--app-status-success-text) !important;` regardless of role — even
though `user-role-badge`/`user-role-{admin,user,moderator}` (`user-grid.css:41-64`) already gives
each role a distinct badge color.

## Suggested fix

1. `AdvertisementViewOverlayModeHandler.buildPrimaryContent()` — add
   `textCard.addClassName("overlay__view-card--" + ad.getAdKind().name().toLowerCase())` next to
   where `textCard` (`overlay__view-card`) is built.
2. `UserViewOverlayModeHandler.buildPrimaryContent()` — add
   `card.addClassName("user-view-card--" + user.role().name().toLowerCase())` next to where
   `card` (`user-view-card`) is built.
3. `advertisement-overlay.css` — add modifier rules for the advertisement overlay, reusing the
   exact same color variables already used by the card border:
   ```css
   .overlay__view-card--offer    { border-top-color: var(--app-status-success-badge-text) !important; }
   .overlay__view-card--request  { border-top-color: var(--app-status-info-text) !important; }
   .overlay__view-card--product  { border-top-color: var(--app-status-entity-advertisement-text) !important; }
   ```
4. `user-overlay.css` — add modifier rules for the user overlay, reusing the exact same color
   variables already used by the role badge:
   ```css
   .user-view-card--admin     { border-top-color: var(--app-accent-primary-strong) !important; }
   .user-view-card--user      { border-top-color: var(--app-status-success-role-text) !important; }
   .user-view-card--moderator { border-top-color: var(--app-status-moderator-text) !important; }
   ```
5. The base `.overlay__view-card { border-top: 3px solid var(--app-status-success-text) !important; }`
   rule (used by other entities' view overlays, e.g. Taxon) and the base `.user-view-card` rule's
   own border-top stay untouched as fallbacks — no behavior change there.
6. **Explicitly out of scope for this pass:** a left-accent border on the Users **grid** rows
   (per-row `classNameGenerator`) — this would be a new UI pattern (grids here have no per-row
   conditional styling today) and was deliberately deferred, not part of this issue.

## Testing

No new spec files — extend existing Playwright coverage only:
- `04-marketplace-advertisement-flow.spec.js`, `adminEn edits UK advertisement` (already asserts
  "listing type set with activity diff and view badge") — add an assertion on the view overlay's
  `.overlay__view-card` accent color/modifier class matching the ad's `AdKind`.
- `03-marketplace-promotion-flow.spec.js`, the three `adminEn promotes ... to MODERATOR/ADMIN`
  tests (already assert "role badge in view and grid") — add an assertion on the `.user-view-card`
  accent color/modifier class matching the promoted role.

## Related

- `advertisement-card.css` (lines 286-297) — the already-correct pattern this issue extends to the overlay.
- `user-grid.css` (lines 41-64) — role badge colors this issue reuses for the overlay border.
- `.claude/rules.md` "Reference Implementations" — no new UI pattern introduced, pure CSS/class-name extension of the existing card-accent convention.

## Phase 2 (found 2026-07-27, same review round) — attachment gallery card + "Listing type" label

**Problem A — gallery card has its own unrelated color:** `.attachment-gallery`
(`attachment-gallery.css:3-27`) renders as a second card below the text card in the advertisement
view overlay, with its own hardcoded `border-top-color: var(--app-accent-gallery)` and a
purple/violet title gradient (`--app-accent-gallery-bg-soft`/`--app-accent-violet-bg`) — completely
independent of `AdKind`, so it still visually disagrees with the now-synced text-card border and
badge whenever the ad has media. `UserViewOverlayModeHandler` has no gallery at all (users have no
attachments) — confirmed by direct read, so there is nothing analogous to sync on the user side for
this specific element; Phase 1's `.user-view-card--{role}` border already covers the only colored
card that exists there.

**Fix A:**
1. `AdvertisementViewOverlayModeHandler.buildPrimaryContent()` — the gallery `Component` returned
   by `galleryServiceFactory.get().buildGalleryForView(...)` gets
   `.addClassName("attachment-gallery--" + ad.getAdKind().name().toLowerCase())` before being
   added to `viewBody` (mirrors how `textCard` already gets its own modifier).
2. `attachment-gallery.css` — add modifier rules reusing the exact same badge color pairs as
   `advertisement-card.css`'s badges (background **and** text color, not just the border, since the
   gallery title currently shows its own background tint too):
   ```css
   .attachment-gallery--offer    { border-top-color: var(--app-status-success-badge-text) !important; }
   .attachment-gallery--request  { border-top-color: var(--app-status-info-text) !important; }
   .attachment-gallery--product  { border-top-color: var(--app-status-entity-advertisement-text) !important; }

   .attachment-gallery--offer .attachment-gallery__title    { background: var(--app-status-success-badge-bg); color: var(--app-status-success-badge-text); }
   .attachment-gallery--request .attachment-gallery__title  { background: var(--app-accent-primary-bg); color: var(--app-status-info-text); }
   .attachment-gallery--product .attachment-gallery__title  { background: var(--app-status-entity-advertisement-bg); color: var(--app-status-entity-advertisement-text); }
   ```
3. The base `.attachment-gallery`/`.attachment-gallery__title` rules (violet) stay as the fallback
   for every other entity's gallery (if any is ever added) that doesn't get one of these modifiers.

**Problem B — "Listing type" field label renamed to "Advertisement kind" (decided 2026-07-27):**
user decision — rename every occurrence of the label "Listing type" to "Advertisement kind" (an
earlier "Ad kind" draft was rejected as an unnatural abbreviation). Three English keys in
`messages_en.properties` carry this exact string: `advertisement.overlay.field.adKind` (field
label), `advertisement.filter.adKind` (filter label), `changes.field.adKind` (activity-diff field
label) — plus `advertisement.overlay.validation.adKind.required` ("Listing type is required" →
"Advertisement kind is required"). **Applied 2026-07-27** (all 4 EN keys). Ukrainian equivalents
(`advertisement.overlay.field.adKind`/`advertisement.filter.adKind`/`changes.field.adKind`,
currently "Тип оголошення", plus the validation key) still pending — proposed Ukrainian wording:
"Вид оголошення" (not "Тип", to mirror "kind"/"Advertisement kind" — awaiting user confirmation).

## Phase 3 (found 2026-07-27, same review round) — colored header strip, VIEW MODE ONLY

**Decided 2026-07-27: edit mode is explicitly out of scope, for both entities.** The edit-mode
form card header (`.overlay__form-card-header`, always blue) and the edit-mode gallery
(`.attachment-gallery` inside `AdvertisementFormOverlayModeHandler.buildGalleryForEdit`/
`buildGalleryForCreate`) both stay their current universal, non-`AdKind`/`Role`-specific color —
deliberately, since `AdKind`/role can change interactively in the edit form before save (the
`RadioButtonGroup<AdKind>`'s `addValueChangeListener` today only calls `updateButtons(...)`, no
live color update), and syncing edit-mode color live is a separate, bigger interactive-UI feature
not undertaken here. Only VIEW mode (read-only, value is fixed once the overlay opens) gets colored.

**Problem C:** `.overlay__view-card-header` (`forms.css:31-44`) — the small labeled strip inside
every view-mode card (shows "Advertisement" text for `AdvertisementViewOverlayModeHandler`, "View"
for `UserViewOverlayModeHandler`) — has a hardcoded green gradient background
(`--app-status-success-bg-soft`/`--app-status-success-bg-softer`) and green text
(`--app-status-success-text`), shared across every entity's view overlay, regardless of `AdKind`/
`Role`. This is the actual "background" mismatch the user pointed at — distinct from the
already-fixed border (Phase 1) and the plain white card body (deliberately left untouched — see
Fix C below).

**Fix C (view mode only):**
1. `AdvertisementViewOverlayModeHandler.buildPrimaryContent()` — `cardHeader` gets
   `.addClassName("overlay__view-card-header--" + ad.getAdKind().name().toLowerCase())`.
2. `UserViewOverlayModeHandler.buildPrimaryContent()` — `cardHeader` gets
   `.addClassName("overlay__view-card-header--" + user.role().name().toLowerCase())`.
3. `forms.css` — add modifier rules for `.overlay__view-card-header`, reusing the exact same
   badge/role color pairs (background + text), 3 `AdKind` + 3 `Role` suffixes (shared class
   between Advertisement and User view overlays):
   ```css
   .overlay__view-card-header--offer     { background: var(--app-status-success-badge-bg); color: var(--app-status-success-badge-text); border-bottom-color: var(--app-status-success-badge-text); }
   .overlay__view-card-header--request   { background: var(--app-accent-primary-bg); color: var(--app-status-info-text); border-bottom-color: var(--app-status-info-text); }
   .overlay__view-card-header--product   { background: var(--app-status-entity-advertisement-bg); color: var(--app-status-entity-advertisement-text); border-bottom-color: var(--app-status-entity-advertisement-text); }
   .overlay__view-card-header--admin     { background: var(--app-accent-primary-bg); color: var(--app-accent-primary-strong); border-bottom-color: var(--app-accent-primary-strong); }
   .overlay__view-card-header--user      { background: var(--app-status-success-badge-bg); color: var(--app-status-success-role-text); border-bottom-color: var(--app-status-success-role-text); }
   .overlay__view-card-header--moderator { background: var(--app-status-moderator-bg); color: var(--app-status-moderator-text); border-bottom-color: var(--app-status-moderator-text); }
   ```
4. Base `.overlay__view-card-header` (green) rule stays as fallback for Taxon/City view overlays,
   which have neither `AdKind` nor `Role`. `.overlay__form-card-header` (edit mode, blue) is not
   touched at all — see the "Decided" note above.
5. **Card body background stays white** (`.overlay__view-card`) — only the header strip and the
   border carry the accent color, same visual shape the Gallery card already used before this
   issue (colored title band + neutral white content area).
6. Users grid rows and the Advertisement list-card body: **untouched**, confirmed already correct
   (border-only, no header strip there).

**Testing (Phase 2+3, extend the same existing tests, no new spec files):**
- `04-marketplace-advertisement-flow.spec.js`, `adminEn edits UK advertisement` — extend
  `assertViewOverlayHasAdKind` to also assert the gallery's `.attachment-gallery--{kind}` class and
  the view header's `.overlay__view-card-header--{kind}` class (view mode only — no edit-mode
  assertion, since edit mode is unchanged).
- `03-marketplace-promotion-flow.spec.js`, the three `adminEn promotes ... to MODERATOR/ADMIN`
  tests — extend `runPromoteUserFlow`'s existing VIEW-mode role-badge check to also assert
  `.overlay__view-card-header--{role}` (view mode only).
- Verify actual computed CSS values (not just class-name presence) for every assertion above — read
  the element's resolved `background-color`/`border-top-color` via `page.evaluate(...)
  getComputedStyle` and compare against the expected value, since class-name presence alone doesn't
  prove the CSS rule actually won (selector-specificity/`!important`-ordering mistakes wouldn't be
  caught otherwise).
