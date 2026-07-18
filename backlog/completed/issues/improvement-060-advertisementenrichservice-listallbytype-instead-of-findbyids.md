# improvement-060: `AdvertisementEnrichService.resolveCategoryNames()` loads all categories instead of the bulk lookup by id

**Type:** improvement — scalability/correctness. Found via direct code review, verified against
current source (2026-07-16).
**Module:** `marketplace-app` (`services/advertisement/AdvertisementEnrichService.java`).
**Priority:** medium — no bug at today's data volume, but a genuine "memory wall" once the taxon
table grows; the fix already exists as a one-call swap, no new API needed.
**When:** independent, no blockers.

## Problem

`AdvertisementEnrichService.resolveCategoryNames()` (line ~101) resolves category names for a
handful of category ids referenced in an activity/timeline page:
```java
return taxonPortFactory.findIfAvailable()
        .map(p -> p.listAllByType(TaxonType.CATEGORY, Locale.ENGLISH, true).stream()
                .filter(t -> ids.contains(t.getId()))
                .collect(Collectors.toMap(TaxonDto::getId, TaxonDto::getName)))
        .orElse(Map.of());
```
`listAllByType()` loads **every** category row (all types, `includeDeleted=true`) into a `List`,
then filters it down to the handful of ids actually needed via an in-memory Java stream. At
today's category count this is invisible; at a few thousand categories (plausible for a mature
marketplace taxonomy), every activity/timeline page render pulls the entire category table into
memory just to resolve a handful of names.

## Suggested fix

`TaxonPort` already has exactly the bulk-by-id lookup this needs:
```java
Map<Long, TaxonDto> findByIds(@NonNull Set<Long> taxonIds, @NonNull Locale locale);
```
(`platform-commons/.../taxon/spi/TaxonPort.java:55`, added by improvement-007 for the same
N+1-avoidance reason). Swap `resolveCategoryNames()` to call `findByIds(ids, Locale.ENGLISH)` and
map the resulting `Map<Long, TaxonDto>` to `Map<Long, String>` via `TaxonDto::getName` — no new
port method needed, this is a pure call-site fix.

## Related

- `backlog/completed/issues/improvement-007-taxon-findbyids-and-snapshot-captureandgetid.md` —
  where `TaxonPort.findByIds()` was originally added, for exactly this class of N+1/bulk-load
  problem elsewhere in the codebase.
- `platform-commons/src/main/java/org/ost/platform/taxon/spi/TaxonPort.java` — the interface
  already exposing the needed method.
