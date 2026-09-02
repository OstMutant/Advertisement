# improvement-073: Add REST API infrastructure — dev-gated Playwright seeding endpoints + a real external/public API (not the first REST controller — see correction)

**Type:** improvement — new infrastructure capability, prerequisite for improvement-035 and any
future REST-dependent work. Filed 2026-07-16 after deciding a browser-driven Playwright spec
(06-seed-filter-sort-pagination) needs a faster, audit-trail-correct seeding path than either raw
SQL (breaks the spec's own timeline assertions — see improvement-035's correction) or full UI
automation (slow — the actual thing being optimized away). Scope widened (2026-09-01, explicit
user request) to also cover a genuinely public-facing, prod-reachable REST API for external
consumers — a different audience and security posture than the dev-gated seeding endpoints, but
sharing the same base routing/controller infrastructure, so tracked in one issue rather than two.
**Module:** `marketplace-app` (new `web/` or `api/` package for REST controllers, `SecurityConfig`).
**Priority:** 🟡 high — no longer a "nice to have": real external API is a stated product goal, and
`improvement-111` (authorization at the service boundary) is a hard gate that must land before or
alongside the external-facing portion of this work.
**When:** Test-seeding portion (dev-gated, non-prod-reachable) can proceed independently — do this
before improvement-035 (which depends on it). The external/public API portion is blocked on
`improvement-111` landing first or in the same batch — do not ship a prod-reachable mutation
endpoint before the service-boundary authorization gap is closed.

## External API scope (added 2026-09-01)

Distinct from the test-seeding endpoints below in every way that matters for security:

- **Audience:** real external consumers (not Playwright test code), reachable in `prod`.
- **Security posture:** the opposite of the seeding endpoints' `@Profile("!prod")` gate — this must
  work correctly *in* `prod`, which is exactly why `improvement-111`'s service-boundary
  authorization gap cannot stay open once this ships. `HealthController`'s `permitAll()` precedent
  does not apply here; this needs real per-request authentication/authorization, not a public rule.
- **Known future consumer, from the private roadmap:** `private/features/F-07-phone-verification.md`
  already plans a Telegram Bot API webhook (`@RestController`) and external SMS-provider REST calls
  — this issue's infrastructure (routing conventions, `SecurityConfig` patterns, controller
  package structure) is what that future feature will build on, so shape it generically rather than
  narrowly for one caller.
- **Exact endpoint shape/business scope:** not yet decided — a separate design pass, once this
  issue's infrastructure and `improvement-111`'s authorization fix are both in place.

## Correction (2026-07-16): this app already has one REST controller — `HealthController`

The original wording claimed "no REST controllers at all today." **Confirmed wrong**:
`org.ost.marketplace.rest.HealthController` (`GET /health`) already exists, already documented in
`marketplace-app/CLAUDE.md`'s Security section as "intentionally public (load balancer probe)."
It even already follows the exact precedent this issue needs — its own explicit
`requestMatchers("/health").permitAll()` rule in `SecurityConfig`, ahead of the
`anyRequest().permitAll()` catch-all (confirmed directly in `SecurityConfig.java`). What this app
does **not** have is any REST endpoint that (a) is gated to non-prod environments, or (b) invokes
real business/service-layer logic rather than returning a static string — `HealthController` does
neither. The `marketplace-app/CLAUDE.md` correction landed alongside this one (see that file's
Security section).

## Problem

Playwright specs are Node.js and cannot call Java service methods directly; the only way to seed
test data through the *real* application service layer (`UserService.register()`,
`AdvertisementSaveService.save()`, so `audit_log` rows and all other side effects are produced
correctly, unlike raw SQL) from a Playwright spec is via an HTTP call into the running app — which
requires an endpoint that does more than `HealthController`'s static response, and one that must
never be reachable in a production deployment (unlike `/health`, which is meant to stay public
everywhere).

## Suggested fix

- Add a dedicated REST controller package (e.g. `org.ost.marketplace.web` or `.api`, sibling to the
  existing `rest/` package — naming TBD, may just extend `rest/`), active **only** outside the
  `prod` profile (`@Profile("!prod")` or equivalent) — this must never be reachable in a production
  deployment, unlike `/health`.
- Explicit `SecurityConfig` handling for this new path prefix, following the exact precedent
  `HealthController` already set (`requestMatchers("/test-seed/**").permitAll()` or equivalent,
  ahead of the catch-all) rather than relying on the pre-existing `anyRequest().permitAll()`
  catch-all to silently cover it.
- First endpoint(s): whatever improvement-035 needs to seed users/advertisements through the real
  service layer (e.g. `POST /test-seed/users`, `POST /test-seed/advertisements`) — exact shape is
  improvement-035's concern, this issue only needs to land the general capability (routing,
  profile-gating, security rule) that any REST endpoint in this app — test-seeding or otherwise —
  would need going forward.

## Related

- [improvement-035](improvement-035-sql-seeding-for-playwright-spec-05.md) — the concrete consumer
  of this infrastructure; blocked on this issue.
- `marketplace-app/CLAUDE.md` "Security: @PreAuthorize and Vaadin" — the existing
  security-config context this issue extends.
- `marketplace-app/DECISIONS.md` ADR-025 — the `anyRequest().permitAll()` decision this issue's
  new endpoint(s) would sit under.
