# improvement-214: Administrator credentials from environment variables, shared by all automation

**Type:** improvement — developer and test automation convenience
**Module:** repo-root `.env` (and its documentation), `scripts/deploy-and-run/` (compose environment), `playwright/e2e` (test-user helpers), `integration-tests` (REST scenario tests), `docs/api-collections/postman/` (collection variables), the API flow runner defaults ([improvement-212](improvement-212-api-flow-runner-page.md)), `README.md`
**Priority:** high — Top, first: every other task that needs an administrator account (212, 213) reads these values
**When:** independent; do before [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md) and [improvement-212](improvement-212-api-flow-runner-page.md) rely on an agreed administrator

## Current state

Verified against code, 2026-10-17. The first account registered becomes `ADMIN` and every later one `USER` (`UserService.register`, line 115 and 120: `isFirstUser = count == 0`); this holds for both the Vaadin sign-up and `POST /api/users`, and it is the documented rule (`README.md`, `.claude/rules/user-spring-boot-starter.md`). `scripts/deploy-and-run/reset-clean.sql` empties the user tables, so after a reset the next registration is the administrator again. Nothing names the account that is meant to register first: the Playwright helpers (test-user fixtures), the integration tests and the Postman collection each carry their own test accounts, so automation cannot know which credentials belong to the administrator. Roles are `USER`, `ADMIN`, `MODERATOR`; no REST endpoint changes a role.

## Why change

Automated processes (end-to-end tests, REST scenario tests, the flow runner, the Postman collection) all need the same administrator account, and it is currently re-invented in each of them.

## Expected benefit

One agreed pair of values that every tool uses for the account it registers first, so a reset followed by any automated run produces the same, known administrator.

## Approach

1. **Define the pair (done 2026-10-17):** `ADMIN_EMAIL=admin.en@example.com` and `ADMIN_PASSWORD=password` in the repo-root `.env`, the values of the Playwright `adminEn` user (the first account its suite signs up). The application does not read them and creates nothing at startup; they are read by tooling only.
2. **Use them everywhere automation needs an administrator** (Postman collection variables done 2026-10-17; the Playwright `adminEn` user already has the same values but they are not yet read from `.env`, which needs `playwright/run.sh` to forward them into the runner container; the REST scenario tests register a fresh random user per scenario and have no fixed administrator, so nothing to replace there; the flow runner does not exist yet): the Playwright helpers, the integration-test fixtures, the Postman collection variables and the flow runner's Authentication defaults take their administrator account from these values instead of their own copies.
3. **The registration rule is unchanged:** after a reset (`deploy-and-run.sh --reset-only-db`) the automation registers this account first, so it becomes `ADMIN`. A moderator is made by an administrator through the role-change endpoint planned in [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md).
4. **Documentation:** a comment beside the values in `.env` and a note in the deploy script's README that they are development values and must never be used on a real deployment.
5. **Out of scope, still open:** the registration race and the first-registrant-wins weakness on a new production instance ([improvement-052](improvement-052-first-admin-registration-toctou-race.md)). Whether production should get an explicit, configured administrator instead is a separate decision for pre-production hardening; it is not part of this task.

## Related

- [improvement-052](improvement-052-first-admin-registration-toctou-race.md) — the registration race, left open
- [improvement-213](improvement-213-rest-api-revision-parity-with-ui.md) — REST/UI parity audit, including role changes
- [improvement-212](improvement-212-api-flow-runner-page.md) — flow runner whose Authentication defaults use these values

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
