# improvement-197: REST API contract bugs — If-Match nullability, Link header missing path

**Type:** bug — REST API correctness
**Module:** `marketplace-rest-api`, `advertisement-spring-boot-starter`, `provider-profile-spring-boot-starter`, `taxon-spring-boot-starter`
**Priority:** 🟡 High/medium ROI — real, reproducible contract bugs; proportionate fix effort
**When:** independent, no blockers

## Current state

Found during a targeted REST/API + security delta-audit pass (2026-09-17), verified directly
against the running app and real code — not from a generic checklist. Both findings are new: not
mentioned in `improvement-183` (the ADR that designed this exact If-Match/ETag/pagination shape,
completed 2026-09-08) or anywhere else in `backlog/` (checked by grep for `if-match`/`etag`/
`link header`/`rfc 8288` across `backlog/tasks/`, `backlog/completed/tasks/`, and
`BACKLOG-ARCHIVE.md`).

## Why change

Both are real, deterministic bugs a real API client can hit today, not theoretical or stylistic
concerns — see each phase's failure scenario.

## Expected benefit

`If-Match`'s documented "optional" contract actually behaves as documented, and RFC 8288 `Link`
header navigation (this project's own chosen pagination mechanism, per `improvement-182`) actually
points callers at the right resource.

## Approach — Phase 1: omitting the optional `If-Match` header always fails, never succeeds

**Evidence.** `AdvertisementApiController`/`ProviderProfileApiController`/`TaxonApiController`
declare `If-Match` as `@RequestHeader(value = HttpHeaders.IF_MATCH, required = false)` on
`PUT`/`DELETE` (a deliberate design choice from `improvement-183`, contrasted there with the
*mandatory* `If-Match` on the settings endpoint). `ETagUtil.parseIfMatch(String)`
(`marketplace-rest-api/src/main/java/org/ost/restapi/api/concurrency/ETagUtil.java`) returns `null`
for a missing header. That `null` flows through
`AdvertisementSaveService.delete(id, actorId, version)` /
`AdvertisementPortImpl.delete(id, actingUserId, version)` straight into
`AdvertisementRepository.softDelete(id, deletedByUserId, version)`
(`advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/repository/AdvertisementRepository.java:120-131`),
whose SQL is:
```sql
UPDATE advertisement SET deleted_at = NOW(), deleted_by = :deletedBy, version = version + 1
WHERE id = :id AND version = :version
```
When the bound `:version` parameter is Java `null`, this renders as SQL `version = NULL` — which is
never `TRUE` in standard SQL (`NULL` comparisons evaluate to `UNKNOWN`), so `WHERE` never matches,
`updated == 0` unconditionally, and the method unconditionally throws
`StaleWriteException` → mapped to `412 Precondition Failed` by `ApiExceptionHandler`.
The same `version`-column `UPDATE ... WHERE version = :version` shape is used for the corresponding
update path too, and equivalently for `provider-profile-spring-boot-starter`/
`taxon-spring-boot-starter`'s own repositories (not yet individually re-verified line-by-line —
confirm during implementation, but they share the same `ETagUtil.parseIfMatch` → optional-version
plumbing from the same `improvement-183` redesign).

**Failure scenario:**
```text
DELETE /api/advertisements/1  (no If-Match header — allowed per Swagger's required=false)
→ ETagUtil.parseIfMatch(null) → null
→ AdvertisementRepository.softDelete(1, actorId, null)
→ SQL: ... WHERE id = 1 AND version = NULL   -- never matches, by SQL semantics
→ updated == 0 → StaleWriteException
→ 412 Precondition Failed, even though nobody else touched the resource
```

**Why existing tests/tooling don't already catch this:** confirmed via grep — every existing test
touching `If-Match` (`AdvertisementApiControllerTest`, and the equivalent Taxon/ProviderProfile
tests) always sends the header; none omit it. The "optional" path is completely untested.

