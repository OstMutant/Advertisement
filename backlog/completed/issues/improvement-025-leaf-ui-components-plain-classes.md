# improvement-025: Convert stateless leaf UI components from Spring prototype beans to plain Java classes

**Type:** improvement — architectural/tech-debt, self-identified during a conversation review of
UI component boilerplate
**Module:** marketplace-app
**Priority:** low-medium — no bug, no user-facing behavior change; reduces constructor bloat and
brings 14+ classes into line with an already-written rule this codebase does not currently follow
**When:** independent, no blockers — recommended to execute in phased batches (see Suggested fix),
not as one large PR

**Progress:** DONE 2026-07-22 — all four batches complete. Batch 1 (buttons: `UiPrimaryButton`,
`UiTertiaryButton`, `UiIconButton`, `DeleteActionButton`, `EditActionButton`,
`OverlayBreadcrumbBackButton`) — see `marketplace-app/DECISIONS.md` ADR-052. Batch 2 (fields:
`UiTextField`, `UiTextArea`, `UiEmailField`, `UiPasswordField`, `UiComboBox`, `UiLabeledField`) —
see ADR-053. Batch 3 (structural/no-dep: `EmptyStateView`, `DialogLayout`, `OverlayLayout`) — see
ADR-054. `PaginationBar` was reviewed as part of Batch 3 and deliberately kept a Spring bean
permanently (not deferred) — see ADR-054 for why. Batch 4 (`ConfirmActionDialog`) — see ADR-055,
which also records an unrelated pre-existing Playwright flake found and fixed in
`fillActorPicker`'s `useSearch` path during this batch's verification.

## Problem

`marketplace-app/CLAUDE.md` already states the rule these classes should follow:

> **When NOT to use Configurable:** Component needs only 1–2 simple setters → plain setters, no
> `Parameters`.

In practice, a set of small leaf UI widgets ignore this and are implemented as full
`@SpringComponent @Scope("prototype")` beans implementing `Configurable<T, Parameters>` +
`Initialization<T>`, even though their only real dependency (when they have one at all) is
`I18nService`, used solely to resolve a label/placeholder string at construction time. Verified by
reading every file in scope (not assumed from the class name):

| Class | Constructor deps found | Notes |
|---|---|---|
| `UiPrimaryButton`, `UiTertiaryButton`, `UiIconButton` | `I18nService` only | — |
| `DeleteActionButton`, `EditActionButton` | `I18nService` only | — |
| `UiTextField`, `UiTextArea`, `UiEmailField`, `UiPasswordField` | `I18nService` only | — |
| `UiComboBox<T>` | `I18nService` only | generic type param, see Investigation notes |
| `UiLabeledField` | `I18nService` only | — |
| `OverlayBreadcrumbBackButton` | `I18nService` only | — |
| `EmptyStateView` | **none** | `Parameters` already carries pre-resolved `String title`/`hint`, not `I18nKey` — no i18n move needed at all |

This causes concrete, measurable pain:
- Every parent `ModeHandler`/`View` must constructor-inject a `UiComponentFactory<T>` (or a
  direct field, for widgets built once per parent instance) per widget it uses.
  `UserFormOverlayModeHandler` — a form with exactly two real fields (name, role) — has **13**
  constructor-injected fields, more than half of which exist only to build cosmetic leaf widgets
  (`UiComponentFactory<UiIconButton> cancelButtonFactory`, direct `UiTextField nameField`,
  `UiComboBox<Role> roleComboBox`, `UiPrimaryButton saveButton`, `UiTertiaryButton discardButton`,
  plus the audit-related factories tracked separately under improvement-011).
- Container overhead and an extra layer of indirection (`ObjectProvider` → `ComponentFactory` →
  `.build(Parameters)`) for objects with no real dependency graph and a lifecycle no longer than
  the parent form.
- Boilerplate: a `@Builder` `Parameters` record + `Configurable` + `Initialization` interface
  implementation per widget, for widgets that do nothing at construction time beyond
  `setLabel(...)`/`addClassName(...)`.
