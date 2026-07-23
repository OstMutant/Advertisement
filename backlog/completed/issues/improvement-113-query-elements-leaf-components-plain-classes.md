# improvement-113: Convert `ui/query/elements/*` leaf UI components from Spring prototype beans to plain Java classes

**Type:** improvement — architectural/tech-debt, found during a post-improvement-025 audit of the
rest of the Vaadin UI layer for the same anti-pattern
**Module:** marketplace-app
**Priority:** low-medium — no bug, no user-facing behavior change; same class of fix as
improvement-025, applied to a sibling tree that improvement-025 never audited
**When:** independent, no blockers — execute in phased batches (see Suggested fix), not one PR

**Progress:** DONE 2026-07-23 — all six batches complete (dead-code removal, `SvgIcon`, `SortIcon`,
`QueryActionButton`+`QueryActionBlock`, `QueryInlineRow`, remaining simple fields). See
`marketplace-app/DECISIONS.md` ADR-056 for the full breakdown, including the `SortIcon`/
`PaginationBar` design decision and the dead-code findings (`QueryComboField`, `QueryNumberField`).

## Problem

[improvement-025](../completed/issues/improvement-025-leaf-ui-components-plain-classes.md) removed
`@SpringComponent @Scope("prototype") + Configurable + Initialization` from every leaf UI widget
under `ui/views/components/` whose only real dependency was `I18nService`, used solely to resolve a
label/placeholder string at construction time. **`ui/query/elements/*` — the query-bar/filter-panel
widget tree — was never included in that issue's scope and still carries the identical pattern.**
Verified by reading every file in scope (not assumed from the class name):

| Class | Constructor deps found | Notes |
|---|---|---|
| `SvgIcon` | **none** | zero dependencies at all — the strongest candidate in the whole set |
| `QueryActionButton` | `I18nService` + `UiComponentFactory<SvgIcon>` | resolves tooltip once in `configure()` |
| `QueryActionBlock` | **none** (composes 2 `QueryActionButton` beans, Spring-injected) | fixed tooltip keys (`ACTIONS_APPLY_TOOLTIP`/`ACTIONS_CLEAR_TOOLTIP`), never parameterized per call site |
| `QueryInlineRow` | `I18nService` | resolves label once in `configure()` |
| `QueryTextField` | `I18nService` | resolves placeholder once in `configure()` |
| `QueryLongField` | `I18nService` (+ static `SupportUtil`) | resolves placeholder + invalid-number message once |
| `QueryDateTimeField` | `I18nService` | resolves 2 placeholders once in `configure()`; real internal state (`DatePicker`+`TimePicker`, `generateModelValue`/`setPresentationValue`) is not a blocker to conversion — same precedent `PaginationBar` already established (state is fine in a POJO) |
| `QueryMultiSelectComboField<T>` | `I18nService` | resolves placeholder once; generic type param, no special handling needed (same precedent as `UiComboBox<T>` in improvement-025) |
| `SortIcon` | `I18nService` (+ `SvgIcon`) | **the one real exception — see Investigation notes** |
| `QueryComboField<T>` | `I18nService` | **dead code — zero consumers anywhere, see Investigation notes** |
| `QueryNumberField` | `I18nService` | **dead code — zero real consumers anywhere, see Investigation notes** |

This causes the exact same measurable pain improvement-025 already fixed elsewhere: every
`*QueryBlock` (`AdvertisementQueryBlock`, `TimelineQueryBlock`, `UserQueryBlock`) constructor-injects
4-6 `UiComponentFactory<T>` fields purely to build these leaf widgets, and **8 of the remaining 21
`@Bean` declarations in `MarketplaceUiConfiguration`** exist solely for this family (`queryTextFieldFactory`,
`queryDateTimeFieldFactory`, `queryNumberFieldFactory`, `queryLongFieldFactory`,
`queryMultiSelectComboFieldFactory`, `queryInlineRowFactory`, `sortIconFactory`, `svgIconFactory`).

## Investigation notes (verified beyond the original proposal)

1. **`SortIcon` does NOT fit the simple "resolve once, pass a `String`" template** used everywhere
   else in improvement-025. It re-resolves its tooltip **dynamically, during the component's
   lifetime**, not just at construction:
   ```java
   private void switchIcon() {
       SortIconState state = SortIconState.fromDirection(currentDirection);
       icon.setTitle(i18nService.get(state.getTooltipKey()));
       getElement().setAttribute("aria-label", i18nService.get(state.getTooltipKey()));
   }
   ```
   `switchIcon()` runs every time the user clicks to cycle NEUTRAL→ASC→DESC→NEUTRAL, each state
   needing a different resolved tooltip. A single pre-resolved `String` passed at construction
   cannot represent this. **Decision (discussed and confirmed before filing this issue):** follow
   the precedent `PaginationBar` already set for this exact shape — `PaginationBar` stayed a plain
   constructor `PaginationBar(I18nService i18nService)` (not `Configurable`) specifically because it
   needs to keep calling `.get()` internally as its own state changes. `SortIcon` does the same:
   `SortIcon(I18nService i18nService)`, no `Configurable`/`Parameters`, holds `i18nService` as a
   plain field and calls `.get()` from `switchIcon()` whenever direction changes. This is a plain
   Java object holding a reference to an already-existing singleton bean — nothing about it requires
   `SortIcon` itself to be Spring-managed.

