# improvement-182: REST API list endpoints ignore filters/sort entirely — wire query params through to existing service-layer capability, add Link-header pagination

**Type:** improvement — REST API completeness/maturity gap
**Module:** `marketplace-rest-api` (`AdvertisementApiController`, `ProviderProfileApiController`,
`TaxonApiController`), `marketplace-orchestrator` (`TaxonCatalogService` gains a paginated/counted
variant).
**Priority:** 🟡 medium-high
**When:** independent, no blockers — builds on already-shipped
[improvement-073](completed/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md).

## Current state

`GET /api/advertisements`/`GET /api/provider-profiles` hardcode `<Domain>FilterDto.empty()` and
`Sort.unsorted()` when calling their own `ReadService` — no filter or sort query parameter is
exposed at all, only offset `page`/`size` pagination. `GET /api/taxons` has an even narrower shape
(`type`+`locale` only), no pagination of any kind.

The service layer underneath already has everything needed and is already used correctly by the
Vaadin UI's own query bar:
- `AdvertisementReadService.getFiltered(filter, page, size, sort)` + `.count(filter)`, and the same
  pair on `ProviderProfileReadService`, already live in `marketplace-orchestrator`.
- `TaxonCatalogService.getAllByType(type, locale)` returns the full list — no paginated/counted
  variant exists yet for this domain.
- `AdvertisementFilterDto`/`ProviderProfileFilterDto` already carry real Jakarta Bean Validation
  constraints (`@Size`, `@ValidRange` for date-range fields) — already exercised by `POST`/`PUT` via
  `@Valid` and `ApiExceptionHandler`'s `MethodArgumentNotValidException` → 400 mapping, but never
  exercised for `GET` today since no real filter DTO is ever constructed from a request.
- The UI's own field-name source of truth for filter/sort is already the DTOs' own
  `@FieldNameConstants`-generated `Fields.*` constants (`AdvertisementFilterDto.Fields.*`,
  `AdvertisementInfoDto.Fields.*`) — `AdvertisementFilterMeta`/`AdvertisementSortMeta` (UI-only
  wrapper classes, Vaadin/`I18nKey`-bound) just reference the same constants.
- `marketplace-app` depends on `marketplace-rest-api` (not the reverse) — `marketplace-rest-api`
  architecturally cannot import `AdvertisementFilterMeta`/`AdvertisementSortMeta` even if it wanted
  to; the real shared source of truth for "which fields exist" is the DTOs' own `Fields.*`
  constants, reachable from both sides via `platform-commons`.
- `query-lib/DECISIONS.md` ADR-003 explicitly freezes that library's SQL-DSL scope, rejecting
  "generic pagination abstractions" (with one narrow, already-existing exception,
  `PaginationSqlBuilder`'s `LIMIT`/`OFFSET` builder) — new pagination-response-shaping code belongs
  at the HTTP-adapter layer (`marketplace-rest-api`), not `query-lib`.

## Why change

The external REST API's list endpoints are functionally crippled for a real external consumer — no
way to search by title, date range, category, city, ad kind, etc., no way to sort — despite the
exact same capability already existing and working in the Vaadin UI and the orchestrator service
layer underneath. This is a real functional gap against the issue's own stated goal
(`improvement-073`: "a genuinely public-facing, prod-reachable REST API for external consumers").

## Expected benefit

- `GET /api/advertisements`/`GET /api/provider-profiles`/`GET /api/taxons` support the same real
  filters and sortable fields as the UI's own query bar, validated by the exact same constraints —
  no drift between UI and REST validation, since both read the same annotated DTOs.
- Pagination becomes genuinely navigable for an external client: real `next`/`prev`/`first`/`last`
  links, not just a bare offset the caller has to compute themselves.
- No new abstraction added to `query-lib` (respects its frozen-scope ADR-003); no new heavy
  dependency (Spring HATEOAS) added just for pagination links.

## Approach

1. **Filters:** bind `GET` query parameters directly onto the existing `AdvertisementFilterDto`/
   `ProviderProfileFilterDto` via Spring MVC's `@ModelAttribute @Valid` binding (matches property
   names automatically — same DTO already used for create/update, no parallel copy).
   `MethodArgumentNotValidException` already maps to 400 via the existing `ApiExceptionHandler`.
