# improvement-109: Reference Data (categories) view has no pagination — loads and renders every taxon

**Type:** improvement — consistency + scalability. Found via edge-case review (2026-07-19).
**Module:** `marketplace-app` (`ui/.../referencedata/TaxonManagementView.java`),
`taxon-spring-boot-starter` (`DefaultTaxonPort.listAllByType`, repository)
**Priority:** low-medium — no bug at today's small dictionary; the only list view in the app that
breaks the paginated pattern, and it degrades as the category dictionary grows
**When:** Deferred — trigger: category dictionary growing past a couple screens' worth, or a
dedicated UI-consistency pass; batch with a reference-data touch

## Problem

`TaxonManagementView` renders the whole category dictionary in one shot:

```java
List<TaxonDto> all    = port.listAllByType(TaxonType.CATEGORY, Locale.ENGLISH, true);
Map<Long,Long> counts = all.isEmpty() ? Map.of() : port.getUsageCounts(TaxonType.CATEGORY);
active.forEach(t  -> listContainer.add(buildRow(t, ..., false)));
deleted.forEach(t -> listContainer.add(buildRow(t, ..., true)));
```

Every category (active + soft-deleted) is loaded into memory and rendered as a row, plus a
usage-count map over all of them. Every other list in the app — Advertisements, Users, Timeline —
paginates via `PaginationBar` + `settingsPagination`. Reference Data is the lone exception:

- **Consistency:** it doesn't follow the established View pattern (no `PaginationBar`, no
  page-size setting), so it looks and behaves differently from every sibling view.
- **Scalability:** marketplaces routinely reach hundreds of categories; unbounded load + full DOM
  render degrades linearly with dictionary size, with no upper bound.

## Suggested fix

Bring it onto the standard paginated View pattern: `listByType` with a `Pageable` (the taxon
repository already sorts/filters; add limit/offset like the others), a `PaginationBar`, and the
`settingsPagination` page-size binding. `getUsageCounts` should then be scoped to the visible page
(bulk lookup over the page's ids, same shape as `AttachmentPort.getMediaSummaries`) instead of the
whole type.

Keep the active/deleted split visible — either two paginated sections or a filter toggle
(active / all / deleted) reusing `TaxonFilter`, which already exists.

## Related

- `.claude/rules.md` "View Pattern" / `marketplace-app/CLAUDE.md` — the paginated View pattern
  this view should conform to.
- `backlog/issues/improvement-055-ui-vaadin-template-consistency-audit.md` — the broader
  consistency audit; this is a concrete instance of it.
