# improvement-026: Raw `new Button(...)` duplicates scattered across auxiliary UI instead of reusing Ui*Button wrappers

**Type:** improvement — consistency/tech-debt, found while reviewing improvement-025 (leaf UI
components refactor) and asked to extend the audit specifically for button duplication
**Module:** marketplace-app
**Priority:** medium — two of the findings are actual visible UX bugs (unstyled buttons in the
header and in lightboxes), not just code duplication; the rest are pure consistency debt
**When:** independent, no blockers — recommended in phased batches, same reasoning as
improvement-025 (wide, cross-cutting consumer footprint)

## Problem

The reusable button wrapper components — `UiPrimaryButton` (`LUMO_PRIMARY`), `UiTertiaryButton`
(`LUMO_TERTIARY`), `UiIconButton` (`LUMO_TERTIARY + LUMO_ICON`), `DeleteActionButton`/
`EditActionButton` (icon + tooltip + click wiring via `BaseActionButton`) — are used consistently
across the four main domain overlays (Advertisement, User, Taxon, Settings) and their grids. But a
second, uncoordinated population of hand-built `new Button(...)` calls exists in auxiliary/
infrastructural UI, verified by direct file inspection (not grep-only):

| File | Lines | What it builds | Theme variant applied? |
|---|---|---|---|
| `ui/views/main/header/HeaderBar.java` | 85–111 | 4 buttons: Settings/Login/SignUp/Logout — text+icon | **none at all** — plain default Vaadin Button |
| `ui/views/components/audit/AuditActivityRowRenderer.java` | 78–84 | "Restore" button — text, tertiary+small | `LUMO_TERTIARY, LUMO_SMALL` |
| `ui/views/components/PaginationBar.java` | 52–55, 83–86 | first/prev/next/last icon buttons ×4 | `LUMO_TERTIARY, LUMO_ICON` (exact `UiIconButton` variants, hand-repeated 4×) |
| `ui/views/components/attachment/AttachmentThumbnail.java` | 55–63 | delete icon button on a thumbnail | `LUMO_TERTIARY, LUMO_ERROR, LUMO_ICON` |
| `ui/views/components/attachment/CardMediaLightbox.java` | 34–36 | close icon button | **none at all** |
| `ui/views/components/attachment/AttachmentLightbox.java` | 22–24 | close icon button — near-duplicate of the one above | **none at all** |
| `ui/views/components/attachment/CardLightboxViewer.java` | 40–43 | prev/next nav icon buttons | **none at all** |
| `ui/views/components/attachment/AttachmentGallery.java` | 303–309 | "add video url" icon button | **none at all** |
| `ui/query/elements/fields/UserPickerField.java` | 54, 59, 115–117 | clear / open / search icon buttons | 2 of 3 have **none**; the third has `LUMO_ICON, LUMO_TERTIARY_INLINE` |
| `ui/views/services/NotificationService.java` | 82–83 | notification close button | `LUMO_TERTIARY_INLINE` (borderline case, see below) |

Concretely, this is two overlapping problems:

