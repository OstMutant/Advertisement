# improvement-023: No request/trace correlation id in logs

**Status:** ✅ RESOLVED (2026-07-13) — `RequestCorrelationFilter` added (`OncePerRequestFilter`,
auto-registered), `%.8X{requestId}` added to the console logging pattern. Verified end-to-end:
`docker logs` shows a distinct 8-char id per HTTP request during the e2e run. Also closed several
completely-silent service classes found during the review (TaxonService, AuthService,
AttachmentService, TaxonAssignmentService, AttachmentSnapshotService, UserSettingsService,
AdvertisementSaveService, both cleanup services, LoginDialog's missing catch-all log) so the new
correlation id has something meaningful to correlate. See `marketplace-app/DECISIONS.md`
ADR-032. Full e2e suite 48/48 green.

**Type:** improvement — observability, found during improvement-015 discussion (was `version`
column a viable correlation id? no — see below)
**Module:** marketplace-app
**Priority:** low-medium — no incident depends on it yet, but debugging any multi-line issue
(e.g. an optimistic-locking conflict, a failed save) currently means guessing which log lines
across services/threads belong to the same HTTP request
**When:** Wave 2 — independent, can be picked up any time, no blockers

## Problem

Log lines from a single HTTP request/UI action are not tagged with any shared identifier.
`AdvertisementService`, `UserRepository`, `AuditLogRepository`, etc. all log independently
(`log.info(...)`, `log.error(...)`) with no correlation field, so reconstructing "everything
that happened for this one save click" means matching on timestamps and eyeballing content —
already awkward, and outright unreliable once the app has real concurrent traffic (interleaved
log lines from different users' requests on different threads).

`Advertisement.version` / `User.version` / `Taxon.version` (added in improvement-015) is **not**
a substitute for this: it is unique only within one entity row (two different rows can carry the
same version number at the same time), many requests touch zero or several entities, and a
version is only assigned *after* a successful write — a failed/conflicting request has no version
to log against at all. Solving observability by overloading a concurrency-control column would
conflate two unrelated concerns.

## Suggested fix

Standard SLF4J MDC-based request correlation:

1. Add a `Filter` (e.g. `RequestCorrelationFilter`) in `marketplace-app/config` that generates
   `UUID.randomUUID()` once per incoming HTTP request, puts it in MDC (`MDC.put("requestId", ...)`)
   before the request is processed, and removes it in a `finally` block after.
2. Update the logging pattern (`logback-spring.xml` or `application.yml` logging pattern) to
   include `%X{requestId}` so every log line automatically carries it — no call-site changes
   needed anywhere else.
3. Alternative considered: Spring Boot's built-in `micrometer-tracing` (trace/span ids,
   auto-configured MDC insertion when the dependency is on the classpath) — more powerful
   (distributed tracing, span hierarchy) but heavier for a single-instance monolith with no
   downstream services to trace across. A hand-rolled filter is simpler and sufficient here;
   revisit `micrometer-tracing` if the app ever splits into multiple services.
4. Vaadin-specific caveat: a single Vaadin UI session spans many HTTP requests (one per
   server round-trip), so `requestId` correlates *one round-trip*, not "the whole time the user
   had the edit form open" — that's expected and fine; it matches how a save-click, not an
   editing session, is the unit anyone actually wants to correlate.

## Scope note

Purely additive (a new filter + a logging pattern change) — no existing behavior changes, no
schema, no port/hook signature changes. Safe to pick up independently of anything else in the
backlog.
