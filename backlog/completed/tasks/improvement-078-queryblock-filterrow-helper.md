# improvement-078: QueryBlock — extract filterRow() helper to reduce filter-row duplication

**Type:** improvement — structural duplication cleanup, found via manual UI review.
**Module:** `marketplace-app` (`ui/query/QueryBlock.java`, `AdvertisementQueryBlock`,
`UserQueryBlock`, `TimelineQueryBlock`).
**Priority:** medium — real, confirmed duplication (~13 of 14 filter rows across 3 files follow an
identical shape); moderate structural change, low risk since it's additive (new protected helper,
existing call sites migrate one at a time).
**When:** after the low-risk items in this batch (improvement-076/077); no hard blocker.

## Problem

`AdvertisementQueryBlock`, `UserQueryBlock`, and `TimelineQueryBlock` each repeat the same
"build field → build row → add → register" shape for most of their filter rows: build one or more
`Query*Field`s, wrap in a `QueryInlineRow` (optionally with a `SortIcon`), `add()` it to the
layout, then call `filterProcessor.register(...)` (and `sortProcessor.register(...)` where sort
applies).

**Confirmed scope (verified directly against each file):**
- `AdvertisementQueryBlock` (121 lines) — 4 rows (title, created, updated, categories), all match.
- `UserQueryBlock` (137 lines) — 6 rows (id, name, email, role, created, updated), all match; the
  most regular of the three.
- `TimelineQueryBlock` (113 lines) — 4 rows (entityType, actionType, date, actor); 3 match
  cleanly, the `actorRow` is conditionally built inside `if (access.canView())`
  (`TimelineQueryBlock.java:94-101`) — a genuine deviation from the plain shape.

Roughly 13 of 14 rows across the three files match the exact repeated shape.

## Suggested fix

Add a protected helper to `QueryBlock<T>`:
```java
protected <F extends Component> F filterRow(I18nKey labelKey, F field, FilterFieldMeta meta,
        UiComponentFactory<QueryInlineRow> rowFactory) {
    add(rowFactory.build(QueryInlineRow.Parameters.builder().labelKey(labelKey).filterField(field).build()));
    getFilterProcessor().register(meta, field, getQueryActionBlock());
    return field;
}
```
**Correction to the original proposal:** this naive single-field, filter-only signature does not
cover every real call site. The helper needs to flex for: rows with a `SortIcon` +
`sortProcessor.register(...)` in addition to the filter registration (most rows in
`AdvertisementQueryBlock`/`UserQueryBlock`), and rows with more than one filter field. Design the
helper (or a small family of overloads) to cover these variations before migrating call sites.
Leave `TimelineQueryBlock`'s conditional `actorRow` and anything else that doesn't fit cleanly
untouched rather than forcing it through the helper.

Migrate `AdvertisementQueryBlock`, `UserQueryBlock`, `TimelineQueryBlock` to use the helper
wherever the shape actually matches.

## Related

- Filed as part of a verified 8-item Vaadin UI refactor batch (2026-07-17); see improvement-076,
  improvement-077, improvement-079 through improvement-083 for the rest.
