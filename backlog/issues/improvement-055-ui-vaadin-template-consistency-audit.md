# improvement-055: UI/Vaadin template consistency audit — findings only, no agreed fix yet

**Type:** design discussion — captures audit findings for later human discussion, not an agreed
implementation. Same shape as [improvement-053](improvement-053-advertisement-listing-expiry-archive-strategy.md)
("design discussion, no agreed fix yet").
**Module:** `marketplace-app` (`ui/` tree broadly — spans `Configurable`/overlay/view/form-handler
templates, not any single component family).
**Priority:** low-medium — no functional bug; this is consistency/maintainability debt across the
UI component templates. Useful to resolve deliberately before further UI expansion, not urgent.
**When:** trigger-based — before the next large UI-pattern rollout (e.g. a new domain's Overlay/View
pair), or whenever a dedicated UI consistency pass is scheduled. Do not act on any single finding
below in isolation without first deciding, as a group, which variant becomes the standard.

## Problem

Raised 2026-07-15: the codebase has accumulated several different structural "templates" for
building UI components over Vaadin (`Configurable<T,P>` prototype beans, the Overlay pattern, the
View pattern, the Form Handler pattern, component suffix conventions, CSS naming), each documented
to some degree in `marketplace-app/CLAUDE.md` / `.claude/rules.md`. A full-tree audit (read-only,
no code changes) was run to check how consistently each documented rule is actually followed in
real code, and to surface duplicated widgets that solve the same problem independently. Goal: pick
the optimal variant of each template later and sync the whole UI to it — this issue is the raw
material for that discussion, not the decision itself.

## Findings

### 1. `Configurable<T,P>` prototype-bean pattern
**34/34 `Configurable` classes use `@Value @lombok.Builder` for `Parameters` — 0 use a plain
record.** The documented "record for ≤4 simple fields, no callback" rule in `marketplace-app/CLAUDE.md`
is never followed in practice — and the code example directly under that rule in the doc itself
already uses `@Value @Builder`, so the prose rule contradicts its own example.

**Superseded by [improvement-025](improvement-025-leaf-ui-components-plain-classes.md) — do not
fix via a doc update.** That issue already resolves this at the architectural level for most of
the affected classes: ~17 leaf widgets (`UiPrimaryButton`, `UiIconButton`, `UiTextField`, etc.)
are slated to lose `Configurable`/`@SpringComponent` entirely and become plain Java classes with a
resolved-value constructor — at which point "record vs `@Builder`" is moot for them, since they
won't be `Configurable` at all. Only update the `CLAUDE.md` rule directly if/when 025 is
explicitly deferred or rejected; until then this finding is a symptom 025 already has a fix for,
not a separate doc-hygiene task.

- 1-field cases that per-rule should be records but use `@Value @Builder`:
  `ui/query/elements/SvgIcon.java:17-21`, `ui/query/elements/fields/QueryTextField.java:25-29`,
  `QueryNumberField.java:24-29`, `ui/views/components/overlay/fields/OverlayBreadcrumbBackButton.java:22-24`,
  `ui/views/components/buttons/UiTertiaryButton.java:22-24`
- 2-4 field, no-callback cases, same deviation: `UiPrimaryButton.java:23-25`,
  `UiIconButton.java:23-25`, `UiTextField.java:21-23`, `UiTextArea.java:21-23`,
  `UiComboBox.java:23-25`, `UiEmailField.java:21-23`, `UiPasswordField.java:21-23`,
  `EmptyStateView.java:23-25`, `AdvertisementCardMetaPanel.java:20-22`
- Callback-bearing `Parameters` correctly warrant `@Builder` (compliant):
  `DeleteActionButton.java:18-20`, `EditActionButton.java:18-20`, `ConfirmActionDialog.java:31-33`,
  `UserGridConfigurator.java:33-35`
- Outlier: `AttachmentLightbox.java:24,29-32` implements `Configurable` but not
  `Initialization<T>` (unlike all 33 siblings), has no `init()`, and uses bare `@Value` (no
  `@Builder`)
- `configureFor*()` exception correctly applied: `AttachmentGallery.java:107,114,130`
- Plain-setter exception correctly applied: `PaginationBar.java:85,91`, `QuillEditor.java:21,30`
- Borderline: `OverlayLayout.java:46,51,56` has 3 setters (over the documented "1-2" threshold)
  yet stays plain-setter, not `Configurable`