- Precise consumer counts (corrected during a later merge with a pre-existing, more carefully
  counted `backlog/leaf-widgets-plain-classes/SPEC.md` — the initial `grep` pass in this issue
  undercounted `UiPrimaryButton` at 7 files; re-verified directly with `grep -rl` and confirmed
  **14** files reference it, matching that SPEC):

  | Class | Consumer files |
  |---|---|
  | `UiPrimaryButton` | 14 |
  | `UiIconButton` | 9 |
  | `UiTertiaryButton` | 8 |
  | `UiTextField` | 5 |
  | `ConfirmActionDialog` | 5 |
  | `DeleteActionButton`, `EditActionButton`, `UiEmailField`, `UiPasswordField`, `OverlayBreadcrumbBackButton` | 3 each |
  | `UiLabeledField`, `EmptyStateView` | 2 each |
  | `UiTextArea`, `UiComboBox` | 1 each |

  Real blast radius: every `ModeHandler`/`View` that builds a form or an action button — estimated
  ~30-40 files once overlaps are collapsed (some consumers, e.g. `UserFormOverlayModeHandler`, use
  4-5 of these widget types at once).

## Suggested fix

Convert each listed class from the Spring-managed `Configurable` pattern to a plain Java class
with a constructor that takes already-resolved values:

```java
// before
@SpringComponent @Scope("prototype")
public class UiTextField extends TextField
        implements Configurable<UiTextField, Parameters>, I18nParams, Initialization<UiTextField> {
    @Value @Builder static class Parameters { @NonNull I18nKey labelKey; @NonNull I18nKey placeholderKey; int maxLength; boolean required; }
    @PostConstruct init() { setWidthFull(); addClassName("text-field"); return this; }
    configure(Parameters p) { setLabel(getValue(p.getLabelKey())); ...; getElement().setAttribute("data-testid", p.getLabelKey().toTestId()); return this; }
}

// after
public class UiTextField extends TextField {
    public UiTextField(String label, String placeholder, int maxLength, boolean required, String testId) {
        setWidthFull();
        addClassName("text-field");
        setLabel(label);
        setPlaceholder(placeholder);
        if (maxLength > 0) setMaxLength(maxLength);
        setRequired(required);
        getElement().setAttribute("data-testid", testId);
    }
}
```

`I18nKey` resolution moves one level up, to the call site (the `ModeHandler`/`View`, which stays a
Spring bean and already has `I18nService` injected):

```java
new UiTextField(i18n.get(FIELD_NAME_LABEL), i18n.get(FIELD_NAME_PLACEHOLDER), 255, true, FIELD_NAME_LABEL.toTestId())
```

For the `data-testid` (currently derived from `I18nKey.toTestId()` inside `configure()`), pass the
already-computed test id as an explicit `String` constructor param (as shown above) rather than
passing the `I18nKey` itself into the widget — keeps the plain class free of any `I18nKey`/i18n
import entirely, matching the "no hidden framework magic" principle more strictly than the
alternative.

### Files confirmed by direct inspection to convert cleanly (only `I18nService`, or nothing)

```
ui/views/components/buttons/UiPrimaryButton.java
ui/views/components/buttons/UiTertiaryButton.java
ui/views/components/buttons/UiIconButton.java
ui/views/components/buttons/action/DeleteActionButton.java
ui/views/components/buttons/action/EditActionButton.java
ui/views/components/fields/UiTextField.java
ui/views/components/fields/UiTextArea.java
ui/views/components/fields/UiEmailField.java
ui/views/components/fields/UiPasswordField.java
ui/views/components/fields/UiComboBox.java
ui/views/components/fields/UiLabeledField.java
ui/views/components/EmptyStateView.java
ui/views/components/overlay/fields/OverlayBreadcrumbBackButton.java
```

### Do NOT touch (real dependencies or logic beyond i18n — keep as Spring beans)

- `OverlayFormBinder.java` (snapshot-diff / restore logic)
- `AuditActivityPanel.java`, `AuditActivityListRenderer.java`, `AuditActivityRowRenderer.java`,
  `AuditTimelineListRenderer.java`, `AuditTimelineRowRenderer.java` (`AuditPort` dependency)
- `AttachmentGallery.java`, `AttachmentGalleryService.java`, `CardMediaLightbox.java`
  (`AttachmentPort` dependency — tracked separately under improvement-011; do not conflate the two
  efforts, they touch overlapping call sites for different reasons)
- `BaseActionButton`, `BaseDialog`, `AbstractEntityOverlay`, `AbstractFormOverlayModeHandler`,
  `AbstractViewOverlayModeHandler`, `OverlayModeHandler`, `EntityOverlaySupport` (already plain or
  legitimate SPI — out of scope)

## Investigation notes (verified beyond the original proposal)