2. **`QueryComboField<T>` is dead code** — confirmed via `grep -rn "QueryComboField" --include=*.java`
   across the entire module: the only match is its own file. No `@Bean` declaration for it exists in
   `MarketplaceUiConfiguration` either (unlike every other class in this family). Delete entirely,
   do not convert.

3. **`QueryNumberField` is also dead code in practice** — has a `@Bean` in
   `MarketplaceUiConfiguration` and a defensive `@Uses(QueryNumberField.class)` /
   `@Uses(NumberField.class)` in `MainView.java`, but zero real construction sites anywhere
   (confirmed via grep excluding those two files). Delete entirely, remove its `@Bean` and both
   `@Uses` lines from `MainView.java` (re-check `@Uses(NumberField.class)` isn't needed for anything
   else before removing it — it currently exists only because `QueryNumberField extends NumberField`).

4. **The base `QueryBlock<T>` class itself needs signature changes, not just its leaf consumers** —
   `filterRow(...)`'s three overloads currently take `UiComponentFactory<QueryInlineRow> rowFactory`
   and `UiComponentFactory<SortIcon> sortIconFactory` as method parameters and build both internally
   via `.build(...)`/`.get()`. Once `QueryInlineRow`/`SortIcon` are plain classes, these overloads
   change to take `I18nService i18nService` instead (to build `new SortIcon(i18nService)` and
   resolve `QueryInlineRow`'s label internally), and the `I18nKey labelKey` parameter becomes an
   already-resolved `String label` (resolved by each `*QueryBlock` caller before invoking
   `filterRow(...)`, matching every other conversion in this issue and in improvement-025). This
   cascades to all three `*QueryBlock` subclasses regardless of which batch they're touched in.

5. **`TimelineQueryBlock`/`UserQueryBlock` do not currently hold an `I18nService` field** (unlike
   `AdvertisementQueryBlock`, which already has one for its manual `categoriesField` placeholder) —
   both will need to gain one as part of this conversion, purely to resolve keys before calling
   `filterRow(...)`/constructing `QueryActionBlock`. Not a design problem, just a note so it isn't
   missed mid-batch.

6. **`TimelineQueryBlock` has one call site that builds a `QueryInlineRow` directly**, bypassing
   `filterRow()` entirely (the actor-picker row, gated behind `access.canView()`) — this call site
   needs the same `Parameters.builder()` → `new QueryInlineRow(...)` conversion as every other
   `QueryInlineRow` construction.

7. **`SortProcessor`/`QueryBlock` reference `SortIcon` only as a type** (a registry
   `Map<SortFieldMeta, SortIcon>`, a method parameter) — no construction there, unaffected beyond
   the import staying valid.

## Suggested fix

Same target shape as improvement-025 — plain constructor taking already-resolved values, i18n
resolution moved to the call site — with the one documented exception (`SortIcon` keeps
`I18nService` as a plain field, `PaginationBar`-style, since it must re-resolve dynamically).

```java
// before
@SpringComponent @Scope("prototype")
public class QueryTextField extends TextField
        implements Configurable<QueryTextField, Parameters>, Initialization<QueryTextField> {
    @Value @Builder static class Parameters { @NonNull I18nKey placeholderKey; }
    @PostConstruct init() { addClassName("query-text"); setClearButtonVisible(true); ...; return this; }
    configure(Parameters p) { setPlaceholder(i18nService.get(p.getPlaceholderKey())); return this; }
}

// after
public class QueryTextField extends TextField {
    public QueryTextField(String placeholder) {
        addClassName("query-text");
        setClearButtonVisible(true);
        setValueChangeMode(ValueChangeMode.EAGER);
        HighlighterUtil.setDefaultBorder(this);
        setPlaceholder(placeholder);
    }
}
```

## Suggested phased execution (bottom-up dependency order — do not merge into one PR)

1. **Batch 1 — dead code removal:** delete `QueryComboField<T>` and `QueryNumberField` entirely;
   remove `QueryNumberField`'s `@Bean` from `MarketplaceUiConfiguration` and its two `@Uses` lines
   (`QueryNumberField`, and `NumberField` if nothing else needs it) from `MainView.java`.
2. **Batch 2 — `SvgIcon`:** zero dependencies, convert first since `SortIcon`/`QueryActionButton`
   both consume it. `new SvgIcon(String resourcePath)`.
3. **Batch 3 — `SortIcon`:** `new SortIcon(I18nService i18nService)`, builds its own initial
   `SvgIcon` internally, keeps `i18nService` as a field (documented exception above). Cascades into
   `QueryBlock.filterRow()`'s two `SortIcon`-building overloads (swap
   `UiComponentFactory<SortIcon> sortIconFactory` param for `I18nService i18nService`) and into all
   three `*QueryBlock` subclasses (drop `sortIconFactory` field, pass `i18nService` instead —
   `TimelineQueryBlock`/`UserQueryBlock` gain the field).
