Run all three test suites for daily iteration: unit-tests, integration-tests, and Playwright.

Usage: /run-all-tests [--unit-test <arg>] [--integration-test <arg>] [--sandbox] [--archunit-metrics] [--reset] [--playwright "..."] [--background]
Examples:
  /run-all-tests
  /run-all-tests --unit-test AccessEvaluatorTest --integration-test smoke --sandbox --playwright "01-marketplace-empty-flow --ux"
  /run-all-tests --background
  /run-all-tests --archunit-metrics
  /run-all-tests --reset

Runs scripts/build-and-test.sh --unit --integration (installs the whole reactor once, then runs
unit+integration tests in parallel against it -- see scripts/DECISIONS.md ADR-004's annotation) in
parallel with a deploy-and-run.sh + playwright.sh sequence: deploy-and-run.sh always clears app
data first (--reset-only-db by default -- fast, truncate-only; --reset when --reset is passed to
this command -- full DB/MinIO volume wipe, only needed when the schema itself changed), then
playwright.sh runs once that succeeds, so it always tests a freshly-rebuilt marketplace-app
container against guaranteed-clean data (see playwright/CLAUDE.md's own "Always deploy with a
clean database" rule -- this is what enforces it automatically). unit/integration and Playwright
use fully separate databases (dev Postgres on 5432 for Playwright's app container vs. an ephemeral
Testcontainers Postgres for integration tests, different port and DB name) -- no cross-suite data
interference. --sandbox is only needed inside this claude-dev sandbox (Testcontainers/Ryuk
networking workaround) -- never pass it on a real developer machine.

Steps:
1. Parse $ARGUMENTS: --unit-test/--integration-test take one value each (module/class name),
   --sandbox, --archunit-metrics, and --reset are bare flags, --playwright takes one quoted block
   forwarded verbatim to playwright.sh. If a --playwright block is present and doesn't already
   include --ux, append it (project convention — always run Playwright with --ux).
2. Launch two Monitor tool calls (persistent: true):
   - watching scripts/run-all-tests/reports/build-and-test.log every 10s, catch
     PASSED|FAILED|ERROR|BUILD SUCCESS|BUILD FAILURE
   - watching scripts/run-all-tests/reports/playwright.log every 10s, catch passed|failed|Error
3. Default (no --background): run synchronously in foreground with tee, same pattern as every
   other test script:
   ```
   bash scripts/run-all-tests.sh [grouped args] 2>&1 | tee /tmp/run-all-tests.log
   ```
   with timeout: 600000
4. --background: run the same command with run_in_background: true instead, so the conversation
   stays open; report back when the harness notifies completion. Only use this mode when the user
   explicitly passes --background — default always stays synchronous.
5. After completion — call TaskStop on both monitor tasks if not already stopped.
6. Report the final summary (ALL PASSED / SOME FAILED) plus which suite(s) failed and why, reading
   the actual failing log lines from the relevant report file — never just "it failed."
