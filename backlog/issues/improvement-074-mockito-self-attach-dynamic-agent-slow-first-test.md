# improvement-074: Mockito dynamic self-attach adds a 40-90s one-time delay to whichever test runs first in each Maven test JVM fork

**Type:** improvement — build/test performance, found via a direct performance investigation
requested mid-session (2026-07-16) into why `scripts/unit-tests.sh`'s "Marketplace" reactor module
was taking ~4:43 min versus ~85s combined for all 7 other modules.
**Module:** root `pom.xml` (Surefire/dependency-plugin configuration, inherited by every module) —
affects any module whose tests use Mockito: confirmed present via `spring-boot-starter-test` in
`marketplace-app`, `query-lib`, `taxon-spring-boot-starter`, `integration-tests`.
**Priority:** highest — cheap, mechanical, zero-risk fix (Mockito's own documented recommendation)
that removes a real, reproducible 40-90s tax from every `mvn test` run touching any of these
modules; disproportionately high ROI for the size of the change.
**When:** next — no blockers, no design decision needed, config-only change.

## Problem

Every Maven test JVM fork pays a one-time 40-90 second penalty the first time any test creates a
Mockito `@Mock`. Confirmed directly, not guessed:

- `scripts/unit-tests/reports/run.log` shows this warning verbatim the first time a mock is
  created in a fork:
  ```
  Mockito is currently self-attaching to enable the inline-mock-maker. This will no longer work
  in future releases of the JDK. Please add Mockito as an agent to your build as described in
  Mockito's documentation...
  WARNING: A Java agent has been loaded dynamically (byte-buddy-agent-1.17.8.jar)
  WARNING: Dynamic loading of agents will be disallowed by default in a future release
  ```
- This is **not specific to any one test class's logic**. Proven by isolating two different test
  classes and running each alone (thus each is "first" in its own fork):
  - `AuthServiceTest` (5 trivial mock-based tests, no sleeps/waits in source or test) — 41.47s to
    59.33s across repeated runs.
  - `AccessEvaluatorTest` (17 tests, 1.295s when run as part of the full suite where it isn't
    first) — **43.42s** when run alone/first, i.e. the exact same ~40s tax landed on a
    completely different, otherwise-fast test class.
  - Conclusion: the delay is a fixed, environment-caused cost of the *first* Mockito mock
    creation per JVM fork (JDK 25 + this sandbox's constrained dynamic-agent-attach path), not a
    property of whichever test happens to trigger it.
- Project runs on JDK 25 (`Temurin-25.0.3+9`), where the JDK increasingly restricts/deprecates
  runtime dynamic agent self-attach (see JEP 451 direction) — exactly the mechanism Mockito's
  inline mock maker falls back to when it isn't loaded as a proper `-javaagent` up front.

## Suggested fix

Configure Mockito as a real `-javaagent` at JVM startup instead of relying on Mockito's runtime
self-attach fallback — this is literally what the Mockito warning itself recommends and is a
standard, documented Maven recipe:

- Root `pom.xml`: add `maven-dependency-plugin`'s `properties` goal (bound to the `initialize`
  phase) so `${org.mockito:mockito-core:jar}` resolves to the actual jar path on disk.
- Root `pom.xml`: configure `maven-surefire-plugin`'s `argLine` to include
  `-javaagent:${org.mockito:mockito-core:jar}` (combined with `@{argLine}` if any other plugin,
  e.g. Jacoco, already contributes to `argLine`).
- Apply at the root `pom.xml` level (not per-module) since every module with
  `spring-boot-starter-test` on its classpath (`marketplace-app`, `query-lib`,
  `taxon-spring-boot-starter`, `integration-tests`) is equally exposed — none of them currently
  have any `maven-surefire-plugin` configuration of their own to override.
- Verify via `bash scripts/unit-tests.sh` (full run) and a couple of single-class isolated runs
  (`bash scripts/unit-tests.sh AccessEvaluatorTest`, `bash scripts/unit-tests.sh AuthServiceTest`)
  that the self-attach warning no longer appears in `run.log` and that whichever test runs first
  no longer shows an outlier multi-second-to-minute timing.

## Related

- Not related to Vaadin's `prepare-frontend` Maven goal (~27.6s, runs on every `mvn test`
  regardless of frontend changes — confirmed `build-frontend`, the actually expensive goal, does
  **not** run during `test` phase, only during `package`) — a separate, smaller, and largely
  unavoidable contributor to the same module's total time, not in scope for this issue.
- Not related to `ArchitectureRulesTest` (ArchUnit, ~82-93s) — that cost is inherent to scanning
  ~16,500 classes across every `org.ost` module for the architecture rules and is not caused by
  Mockito; a separate, lower-priority concern if ever revisited.
