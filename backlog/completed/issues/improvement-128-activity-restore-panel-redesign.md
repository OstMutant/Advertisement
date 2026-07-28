# improvement-128: Activity/restore panel doesn't scale past one content tab — needs a non-tab design

**Type:** UX/architecture — Settings pilot implemented and verified 2026-07-28; other four overlays
and the Account overlay rollout still open
**Module:** marketplace-app (`ui/views/main/header/settings/`, `ui/views/components/overlay/BaseOverlay.java`)
**Priority:** top — blocks improvement-124 Part 2 (unified "My Account" overlay), surfaced while planning it
**When:** Settings experiment done. Remaining scope (Advertisement/Taxon/City/User overlays,
improvement-124's Account overlay) picks up next, informed by this pilot.

## Problem

Today, every entity overlay pairs its one content tab with exactly one "Activity" tab
(`AbstractFormOverlayModeHandler.buildContentWithActivity()` → `buildTabbedContent()`): Edit +
Activity, always 2 tabs, always 1:1. The Activity tab shows a snapshot history list
(`AuditActivityPanel`) with a Restore button per row (`onRestoreRequested` →
`handleRestoreFromActivity`), scoped to one `EntityRef`. Confirmed current consumers of this exact
shape: `AdvertisementFormOverlayModeHandler`, `TaxonFormOverlayModeHandler`,
`CityFormOverlayModeHandler`, `UserFormOverlayModeHandler`, `SettingsFormModeHandler` — five
overlays, all the same 1 content tab + 1 Activity tab pattern.

This does not generalize. improvement-124's planned "My Account" overlay needs 3 content tabs
(Name / Settings / Provider Profile) spanning 2 different backing entities (`EntityType.USER` for
Name, `EntityType.ACTOR_PROFILE` for Settings + Provider Profile). Naively extending today's 1:1
pairing would mean either 6 tabs (one Activity per content tab) or awkward asymmetric pairing —
confirmed by hands-on review during improvement-124 planning that this doesn't feel right at any
tab count above 1.

**Restore is not optional to drop** — verified `AuditTimelineRowRenderer.java` (the global Timeline
tab) has no restore action at all; restore-from-snapshot only exists in the per-entity Activity
panel. Any redesign must keep restore working, just not necessarily as a same-level tab.

## What exists today (verified against code, for the redesign to build on)

- `AbstractFormOverlayModeHandler.buildTabbedContent(Tabs, Tab primaryTab, Div primaryContent, Supplier<Component> secondaryLoader)`
  (`ui/views/components/overlay/AbstractFormOverlayModeHandler.java:52-66`) — hardcoded one
  primary/secondary pair, lazy-loads the secondary tab's content on first switch.
- `buildContentWithActivity(ActivityTabParams)` (same file, lines 68-84) — the Edit+Activity
  convenience wrapper every current overlay uses; builds `editTab`/`activityTab`, calls
  `buildTabbedContent()`.
- `AuditActivityPanel` (`ui/views/components/audit/AuditActivityPanel.java`) — the actual history
  list + restore UI, `Configurable` prototype bean, parameterized by `entityRef` (`EntityType` +
  id), `onRestoreRequested` callback.
- `AuditActivityRowRenderer` — renders each snapshot row, restore button vs. "current state" badge.
- Global Timeline tab (`ui/views/main/tabs/timeline/TimelineView.java`) — flat, cross-entity,
  filterable browsing feed, no restore action, unrelated mechanism (kept as-is, out of scope here).

## Decided (2026-07-28, revised): stacked overlay, not a modal dialog

**Revised from the initial "modal dialog" direction after further discussion** — history/restore
still moves out of the tab strip entirely, but into a **second, standard `*Overlay`** (same
`BaseOverlay`/`OverlayLayout`/breadcrumb pattern already used everywhere else in the app — own
breadcrumb "back" link, own close (X) button), opened via an icon button in the parent overlay's
header actions and visually stacked on top of it (the parent overlay is never actually closed —
just covered). Closing the Activity overlay (breadcrumb back or X) uncovers the parent overlay
again, unchanged. Clicking Restore inside it stages the restored values into the parent form and
then closes the Activity overlay the same way — landing back on the parent overlay in its edit
state with the new values already loaded, mirroring exactly what `formTabs.setSelectedTab(editTab)`
does today, just via overlay-close instead of tab-select.

Rejected: a `Dialog`-based version of this (smaller lightbox instead of a full second overlay) —
dropped in favor of reusing the app's one existing full-page overlay navigation language
end-to-end, so this doesn't introduce a second, unfamiliar "history lives in a small popup"
interaction pattern alongside the app's only other current navigation idiom (overlay + breadcrumb).

**Verified precedent, not a novel concept:** `AdvertisementOverlay`'s View→Edit `OverlaySession`
mode-switching (`.claude/rules.md` "Overlay Pattern") already establishes that one overlay
component can show more than one internal state/screen — this is a natural extension of that
existing idea to a second, actually-separate overlay component, not a wholly new UI concept for
this codebase.

**New, small infrastructure needed** — verified by reading `BaseOverlay.java`/`OverlayLayout.java`/
`EntityOverlaySupport.java` directly: today exactly one overlay is ever open at a time per view,
and `BaseOverlay.open()`/`closeToList()` couple visibility toggling with **page-level** scroll-lock
+ focus-trap JS and a single `ui`-level ESC `Shortcuts` registration. Opening a second `BaseOverlay`
on top of an already-open one and calling its own `open()`/`closeToList()` unmodified would: (a)
double-register the scroll-lock JS (harmless-ish, idempotent), but (b) the inner overlay's
`closeToList()` would release the **page** scroll-lock/focus-trap that the outer (parent) overlay
still needs, and (c) both overlays would have independent ESC-key listeners bound to the same `UI`,
so pressing Escape while the inner overlay is open is not guaranteed to only close the inner one.
**Fix, additive and small:** add a "nested" open/close variant to `BaseOverlay` (e.g.
`openNested()`/`closeNested()`) that only toggles the `overlay--visible` CSS class + its own ESC
shortcut, without touching `document.body` scroll/focus-trap state — the existing `open()`/
`closeToList()` stay exactly as-is for every current single-level overlay (`AbstractEntityOverlay`
subclasses keep using them unchanged). CSS: `.base-overlay` is `position: fixed; z-index: 100;`
(`advertisement-overlay.css`) for every overlay today — the nested overlay needs a slightly higher
explicit `z-index` (e.g. `101`) rather than relying on DOM insertion order for stacking, since that
would be fragile.

Chosen direction (superseded text below still describes the icon-button-in-header-actions part
accurately; only the container changed from `Dialog` to a second overlay):

**Consequence, called out explicitly by the user:** once Activity is no longer a tab, a
single-content overlay (today: Settings, Advertisement, Taxon, City, User — all currently "1
content tab + 1 Activity tab") has nothing left to switch between. The primary content tab stops
being a tab too — its `Tabs` wrapper is removed, the form content renders directly as the
overlay's body. Multi-content overlays (the future Account overlay: Name/Settings/Provider
Profile) keep their `Tabs` for the real content sections, just with no Activity tab among them —
history is reached via icon button(s) instead, one per distinct backing entity involved (2 for
Account: USER for Name, ACTOR_PROFILE for Settings+Provider Profile).

**Rollout order — experiment first, then scale.** Implement and validate on the smallest,
self-contained case first: **`SettingsOverlay`/`SettingsFormModeHandler`**. Only after that's
working and covered by Playwright, apply the same pattern to
`AdvertisementFormOverlayModeHandler`/`TaxonFormOverlayModeHandler`/`CityFormOverlayModeHandler`/
`UserFormOverlayModeHandler`, and finally use it as the foundation for improvement-124's Account
overlay.

### Settings experiment — exact technical plan (verified against current code)

Current shape read directly from `SettingsFormModeHandler.java`/`SettingsOverlay.java`:

- `activate()` (lines 78-129) currently forks on `auditPortFactory.findIfAvailable()`: if present,
  wraps `settingsContent` in a `Tabs(settingsTab, historyTab)` via `buildTabbedContent()`; if
  absent, renders `settingsContent` directly. `buildHistoryContent()` (169-177) lazily builds
  `AuditActivityPanel` when the Activity tab is first selected. `afterSave()` (140-148) and
  `loadRestored()` (194-211) both reset `formTabs.setSelectedTab(settingsTab)` after a save/restore.

**Scope for this pass: `SettingsOverlay` only.** The other four overlays
(Advertisement/Taxon/City/User) and the future Account overlay are explicitly deferred until this
experiment is validated — no changes to `AbstractFormOverlayModeHandler`'s shared
`buildTabbedContent()`/`buildContentWithActivity()` methods in this pass, since the other four
overlays still use them unchanged.

`BaseOverlay.java` — additive change, used by the new overlay only:
- Add `openNested()` / `closeNested()`: same `overlay--visible` class toggle + own ESC
  `Shortcuts` registration as `open()`/`closeToList()`, but **without** the `document.body`
  scroll-lock/focus-trap JS (that stays owned by whichever overlay is outermost). Existing
  `open()`/`closeToList()` and every current `*Overlay` subclass stay byte-for-byte unchanged.

New class `SettingsActivityOverlay` (`ui/views/main/header/settings/`, `@SpringComponent
@UIScope`, extends `BaseOverlay` directly — not `AbstractEntityOverlay`, since there's no
save/cancel form here, just a breadcrumb, a close button, and the read-only
`AuditActivityPanel`):
- `openFor(Long userId, boolean isPrivileged, boolean canOperate, Runnable onClosed, LongConsumer onRestoreRequested)`
  builds its `OverlayLayout` (via `EntityOverlaySupport.createLayout()`, same as every other
  overlay) with a breadcrumb back-button labeled with the parent's title (e.g. "‹ Settings") and
  an X close button in `headerActions` — **both call the same `close()`**, which does
  `closeNested()` then `onClosed.run()` (uncovers the parent `SettingsOverlay`, which was never
  actually closed underneath). Body = `auditActivityPanelFactory.build(...)`, `onRestoreRequested`
  wired to call the passed-in `onRestoreRequested` callback **then** `close()` — restoring lands
  back on the parent overlay's edit state with the new values already staged, no tab-select needed
  since the parent was visible underneath the whole time.
- Wired into `HeaderBar` right after `settingsOverlay` (`add(settingsOverlay); add(settingsActivityOverlay);`)
  so it's a later DOM sibling (natural stacking) — plus an explicit higher `z-index` in CSS for
  robustness, not relying on DOM order alone.

`SettingsFormModeHandler.java` changes:
- `activate()` (lines 78-129): remove the `Tabs`/`Tab settingsTab`/`buildTabbedContent()` fork —
  `layout.setContent(settingsContent)` unconditionally.
- Add a history icon button (`UiIconButton`, e.g. `VaadinIcon.CLOCK`) to
  `layout.setHeaderActions(...)` alongside `saveButton`/`discardButton`/`closeBtn` — shown only
  when `auditPortFactory.findIfAvailable()` is present (same guard as today). Click →
  `settingsActivityOverlay.openFor(params.getUserId(), ..., /* onRestoreRequested */ this::loadRestored)`.
- `loadRestored()` (194-211): keep the exact same field-staging logic; drop the
  `formTabs.setSelectedTab(settingsTab)` line — closing the Activity overlay (done by
  `SettingsActivityOverlay` itself right after invoking this callback) is what reveals the parent
  again, nothing left to select.
- `afterSave()` (140-148): drop the `tabbedSecondaryContent`/`formTabs` reset lines (140-143) —
  nothing to reset anymore.
- `buildHistoryContent()` (169-177) moves as-is into `SettingsActivityOverlay` (same
  `AuditActivityPanel.Parameters` construction); `settingsTab`/`formTabs`/`tabbedSecondaryContent`
  fields removed from `SettingsFormModeHandler`.

**Remove the tab look and its localization entirely** (explicit user instruction — this is not a
relabel, the tab concept itself is gone for Settings):
- `SETTINGS_ACTIVITY_TAB` (`I18nKey`) — delete this key and its EN/UK message-bundle entries;
  replace with a new key for whatever labels the new icon button / breadcrumb-back text (e.g.
  `SETTINGS_ACTIVITY_BUTTON` and/or a breadcrumb label key) — not a rename-in-place, since the
  semantic (tab label vs. button tooltip vs. breadcrumb text) may need distinct copy.
- CSS class `user-view-tabs` (only ever applied to this now-deleted `formTabs` in
  `SettingsFormModeHandler`) — confirm no other class reuses it before deleting its rule.

**CSS:** `.settings-activity-overlay` (or similarly named) rule for the new overlay class, higher
`z-index` than the base `100`; button class for the new history icon.

**Playwright — update in place, do not duplicate.** Whatever flow helper currently clicks the
Settings "Activity" tab and asserts the restore banner/values (the existing Settings restore-flow
test) must be repointed: click the new history icon → assert the new overlay is visible with its
own breadcrumb/close button → click Restore on a snapshot row → assert the new overlay closes,
the Settings overlay is visible again, and the restored values are present in the actual form
fields (not just "a success notification appeared" — check the real field values, matching how
today's test presumably already asserts post-restore field state). Also add: pressing the new
overlay's own X, and its breadcrumb back-button, both close it back to Settings without restoring
anything (discard-equivalent for a read-only panel — no data changes, so nothing to confirm-discard,
just visibility). Check for screenshot-name collisions per the project's existing convention before
adding any new `screenshot()` calls.

### After the experiment validates

Re-evaluate before generalizing to the other four overlays and the Account overlay: does
`AbstractEntityOverlay`/`BaseOverlay` need a new shared helper for "history icon button + nested
overlay" (mirroring how `buildContentWithActivity()` centralizes today's tab pattern), so the
other four overlays and the Account overlay don't each hand-roll their own
`XxxActivityOverlay` class? Likely yes — a generic, `EntityRef`-parameterized reusable component
is the natural next step — but
defer designing that helper until the Settings experiment proves the interaction pattern itself is
right.

## Rollout plan — Advertisement / Taxon / City / User (2026-07-28)

Grounded by reading all four `*FormOverlayModeHandler` classes plus `AbstractFormOverlayModeHandler`,
`AdvertisementOverlay`/`TaxonOverlay`/`CityOverlay`/`UserOverlay`, and the full Playwright surface
that touches Activity tabs, before writing this plan.

**Verified: all four handlers share the exact same shape** `SettingsFormModeHandler` had before
the pilot — `buildContentWithActivity(ActivityTabParams...)`, a `buildActivityContent()` supplier,
`handleRestoreFromActivity()` + `loadRestored()` (stages values, `formTabs.setSelectedTab(editTab)`),
and `afterSave()` resetting `formTabs`/`tabbedSecondaryContent`. Per-domain differences are only:
`EntityType` (`ADVERTISEMENT`/`TAXON`/`TAXON` again for City — reuses `TaxonType.CITY`, same
`EntityType.TAXON`/`USER`), the restore DTO type, `isPrivileged`/`canOperate` computation (Taxon/City
hardcode `access.isPrivileged()`; User/Advertisement use `access.canOperate(ownerId)`), and whether
a create-mode with no history exists yet (Advertisement/Taxon/City yes, User no — never created via
this overlay).

**Verified: each domain's own overlay breadcrumb link is not literally "Home"** —
`AdvertisementOverlay`/`TaxonOverlay`/`CityOverlay`/`UserOverlay.getBreadcrumbLabelKey()` return
`MAIN_TAB_ADVERTISEMENTS`/`MAIN_TAB_REFERENCE_DATA`/`MAIN_TAB_REFERENCE_DATA`/`MAIN_TAB_USERS`
respectively — only Settings' own outer link happens to be `HEADER_HOME`. The generic component
below must take the outer label as a parameter, not hardcode it.

### Decision: build one shared, generic `EntityActivityOverlay`, retire `SettingsActivityOverlay`

Per this issue's own "after the experiment validates" question — yes, extract a shared component
now rather than hand-rolling 4 more `XxxActivityOverlay` classes. One `@SpringComponent @UIScope`
singleton, registered once in `HeaderBar` (reachable from every domain regardless of which routed
View is currently showing, exactly like `SettingsActivityOverlay`/`SettingsOverlay` today), reused
by all five domains including a retrofitted Settings.

`ui/views/components/audit/EntityActivityOverlay.java` (next to `AuditActivityPanel`):
```java
@Value @Builder
public static class Parameters {
    @NonNull EntityRef   entityRef;
    @NonNull Long        userId;
    boolean               isPrivileged;
    boolean               canOperate;
    @NonNull I18nKey      outerLabelKey;    // e.g. MAIN_TAB_ADVERTISEMENTS, HEADER_HOME for Settings
    @NonNull I18nKey      parentLabelKey;   // e.g. ADVERTISEMENT_OVERLAY_SECTION_BASIC, SETTINGS_SECTION_TITLE
    @NonNull I18nKey      currentLabelKey;  // e.g. a reused "*_ACTIVITY_BUTTON" key per domain
    @NonNull Runnable     onCloseToOuter;   // == params.getOnCancel() of the calling FormOverlayModeHandler
    @NonNull LongConsumer onRestoreRequested;
}
public void openFor(@NonNull Parameters p) { ... }
```
Internals mirror `SettingsActivityOverlay` exactly (breadcrumb chain `[outer, parent]` via
`OverlayLayout.setBreadcrumbLinks()`, X closes to `parentLabelKey`'s target — i.e. `closeToParent()`
— same "X = screen that opened this" rule verified for Settings; only the breadcrumb's `outerLink`
exits all the way via `onCloseToOuter`), just parameterized instead of Settings-hardcoded. CSS
classes become domain-agnostic (`entity-activity-overlay`, `entity-activity-close-button`,
`entity-activity-breadcrumb-outer`, `entity-activity-breadcrumb-parent`) since only one instance is
ever open — no per-domain CSS class needed for the overlay chrome itself (only the *opening* icon
button in each `*FormModeHandler`'s header keeps its own domain-specific class, e.g.
`advertisement-history-button`, for Playwright scoping when needed).

**`SettingsActivityOverlay.java` is deleted**; `SettingsFormModeHandler` switches to injecting
`EntityActivityOverlay` and building `Parameters` with `outerLabelKey(HEADER_HOME)`,
`parentLabelKey(SETTINGS_SECTION_TITLE)`, `entityRef(EntityType.USER_SETTINGS, userId)`. This is
the first, lowest-risk proof that the generalized component behaves identically to the pilot before
touching the other four domains.

### Per-domain wiring (same shape for all four, in this order: Taxon, City, Advertisement, User)

For each `*FormOverlayModeHandler`:
- Remove `buildContentWithActivity(ActivityTabParams...)` call — `layout.setContent(editContent)`
  unconditionally (mirrors Settings' `activate()` change).
- Add a history icon button to `layout.setHeaderActions(...)`, guarded by
  `auditPortFactory.findIfAvailable()` **and** the same `canOperate`/`isCreateMode` condition
  `ActivityTabParams` used to gate on (a create-mode Taxon/City/Advertisement has no history yet —
  don't show the button at all, not just an empty panel).
- `buildActivityContent()` deleted; its `AuditActivityPanel.Parameters` fields move into the
  `EntityActivityOverlay.Parameters` builder at the click-listener call site.
- `handleRestoreFromActivity()` stays (still owns the restore-DTO-building logic per domain) but is
  now the `onRestoreRequested` callback passed to `EntityActivityOverlay.openFor()`, exactly like
  Settings' `this::handleRestoreFromActivity`.
- `loadRestored()`: drop the `formTabs.setSelectedTab(editTab)` line.
- `afterSave()`: drop the `formTabs`/`tabbedSecondaryContent` reset lines.
- Breadcrumb labels: reuse each domain's own existing "section label" key as `parentLabelKey`
  (`ADVERTISEMENT_OVERLAY_SECTION_BASIC`-equivalent, `TAXON_OVERLAY_SECTION_LABEL`,
  `CITY_OVERLAY_SECTION_LABEL`, `USER_DIALOG_SECTION_LABEL` — verify exact key name per class
  during implementation, same reuse pattern Settings used for `SETTINGS_SECTION_TITLE`) and each
  domain's `*Overlay.getBreadcrumbLabelKey()` value as `outerLabelKey`. `currentLabelKey`: reuse
  the domain's existing `*_ACTIVITY_TAB`/`*_TAB_ACTIVITY` key's **value** but a renamed constant
  (`ADVERTISEMENT_ACTIVITY_BUTTON`, `TAXON_ACTIVITY_BUTTON`, `CITY_ACTIVITY_BUTTON`, already-shared
  `USER_ACTIVITY_TAB`→`USER_ACTIVITY_BUTTON`), same rename-not-reuse-in-place reasoning as
  `SETTINGS_ACTIVITY_TAB`→`SETTINGS_ACTIVITY_BUTTON`.

### Cleanup once all five domains are migrated

- `AbstractFormOverlayModeHandler`: delete `buildTabbedContent()`, `buildContentWithActivity()`,
  `ActivityTabParams`, and the now-unused `tabbedSecondaryContent`/`formTabs`/`editTab` protected
  fields — verify zero remaining callers first.
- CSS: delete `.advertisement-form-tabs`(or whatever the real class is)/`.taxon-form-tabs`/
  `.city-form-tabs`/`.user-form-tabs` rules and the `.activity-feed-content` tab-pane class if
  nothing else references it (verify per class before deleting, same discipline as
  `.user-view-tabs` in the pilot).
- i18n: delete the four old `*_ACTIVITY_TAB`/`*_TAB_ACTIVITY` keys and their EN/UK strings once
  renamed (not just left dangling).

### Playwright — extract a shared flow file, this is the largest part of the rollout

Verified surface (file:line, current tab-based mechanics):
- `_flows/advertisement.flow.js:97-98` — `openActivityTab(overlay)`, the central helper, ~15
  internal call sites plus 3 more from `04-marketplace-advertisement-flow.spec.js` and 1 from
  `03-marketplace-promotion-flow.spec.js`.
- `03-marketplace-promotion-flow.spec.js` — inline `vaadin-tab` clicks for Taxon/City (lines 339,
  399, 482) and User (lines 193, 207, 542, 560).
- `_flows/audit.flow.js:79` (`runVerifyUserAuditActivityFlow`) and
  `_flows/user-management.flow.js:104` — both User overlay activity-tab clicks.

Per the project's helper-organization rule ("extract to a shared file only when two or more flow
files need the same helper") — this now clearly qualifies: create
`_flows/entity-activity.flow.js` with `openEntityActivity(page, openButtonSelector)` /
`closeEntityActivity(page, via)` (`via: 'outer' | 'parent' | 'x'`, mirroring `settings.flow.js`'s
`closeHistory` shape but generalized) / helpers returning the same `activityList` locator shape
existing call sites already destructure, so assertion bodies (`.entity-activity-restore-btn`
counts, etc.) don't need rewriting, only the "how do you get to the list" part. Repoint
`advertisement.flow.js`'s `openActivityTab`, the inline `vaadin-tab` clicks in spec 03, and
`audit.flow.js`/`user-management.flow.js`'s User overlay clicks to the new shared helper. Add
matching `closeEntityActivity` calls wherever a test currently relies on `formTabs.setSelectedTab`
happening implicitly after save/restore (search for `.entity-activity-restore-btn` click sites
immediately followed by a Save-button click, mirroring the pilot's `restoreLatestFromActivity`
fix). Re-run the exact same "no doubled `›`" / separator-count regression assertions added for
Settings against at least one multi-word-breadcrumb domain (Advertisement or Taxon) to confirm the
generalized component doesn't regress the CSS fix.

### Verification

Same bar as the pilot: `unit-tests.sh`, `integration-tests.sh --sandbox` (no schema change expected
— confirm), full Playwright `e2e --full --ux`. Given the size of this rollout, expect to hit and
fix real issues along the way (per this session's pattern with the pilot) rather than a clean
first pass — root-cause and fix in the same run per the project's autopilot convention, don't stop
to ask.

### Once this rollout is done

`AbstractEntityOverlay`/`BaseOverlay` still won't need changes for improvement-124's Account
overlay case (2 backing entities, 3 content tabs) — `EntityActivityOverlay` is already generic
over `EntityRef`, so the Account overlay just calls `openFor()` twice (once scoped to
`EntityType.USER`, once to `EntityType.ACTOR_PROFILE`) with its own two history icon buttons. Move
this issue to `backlog/completed/issues/` once Advertisement/Taxon/City/User are done and verified
— improvement-128's full scope (redesign + pilot + rollout) will be complete; improvement-124
picks up the now-proven pattern from there.

## Related

- [improvement-124](improvement-124-provider-profile.md) — the "My Account" overlay whose Part 2
  planning surfaced this problem; blocked on this issue's outcome for its tab structure.
- `.claude/rules.md` "Form Handler Pattern" — `buildTabbedContent()` "do not duplicate" rule;
  whatever comes out of this issue must respect or consciously supersede that rule.

## Resolution — Settings pilot (2026-07-28)

Implemented exactly per the "Decided" plan above, with one refinement made mid-implementation
(see below) after direct user feedback. Full design rationale recorded in
`marketplace-app/DECISIONS.md` ADR-067 — this section summarizes what changed and how it was
verified.

**Implemented:**
- `BaseOverlay.openNested()` / `closeNested()` (new, additive) — visibility + own ESC listener
  only, no page-level scroll-lock/focus-trap. Every existing overlay's `open()`/`closeToList()`
  untouched.
- New `SettingsActivityOverlay` (`ui/views/main/header/settings/`) — plain `BaseOverlay`, wired
  into `HeaderBar` right after `settingsOverlay`.
- **Final breadcrumb/close design, after two rounds of correction from direct user testing of the
  running app** (both documented in `marketplace-app/DECISIONS.md` ADR-067's "Update"):
  - The breadcrumb is a real 3-segment chain — **Home / Settings / Activity** — with "Home" and
    "Settings" each independently clickable, "Activity" plain text (the current page). This
    required a small, additive generalization of the shared `OverlayLayout`/`EntityOverlaySupport`
    (`setBreadcrumbLinks(List<Component>)`, new `createLayout(List<Component>)` overload) — every
    existing single-link overlay is unaffected, it's still exactly the 1-link case underneath.
  - **X goes back to Settings** (the screen this overlay was opened from), matching what every
    overlay's X has always actually meant ("back to the opening screen" — indistinguishable from
    "Home" until now, since single-level overlays only ever opened from Home). The breadcrumb's
    "Home" link is the one, single path that exits all the way out. (An intermediate version had
    this backwards — X going to Home — reverted after the user pointed out the app's existing
    convention is "X = previous screen".)
  - Two rendering bugs found via direct visual inspection, both fixed same-day: a doubled-up `›`
    before the current label (trailing separator collided with the always-present outer one), then
    (after the count fix) visibly uneven gaps between segments (`.overlay__breadcrumb-back-slot`
    needed the same `display:flex; gap:4px` as its parent, not just `display:flex`).
- `SettingsFormModeHandler` — removed the `Tabs`/`buildTabbedContent()` fork; added a history icon
  button in the header actions, shown only when the audit starter is present (same guard as
  before).
- Removed `SETTINGS_ACTIVITY_TAB` i18n key and the orphaned `.user-view-tabs` CSS rule (verified no
  other consumer before deleting); added `SETTINGS_ACTIVITY_BUTTON`.
- Playwright: `settings.flow.js` (`openHistory`/`closeHistory(page, via)` with `x`/`settings`/`home`
  variants/updated `restoreLatestFromActivity`), `audit.flow.js` (`runVerifySettingsAfterSignupFlow`
  repointed, dead `runVerifySettingsActivityFlow` deleted), `_helpers.js`'s `closeOverlay()` scoped
  to the currently-visible overlay (defensive fix — a second, occasionally-initialized overlay in
  the header made the old unscoped selector a latent multi-match risk), spec 05's Test 5 rewritten
  for the nested-overlay flow (all three close paths exercised, plus new separator-count/no-double-›
  regression assertions for both the multi-link and single-link breadcrumb cases), and a new
  equivalent-purpose check (unsaved edit survives a trip into history and back) replacing the
  now-meaningless old "save switches tab back" assertion.

**Verified (final pass, after both correction rounds):**
- `unit-tests.sh`: 77/77.
- `integration-tests.sh --sandbox`: 133/133 (no schema/repository changes — pure UI refactor).
- Playwright `e2e --full --ux`: 50/50, including the accessibility spec (new icon-only history
  button has a proper `aria-label` via the existing `UiIconButton` convention) and the new
  breadcrumb-spacing regression assertions.
- One unrelated, non-reproducing flake was hit and ruled out during an earlier run in this session
  (an `ad-kind` radio button click failing with "html intercepts pointer events") — did not
  reproduce on a clean rerun, confirmed unrelated to this change (different domain, different
  overlay, no code path in common).

## Resolution — full rollout (2026-07-28, same day as the pilot)

Rolled out to Advertisement, Taxon, City, and User, completing this issue's full scope. Full
design rationale and the bugs found along the way are recorded in `marketplace-app/DECISIONS.md`
ADR-067's "Update" section — this section summarizes what changed and how it was verified.

**Decision made before starting the rollout:** extract one shared, generic `EntityActivityOverlay`
(`ui/views/components/audit/`) rather than copy `SettingsActivityOverlay` four more times.
`SettingsActivityOverlay` was deleted; Settings itself was retrofitted onto the shared component
first (cheapest possible proof the generalization didn't regress the pilot), then Taxon, City,
Advertisement, and User followed the same shape: remove the `Tabs`/`buildContentWithActivity()`
call, add a history icon button gated by `auditPortFactory.findIfAvailable()` +
`canOperate`/`!isCreateMode`, wire `EntityActivityOverlay.Parameters` with that domain's own
`EntityType`, `outerLabelKey` (that domain's own `*Overlay.getBreadcrumbLabelKey()`), and
`parentLabelKey` (that domain's own form section label).

**Corrected along the way:** the pilot's "X always means exit to Home" framing only held for
Settings by coincidence (its own outer breadcrumb link genuinely is `HEADER_HOME`) — every other
domain's outer link is its own list-view label (`MAIN_TAB_ADVERTISEMENTS`, etc.), so
`EntityActivityOverlay`'s two breadcrumb links (`outerLabelKey`, `parentLabelKey`) are both
supplied per call site, never hardcoded.

**Cleanup once all five domains were migrated:** `AbstractFormOverlayModeHandler`'s
`buildTabbedContent()`/`buildContentWithActivity()`/`ActivityTabParams`/`tabbedSecondaryContent`/
`formTabs`/`editTab` deleted (verified zero remaining callers first) — the base class is now a
handful of lines. Dead i18n keys (`*_ACTIVITY_TAB`/`*_TAB_ACTIVITY`/`*_OVERLAY_TAB_EDIT`) and dead
tab-pane wrapper CSS (`.activity-feed-content`, `.entity-activity-content`) removed.

**Playwright:** new shared `_flows/entity-activity.flow.js` (`openEntityActivity`/
`closeEntityActivity`/`restoreFromEntityActivity`), deliberately idempotent on close since tracking
"did an earlier step already close it" by hand across ~30 call sites proved too error-prone during
the rewrite. `settings.flow.js` simplified to thin wrappers over the shared helper.
`advertisement.flow.js`'s existing `openActivityTab(overlay)` kept its name/signature unchanged
(derives `page` via `overlay.page()`), so its many internal and 3 external (spec 04) call sites
needed no changes beyond the function body. Spec 03's Taxon/City/User inline tab-clicks and
`user-management.flow.js`/`audit.flow.js`'s User overlay activity checks all repointed.

**Real bug caught by an explicit stale-reference sweep before running the suite** (done per
explicit mid-rollout instruction, not just relying on compile+test): `audit.flow.js` and the
pilot's own `05-seed-filter-sort-pagination.spec.js` still referenced the old
`.settings-activity-*` CSS classes after Settings moved onto the generic component's
`.entity-activity-*` classes — CSS/selector typos don't fail a build, only a deliberate grep sweep
catches them. Both fixed before the final verification run.

**Verified (full rollout, all five domains):**
- `unit-tests.sh`: 77/77.
- `integration-tests.sh --sandbox`: 133/133 (no schema/repository changes — pure UI refactor).
- Playwright `e2e --full --ux`: 50/50, including every Advertisement/Taxon/City/User
  activity-diff/restore scenario in specs 03 and 04, plus the pilot's own Settings scenario in
  spec 05 and the accessibility spec (new icon-only history buttons across all five domains have
  proper `aria-label`s).

**improvement-124's Account overlay** (2 backing entities, 3 content tabs) can now reuse
`EntityActivityOverlay` directly — call `openFor()` twice (once scoped to `EntityType.USER`, once
to `EntityType.ACTOR_PROFILE`) with two history icon buttons; no further generalization of the
overlay/breadcrumb infrastructure needed. This issue's full scope (redesign + pilot + rollout) is
complete.