4. **Batch 4 — `QueryActionButton` + `QueryActionBlock`:** `QueryActionButton` takes a pre-built
   `SvgIcon` + resolved `String tooltip` + `ButtonVariant`. `QueryActionBlock` becomes
   `new QueryActionBlock(I18nService i18nService)`, building its two buttons + their `SvgIcon`s
   internally with the two fixed, already-known tooltip keys. All three `*QueryBlock` subclasses
   change their `queryActionBlock` field from Spring-injected to `new QueryActionBlock(i18nService)`
   built in their own `initLayout()`.
5. **Batch 5 — `QueryInlineRow`:** takes a resolved `String label` instead of `I18nKey labelKey`.
   Cascades into `QueryBlock.filterRow()`'s `I18nKey labelKey` parameter (→ `String label`,
   resolved by the caller) across all three overloads, and into `TimelineQueryBlock`'s one manual
   `QueryInlineRow` construction (actor row). All `filterRow(...)` call sites in the three
   `*QueryBlock` subclasses change to pass `i18nService.get(SOME_KEY)` instead of the raw key.
6. **Batch 6 — remaining simple fields:** `QueryTextField`, `QueryLongField`, `QueryDateTimeField`,
   `QueryMultiSelectComboField<T>` — all "resolve once, pass a `String`" conversions, no cascading
   signature changes needed beyond each field's own construction call sites in the three
   `*QueryBlock` subclasses.

Each batch: full Playwright e2e run (`bash scripts/playwright.sh e2e --full --ux`) before moving to
the next — the query/filter bar is exercised by nearly every spec, so a batch that breaks it fails
loudly and immediately, same as improvement-025's own batches.

## Steps (per batch)

1. Convert each listed file per the target pattern above.
2. Update every call site (`xFactory.build(Parameters.builder()...)` / `xFactory.get()` →
   `new X(...)`) — search project-wide for each factory/field type to find all usages before
   starting, not during (already done for this issue file, re-verify at implementation time in case
   anything shifted).
3. Remove now-unused `UiComponentFactory<X>` constructor injections and the corresponding
   `@Bean` declarations from `MarketplaceUiConfiguration`.
4. Run the full reactor build, `bash scripts/unit-tests.sh`, `bash scripts/integration-tests.sh
   --sandbox`, and the full Playwright e2e suite — the query/filter bar's CSS classes and behavior
   must stay byte-identical (this tree doesn't carry `data-testid` attributes the way the button/
   field family did, but several specs assert on `.query-inline-row`/`.query-action-block`/etc. CSS
   classes and sort-icon click behavior).
5. Update `marketplace-app/DECISIONS.md` with one ADR per batch (or a consolidated ADR once all
   batches land), recording the `SortIcon` design decision explicitly.

## Acceptance criteria

- None of the listed widgets carry Spring annotations or implement
  `Configurable`/`Initialization` — except `SortIcon`, which is a plain class but intentionally
  keeps `I18nService` as a constructor field (documented exception, not an oversight).
- `QueryComboField`/`QueryNumberField` deleted, not converted.
- No `UiComponentFactory` bean declarations remain in `MarketplaceUiConfiguration` for any class in
  this issue's scope.
- `AdvertisementQueryBlock`/`TimelineQueryBlock`/`UserQueryBlock` constructor dependency counts drop
  measurably (each currently holds 4-6 `UiComponentFactory<T>` fields purely for this family).
- Full e2e suite passes without modifying any spec selectors.
- `marketplace-app/DECISIONS.md` updated in the same PR as the batch(es) that land.

## Related

- [improvement-025](../completed/issues/improvement-025-leaf-ui-components-plain-classes.md) — the
  sibling refactor this issue extends to the `ui/query/elements/*` tree; same target pattern, same
  `PaginationBar` precedent used to resolve `SortIcon`'s one real exception.
- `marketplace-app/CLAUDE.md` — "When NOT to use Configurable" (the rule both issues bring the code
  into compliance with).
