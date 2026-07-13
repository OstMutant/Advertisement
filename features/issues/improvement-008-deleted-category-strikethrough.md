# improvement-008: Soft-deleted categories not visually distinguished in advertisement view

**Type:** improvement — follow-up gap from category-ids-in-snapshot
**Module:** marketplace-app
**Priority:** low — cosmetic, no functional breakage
**When:** Deferred — batch into any nearby UI-touching PR

## Problem

`category-ids-in-snapshot` spec required soft-deleted categories still assigned to an
advertisement to render with a strikethrough in the view overlay. The data is already
available — `TaxonPort.getForEntity()` resolves assignments with `includeDeleted=true`
(`DefaultTaxonPort.java:57`, `resolveDtos(..., true)`), and `TaxonDto.deleted` is a plain
boolean field — but `AdvertisementViewOverlayModeHandler.java:80-90` renders every category
chip identically:

```java
cats.forEach(cat -> {
    Span chip = new Span(cat.getName());
    chip.addClassName("advertisement-category-chip");
    categoriesRow.add(chip);
});
```

No check on `cat.isDeleted()`, no distinguishing CSS class.

## Suggested fix

```java
cats.forEach(cat -> {
    Span chip = new Span(cat.getName());
    chip.addClassName("advertisement-category-chip");
    if (cat.isDeleted()) chip.addClassName("advertisement-category-chip--deleted");
    categoriesRow.add(chip);
});
```

Add `.advertisement-category-chip--deleted { text-decoration: line-through; }` to
`advertisement-card.css` (or the relevant overlay stylesheet).
