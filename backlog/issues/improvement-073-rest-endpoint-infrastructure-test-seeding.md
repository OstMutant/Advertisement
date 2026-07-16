# improvement-073: Add REST endpoint infrastructure (test-only, dev-gated) — first non-Vaadin controller in this app

**Type:** improvement — new infrastructure capability, prerequisite for improvement-035 and any
future REST-dependent work. Filed 2026-07-16 after deciding a browser-driven Playwright spec
(05-seed-filter-sort-pagination) needs a faster, audit-trail-correct seeding path than either raw
SQL (breaks the spec's own timeline assertions — see improvement-035's correction) or full UI
automation (slow — the actual thing being optimized away).
**Module:** `marketplace-app` (new `web/` or `api/` package for REST controllers, `SecurityConfig`).
**Priority:** low — deliberately sequenced *after* everything achievable on the existing codebase
without new infrastructure; this is genuinely new surface area (first REST controller in an
otherwise Vaadin-only app) and shouldn't be rushed ahead of cheaper, already-scoped fixes.
**When:** blocked by nothing technically, but intentionally deprioritized — see Priority. Do this
before improvement-035 (which depends on it), not before the rest of the currently-open backlog.

## Problem

This app has **no REST controllers at all today** — `marketplace-app/CLAUDE.md`'s own Security
section only anticipates this as a future possibility ("Any future non-Vaadin REST controller must
add its own explicit `requestMatchers(...)` rule ahead of the catch-all"). Playwright specs are
Node.js and cannot call Java service methods directly; the only way to seed test data through the
*real* application service layer (`UserService.register()`, `AdvertisementSaveService.save()`, so
`audit_log` rows and all other side effects are produced correctly, unlike raw SQL) from a
Playwright spec is via an HTTP call into the running app — which requires an actual endpoint to
exist.

## Suggested fix

- Add a dedicated REST controller package (e.g. `org.ost.marketplace.web` or `.api`), active
  **only** outside the `prod` profile (`@Profile("!prod")` or equivalent) — this must never be
  reachable in a production deployment.
- Explicit `SecurityConfig` handling for this new path prefix — the existing
  `anyRequest().permitAll()` catch-all (see `marketplace-app/DECISIONS.md` ADR-025) already covers
  it technically, but this issue should make that an explicit, deliberate rule for the new prefix
  rather than relying on the pre-existing catch-all silently covering it.
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