- **New since improvement-025 was filed**: `AttachmentLightbox`, `CardLightboxViewer`, and
  `AttachmentThumbnail` were promoted from plain classes to `@SpringComponent` beans during
  `improvement-026` Batch 3 (2026-07-15), specifically to inject `UiComponentFactory<UiIconButton>`
  per the codebase-wide "constructors take beans, not pre-built widgets" rule. None of the three
  appear in 025's file lists (they weren't beans yet when 025 was written). If 025 executes and
  `UiIconButton` becomes a plain class, the reason these three needed to be beans evaporates —
  they become new candidates for reverting to plain classes. Flag for re-evaluation when 025 is
  picked up, not fixed here.

### 2. Overlay pattern
**4/4 concrete overlays compliant on session/switchTo/handler-reset. The doc's `afterSave()`
method name doesn't exist anywhere — the real template method is `proceed()`.**

- `OverlaySession` present/compliant: `AdvertisementOverlay.java:27-36`, `TaxonOverlay.java:31-40`,
  `UserOverlay.java:27-36`; `SettingsOverlay` has none (single-mode, reasonably out of scope)
- `launchSession()` confined to initial opens only (4 hits, all in
  `openForView/openForEdit/openForCreate/openSettings`)
- `currentFormHandler = null` as first line of `switchTo()`: `AdvertisementOverlay.java:99`,
  `TaxonOverlay.java:104`, `UserOverlay.java:90` — compliant
- Naming gap: no class has `afterSave()`; actual flow is
  `AbstractEntityOverlay.handleSave()` (`ui/views/components/overlay/AbstractEntityOverlay.java:33-52`)
  calling `currentFormHandler.afterSave(true)` (on the form handler) then `proceed()` (on the
  overlay), implemented per-overlay at `AdvertisementOverlay.java:56-65`, `TaxonOverlay.java:61-70`,
  `UserOverlay.java:56-61`

### 3. View pattern
**`init()` visibility: 5/6 compliant, 1 violation. `refresh()` two-catch-block shape: only 1/4
fully matches.**

- Violation: `ui/views/main/MainView.java:60-61` — `public void init()`, not `protected`
- `refresh()` shape: `UserView.java:97-122` fully compliant (two catch blocks + notification +
  finally). `AdvertisementsView.java:138-158` — single generic catch, no notification on failure
  (silent blank grid) — **already tracked**, see
  [improvement-010](improvement-010-advertisements-view-refresh-error-notification.md).
  `TaxonManagementView.java:57-91` — single generic catch but does notify (partial).
  `TimelineView.java:97-109` — single generic catch, no notification — **new, not yet tracked**;
  same fix shape as improvement-010, worth folding into that issue's scope rather than filing
  separately.
- No `refreshGrid`/`refreshData` naming variants found — fully compliant on naming
- `ReferenceDataView.java` has no `refresh()` (pure tab container, out of scope)

### 4. Form Handler pattern
**4/4 fully compliant** — `buildTabbedContent()` used consistently
(`AdvertisementFormOverlayModeHandler.java:205`, `TaxonFormOverlayModeHandler.java:221`,
`UserFormOverlayModeHandler.java:139`, `SettingsFormModeHandler.java`), `buildBinder(dto)` always
separate from `activate()`. The doc's example call signature for `buildTabbedContent()` (5 args)
doesn't match the real 4-arg method — stale doc snippet, not a code issue.

- Out-of-template note: `ui/views/main/header/dialogs/SignUpDialog.java:49` builds a raw
  `BeanValidationBinder` instead of the shared `OverlayFormBinder` used by all 4 real form
  handlers (architecturally outside the Overlay pattern since it's a plain Dialog, but the only
  binder call site bypassing the shared wrapper)

### 5. Component suffix/package conventions
- `*Projection` (doc says lives in `repository/*`) — **zero matches, package doesn't exist at
  all**; doc is stale
- `*Util` — 4/4 correctly static + `@NoArgsConstructor(PRIVATE)`, but `ui/query/utils/` (2 of 4
  instances) isn't listed in `CLAUDE.md`'s package structure (only `ui/views/utils/` is documented)
- `*Panel`, `*Binding`, `*Config`, `*Overlay` — all compliant with stated locations
- Undocumented real packages: `ui/mappers/` (5 classes), `ui/views/rules/` (`I18nParams.java` —
  despite being named in the I18n doc section, its package is never stated)
- Undocumented but internally-consistent suffix family: `*Renderer` (4 classes in
  `ui/views/components/audit/`) — not in `CLAUDE.md`'s suffix list at all