**Recommended fix (needs a decision, not assumed here):** either (a) make `If-Match` mandatory on
these endpoints too, matching the settings endpoint's already-established mandatory pattern, and
update the Swagger annotation/docs to say so, or (b) if "optional" is genuinely intended (e.g. "I
don't care about lost-update protection, just do it"), change the repository methods to skip the
version predicate entirely when `version == null` (`WHERE id = :id` alone) rather than binding a
`NULL` that can never match. Option (a) is simpler and matches this project's own explicit-over-
implicit principle; presenting both for a decision before implementing.

**Tests:** add a test per affected endpoint (`PUT`/`DELETE`, all three domains) that omits
`If-Match` entirely and asserts the actually-intended behavior (currently: an incorrect, silent
`412` — the fix should make this either a clear `400`/documented-mandatory error, or a real
successful skip-version-check update, depending on which option is chosen).

## Approach — Phase 2: pagination `Link` header URLs are missing the resource path

**Evidence.** Confirmed live against the running app (`curl`, 2026-09-17):
```text
$ curl -D - "http://localhost:8081/api/advertisements?page=99999"
HTTP/1.1 200
X-Total-Count: 3
Link: <http://localhost:8081?page=0&size=20>; rel="first", <http://localhost:8081?page=99998&size=20>; rel="prev"
```
The `Link` URLs are `http://localhost:8081?page=...` — missing `/api/advertisements` entirely, so
following them hits the application root, not the advertisements collection.
`PageLinkHeaderBuilder.build()`/`linkFor()`
(`marketplace-rest-api/src/main/java/org/ost/restapi/api/paging/PageLinkHeaderBuilder.java`) itself
is correct — it only clones the `UriComponentsBuilder` it's given and replaces the `page`/`size`
query params, never touching the path. The bug is upstream: whatever produces the `UriComponentsBuilder
uriBuilder` auto-injected into `AdvertisementApiController.list(...)` (and presumably the other two
domains' equivalent list endpoints, same `PagedResponseBuilder`/`PageLinkHeaderBuilder` machinery)
isn't carrying the request's real path — not yet root-caused to one exact line; needs isolation
during implementation (candidates: how this app's co-hosted Vaadin servlet mapping interacts with
Spring MVC's default `UriComponentsBuilder` argument-resolution, or a `WebMvcConfigurer`/path-match
setting affecting only `/api/**`).

**Failure scenario:**
```text
GET /api/advertisements?page=0  (client is paginating, following the documented RFC 8288 contract)
→ response Link header: <http://localhost:8081?page=1&size=20>; rel="next"
→ client follows it → GET http://localhost:8081?page=1&size=20
→ hits the Vaadin app root, not the advertisements API — pagination navigation is broken
```

**Why existing tests don't already catch this:** `AdvertisementApiControllerTest`'s only Link-header
assertion (`list_notLastPage_setsLinkHeaderWithNext`) is
`header().string("Link", containsString("rel=\"next\""))` — it never asserts the URL's path, so a
missing path segment passes silently. Also worth confirming during implementation whether MockMvc's
own mock request environment can even reproduce this bug at all (it may only manifest in a real
deployed servlet container, given this app's Vaadin+REST co-hosting) — if so, the regression test
needs to run against a real deployment (e.g. an `integration-tests` level3 REST scenario hitting a
real embedded server), not `MockMvc` alone.

**Recommended fix:** once root-caused, either construct the `UriComponentsBuilder` explicitly from
the current request's real path (e.g. `ServletUriComponentsBuilder.fromCurrentRequest()`) instead of
relying on Spring's auto-injection, or fix whatever is stripping the path from the auto-injected
instance.

**Tests:** strengthen `list_notLastPage_setsLinkHeaderWithNext` (and add the missing `rel="prev"`/
`"first"`/`"last"` cases) to assert the full URL, not just a `containsString` on the `rel` value —
and add a real-deployment-level check (see above) since this exact bug already slipped past the
MockMvc-only assertion once.

## Related

- `marketplace-app/DECISIONS.md` — the ADR from `improvement-183` that designed the If-Match/ETag/
  pagination shape Phase 1 and Phase 2 both build on.
- `backlog/completed/tasks/improvement-182-rest-api-filter-sort-pagination-parity-with-ui.md` — the
  ADR that chose RFC 8288 `Link` + `X-Total-Count` over Spring HATEOAS/an envelope body; Phase 2's
  bug is in that mechanism's own implementation, not the design choice itself.
- [improvement-196](improvement-196-rest-api-rate-limiting.md) — a separate, unrelated REST API
  finding from the same general delta-audit effort (per-API-key rate limiting), filed the same day.