1. **`ConfirmActionDialog.java` does NOT fit the simple "only `I18nService`" template** — read
   directly, it has four constructor fields: `I18nService`, `DialogLayout layout`,
   `UiComponentFactory<UiPrimaryButton> primaryButtonFactory`,
   `UiComponentFactory<UiTertiaryButton> tertiaryButtonFactory`. If `UiPrimaryButton`/
   `UiTertiaryButton` convert to plain classes per this issue, `ConfirmActionDialog` would
   construct them directly (`new UiPrimaryButton(...)`) instead of through a factory — a
   *composition* change, not a pure "remove Configurable" change. Whether `ConfirmActionDialog`
   itself becomes a plain class too depends on whether `DialogLayout` also converts (see next
   point). Treat `ConfirmActionDialog` as its own small sub-task, not a mechanical batch entry.

2. **`DialogLayout.java` and `OverlayLayout.java` have ZERO constructor dependencies** (verified
   by reading both in full) — no `I18nService`, nothing. Both are stronger candidates for
   conversion than the originally-listed widgets, since they need nothing resolved at the call
   site at all; the only change is removing `@SpringComponent`/`@Scope("prototype")` and switching
   consumers from field-injection to `new DialogLayout()` / `new OverlayLayout()`.

3. **`PaginationBar.java` already uses a plain constructor**, not `Configurable`/`Parameters` —
   `public PaginationBar(I18nService i18nService)`. It holds real mutable state (`currentPage`,
   `totalCount`, `pageChangeListener`) but that is not a blocker to being a plain class (state is
   fine in a POJO); it is a smaller version of the same conversion (just drop
   `@SpringComponent`/`@Scope`, change the 3 consumer files — `AdvertisementsView`, `UserView`,
   `TimelineView` — from field-injection to `new PaginationBar(i18nService)`).
   **Reconciled disagreement:** the pre-existing `backlog/leaf-widgets-plain-classes/SPEC.md`
   (merged into this issue, see Related) called this "verified, stays a bean," presumably out of
   caution around `SettingsPaginationBinding`'s cross-session registration (improvement-018).
   Checked directly: `SettingsPaginationBinding.register(PaginationBar bar, ...)` holds `bar` by
   plain object reference, not by Spring bean identity/scope — converting `PaginationBar` to a
   plain class does not affect that registration mechanism at all. This issue's original
   conclusion (safe to convert) stands; the SPEC's caution here did not hold up under
   verification.

4. **`UiComboBox<T>` is generic** — the only generic class in the batch. Converting to a plain
   class preserves the type parameter with no special handling needed
   (`public class UiComboBox<T> extends ComboBox<T> { public UiComboBox(String label, List<T>
   items, boolean required, String testId) { ... } }`, consumers already call it as
   `UiComboBox<Role>` — `new UiComboBox<Role>(...)` compiles the same way). Flagging only so it
   isn't mistakenly skipped for being "different" during implementation.

5. **Consumer count is understated by file-count alone** — some consumers (e.g.
   `UserFormOverlayModeHandler`) use 4-5 of these widget types simultaneously, so the same file
   will be touched multiple times across a phased rollout. Track by consumer file, not just by
   widget type, when scoping each phase.

6. **New since this issue was filed**: `improvement-026` Batch 3 (2026-07-15) promoted
   `AttachmentLightbox`, `CardLightboxViewer`, and `AttachmentThumbnail` from plain classes to
   `@SpringComponent` prototype beans implementing `Configurable`, specifically to inject
   `UiComponentFactory<UiIconButton>` (per the "constructors take beans, not pre-built widgets"
   rule established during that work). None of the three appear in this issue's file lists. Once
   `UiIconButton` converts to a plain class per this issue, the reason these three needed to be
   beans evaporates — check each for remaining real dependencies (beyond `I18nService`) before
   assuming they revert to plain classes; add them to whichever batch's audit step turns up
   nothing else. See [improvement-055](improvement-055-ui-vaadin-template-consistency-audit.md)
   Finding 1 for the full cross-reference.

## Suggested phased execution (not one PR)

Given the wide, cross-cutting consumer footprint and this session's track record of two
previously-approved-looking changes that broke the app only once deployed (deny-by-default,
`@ConditionalOnBean` ordering — both post-mortemed in `marketplace-app/DECISIONS.md`), do this in
batches with a full Playwright run after each, not all ~17 classes in one PR:

