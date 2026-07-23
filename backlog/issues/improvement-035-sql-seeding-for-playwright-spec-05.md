# improvement-035: Service-layer seeding for Playwright spec 05 — full e2e 11 min → ~7-8 min (NOT raw SQL — see correction below)

**Type:** improvement — test performance. Migrated from `backlog/process-improvements.md` Part 1,
item 6.
**Module:** `playwright/e2e/05-seed-filter-sort-pagination.spec.js`
**Priority:** low — pure speed optimization, no coverage gap; the current approach is slow but not
wrong; further deprioritized behind its new prerequisite (see When).
**When:** blocked on [improvement-073](improvement-073-rest-endpoint-infrastructure-test-seeding.md)
(REST endpoint infrastructure — this app has none today, and the service-layer seeding approach
below needs one to call into `UserService`/`AdvertisementSaveService` from a Playwright spec).
Deliberately sequenced after everything achievable on the existing codebase without new
infrastructure — revisit if the ~9-10 min full-suite runtime becomes a real friction point (it
hasn't blocked anything so far this session, per the actual observed runtimes: 48/48 in 9.6-9.7 min).

## Problem

Spec `05-seed-filter-sort-pagination` seeds 50 users + 50 advertisements by driving the actual
signup/creation UI in the browser (measured: ~1.3 min for the 50 users, ~45-57s for the 50 ads).
The signup flow itself is already covered by spec 01/02; re-exercising it 50 times through the UI
adds runtime, not additional coverage — it's needed only to produce enough rows for the
filter/sort/pagination assertions later in the same spec.

## Correction (2026-07-16): raw SQL seeding does not work for this spec — do not implement as originally worded

The original "Suggested fix" below listed a raw SQL fixture as the primary option. **Confirmed this
does not work**: the same spec's Test 6 (`adminEn verifies timeline`) opens the Timeline tab and
asserts CREATED/UPDATED action filters, entity-type filters, and pagination — all of which depend
on `audit_log` rows existing for the seeded users/advertisements. A raw `INSERT INTO
user_information`/`INSERT INTO advertisement` bypasses `UserService`/`AdvertisementSaveService`/
`AuditPort` entirely, so **no audit_log rows would be created** for the 100 seeded entities — Test
6 would fail outright (empty timeline, no pagination, filters showing nothing). Manually crafting
matching `audit_log` rows via SQL (including correctly-shaped snapshot JSON) would duplicate the
real `AuditableSnapshot` serialization logic in raw SQL — exactly the kind of "two sources of
truth" drift risk this backlog has already found real bugs from elsewhere (e.g. the `POSTGRES_IMAGE`
duplication class of issue noted in `integration-tests/CLAUDE.md`).

## Suggested fix (revised — service layer, not raw SQL)

- Seed the 50 users + 50 advertisements by calling the **real application service layer**
  in-process (a test-only seeding endpoint, or a directly-invoked Spring service from a setup
  script/test hook) — i.e. actually calling `UserService.register(...)`/
  `AdvertisementSaveService.save(...)`, not the browser UI and not raw SQL. This produces fully
  correct `audit_log` rows (same code path as normal signup/creation) while skipping the slow
  browser rendering/interaction layer that's the actual cost being optimized away.
- Keep exactly one UI-created entity in the seeding step to preserve an actual end-to-end
  creation path being exercised somewhere in the suite.
- Once seeding is confirmed data-isolated (no cross-test interference), gradually enable
  `workers: 2` in the Playwright config (currently `workers: 1, fullyParallel: false` per
  `playwright.config.js`) for further speedup.

## Related

- [improvement-073](improvement-073-rest-endpoint-infrastructure-test-seeding.md) — the REST
  endpoint infrastructure this issue now depends on; that issue lands the general capability
  (routing, profile-gating, security rule), this one adds the specific seeding endpoints and
  updates the spec to call them.
- `backlog/process-improvements.md` Part 1, item 6 — source item, now superseded by this issue.
- `playwright/e2e/05-seed-filter-sort-pagination.spec.js` Test 6 (`adminEn verifies timeline`) —
  the audit-trail dependency that rules out raw SQL seeding.
- `playwright/CLAUDE.md` — documents the current `--full` flag behavior this issue would change
  the internals of (external behavior — "seeds 50 users + 50 ads" — stays the same; only the
  mechanism changes from UI-driven to service-layer-driven).