2. **Sort:** a `?sort=field,dir` query parameter (matches the Spring Data `Pageable`/`Sort`
   convention `AdvertisementReadService`'s own `Sort` parameter already expects), parsed and
   validated against an explicit allow-list built from each domain's own `Fields.*` constants
   (compiler-checked, not raw strings — same discipline `AdvertisementSortMeta` already applies
   UI-side) — an unknown field name is a 400, not a silent no-op or a 500. This parsing/validation
   lives in `marketplace-rest-api` as a small shared helper reused by all three controllers — never
   in `marketplace-orchestrator` (stays transport-agnostic, per root `CLAUDE.md`'s 3-layer
   principle) and never in `query-lib` (frozen scope, ADR-003).
3. **Pagination links:** `Link` HTTP response header (RFC 8288, `rel="next"`/`"prev"`/`"first"`/
   `"last"`, only the relations that actually apply at each page) plus an `X-Total-Count` header
   carrying the real `count(filter)` value — same convention GitHub's/GitLab's own REST APIs use.
   Response body stays a plain `List<T>`, unchanged shape — no breaking change for anything already
   consuming the current array response, no new Spring HATEOAS dependency.
4. **Taxon:** gets the same treatment as the other two domains. `TaxonCatalogService` needs a new
   paginated/counted method added first (currently `getAllByType` returns the unpaginated full
   list) before `TaxonApiController` can apply the same query-param/sort/`Link`-header wiring.

## Implementation (2026-09-04, `/autopilot`)

Built as planned above, with one scope refinement found mid-implementation: `TaxonPort` had **no**
pagination/count capability at any layer (unlike Advertisement/ProviderProfile, whose service layer
already had it) — the repository (`TaxonRepository.findAllByType`) already had filter/sort
infrastructure (`TaxonFilter`, `SqlFilterBuilder`, `OrderByBuilder`), just no `LIMIT`/`OFFSET` or a
count method. Threaded pagination all the way down: `TaxonRepository`/`TaxonService`'s `Sort`
parameter became `Pageable` (the 3 pre-existing unpaginated callers now pass
`Pageable.unpaged(sort)`/`Pageable.unpaged()`, no behavior change), new `TaxonPort.getPageByType`/
`.count` (mirroring `AdvertisementPort`/`ProviderProfilePort`'s already-established shape exactly),
new `TaxonFilterDto` (`platform-commons`, mirrors internal `TaxonFilter`'s one real field, `name`).
Taxon's own sort allow-list is `id` only (not `createdAt`/`updatedAt`, which the repository
technically supports but `TaxonDto` itself doesn't carry as response fields).

New shared `marketplace-rest-api` package `org.ost.restapi.api.paging`: `SortQueryParser`,
`PageLinkHeaderBuilder`, `PagedResponseBuilder` (the third extracted after a `/review` pass — see
below). Recorded as `marketplace-app/DECISIONS.md` ADR-080 (also affects `marketplace-rest-api`,
`taxon-spring-boot-starter`) — the Link-header-vs-envelope-vs-HATEOAS decision and the filter/sort
architecture reasoning are both future-constraining enough to clear the ADR bar.

**`/review` finding applied:** `deep-review-orchestrator` found the three controllers' `list()`
methods duplicated the same 7-statement response-assembly sequence verbatim. Extracted into
`PagedResponseBuilder` (low-risk dedup, applied directly per autopilot's own default-to-applying
rule — not deferred to a new issue).

**Verified:** unit 255/255 (`marketplace-rest-api` 49, up from 27), integration 186/186 (3 new
`TaxonRepository` pagination/count tests, real Postgres), `ArchitectureRulesTest` 20/20 unaffected.
Full `bash scripts/ci.sh` (unit → integration → e2e → sonar → archunit → docs): build/unit/
integration/e2e/archunit_metrics/pipeline_metrics/docs all succeeded; `sonar`'s quality gate failed
only on the pre-existing, separately-tracked `new_coverage=0%` gap (`improvement-114`, JaCoCo not
wired into the scan) — confirmed via direct SonarQube REST API query (`dagu-analyst`): zero new
BUG/CRITICAL/BLOCKER issues, `new_violations=0`. Along the way, this same Sonar pass caught and
fixed one real, unrelated pre-existing CRITICAL finding (`java:S1192`, the literal `"actorId"`
duplicated 3× in `apikey-spring-boot-starter/.../ApiKeyRepository.java`, from the earlier
`improvement-073` commit) — extracted into a `PARAM_ACTOR_ID` constant. A first `ci.sh --sonar`
pass also failed its `docs` stage (`adr-index.md` stale) — self-inflicted: `DECISIONS.md`/
`adr-index.md` were edited on the host while that run's own `docker build` was already in flight,
racing its `COPY . .` snapshot, exactly the failure mode `.claude/commands/autopilot.md`'s own
step-2 exception warns about. Fixed by not editing those files while a triggered run is in flight,
and re-triggering a fresh full run once they were stable.

## Related

- [improvement-073](completed/issues/improvement-073-rest-endpoint-infrastructure-test-seeding.md)
  — the REST API infrastructure this issue extends; `AdvertisementReadService.getFiltered`/`.count`
  already shipped there.
- `query-lib/DECISIONS.md` ADR-003 — frozen SQL-DSL scope, the boundary this issue's new code must
  stay outside of.
- `.claude/rules/marketplace-rest-api.md` — this module's own constraints (no direct `*Port`/
  `*Hook` usage, must not import starter internals, etc.) any new code here must keep following.

## Operational notes
- token_cost_review: 255177
- token_cost_research: n/a
- token_cost_verification: 114945
- review_signal_ratio: 1 / 2
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: implement a scoped, already-approved REST feature end-to-end
- flows_chosen: /autopilot
- flows_matched: yes

### Agent calls
- Code review of filter/sort/pagination diff | subagent_type=deep-review-orchestrator | tokens=105268 | tool_uses=82 | duration_s=439 | mode=background | batch=solo
- Diagnose failed sonar/docs CI stages | subagent_type=dagu-analyst | tokens=68892 | tool_uses=9 | duration_s=43 | mode=background | batch=solo
- Diagnose latest sonar stage failure | subagent_type=dagu-analyst | tokens=46053 | tool_uses=7 | duration_s=40 | mode=background | batch=solo

### Script/command runs
- scripts/build-and-test.sh --unit --integration (initial implementation) | duration_s=169 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration (after DRY fix) | duration_s=137 | mode=background | result=pass
- docs/architecture/scripts/generate-architecture-model.sh (x2, after ADR-080/.claude/rules updates) | duration_s=n/a | mode=background | result=pass
- .claude/nav/scripts/generate-adr-index.sh | duration_s=n/a | mode=foreground | result=pass
- scripts/ci.sh --sonar | duration_s=n/a | mode=background | result=fail (sonar: pre-existing new_coverage gap; docs: self-inflicted stale-snapshot race, see Implementation notes above)
- scripts/ci.sh (full, after fixing the S1192 finding and the race) | duration_s=n/a | mode=background | result=partially_succeeded (sonar: same pre-existing new_coverage gap only; build/unit/integration/e2e/archunit_metrics/pipeline_metrics/docs all passed)

### Review angle yield
- dry-kiss-yagni | survived=1 | total_candidates=1 | tokens=77943
- solid | survived=0 | total_candidates=0 | tokens=83768
- precedent | survived=0 | total_candidates=1 | tokens=93466