### 6. Duplicated near-identical widgets
- **Confirm dialogs**: consolidated — `ConfirmActionDialog` used in 5 files; only 1 raw
  `new Dialog(` bypass found (`UserPickerField.java:88`, different purpose — user picker, not
  confirm/delete)
- **Notifications**: `NotificationService` used in 8 files; 1 direct bypass —
  `AttachmentGallery.java:362` calls `Notification.show(...)` directly
- **Date/time formatting**: two independent utilities. `InstantFormatter`/`VaadinInstantFormatter`
  (documented, DI-based) used by only 2 files (`AuditActivityRowRenderer`, `AuditTimelineRowRenderer`).
  The undocumented static `TimeZoneUtil` (`ui/query/utils/TimeZoneUtil.java`, owns the actual
  `DateTimeFormatter` calls at lines 38, 49) is called directly by 11 other files, bypassing the
  documented abstraction entirely
- **Empty states**: `EmptyStateView` used exactly once (`AdvertisementsView`); 3 other classes
  hand-roll their own `Span`-based empty state: `AttachmentGallery.java:100`,
  `AuditActivityPanel.java:75`, `TaxonManagementView.java:79-80`
- **Loading/spinner indicators**: zero hits anywhere — no convention exists at all
- **Badges/chips**: no shared component; 6 independent inline implementations, two using the
  identical class name `"user-role-badge"` built separately in `UserGridConfigurator.java:74` and
  `UserViewOverlayModeHandler.java:102`

### 7. CSS class naming
**Two competing, undocumented conventions coexist, sometimes in the same file.** Majority: flat
kebab-case (`"primary-button"`, `"advertisement-card"`, `"query-status-bar"`, etc. — 14 sampled
instances). Minority but internally consistent: BEM-style double-underscore, confined mostly to
the overlay component family (`"overlay__inner"`, `"overlay__header"`, `"overlay__breadcrumb-back"`
in `BaseOverlay.java`, `OverlayLayout.java`, `OverlayBreadcrumbBackButton.java`, and the 3
form/view overlay-mode-handler classes at e.g. `AdvertisementFormOverlayModeHandler.java:157,163`).
Mixed-within-one-file evidence: `UiLabeledField.java:38,45,48` (flat root `"labeled-field"`, BEM
children `"labeled-field__label"`/`"labeled-field__value"`) and `AdvertisementCardView.java:72,140,173`
(flat `"advertisement-card"`/`"advertisement-content"` alongside BEM-modifier
`"advertisement-description--truncated"`).

## Suggested next steps (for discussion — not decided here)

1. **Configurable Parameters shape — do not act here.** Already resolved architecturally by
   [improvement-025](improvement-025-leaf-ui-components-plain-classes.md); re-evaluate this
   finding only if 025 is explicitly deferred/rejected. Separately, once 025 is picked up, add
   `AttachmentLightbox`/`CardLightboxViewer`/`AttachmentThumbnail` (new since 025 was filed, see
   Finding 1) to its scope for re-evaluation.
2. **`afterSave()` vs `proceed()`** — same shape: either rename the rule in `.claude/rules.md` to
   match the real method name, or decide `proceed()` should be renamed (higher-risk, touches 3 overlays).
3. **`refresh()` two-catch-block shape** — genuine inconsistency (1/4 fully compliant), worth
   fixing for real: `AdvertisementsView` (already tracked, improvement-010) and `TimelineView`
   (not yet tracked — fold into improvement-010's scope) silently blank the screen instead of
   showing validation errors.
4. **CSS naming** — pick one convention (flat vs BEM) and decide whether the overlay family's BEM
   style should spread everywhere or be reverted to flat; the two mixed-within-one-file cases
   (`UiLabeledField`, `AdvertisementCardView`) are the clearest immediate cleanup candidates.
5. **`TimeZoneUtil` vs `InstantFormatter`** — 11 files bypass the documented DI-based formatter for
   the undocumented static utility; decide which one is actually the standard and document it.
6. **Badges/empty-states/loading-indicators** — no shared component exists for any of these three;
   decide whether they're worth extracting (badges have a concrete duplicate-CSS-class collision
   today: `"user-role-badge"` built twice independently).
7. Stale doc references to fix regardless of any above decision: `*Projection` package claim,
   `buildTabbedContent()` 5-arg example, `ui/query/utils/` package undocumented.

## Related