1. **Visible UX inconsistency, not just code duplication.** `HeaderBar`'s four auth buttons
   (Settings/Login/SignUp/Logout) render as plain unstyled Vaadin buttons in the single most
   visible, always-on-screen UI element of the app, while every Save/Discard/Edit button in every
   overlay uses `UiPrimaryButton`/`UiTertiaryButton`. Five more spots (`CardMediaLightbox`,
   `AttachmentLightbox`, `CardLightboxViewer`, `AttachmentGallery`'s add-video button, two of
   `UserPickerField`'s three buttons) apply **no theme variant whatsoever** — they are not
   "styled differently," they are unstyled by omission, which reads as a rendering bug to a user
   even though the code technically "works."
2. **True duplication with no visual bug.** `PaginationBar`'s four nav buttons and
   `AttachmentThumbnail`'s delete button reproduce theme-variant combinations that already exist,
   verbatim, in `UiIconButton`/`DeleteActionButton` — just copy-pasted instead of reused.
   `CardMediaLightbox` and `AttachmentLightbox` additionally duplicate *each other* (two
   near-identical close-button implementations, same CSS class `card-lightbox__close`, in two
   different lightbox components).

## Suggested fix

Same target shape as improvement-025: replace each hand-built `Button` with the equivalent
`Ui*Button` wrapper, wiring click/state behavior the same way call sites already do for the
existing consistent usages (`AdvertisementViewOverlayModeHandler`, `UserGridConfigurator`, etc. —
build via factory/constructor, then `.addClickListener(...)` separately).

### Gaps in the wrappers to close first (found during this investigation)

- `UiTertiaryButton` has no "small" variant. `AuditActivityRowRenderer`'s restore button needs
  `LUMO_TERTIARY + LUMO_SMALL`. **Do not add a `small` flag to `UiTertiaryButton` itself** — the
  existing, already-used pattern for "wrapper + one extra variant" is `ConfirmActionDialog.java`
  layering `LUMO_ERROR` onto a built `UiPrimaryButton` via `confirmButton.addThemeVariants(...)`
  after `.build(...)`. Reuse that exact technique:
  `restoreButton.addThemeVariants(ButtonVariant.LUMO_SMALL)` after building it from
  `UiTertiaryButton`'s factory — no wrapper class change needed.
- `UiIconButton`'s `Parameters` currently take an icon + label/tooltip (`I18nKey`) at build time;
  `PaginationBar`'s four nav buttons and the lightbox nav/close buttons need click wiring
  attached *after* construction and, for `PaginationBar`, an enabled/disabled state toggled later
  (`firstButton.setEnabled(...)`) — already how every existing `UiIconButton` consumer works
  (build, then wire), so no interface change is expected here either. Verify at implementation
  time that `UiIconButton` doesn't require an `I18nKey` where a lightbox close button has no
  natural translated tooltip text — if so, decide per call site whether to add a real tooltip
  (arguably a UX improvement, e.g. "Close" on a lightbox) rather than force an empty key.

### Do NOT touch as part of this issue

- `query/elements/action/QueryActionButton.java` — this is a legitimate sibling wrapper (its own
  `Configurable` component, SVG icons, `ButtonVariant` passed as an explicit parameter), used only
  by `QueryActionBlock` for the query bar's Apply/Clear buttons. It is *architecturally
  inconsistent* with the `Ui*Button` family (variant-as-parameter vs. variant-hardcoded-per-class)
  but that is a design-unification question, not a raw-`Button` duplication — track separately if
  ever revisited, do not fold into this issue's scope.
- `dialogs/ConfirmActionDialog.java` — already correct; it is the reference pattern this issue's
  fixes should copy (wrapper + `.addThemeVariants(...)` layered on top for the one-off case).
- `services/NotificationService.java` — borderline. It is a plain `@Service`, not a
  `@SpringComponent` Vaadin bean, and its close button uses `new Icon("lumo", "cross")` (a raw
  Lumo font glyph) rather than `VaadinIcon.*.create()`, which every `Ui*Button` consumer uses. Its
  `LUMO_TERTIARY_INLINE` variant is close to but not identical to `UiIconButton`'s
  `LUMO_TERTIARY`. Converting it would require this service to inject
  `UiComponentFactory<UiIconButton>` (architecturally fine) and would change the icon rendering
  from a Lumo glyph to a `VaadinIcon` — a small visual change, not obviously a pure refactor.
  Decide explicitly whether to include it or leave it; do not silently skip or silently convert.
- Advertisement/User/Taxon/Settings overlays and their grids (7 files, verified zero raw `Button`
  usage) — already fully consistent, nothing to do.
- `AttachmentUploadButton` (used inside `AttachmentGallery`) — this wraps Vaadin's `Upload`
  component, a materially different widget (drag-and-drop file receiver), not a `Button` at all;
  not in scope.

## Critical risk — header buttons carry test-selector CSS classes

`HeaderBar`'s four buttons have CSS classes (`header-settings-button`, `header-login-button`,
`header-signup-button`, `header-logout-button`) that Playwright selects on directly and constantly
throughout the e2e suite (`page.locator('.header-settings-button')` appears in nearly every spec
file's login-verification step — see `e2e/_flows/auth.flow.js`). **These class names must be
preserved exactly** when converting to `UiPrimaryButton`/whatever wrapper is chosen (the wrapper
components support `addClassName(...)` same as raw `Button`) — this is the single highest-risk
spot in the whole issue, since a missed or renamed class would silently break nearly every e2e
test's login-success assertion, not just one.

## Suggested phased execution (mirrors improvement-025's reasoning)

1. **Batch 1 — `HeaderBar` (highest visibility, highest test-selector risk):** convert all four
   buttons to `UiPrimaryButton`, preserving every existing CSS class. Run full e2e immediately
   after — this batch alone touches the assertion nearly every other spec file depends on.
2. **Batch 2 — `PaginationBar`:** convert 4 nav buttons to `UiIconButton`; low visual risk (icons
   unchanged), but touches 3 consumer views (`AdvertisementsView`, `UserView`, `TimelineView`
   indirectly via the shared bar).
3. **Batch 3 — attachment lightboxes/gallery:** `CardMediaLightbox`, `AttachmentLightbox`,
   `CardLightboxViewer`, `AttachmentGallery`'s add-button, `AttachmentThumbnail`'s delete button.
   Bundle these together since they're all in one package and several are already near-duplicates
   of each other — a good opportunity to also de-duplicate `CardMediaLightbox` vs
   `AttachmentLightbox`'s close button while converting both.
4. **Batch 4 — `audit/AuditActivityRowRenderer` restore button + `query/elements/fields/
   UserPickerField`'s three buttons:** smallest, most contained batch, do last.
5. **Decide `NotificationService` explicitly** (in or out of scope) before closing the issue —
   do not let it fall through silently.

## Steps (per batch)

1. Convert each hand-built `Button` to the matching `Ui*Button`, preserving every CSS class and
   attribute (`data-testid` if present, `title`/tooltip attributes) exactly.
2. Wire click listeners / enabled-state toggles after construction, same as every existing correct
   consumer already does.
3. Run the full reactor build + full Playwright e2e suite (`bash scripts/playwright.sh e2e --full
   --ux`) after each batch — do not batch multiple of the above together to save time, per the
   lesson already recorded twice this session (improvement-020, improvement-011) that a
   looks-safe UI/config change deployed without full verification has broken the app outright.
4. Update `marketplace-app/DECISIONS.md` with one entry (can cover the whole issue in one ADR
   once all batches land, unlike improvement-025 which may warrant one per batch given it touches
   more architecturally distinct classes) recording: which raw-Button spots were converted, which
   were explicitly excluded and why (`QueryActionButton`, `NotificationService` decision,
   `AttachmentUploadButton`).

## Required test coverage before merging

Full e2e suite must stay green after every batch. Batch 1 (`HeaderBar`) in particular exercises
the exact assertion (`'.header-settings-button'` visibility) used as the login-success check in
nearly every spec file — treat any failure there as a full-suite-blocking regression, not a
one-test flake.

## Related

- `features/issues/improvement-025-leaf-ui-components-plain-classes.md` — the sibling refactor
  this investigation was originally spawned from; that issue converts the *wrapper components
  themselves* from Spring beans to plain classes, this issue is about *getting more call sites to
  use the wrappers at all*. The two can land in either order, but converting a wrapper (025) before
  fixing its remaining raw-Button call sites (026) means those call sites migrate straight to the
  plain-class constructor form instead of via `UiComponentFactory`/`Configurable`.
- `marketplace-app/CLAUDE.md` — button/component naming conventions this issue restores adherence
  to.
