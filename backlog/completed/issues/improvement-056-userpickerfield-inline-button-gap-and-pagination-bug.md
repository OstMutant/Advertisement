# improvement-056: `UserPickerField` — inline search-button variant gap + `CallbackDataProvider` offset→page pagination bug

**Type:** bug fix (pagination correctness) + component gap (UI wrapper), both found in the same file
while scoping improvement-026 Batch 4.
**Module:** `marketplace-app` (`ui/query/elements/fields/UserPickerField.java`).
**Priority:** medium — item 2 is a real correctness bug (wrong/duplicate/skipped rows under certain
scroll patterns), though not observed in production and never triggered by the current Playwright
seed volume (see below); item 1 is cosmetic/consistency only.
**When:** independent, no blockers — both touch the same file, worth bundling into one PR.

## Problem

Found while scoping [improvement-026](../completed/issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md)
Batch 4 (2026-07-15).

### 1. Inline search-button not converted to `UiIconButton`

`UserPickerField.openDialog()` (`UserPickerField.java:115-118`) builds a `Button` used as a
`TextField` suffix component:
```java
Button searchButton = new Button(VaadinIcon.SEARCH.create(),
        e -> filterable.setFilter(searchField.getValue().isBlank() ? null : searchField.getValue()));
searchButton.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY_INLINE);
searchField.setSuffixComponent(searchButton);
```
`UiIconButton.init()` hardcodes `LUMO_TERTIARY + LUMO_ICON` (`UiIconButton.java:36`) — no variant
for the "inline" case Vaadin uses for suffix/prefix slot buttons (`LUMO_TERTIARY_INLINE`, a
distinct visual treatment: no button-like padding/hover ring, matches inline text-field
placement). Forcing this button through `UiIconButton` as-is would change its rendered appearance
inside the text field slot. Deliberately excluded from improvement-026 Batch 4 rather than risk a
visual regression for zero UX benefit (no bug today, just wrapper-vs-raw-Button duplication).

### 2. `CallbackDataProvider` offset→page conversion is unsound

`UserPickerField.java:103-111`:
```java
CallbackDataProvider<UserDto, String> dataProvider = DataProvider.fromFilteringCallbacks(
        query -> userPort.getFiltered(
                UserFilterDto.builder().name(query.getFilter().orElse(null)).build(),
                query.getOffset() / Math.max(1, query.getLimit()),
                query.getLimit(),
                Sort.by(Sort.Order.asc("name"))).stream(),
        query -> userPort.count(
                UserFilterDto.builder().name(query.getFilter().orElse(null)).build())
);
```
`UserPort.getFiltered(filter, int page, int size, Sort sort)` (`platform-commons/.../UserPort.java:23`)
takes a **page number**, but Vaadin's `Query<T,F>` gives a **row offset**. `offset / limit` only
yields the correct page when `offset` is an exact multiple of `limit`. Vaadin's
`Grid`/`DataCommunicator` does not guarantee page-aligned offsets for every fetch (in particular
under fast/jump scrolling through a long list) — a fetch at `offset=137, limit=50` computes
`page=2` (rows 100-149) via integer division, when the actually-requested window starts at row
137. Result: wrong, duplicated, or skipped rows in the picker grid, specifically more likely to
manifest as the number of users grows (more scroll positions, more chances of a non-page-aligned
fetch) — exactly the "many users in the picker" scenario this issue was raised to check.

`UserPickerField` is the **only** place in `marketplace-app` using Vaadin's native lazy
`CallbackDataProvider`/`DataProvider.fromFilteringCallbacks` — every other grid
(`AdvertisementsView`, `UserView`, `TimelineView`) uses the app's own `PaginationBar` with
explicit page-state tracking instead, which doesn't have this class of bug (the page number is
tracked directly, never back-derived from a Vaadin-controlled offset). This makes `UserPickerField`
an architectural outlier as well as the sole carrier of this bug.

**Why untriggered so far:** `05-seed-filter-sort-pagination.spec.js` seeds exactly ~50 users,
matching Vaadin `Grid`'s default `pageSize` of 50 — the entire result set fits in a single,
always-page-aligned fetch (offset always 0). The misalignment only occurs once the result set
spans multiple Grid-internal pages and a fetch lands on a non-aligned boundary, which the current
test data volume never exercises.

## Suggested fix

1. **Item 2 (bug):** replace the `offset/limit` page derivation with an offset-based
   repository/port call, or clamp/round appropriately — the more robust fix is likely to change
   `UserPickerField`'s data provider to request by offset directly (a `DataProvider` variant that
   passes the raw offset through to a repository method accepting `LIMIT/OFFSET` instead of
   `page/size`), rather than shoehorning a Vaadin-controlled offset into the page-based
   `UserPort.getFiltered()` contract designed for `PaginationBar`-style callers. Needs a decision
   on whether `UserPort` gains an offset-based overload or whether `UserPickerField` gets its own
   narrower lookup — investigate at implementation time.
2. **Item 1 (gap):** decide whether to add an "inline" variant option to `UiIconButton` (e.g. a
   boolean `inline` flag in `Parameters`, applying `LUMO_TERTIARY_INLINE` instead of
   `LUMO_TERTIARY` in `configure()`) or leave this one call site as a raw `Button` permanently — a
   real architectural call, not a mechanical fix.

## Verification plan

- Item 2: a Playwright test seeding enough users to exceed one Grid page (>50) and
  scrolling/jumping through the picker, or a direct unit/integration test of the data provider's
  fetch callback with a deliberately non-page-aligned `Query` (offset not a multiple of limit),
  asserting the correct rows come back.
- Item 1: visual check only if an inline variant is added — no functional test needed.

## Related

- [improvement-026](../completed/issues/improvement-026-duplicate-raw-buttons-instead-of-ui-button-wrappers.md) Batch
  4 — where this was found; both items deliberately excluded from that batch's mechanical scope.