- Audit performed via a dedicated read-only research pass across `marketplace-app/src/main/java/org/ost/marketplace/ui/`, 2026-07-15 — no code changed, no builds/tests run as part of this issue.
- [improvement-010](improvement-010-advertisements-view-refresh-error-notification.md) — already
  tracks the `AdvertisementsView.refresh()` half of Finding 3; this issue only adds `TimelineView`
  to that scope, does not duplicate it.
- [improvement-025](improvement-025-leaf-ui-components-plain-classes.md) — already resolves
  Finding 1 (Configurable Parameters shape) architecturally; see the note in that finding. Needs a
  scope addition once picked up: `AttachmentLightbox`/`CardLightboxViewer`/`AttachmentThumbnail`
  (new `Configurable` beans added by improvement-026 Batch 3, after 025 was filed).
- [improvement-026](../completed/issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md) — the
  raw `Button` → `Ui*Button` wrapper migration currently in progress (Batches 1-3 done); related
  but separate scope, not re-litigated here.

## Additional polish findings from the 2026-07-19 UX screenshot review

Collected during the UX review that filed improvement-096–101 — each too small for its own
issue, all needing the same standardization-decision treatment as the findings above:

- **Required-field marker** is a tiny dot next to the label (`Title •`, `Email •`) — easy to miss;
  decide on the conventional `*` plus a one-line "required" legend on forms.
- **Pagination bar renders in full on single-page results** («« ‹ Page 1 of 1 › »» + record
  count) — hide it, or at least the arrows, when there is only one page.
- **Card metadata shows categories as run-on text** ("Categories: Електроніка, Транспорт") while
  the form shows chips — decide one representation (chips are also where improvement-008's
  deleted-strikethrough would land).
- **Filter-panel expand toggle (`▸`)** is a small click target (well under ~44px) for the primary
  entry point to filtering; the filter apply (funnel) / clear (eraser) icon-buttons have the same
  problem plus no labels (aria half tracked in improvement-098).
- **Header branding**: "ANNOUNCEMENTS" wordmark with decorative glyphs reads as a placeholder,
  and the tab it sits above is called "Advertisements" — one product name should win.
- **Video URL input placeholder** promises "YouTube, Facebook..." but the backend supports
  YouTube + generic iframe embeds (`CT_EMBED`) — a raw Facebook watch URL won't embed; align the
  placeholder with what actually works, or add proper FB URL→embed translation.
- **Success toast (5s, bottom-right)** lingers over subsequent interactions (visible on top of
  the logout confirm in screenshots) — consider shorter duration for pure-success toasts, and
  verify stacking behavior for rapid successive actions.

## Edge-case findings from the 2026-07-19 deep review (minor — fold into a UI-touching PR)

- **Pagination doesn't re-fetch after the total shrinks.** `PaginationBar.setTotalCount()`
  correctly clamps `currentPage` when it exceeds the new page count, but nothing re-fetches the
  clamped page — deleting the last item on page N leaves the user looking at an empty list with
  the indicator already showing the clamped page, until the next interaction. Trigger a `refresh`
  when the clamp actually changes `currentPage`. (Related surface: improvement-010's refresh
  handling.)
- **Optimistic-lock conflict leaves the form on a stale version.** `AbstractEntityOverlay` shows
  the conflict notification correctly, but the form keeps the old `version`, so every subsequent
  save conflicts again until the user manually reloads. Acceptable today, but re-fetching the
  fresh entity into the form after a conflict (and telling the user "reloaded to the latest
  version — reapply your change") would close the loop.

- **Timestamps fall back to server timezone before the client-TZ JS round-trip resolves.**
  `TimeZoneUtil` correctly reads the client zone via `extendedClientDetails` and caches it in the
  session, but its fallback is `ZoneId.systemDefault()` — so the very first render (before the JS
  round-trip completes) formats timestamps in the *server's* timezone, which then silently shifts
  once the client zone arrives. Compounds the already-noted `TimeZoneUtil`/`InstantFormatter`
  split (two formatting paths that can disagree on zone). Decide one zone-resolution path and one
  pre-resolution behavior (e.g. render nothing / a skeleton until the client zone is known,
  rather than a wrong-zone value that later changes).

- **Advertisement title is not trimmed before persist, unlike user registration.**
  `AdvertisementService.buildEntity()` stores `dto.title()` raw, while `UserService.register()`
  trims `name`/`email`/`password`. A title with leading/trailing whitespace ("  My Ad  ") persists
  verbatim, hurting sort/search/display. `@NotBlank` already rejects whitespace-only, so this is
  purely about edge whitespace. Trim `title` (and description's outer whitespace if appropriate)
  on the save path for consistency with the registration path.
