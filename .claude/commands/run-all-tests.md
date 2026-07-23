Run all three test suites for daily iteration: unit-tests, integration-tests, and Playwright.

Usage: /run-all-tests [--unit "..."] [--integration "..."] [--playwright "..."] [--background]
Examples:
  /run-all-tests
  /run-all-tests --unit "AccessEvaluatorTest" --integration "--sandbox smoke" --playwright "01-marketplace-empty-flow --ux"
  /run-all-tests --background

unit-tests -> integration-tests run sequentially (both can compile the same starter modules'
target/ dirs -- concurrent runs risk a Maven build race, see scripts/DECISIONS.md ADR-004), while
playwright runs in parallel from the start (no Maven reactor involved, only an already-running
Docker container -- see scripts/CLAUDE.md and playwright/CLAUDE.md). unit-tests, integration-tests,
and Playwright use fully separate databases (dev Postgres on 5432 for Playwright's app container
vs. an ephemeral Testcontainers Postgres for integration-tests, different port and DB name) -- no
cross-suite data interference.

Steps:
1. Parse $ARGUMENTS: forward everything after --unit/--integration/--playwright verbatim to that
   suite. If a --playwright block is present and doesn't already include --ux, append it (project
   convention — always run Playwright with --ux).
2. Launch two Monitor tool calls (persistent: true):
   - watching scripts/run-all-tests/reports/unit-then-integration.log every 10s, catch
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
