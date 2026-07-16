# improvement-061: `SupportUtil.toLong(Double)` silently truncates fractional input on ID range filters

**Type:** improvement — correctness/UX. Found via direct code review, verified against current
source (2026-07-16).
**Module:** `marketplace-app` (`ui/views/utils/SupportUtil.java`, `ui/views/main/tabs/users/query/UserFilterMeta.java`).
**Priority:** medium-high — a real, silent correctness bug (wrong filter results with no error
shown), though narrow blast radius (only the user-id range filter uses this conversion today).
**When:** independent, no blockers.

## Problem

`SupportUtil.toLong()`:
```java
public static Long toLong(Double value) {
    return value != null ? value.longValue() : null;
}
```
is used by `UserFilterMeta.ID_MIN`/`ID_MAX` to convert the `NumberField`'s `Double` value into the
`Long` the `UserFilterDto.startId`/`endId` fields expect:
```java
FilterFieldMeta.of(startId, UserFilterDto::getStartId, (dto, v) -> dto.setStartId(toLong(v)), idValid);
```
`idValid` (`ValidationPredicates.range(startId, endId)`) only checks that `startId <= endId` — it
never checks that the entered value is a whole number. If a user types `123.99` into the ID-min
filter field, `toLong()` silently truncates it to `123` via `Double.longValue()` — no validation
error is shown, and the filter silently applies to a different id boundary than the one the user
actually typed.

## Suggested fix

Add a whole-number check to `idValid` (or a dedicated predicate), e.g. rejecting any `Double` where
`value % 1 != 0`, so a fractional entry shows a validation error instead of being silently
truncated. Alternative considered: swap `NumberField` for `IntegerField` on these two fields
specifically — rejected as the first choice here since `Double`-backed `NumberField` is already the
established pattern for numeric filter fields in this codebase (`FilterFieldMeta<Double, ...>`);
an explicit validation predicate is the smaller, more consistent change. `IntegerField` remains a
valid alternative if that established pattern is ever revisited.

## Related

- `marketplace-app/src/main/java/org/ost/marketplace/ui/views/main/tabs/users/query/UserFilterMeta.java`
  — the only current consumer of `toLong()`, lines 33-37.
- `marketplace-app/src/main/java/org/ost/marketplace/ui/query/filter/ValidationPredicates.java` —
  where the existing `range()` predicate lives; a fractional-value predicate belongs alongside it.
