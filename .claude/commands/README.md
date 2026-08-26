# `.claude/commands/`

Slash-command definitions, each invoked directly by name (`/feature`, `/ci`, ...). Every command
here is independent — no file-to-file call chain between them, so no Flow diagram; each is its own
self-contained entry point, described below and linked to its real file.

- [`/autopilot`](autopilot.md) — Plan once, approve once, then execute the whole task end-to-end
  without further check-ins — implementation, all relevant tests, docs/ADR, issue lifecycle —
  reporting back only when genuinely done (or genuinely blocked).
- [`/build-and-test`](build-and-test.md) — Builds the whole reactor inside the shared
  build-and-test container (works even without a local Java install) — refreshes
  `marketplace-app.jar` in the shared `maven-cache` volume. Optionally runs unit/integration tests
  too.
- [`/ci`](ci.md) — Run the local, isolated, parameterized CI runner (`scripts/ci.sh` ->
  `scripts/ci/run.sh`).
- [`/deploy-and-run`](deploy-and-run.md) — Rebuild the `marketplace-app` Docker image and start a
  fresh container using the project deploy script.
- [`/feature`](feature.md) — Scaffold a new tracked issue in `backlog/issues/` from the standard
  template, then rank it in `backlog/BACKLOG.md`'s priority table.
- [`/new-domain`](new-domain.md) — Create a complete new UI domain in `marketplace-app` following
  the established patterns.
- [`/playwright`](playwright.md) — Run Playwright UI tests for the marketplace app.
- [`/record-decision`](record-decision.md) — Record a new architectural decision in the
  appropriate `DECISIONS.md` file.
- [`/review`](review.md) — Run a full code review via the `deep-review-orchestrator` agent --
  evidence-verified SOLID/DRY/KISS/YAGNI findings, never writes code itself.
- [`/run-all-tests`](run-all-tests.md) — Run all three test suites for daily iteration:
  unit-tests, integration-tests, and Playwright.
- [`/screenshots`](screenshots.md) — Find and read named screenshots from the last Playwright test
  run.
- [`/sonar`](sonar.md) — Run SonarQube static analysis for the marketplace app.
- [`/sync-docs`](sync-docs.md) — Analyze what changed in the codebase and update only the affected
  architecture documentation.
