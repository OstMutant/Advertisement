# improvement-129: Modernize AdvertisementsView into a content-feed UI (research-grounded proposal)

**Type:** improvement — large UI-only initiative, proposal stage (not an approved implementation
plan; needs design decisions before work starts).
**Module:** `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/advertisements/`
(`AdvertisementsView.java`, `AdvertisementCardView.java`, `card/AdvertisementCardMetaPanel.java`,
`overlay/AdvertisementOverlay.java`), `marketplace-app/src/main/frontend/themes/my-app/`
(`advertisement-card.css`, `advertisements-view.css`, `styles.css` design tokens),
`platform-commons/src/main/java/org/ost/platform/advertisement/dto/AdvertisementInfoDto.java`,
`playwright/e2e/04-marketplace-advertisement-flow.spec.js` +
`playwright/e2e/_flows/advertisement.flow.js`.
**Priority:** 🔵 (larger tech-debt/UI-polish tier — no live bug, real effort, needs a design
decision before implementation; see "Priority rationale" below for why this isn't 🟡).
**When:** independent, no blockers — but see "Open judgment call: sequencing vs improvement-124"
below before scheduling it.

## Problem

The user received a large, detailed AI-generated spec (reproduced/condensed below) proposing to
modernize `AdvertisementsView` from a "standard Vaadin grid/CRUD-style list" into a modern
LinkedIn/Facebook-Marketplace-style content feed, while preserving all existing architecture,
routing, and backend. The user liked the direction but flagged that the spec assumes several
things exist (a component/class called `ActorProfile`, specific CSS custom property names, an
existing gallery/carousel component, an existing "contact" action, badge/verification/rating
concepts) without verifying them against this actual codebase, and asked for those assumptions to
be checked one by one rather than taken on faith.

**Condensed spec requirements** (user-supplied AI-generated spec, evaluated against actual code
below — none of this is presented as fact about this codebase merely because the spec asserts it):
- No `vaadin-grid` — feed as vertical content layout, each ad a content card.
- Reuse existing design tokens: `--app-surface-page`, `--app-surface-white`, `--app-surface-muted`,
  `--app-accent-primary`, `--app-border-default`. No new design system, no hardcoded colors.
- Desktop 1024px+: feed centered, target width 680-720px, generous whitespace, filters visually
  secondary. Tablet ~768px: feed ~90% width. Mobile ~390px: full width, minimal padding, radius
  may go to 0, no horizontal scroll/clipped content.
- Card hierarchy WHO→WHAT→WHERE→HOW: (WHO) avatar/initials, name, relative time if available, city
  if available, `ActorProfile.kind` badge IF it exists (spec explicitly: do not invent
  verification/ratings/reviews); (WHAT) `AdKind` badge with existing semantic colors, prominent
  title, category as subtle chip; (WHERE) city/service area; media area up to ~450px height
  desktop, `object-fit` chosen per content, lazy loading, reuse existing gallery component if one
  exists; description ~3-line clamp with full text via existing overlay; (HOW) ghost/low-emphasis
  action buttons prioritizing what actually exists (View Details, Share if it exists, Contact if it
  exists, owner-only Edit/Delete, overflow menu if many actions) — explicitly no fake
  likes/comments/followers.
- Card visuals: subtle 1px borders, restrained shadows, 12-16px radius desktop, page bg
  `--app-surface-page`, card bg `--app-surface-white`, border `--app-border-default`.
- Preserve existing `AdvertisementOverlay`/detail flow, deep-link route contract (e.g. `ads/:id`),
  browser history behavior; buttons inside card must not trigger card-level navigation.
- Preserve `QueryBlock` filtering fully; desktop compact side panel or secondary area; mobile
  collapsible/filter-button pattern if supported.
- Keep existing `PaginationBar`, integrate visually. Modernize `EmptyStateView` spacing/hierarchy,
  no fake recommendations.
- Responsive invariants at 1440/1024/768/390px, lazy image loading, accessibility (labels, alt
  text, keyboard-accessible cards, focus states, color not sole indicator, touch targets).
- Explicitly out of scope per the spec itself: no domain/backend rewrite, no new frontend
  framework, no invented data, no fake social features/ratings/reviews/verification.

## What was verified against the actual codebase (2026-07-29)

### 1. `AdvertisementCardView` is already NOT a `vaadin-grid` — the "No Grid Rule" is a non-issue

`AdvertisementCardView` (`marketplace-app/.../advertisements/AdvertisementCardView.java`) is a
`HorizontalLayout` (`extends HorizontalLayout`) — a prototype-scoped `Configurable` component
representing one card, built by `AdvertisementsView` and placed into a `FlexLayout` with
`setFlexWrap(FlexLayout.FlexWrap.WRAP)` (`AdvertisementsView.buildAdvertisementContainer()`).
There is no `vaadin-grid`, no `Grid<AdvertisementInfoDto>`, anywhere in this view today. Card
entrance is already animated (`fadeSlideUp` keyframes, staggered per-index delay, in
`advertisements-view.css`). **This means the spec's central premise — "transform a grid into a
feed" — does not apply here.** The actual gap is narrower: today's layout is a horizontal card
(thumbnail on the left, content on the right, `flex-direction: row` — see
`advertisement-card.css` line 9-10) capped at `max-width: 900px`
(`advertisements-content-wrapper`), not the spec's target vertical LinkedIn-style card at
680-720px with a large stacked media block on top. **This is a visual/layout refresh of an
existing card-based feed, not a structural rebuild from grid to feed.**

### 2. `ActorProfile` does not exist anywhere in this codebase today — confirmed by direct grep

`grep -rl "ActorProfile" /app --include="*.java"` returns zero hits outside
`backlog/issues/improvement-124-provider-profile.md` itself. `improvement-124` (currently the
sole "Top priority" item in `backlog/BACKLOG.md`, unblocked as of 2026-07-28) *proposes*
introducing `ActorProfile`/`actor-profile-spring-boot-starter`/`EntityType.ACTOR_PROFILE`/
`ProviderKind {MASTER, SHOP, SUPPORT}` — none of it is implemented yet. **Any card element keyed
off `ActorProfile.kind` cannot be built today; it would need to be deferred until
improvement-124 ships, or descoped from this issue entirely.**

### 3. No avatar/photo field exists for users

`UserDto` (`platform-commons/.../user/dto/UserDto.java`) has exactly 8 fields: `id`, `name`,
`email`, `role`, `createdAt`, `updatedAt`, `locale`, `version` — no avatar/photo/image URL field
of any kind. `AdvertisementInfoDto` similarly carries only `createdByUserName`/
`createdByUserEmail` (plain strings) for the author, no avatar reference.
`AdvertisementCardMetaPanel` (the "WHO" line today) renders a plain text `Span` for the author
name plus the email as a `title` tooltip attribute — no image, no initials-avatar component
anywhere in this code path. **A WHO-header with an avatar or initials bubble does not exist and
would need to be built from scratch** (a generated-initials `Div`/CSS circle is cheap and doesn't
require new data; a real photo avatar would require a new field end-to-end, likely deferred to
`ActorProfile`/improvement-124 rather than invented here).

### 4. "Contact" action does not exist

Grepped `AdvertisementCardView`'s action row (`createActions()`): only three buttons exist today —
`EditActionButton` (owner-only), `DeleteActionButton` (owner-only), `ShareActionButton` (always
visible). There is no contact/message/inquire action anywhere in the advertisement domain, no
messaging subsystem exists in this codebase at all. **"Contact" must be descoped from this issue**
— it is a new feature requiring a messaging or contact-reveal mechanism that doesn't exist, not a
restyle of something already present.

### 5. Gallery/lightbox component — exists, but is single-image click-to-open, not an in-card carousel

`AttachmentGalleryService.openMediaLightbox(EntityRef)` (used from
`AdvertisementCardView.createThumbnail()`) opens `CardMediaLightbox` on thumbnail click — a
full lightbox with next/prev nav (`.card-lightbox__nav`, confirmed via
`playwright/e2e/_flows/advertisement.flow.js` selectors), but the **card itself** shows only a
single thumbnail image/video with a small `advertisement-thumbnail-badge` count overlay
(camera icon + count) when `mediaCount > 1` — there is no in-card image carousel/swiper. The spec
term "existing gallery component" is real (`AttachmentGallery`/`CardMediaLightbox`/
`AttachmentGalleryService`, all in `marketplace-app/.../ui/views/components/attachment/`) and
should be reused exactly as today (click thumbnail → lightbox) — a redesign should not invent a
new in-card multi-image carousel; the spec never actually asked for one, it only said "reuse the
existing gallery component if one exists," which this satisfies as-is.

### 6. The 5 named CSS custom properties all exist verbatim, plus many more already-named tokens

Confirmed line-for-line in `marketplace-app/src/main/frontend/themes/my-app/styles.css`
(design-token block, `:root`, lines 43-124, per ADR-038):
- `--app-border-default: #e2e8f0;` (line 53) — exists.
- `--app-surface-page: #f5f7fa;` (line 56) — exists.
- `--app-surface-muted: #f1f5f9;` (line 57) — exists.
- `--app-surface-white: #ffffff;` (line 59) — exists.
- `--app-accent-primary: #3b82f6;` (line 64) — exists.

The spec's 5 named tokens are **not a guess that happened to be right by coincidence** — this
whole token system was deliberately built (improvement-037/039, ADR-038) specifically so hex
values are never re-hardcoded, and it already has everything a redesign needs: `--app-text-*`
(primary/secondary/tertiary/muted), `--app-border-*` (default/subtle/accent),
`--app-accent-primary-*` (7 variants for hover/bg/glow states), and per-`AdKind` semantic colors
already wired (see #7 below). **No new token names need to be invented for this redesign.**

### 7. `AdKind` (Offer/Request/Product) coloring already exists — pure CSS, no enum-level color field

`AdKind` itself (`platform-commons/.../advertisement/model/AdKind.java`) is a bare 3-value enum
with zero color/metadata fields. All coloring is CSS-only, in `advertisement-card.css`:
`.advertisement-ad-kind-badge--offer` (green, reusing `--app-status-success-badge-*`),
`--request` (blue, reusing `--app-accent-primary-bg`/`--app-status-info-text`), `--product`
(amber, reusing `--app-status-entity-advertisement-*`) — plus a left-border accent per kind on
`.advertisement-card--offer/--request/--product`. This matches ADR-066 as described in
`advertisement-spring-boot-starter/CLAUDE.md`. **This part of the spec ("existing semantic
colors") is fully satisfiable by reusing these exact 3 modifier classes — nothing new to invent.**

### 8. Relative time display does NOT exist — spec's "if available" claim is false, so omit it

`AdvertisementCardMetaPanel` calls `TimeZoneUtil.formatInstantHuman()`
(`marketplace-app/.../ui/query/utils/TimeZoneUtil.java`), which formats via
`DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")` — an absolute, locale-aware timestamp, not a
relative one ("2h ago"/"3 days ago"). No relative-time formatter exists anywhere in this codebase.
Per the spec's own instruction ("relative time if available") — it is not available, so the
redesign should keep the existing absolute-timestamp format rather than inventing a new relative-
time utility as an unplanned side quest.

### 9. Verification/rating/review concepts — confirmed absent

`grep -rli "rating\|review\|verif" platform-commons/src/main/java marketplace-app/src/main/java
advertisement-spring-boot-starter/src/main/java` returns zero hits. Per the spec's own explicit
instruction, these must not be invented. Confirmed clean — nothing to accidentally reuse or
half-build.

### 10. Share action — exists exactly as ADR-060 describes

`ShareActionButton` + `ShareUtil.share()` (`marketplace-app/.../ui/views/utils/ShareUtil.java`)
already implement the Web Share API with a clipboard-copy fallback
(`navigator.share(...)` → falls back to `navigator.clipboard.writeText(...)` → success
notification), wired into the card's action row today (`createShareButton()`). Nothing to add;
reuse as-is.

### 11. City display (ADR-065) — confirmed as a plain text line today, not a chip, on the card

`AdvertisementCardView.createCityLine()` renders `ADVERTISEMENT_CARD_CITY` label + `ad.getCityName()`
as a `Span.advertisement-city` text line (same shape as the categories line) — a chip variant
(`.advertisement-city-chip`) exists only in the view overlay, not on the card itself. A WHERE
section in a redesigned card can either keep the plain text line or promote it to the same chip
style already defined in CSS for the overlay — both options reuse existing CSS, no new component
needed.

### 12. Deep-link routing — confirmed `ads/{id}` via manual browser History API, not a Vaadin `@Route`

`AdvertisementOverlay` pushes `ads/{id}` via `UI.getCurrent().getPage().getHistory().pushState(...)`
(`AD_PATH_PREFIX = "ads/"`) on `openForView()`, and listens for back/forward navigation via
`setHistoryStateChangeHandler()` to close the overlay when the path no longer starts with `ads/`.
This is a hand-rolled, non-Vaadin-router deep-link mechanism (separate from
`AdvertisementDeepLinkView`, which handles the initial page load for a shared `/ads/:id` URL). A
redesign must not touch this mechanism — cards must keep calling
`overlay.openForView(ad, onUpdated, onClosed)` exactly as today; only the visual shell around the
click target changes.

### 13. Owner-only actions (Edit/Delete) — exist today as always-rendered-but-opacity-0-until-hover buttons

`createEditButton()`/`createDeleteButton()` render unconditionally but `.setVisible(canOperate)`
gates them (`AccessEvaluator.canOperate(ad.getOwnerUserId())`); CSS (`advertisement-card.css`
lines 147-158) keeps them `opacity: 0` until `:hover`/`:focus-visible` on the card. A redesign
that moves these into a "ghost/low-emphasis" style or an overflow menu (per the spec) is a real,
in-scope visual change — but the *visibility/permission logic* (`canOperate`) must not change.

### 14. `EmptyStateView` — confirmed, simple, three-part (icon/title/hint), reused across the app

`marketplace-app/.../ui/views/components/EmptyStateView.java` is a generic `VerticalLayout`
(icon + title `Span` + hint `Span`), styled via `.empty-state`/`.empty-state-icon`/
`.empty-state-title`/`.empty-state-hint` in `advertisements-view.css`. Confirmed a shared
component (not advertisement-specific) — any visual modernization here should stay in the shared
CSS classes so other views (Users, Timeline, Taxon) that likely reuse the same classes aren't
silently left inconsistent. (Not verified in this pass which other views instantiate it — flag
for the implementer to check before changing shared class names.)

### 15. `AdvertisementQueryBlock`/`PaginationBar` — confirmed as described, no surprises

`AdvertisementQueryBlock` (`query/AdvertisementQueryBlock.java`) builds filter rows (title, created
range, updated range, categories multi-select, city combo, ad-kind multi-select) via the shared
`QueryBlock`/`filterRow()` infrastructure — entirely reusable as-is; the spec's ask ("preserve
existing QueryBlock functionality fully, desktop compact side panel or mobile collapsible pattern")
is a **layout/positioning change only**, no logic changes needed. `PaginationBar` is a
`HorizontalLayout` with first/prev/next/last buttons + page indicator + result count — a
`@Scope("prototype")` component wired into `AdvertisementsView` via `paginationBar.setPageChangeListener(...)`.
Both are pure UI‑layer, no port/hook boundary crossed — safe to restyle/reposition freely.

### 16. Existing Playwright coverage that a redesign would need to preserve or update

`playwright/e2e/04-marketplace-advertisement-flow.spec.js` +
`playwright/e2e/_flows/advertisement.flow.js` assert directly against these CSS selectors/
structure, all of which a redesign must either keep stable or update deliberately (not
accidentally break):
- `.advertisement-card`, `.advertisement-title` (used together via `cardByTitle()` locator helper)
- `.advertisement-thumbnail-wrapper`, `.advertisement-thumbnail-badge` (media count badge presence/
  absence, click-to-open-lightbox)
- `.advertisement-ad-kind-badge` (text + computed-color assertions — a comment in the flow file
  explicitly says "Expected computed colors per AdKind — must match advertisement-card.css's badge
  colors exactly", so badge class names/colors are load-bearing test contracts, not incidental)
- `.card-lightbox__content`, `.card-lightbox__nav`, `.card-lightbox__close` (lightbox, unaffected
  by a card redesign unless the trigger element changes)
- `[data-testid="advertisement-overlay-field-*"]` (overlay form fields, unaffected by a card-only
  redesign)
- `data-ad-id` attribute on the card root (`AdvertisementCardView.configure()`) — used by
  `AdvertisementsView.updateCardInPlace()` to find and swap a card after edit; this is a
  **functional** dependency, not just a test one — must be preserved on whatever the new root
  element is.

## Priority rationale

No live bug or usability failure was discovered in the current card during this research pass —
`AdvertisementCardView` already works, is already card-based (not a grid), already has hover-
reveal actions, entrance animation, and a lightbox. This is a visual/UX polish and information-
hierarchy initiative on top of a functioning feature, matching the 🔵 tier definition ("larger
tech-debt, no live bug, bigger effort or needs a design decision") rather than 🟡 ("real bug or
high-value fix"). It is comparable in shape to the existing `improvement-096` (responsive/mobile
adaptation, its own phased program) already sitting in the "Nice to have" tier.

## Suggested fix (options-oriented — not a locked-in plan)

This section intentionally does not pick final answers for every open question — see "Open design
questions" below for what still needs a decision from the user before implementation starts.

### What must be descoped or explicitly flagged as "new feature, not restyle"

- **`ActorProfile.kind` badge** — cannot be built today (see #2). Options: (a) ship this redesign
  now without any provider-kind badge, add it as a fast-follow once improvement-124 ships; (b)
  sequence this whole issue after improvement-124 so the WHO header can be built once, correctly,
  with the real badge from day one. See "Open judgment call" below.
- **Avatar/photo** — no data field exists (see #3). Cheapest in-scope option: a generated
  initials-in-a-circle bubble (pure CSS/derived from `createdByUserName`, no new backend field,
  no new DTO field) — matches the spirit of "avatar or initials" from the spec without inventing a
  photo-upload feature. A real photo avatar is out of scope for this issue (no field exists to
  read from — would need its own DTO/schema change, arguably belongs with improvement-124's
  provider-profile work instead, which already touches actor-facing display data).
- **"Contact" action** — does not exist (see #4), full descope from this issue. No messaging
  subsystem exists anywhere in the codebase to hook into.
- **Relative time** — does not exist (see #8); keep the existing absolute timestamp format,
  do not add a relative-time utility as a side quest inside this issue.
- **Rating/verification/review badges** — confirmed absent (see #9); do not add, per the spec's
  own instruction and this codebase's current state.

### What is a pure visual/layout change, safely reusing existing infrastructure

- Card root: keep `AdvertisementCardView` as the `Configurable` prototype component it already is;
  the shape change (horizontal thumbnail-left layout → vertical stacked layout with a large top
  media block) is CSS + minor Java layout restructuring in `configure()`/`createContent()` —
  no new Spring beans, no new Ports/Hooks, no `AdvertisementInfoDto` field changes needed for
  anything already covered above (WHO/WHAT/WHERE/HOW all map onto existing DTO fields:
  `createdByUserName`/`createdByUserEmail`, `adKind`, `title`, `description`, `categoryNames`,
  `cityName`, `mediaUrl`/`mediaContentType`/`mediaCount`).
- Container: `AdvertisementsView.buildAdvertisementContainer()` (`FlexLayout`, wrap) and
  `advertisements-content-wrapper`'s `max-width: 900px` are the two places to change for the
  680-720px centered-feed width target — a CSS-only change (`max-width` value) plus removing the
  wrap-multiple-per-row behavior if the target is a single-column feed (today's `FlexWrap.WRAP`
  allows multiple cards per row at wide viewports; a LinkedIn-style feed is strictly single-column,
  which is a small layout mode change, not a rebuild).
- Design tokens: reuse `--app-surface-page`/`--app-surface-white`/`--app-surface-muted`/
  `--app-accent-primary`/`--app-border-default` exactly as named (all confirmed to exist, #6) plus
  the already-established `--app-text-*`/`--app-border-*`/`--app-accent-primary-*` families — no
  new token names needed anywhere in this redesign.
- `AdKind` badge colors: reuse the 3 existing modifier classes verbatim (#7) — this satisfies the
  spec's "existing semantic colors" requirement with zero new CSS values.
- Media block: reuse `AttachmentGalleryService.openMediaLightbox()` exactly as today (#5) for the
  click-to-expand behavior; the visual change is purely to the thumbnail wrapper's size/aspect
  ratio/`object-fit` in `advertisement-card.css` (currently a small fixed 200x150px box —
  a large top-of-card media block up to ~450px height is a CSS dimension change plus adjusting
  `createThumbnail()`'s wrapper structure if a full-width block is needed instead of a fixed
  small box).
- Share/Edit/Delete actions: reuse `ShareActionButton`/`EditActionButton`/`DeleteActionButton`
  and the existing `canOperate` visibility gate verbatim (#10, #13) — only the visual treatment
  (ghost buttons, possible overflow-menu grouping) changes.
- Empty state: restyle `.empty-state`/`.empty-state-icon`/`.empty-state-title`/`.empty-state-hint`
  in place (#14) — check other consumers of `EmptyStateView` before renaming any of these shared
  classes.
- Filters/pagination: reposition `AdvertisementQueryBlock`/`PaginationBar` visually (side panel or
  collapsible, per breakpoint) without touching their internal logic (#15).
- Deep-link/routing/`data-ad-id`: must not change at all (#12) — any new card root element must
  carry `data-ad-id` for `updateCardInPlace()` to keep working, and the click handler must keep
  calling `overlay.openForView(...)` unchanged.

### Phased proposal (grounded in real files, sequencing left to the user to confirm)

1. **Design-only pass**: mock up the new card shape (stacked WHO/WHAT/WHERE/media/description/HOW)
   using only the tokens and structure confirmed above, resolve the "Open design questions" below,
   get sign-off before touching code — this is a visual language change worth seeing before
   building.
2. **Card + container CSS/layout** (`AdvertisementCardView.java`, `AdvertisementCardMetaPanel.java`,
   `advertisement-card.css`, `advertisements-view.css`): restructure to vertical hierarchy, add
   initials-avatar bubble, resize media block, adjust description clamp (2-line → 3-line if
   changed), reposition/redesign action row (ghost style, optional overflow menu). No DTO/service/
   port changes required for this phase per the mapping above.
3. **Container width + responsive breakpoints**: `advertisements-content-wrapper` max-width change,
   single-column feed mode, `@media` rules at 1440/1024/768/390 — likely coordinates with
   `improvement-096` (responsive/mobile adaptation, already an open backlog item covering the same
   general "2 `@media` queries across 26 theme CSS files" territory) rather than duplicating that
   program's own work independently.
4. **Filter/pagination visual integration**: desktop side-panel or secondary placement for
   `AdvertisementQueryBlock`, mobile collapsible pattern, `PaginationBar` visual integration into
   the new feed shell.
5. **Empty state modernization**: restyle shared `EmptyStateView` classes (check other consumers
   first).
6. **Playwright**: update `04-marketplace-advertisement-flow.spec.js`/`_flows/advertisement.flow.js`
   selectors/assertions for whatever structural changes phase 2-3 introduce (badge classes, card
   root structure, `data-ad-id` placement) — per item #16, the ad-kind badge color assertions and
   `data-ad-id` swap logic are the two highest-risk breakage points to verify explicitly, not just
   incidentally re-run.

## Open design questions (user must decide — not silently picked here)

- **Sequencing vs improvement-124**: build the WHO header now without a provider-kind badge (fast-
  follow later), or wait until improvement-124 ships so the header is built once with the real
  badge? Judgment call, not decided here — improvement-124 is currently the sole top-priority
  backlog item, so waiting may cost real calendar time; building twice costs rework.
- **Single-column vs current multi-column wrap**: the spec's 680-720px centered feed implies
  strictly single-column: is that the desired final state at all desktop widths, or should wide
  viewports keep some multi-column benefit (today's `FlexWrap.WRAP` behavior)?
- **Avatar**: initials-bubble now (cheap, no schema change) vs. wait for a real photo field (likely
  arrives with improvement-124's `ActorProfile`, if it ever adds one — not confirmed it will)?
- **Media block sizing**: exact max-height (spec says ~450px desktop) and `object-fit` policy
  (spec says "contain for detail-preserving work photos, cover for uniform thumbnails" — this
  codebase has no per-ad way to distinguish those two cases today; a single fixed `object-fit`
  policy needs to be chosen, or a new classification field would need to be invented, which is out
  of scope per "no invented data").
- **Action row shape**: ghost buttons inline vs. overflow ("⋮") menu for owner actions — the spec
  offers both, current code has 3 always-rendered buttons (2 gated by ownership) with no overflow
  menu component in this codebase yet (would need to build one, or use an existing Vaadin
  `MenuBar`/`ContextMenu` primitive — not currently used anywhere in this domain, confirm before
  introducing a new dependency pattern).
- **Filter panel placement**: desktop compact side panel (structural change to `AdvertisementsView`'s
  layout — currently everything stacks vertically in one `VerticalLayout`) vs. keep the current
  top-of-page placement just restyled smaller/secondary. Side panel is a bigger structural change
  than the spec's wording ("visually secondary") strictly requires.
- **Breakpoint ownership overlap with improvement-096**: should responsive breakpoint work for this
  redesign be split out into (or merged with) improvement-096's existing phased program, or kept
  self-contained in this issue? Avoids two issues touching the same `@media` query surface
  independently.

## Related

- [improvement-124](improvement-124-provider-profile.md) — introduces `ActorProfile`/`ProviderKind`
  (would supply the WHO-header provider badge this issue's spec assumed already exists); currently
  the sole "Top priority" backlog item, unblocked.
- [improvement-096](issues/improvement-096-responsive-mobile-adaptation-pass.md) — existing open
  responsive/mobile-adaptation program covering similar `@media`-query territory; check for overlap
  before finalizing this issue's phase 3.
- `marketplace-app/DECISIONS.md` ADR-038 (design-token naming/rationale), ADR-060 (Share button /
  Web Share API), ADR-066 (AdKind semantic coloring, referenced from
  `advertisement-spring-boot-starter/CLAUDE.md`), ADR-065 (city facet reuse via `TaxonType.CITY`).
- `playwright/e2e/04-marketplace-advertisement-flow.spec.js`,
  `playwright/e2e/_flows/advertisement.flow.js` — existing coverage to preserve/update.
