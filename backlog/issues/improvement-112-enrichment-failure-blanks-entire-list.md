# improvement-112: A failing (but present) enrichment port blanks the entire advertisement list instead of degrading

**Type:** improvement — resilience. Found via edge-case review (2026-07-19).
**Module:** `advertisement-spring-boot-starter` (`AdvertisementService.enrichWithMediaSummary` /
`enrichWithActorInfo` / `enrichWithCategories`)
**Priority:** medium — a media/user/taxon subsystem hiccup takes down the core browse experience,
which is exactly the page that should be most resilient
**When:** Deferred — batch with any advertisement-service resilience touch; cheap and standalone

## Problem

The three enrichers degrade gracefully when a starter is **absent**, but not when it is
**present and failing**:

```java
return attachmentPortFactory.findIfAvailable()   // absent → orElse(ads): fine
        .map(attachmentPort -> { ... attachmentPort.getMediaSummaries(...) ... })  // present but throws → propagates
        .orElse(ads);
```

If `getMediaSummaries()` (S3/attachment down), `UserPort.findByIds()` (user DB hiccup), or
`TaxonPort.getForEntities()` (taxon timeout) throws, the exception propagates up through
`getFiltered()` to the view's `refresh()` catch-all, which blanks the whole advertisement list and
shows a generic error. So a failure in **decorative** enrichment (thumbnail, author name,
category chips) is treated as fatal to the **core** list — the browse page, the most important
and most public surface, goes blank because a thumbnail lookup failed.

## Suggested fix

Wrap each enricher's port call in its own try/catch that logs and returns the un-enriched `ads` on
failure (same end state as "starter absent"), so a subsystem outage degrades to "ads without
thumbnails / without author names / without category chips" rather than an empty page:

```java
try { ... enrich ... } catch (Exception e) {
    log.warn("Media-summary enrichment failed, rendering list without media", e);
    return ads;
}
```

Optionally surface a subtle, non-blocking hint ("media temporarily unavailable") instead of the
current fatal error path. Keep the `findIfAvailable().orElse(ads)` absent-starter path as-is; this
only adds the present-but-failing path.

## Verification

Unit-test each enricher with a `ComponentFactory` whose port throws → assert the original `ads`
list is returned unchanged (not an exception). `AdvertisementService`'s enrich methods are already
covered by the service-layer unit tests established in improvement-048's pattern.

## Related

- `backlog/issues/improvement-010-advertisements-view-refresh-error-notification.md` — same
  `refresh()` error surface; that issue adds the missing notification, this one prevents the
  blank-list trigger in the first place. Coordinate so they don't fight over the catch block.
- `marketplace-app/DECISIONS.md` ADR-035 — read-time media enrichment is the design that makes
  this failure path reachable on every list render.
