# improvement-073: Add test-only, dev-gated REST endpoints for Playwright seeding (not the first REST controller — see correction)

**Type:** improvement — new infrastructure capability, prerequisite for improvement-035 and any
future REST-dependent work. Filed 2026-07-16 after deciding a browser-driven Playwright spec
(05-seed-filter-sort-pagination) needs a faster, audit-trail-correct seeding path than either raw
SQL (breaks the spec's own timeline assertions — see improvement-035's correction) or full UI
automation (slow — the actual thing being optimized away).
**Module:** `marketplace-app` (new `web/` or `api/` package for REST controllers, `SecurityConfig`).
**Priority:** low — deliberately sequenced *after* everything achievable on the existing codebase
without new infrastructure; genuinely new surface area (this app's first *profile-gated,
business-logic-invoking* REST endpoint — `HealthController` is neither) and shouldn't be rushed
ahead of cheaper, already-scoped fixes.
**When:** blocked by nothing technically, but intentionally deprioritized — see Priority. Do this
before improvement-035 (which depends on it), not before the rest of the currently-open backlog.

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
