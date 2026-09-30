# improvement-203: out-of-scope implementation findings (running collection)

**Type:** improvement — meta/process issue, ongoing collection bucket, not a single fix.
**Module:** cross-cutting — whichever module each entry below actually touches.
**Priority:** 🟡 Top — kept close to active work so entries get triaged quickly, not left to rot.
**When:** ongoing; triage entries opportunistically during active implementation work.

## Purpose

A running catch-all for things noticed *during implementation* of some other task that are real
but outside that task's own approved scope — a pre-existing issue in an unrelated file, a question
needing its own separate approval (e.g. a SonarQube false-positive server-side transition), a small
inconsistency spotted while working on something else entirely. Distinct from `improvement-133`
(review findings too large to fit the current batch, needing a design decision before sizing) —
this bucket is for findings that are small/clear but simply belong to a different piece of work,
kept at Top priority so they don't sit forgotten the way a 🔵 tech-debt bucket would.

Each entry below is independent; when one is picked up, evaluate it fresh and resolve it directly.
Remove the entry once resolved — same lifecycle as any other finding, just deferred.

## Entries

### 1. `HeaderBar.java:39` — `onSettingsClosed` field flagged by SonarQube `java:S1450`, likely a false positive (found during improvement-200 Phase 1, 2026-09-30)

SonarQube's quality gate flagged `private Runnable onSettingsClosed` as a field that could be a
local variable. Analysis suggests this is a false positive: `MainView.java:123` calls
`headerBar.setOnSettingsClosed(...)` from outside, *after* Spring has already constructed the
`@UIScope` `HeaderBar` bean and its `@PostConstruct init()` has already built the settings button
(whose click listener reads `onSettingsClosed` lazily, at click time). The field bridges a real
time gap between "button built" and "real callback supplied via setter" — Spring can't inject a
`Runnable` via constructor from another view, so the setter-injection pattern is deliberate, not
an oversight a local variable could replace.

Not resolved inline: confirming and fixing a SonarQube false positive requires marking it in two
places (the SonarQube server itself via a `do_transition` call, plus a `@SuppressWarnings` in
code) and the server-side transition needs its own separate, explicit approval per
`.claude/rules.md`'s "Never mutate SonarQube's own server-side state without a separate, explicit
approval for that specific call" — general approval to "fix Sonar findings" already covered the
other 3 issues found in the same scan (all applied directly), but not this one.

### 2. `ContactRevealPanel`/`FeedbackPanel` (and provider-profile account-tab's own contact block) all render nested in the same flat card — no visual card-per-section separation (found during improvement-200 Phase 1 UI wiring, 2026-09-30)

`.overlay__view-card` (`advertisement-overlay.css`) is one flex column (`gap: 12px`) — every
child (title/description/chips/badge/`ContactRevealPanel`/`FeedbackPanel`/meta) renders as a flat
stack inside the same single white card, no border/background distinguishing one section from
another. `.contact-reveal-panel` has zero CSS rules of its own. The same pattern also exists in
`ProviderProfileViewModeHandler` (`AccountOverlay`'s "Provider Profile" tab) — its own
`buildContactViewsBlock(...)` is likewise just `card.add(...)`'d into the one shared card, not a
separate section.

**Wanted instead:** each section (contact, feedback, and any future similar block) as its own
genuinely separate sibling card — full `.overlay__view-card`-shaped treatment (own white
background, border, top accent, shadow, radius), stacked with a gap between them — not a nested
sub-box inside the description card. Applies symmetrically everywhere `ContactRevealPanel`/
`FeedbackPanel` (or `ProviderProfileViewModeHandler`'s own contact block) are used:
`ProviderProfileCatalogViewModeHandler`, `AdvertisementViewOverlayModeHandler`,
`ProviderProfileViewModeHandler`.

Not resolved inline: user explicitly deferred this until every phase of `improvement-200` itself
is implemented first, rather than context-switching into a UI/CSS restructuring pass mid-Phase-1.
A first implementation attempt (a nested `.overlay__view-subcard` sub-box, not the wanted separate
sibling card) was made and then explicitly reverted at the user's request before this entry was
filed — confirmed via `git diff` that the revert left only the legitimate `FeedbackPanel` wiring
diff behind, no leftover fragments.

## Related

- `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` — sibling bucket, for
  findings too large to size immediately rather than simply out of scope.