1. **Batch 1 — buttons:** `UiPrimaryButton`, `UiTertiaryButton`, `UiIconButton`,
   `DeleteActionButton`, `EditActionButton`, `OverlayBreadcrumbBackButton` (grouped here per the
   merged SPEC's chunking — it's a small `Button` subclass, same shape as the others). Smallest
   blast radius, easiest to verify visually.
2. **Batch 2 — fields:** `UiTextField`, `UiTextArea`, `UiEmailField`, `UiPasswordField`,
   `UiComboBox`, `UiLabeledField`.
3. **Batch 3 — structural/no-dep:** `EmptyStateView`, `DialogLayout`, `OverlayLayout` — done.
   `PaginationBar` was reviewed here too but kept a Spring bean permanently: unlike the other
   three, it's read from a separately-invoked `refresh()` in three `View` classes and already has
   a test mocking it as an injected collaborator — see ADR-054.
4. **Batch 4 — `ConfirmActionDialog`:** handled last and separately, once its two button
   dependencies (Batch 1) are already converted, since its own conversion depends on theirs.

## Steps (per batch)

1. Convert each listed file per the target pattern above.
2. Update every call site (`xFactory.build(Parameters.builder()...)` → `new UiX(...)`, or
   field-injection → `new UiX(...)` inside the parent's `init()`/`activate()`) — search
   project-wide for each factory/field type to find all usages before starting, not during.
3. Remove now-unused `UiComponentFactory<UiX>` constructor injections (and any now-empty
   `ComponentFactoryConfig`/`MarketplaceUiConfiguration` `@Bean` declarations for these types) from
   every `ModeHandler`/`View` that only used them for these leaf widgets.
4. Run the full reactor build and the Playwright E2E suite — `data-testid` attributes must be
   byte-identical post-refactor or tests will break silently (verified via the same locator
   strings, not just "look similar").
5. Update `marketplace-app/DECISIONS.md` with one ADR per batch (or a single ADR covering the
   whole effort once all batches land) recording the decision and why: prototype-scoped beans for
   stateless leaf widgets removed; `Configurable`/DI reserved for components with a real service
   dependency (`AuditPort`, `AttachmentPort`, snapshot/diff logic).

## Required test coverage before merging (each batch)

Full Playwright e2e suite (`bash scripts/playwright.sh e2e --full --ux`) must stay green after
each batch — no new tests are strictly required since this is a pure refactor with no behavior
change, but any test relying on a `data-testid` touched in that batch is the actual regression
detector; do not skip a batch's e2e run because "it's just a mechanical refactor" (the exact
reasoning that preceded both prior incidents in this session).

`data-testid` decision (from the merged SPEC, kept as fixed, do not revisit mid-refactor): pass it
as an explicit `String` constructor parameter, computed at the call site via `I18nKey.toTestId()`
— 61 `data-testid` selector usages in the Playwright e2e suite depend on these attributes staying
byte-identical.

## Acceptance criteria

- None of the listed widgets carry Spring annotations or implement
  `Configurable`/`Initialization`/`I18nParams`.
- No `UiComponentFactory` bean declarations remain for converted widgets in
  `MarketplaceUiConfiguration`/`ComponentFactoryConfig`.
- `UserFormOverlayModeHandler` constructor dependency count drops from 13 to ≤9.
- Full e2e suite passes without modifying any spec selectors.
- `I18nParams`/`Initialization` interfaces checked for remaining users after the last batch;
  deleted if dead.
- `marketplace-app/CLAUDE.md` "When NOT to use Configurable" and `DECISIONS.md` updated in the
  same PR as the batch(es) that land.

## Related

- `marketplace-app/CLAUDE.md` — "When NOT to use Configurable" (the rule this issue brings the
  code into compliance with).
- `backlog/completed/issues/improvement-011-unguarded-port-injection-in-ui-components.md` /
  `marketplace-app/DECISIONS.md` ADR-033 — the two UI classes excluded here
  (`AttachmentGallery`/`AuditActivityPanel` and siblings) for having a real port dependency; do not
  re-open or conflate that work with this one.
- `backlog/leaf-widgets-plain-classes/SPEC.md` — a pre-existing, independently-written spec for
  this exact same refactor, discovered after this issue was already filed. Its more precise
  consumer-count numbers and phased-chunking were merged into this issue (2026-07-13); its
  `PaginationBar` caution was checked and did not hold up (see Investigation notes above). The
  SPEC file itself has been deleted — this issue is now the single source of truth for this
  refactor, per the same "one backlog" principle applied to `process-improvements.md`.
