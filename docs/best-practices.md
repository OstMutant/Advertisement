# Best Practices Reference

A general, project-independent reference of software engineering best practices across six areas:
Java design principles, JUnit/automated testing, Playwright/end-to-end testing, Bash scripting,
documentation, and CI/CD. Each entry names its practice, a short definition, and the source that
originated or best documents it.

This file intentionally contains no project-specific facts, file paths, or references to this
repository's own architecture decisions — it is a standalone, general-purpose checklist. Any other
document in this repository (a rule, an ADR, a code comment) is free to point *to* this file as the
place explaining why a given convention matters; this file itself never points back.

## Java — Design Principles

### SOLID

SOLID is not one practice but an umbrella acronym for five related object-oriented design
principles, coined by Robert C. Martin in "Design Principles and Design Patterns" (2000) and
popularized further in "Clean Code" and "Agile Software Development, Principles, Patterns, and
Practices." Used for: keeping a class-based codebase maintainable and extensible as it grows —
each of the five addresses a different way uncontrolled coupling or bloated responsibility
otherwise creeps in.

- **S — Single Responsibility Principle (SRP)** — a class should have one, and only one, reason to
  change. Used for: keeping a class small and easy to reason about and test — when it has one
  responsibility, a change to one concern can't accidentally break an unrelated one.
- **O — Open/Closed Principle (OCP)** — software entities should be open for extension but closed
  for modification. Used for: adding new behavior without editing existing, already-tested code,
  which keeps the risk of regressing something that already works low. Source for the principle
  itself: Bertrand Meyer, "Object-Oriented Software Construction."
- **L — Liskov Substitution Principle (LSP)** — objects of a subtype must be substitutable for
  objects of their supertype without altering the correctness of the program. Used for: making
  polymorphism actually trustworthy — code written against a base type keeps behaving correctly no
  matter which subtype it's really handed at runtime. Source for the principle itself: Barbara
  Liskov's 1987 keynote address.
- **I — Interface Segregation Principle (ISP)** — clients should not be forced to depend on methods
  they do not use; prefer several small, focused interfaces over one broad one. Used for: avoiding
  unnecessary coupling — a class implementing a fat interface ends up depending on (and being
  affected by changes to) methods it never actually calls.
- **D — Dependency Inversion Principle (DIP)** — high-level modules should depend on abstractions,
  not on low-level concrete implementations. Used for: letting either side of a dependency change
  or be swapped (a real implementation for a test double, one storage backend for another) without
  the other side needing to change too.

### Other core principles

- **DRY (Don't Repeat Yourself)** — every piece of knowledge must have a single, unambiguous,
  authoritative representation. Used for: avoiding the maintenance cost and bug risk of having to
  find and update every duplicated copy of the same fact whenever it changes. Source: Andy Hunt &
  Dave Thomas, "The Pragmatic Programmer" (1999).
- **YAGNI (You Aren't Gonna Need It)** — implement things when they're actually needed, never merely
  because they might be needed later. Used for: avoiding wasted effort and unnecessary complexity
  spent building for a speculative future requirement that may never actually arrive. Source:
  Extreme Programming; popularized by Ron Jeffries.
- **Law of Demeter / Principle of Least Knowledge** — a unit should only talk to its immediate
  "friends," never reach through one object to manipulate another it doesn't directly own. Used
  for: keeping modules loosely coupled, so a change deep inside one object's internals doesn't
  ripple through long chains of callers that reached through it. Source: Ian Holland, the Demeter
  Project, Northeastern University (1987).
- **Command-Query Separation (CQS)** — a method should either be a command that changes state, or a
  query that returns data, never both. Used for: making code easier to reason about — a pure query
  can be called, reordered, or cached freely, while every place a side effect can happen stays
  explicit and easy to find. Source: Bertrand Meyer, "Object-Oriented Software Construction," in
  the design of the Eiffel language.
- **Fail fast** — surface an invalid state or input as close as possible to where it occurs, rather
  than letting it propagate silently. Used for: catching a bug at its real source instead of
  chasing a confusing failure far downstream, once corrupted state has already spread. Source:
  widely attributed to Jim Shore's writing on the practice, and a standard tenet of
  defensive/contract-based programming.

## JUnit / Automated Testing

### FIRST

FIRST groups five qualities a good unit test should have, coined by Robert C. Martin in "Clean
Code" and formalized further in "Pragmatic Unit Testing." Used for: giving a concrete checklist for
what makes a unit test actually trustworthy and worth keeping in a suite, rather than "some test
exists."

- **F — Fast** — a unit test should run in milliseconds. Used for: keeping the whole suite fast
  enough to run on every change without breaking a developer's flow.
- **I — Independent (Isolated)** — a test must not depend on another test's side effects or run
  order. Used for: making a failure mean exactly one thing — this test's own assertion broke — not
  "some other test ran first and left the wrong state behind."
- **R — Repeatable** — a test must produce the same result in any environment, any number of times.
  Used for: giving reliable feedback that isn't affected by the machine, the network, or the time of
  day the test happens to run.
- **S — Self-validating** — a test must produce a clear pass/fail with no manual inspection needed.
  Used for: making a test result usable in an automated pipeline, not just human-readable output
  someone has to interpret.
- **T — Timely** — a test is written together with the code it covers, not added long after. Used
  for: using the test itself as a design tool while the code is still being shaped, instead of
  retrofitting coverage onto an already-fixed design.

### Other testing practices

- **Arrange-Act-Assert (AAA) / Given-When-Then** — structure a single test into a clear setup,
  action, and verification, with no hidden branching in between. Used for: making a test readable
  top-to-bottom, so a failure can be understood without reverse-engineering the test's own control
  flow first. Source: a long-standing testing convention; the Given-When-Then phrasing comes from
  Behavior-Driven Development (Dan North).
- **Descriptive naming** — a test name should state the method under test, the condition, and the
  expected result, so a failure is understandable from its name alone. Used for: letting a failing
  test's report tell you what broke before you even open the file. Source: a widely adopted
  convention discussed in Roy Osherove's "The Art of Unit Testing."
- **Test behavior, not implementation** — assert on observable outcomes reachable through a public
  entry point, not on private internal state or mechanism. Used for: keeping a test valid across an
  internal refactor, since it only breaks when real observable behavior actually changes. Source: a
  core tenet of both classical and London-school unit testing philosophy (Kent Beck, Martin Fowler).
- **Don't mock what you don't own** — write integration tests against third-party/infrastructure
  boundaries you don't control, and reserve mocks for your own collaborators. Used for: catching a
  real mismatch between your assumptions and how a real dependency actually behaves, which a mock of
  that same dependency can never reveal. Source: Steve Freeman & Nat Pryce, "Growing Object-Oriented
  Software, Guided by Tests" (2009).
- **Test doubles by purpose** — dummy, stub, spy, mock, and fake each serve a distinct role; using
  `mock()` for everything blurs what a test is actually verifying. Used for: making it clear, from
  the kind of double used, exactly what a test cares about (a returned value, an interaction, a
  working fake implementation). Source: Gerard Meszaros, "xUnit Test Patterns"; also Martin Fowler's
  "Mocks Aren't Stubs."
- **One abstraction level per test** — a unit test mocks its collaborators, an integration test
  exercises real infrastructure (e.g. a real database), an end-to-end test drives a real browser;
  don't blur the levels within one test. Used for: keeping each test's failure meaningful — a mixed
  test that touches multiple levels makes it unclear which layer actually broke. Source: the
  standard testing-pyramid model (Mike Cohn, "Succeeding with Agile").
- **Prefer parameterized tests over branching inside a test body** — an `if`/`for` inside a test
  usually means it should be two tests or one `@ParameterizedTest`. Used for: making every case run
  and report independently, instead of one branch silently never executing or masking another
  case's failure. Source: the JUnit 5 User Guide's own guidance on parameterized tests.
- **Guard against flaky, timing-dependent assertions** — prefer tolerant/deterministic comparisons
  (e.g. an explicit ordering column) over exact equality on values affected by real-world timing or
  network/database round-trips. Used for: preventing an intermittent, environment-dependent failure
  that erodes trust in the whole suite over time. Source: general test-reliability guidance found
  throughout the test-automation literature (e.g. Martin Fowler's writing on "Eradicating
  Non-Determinism in Tests").

## Playwright / End-to-End Testing

### Official Playwright Best Practices

The following seven practices all come from the same primary source — the official Playwright
documentation's own "Best Practices"/"Locators"/"Authentication"/"Trace Viewer" guides — grouped
together here since they share one source and one underlying philosophy: test what a real user
experiences, and let the tool's own auto-waiting do the timing work instead of the test author.

- **Test user-visible behavior** — assert on what a real user perceives, not internal implementation
  details. Used for: keeping a test valid across an internal refactor that doesn't change what the
  user actually sees.
- **Isolate tests by default** — each test should own its own state (session, database rows,
  cookies); shared state between tests is something to avoid, not an architectural ideal. Used for:
  letting any single test run alone, in any order, and still produce a trustworthy result.
- **Don't test third-party dependencies directly** — stub an external service (an embed, a
  third-party API) via request interception rather than depending on it being reachable from the
  test environment. Used for: keeping a test's pass/fail outcome under your own control, instead of
  at the mercy of a third party's uptime or network reachability.
- **Locator priority: role → label/text/testid → CSS/XPath last** — role-based locators mirror how
  a user (and assistive technology) perceives the page, and survive refactors of styling/markup that
  would break a CSS selector. Used for: reducing how often a purely cosmetic change (a renamed CSS
  class, a restyled component) breaks a test that never cared about styling in the first place.
- **Web-first assertions, no manual waits** — `await expect(locator).toBeVisible()` retries until it
  passes or times out; a manual fixed-duration wait is both slower and less reliable. Used for:
  removing the guesswork of picking "how long is long enough," which is exactly what causes both
  flaky failures (too short) and wasted time (too long).
- **Reuse an authenticated session via `storageState`** — log in through the UI once, persist the
  resulting storage state, and reuse it across tests that don't need to test the login flow itself.
  Used for: cutting the repeated cost of a full UI login in every single test that merely needs to
  already be logged in.
- **Capture a trace on real failure, not unconditionally** — pairing `trace: 'on-first-retry'` with
  at least one retry (or `retain-on-failure`) gives a real post-mortem artifact without the overhead
  of tracing every run. Used for: having a real, replayable record of what happened right when a
  test actually failed, without paying the overhead of recording every successful run too.

### Testing-craft practices

These come from the wider test-automation community rather than Playwright's own docs — general
craft practices that apply to browser-based end-to-end testing regardless of which tool runs it.

- **DAMP over strict DRY inside test code** — Descriptive And Meaningful Phrases can matter more
  than eliminating duplication in a test; the two aren't actually opposed once "what a test does"
  (kept explicit and readable) is separated from "how it does it" (safe to extract and reuse). Used
  for: keeping a failing test's own body readable without having to jump through several layers of
  shared setup to understand what it actually checks. Source: Vladimir Khorikov, "DRY vs. DAMP in
  Unit Tests" (Enterprise Craftsmanship).
- **Page Object Model / flow helpers** — encapsulate "how" (selectors, clicks, low-level steps)
  behind a helper, so a spec/test reads as "what" the scenario is. Used for: containing the fallout
  of a UI change to one helper file instead of every spec that happens to exercise that part of the
  UI. Source: a pattern originating in the Selenium/WebDriver community, broadly adopted across
  browser-automation tooling.
- **Lint test code for lost `await`s** — an un-awaited promise in a test is a real, silent failure
  mode; a rule like `no-floating-promises` catches it statically. Used for: catching, at write time,
  a class of bug that otherwise only shows up later as a flaky or falsely-passing test. Source: the
  `typescript-eslint` rule `no-floating-promises`.
- **Keep the test runner version current** — staying on a recent Playwright release surfaces new
  browser-engine regressions early rather than discovering them once already behind. Used for:
  spreading out the cost of keeping up with browser-engine changes instead of absorbing it all at
  once in a large, overdue upgrade. Source: general test-tooling maintenance guidance, consistent
  with Playwright's own frequent release cadence.

## Bash Scripting

### Strict mode (`set -euo pipefail`)

`set -euo pipefail` is itself a group of three independent flags combined into one standard
"strict mode" convention (e.g. discussed in MIT SIPB's "Writing Safe Shell Scripts"). Used for:
turning a script's default silently-tolerant behavior into fail-fast behavior, so a broken step
stops the script instead of letting it continue on bad data. Important limitation worth knowing:
none of the three trigger inside a condition, on the left side of `&&`/`||`, on a non-final command
in such a chain, or inside a function whose result is being tested — this combination is not a
complete safety net on its own.

- **`-e` (errexit)** — exit immediately if any command exits with a non-zero status. Used for:
  stopping a script the moment something genuinely fails, instead of ploughing ahead on top of a
  failure.
- **`-u` (nounset)** — treat referencing an unset variable as an error. Used for: catching a typo'd
  or missing variable name at the exact line it's used, instead of it silently expanding to an empty
  string and corrupting whatever command uses it (e.g. a destructive path built from it).
- **`-o pipefail`** — make a pipeline's exit status the first non-zero status of any stage, not just
  the last one. Used for: making a failure in an early pipeline stage (e.g. the producing command in
  `cmd | tee log`) actually visible, instead of being masked by a later stage that itself succeeds.

### Other scripting practices

- **Shebang choice is a real, debated tradeoff, not a purely cosmetic one** — the Google Shell Style
  Guide prescribes a hardcoded `#!/bin/bash` deliberately, for a single consistent interpreter across
  managed machines; `#!/usr/bin/env bash` is the competing convention favored when a script must run
  correctly on systems where bash isn't guaranteed to live at `/bin/bash`. Used for: making sure a
  script actually finds a real bash interpreter on whichever machine ends up running it — pick one
  convention deliberately and apply it consistently, rather than mixing both across a codebase.
- **Quote every variable expansion** — an unquoted expansion is subject to word-splitting and glob
  expansion, a leading cause of real Bash bugs. Used for: making sure a value containing a space or a
  glob character (a filename, a path) is treated as one argument, not silently split or expanded
  into several. Source: the Google Shell Style Guide; mechanically checked by ShellCheck's own
  `SC2086` rule.
- **Use `trap` for cleanup** — `set -e` alone does not guarantee cleanup code runs; `trap 'cleanup'
  EXIT`/`ERR` runs regardless of how the script exits. Used for: guaranteeing a resource (a temp
  file, a background process, a container) gets cleaned up even when the script exits early or
  unexpectedly. Source: the Advanced Bash-Scripting Guide's chapter on signal handling.
- **Prefer `mktemp` over a hardcoded temp path** — a predictable temp path is both a race condition
  and, in shared environments, a security risk. Used for: avoiding two concurrent runs (or an
  attacker) colliding on the same predictable filename. Source: standard Unix scripting guidance
  found in most shell style guides, including the Google Shell Style Guide.
- **Idempotency** — a script should be safe to run again without causing harm or duplicate effects
  (e.g. checking a service's health before re-triggering it). Used for: making a re-run (after a
  partial failure, or just to be sure) safe by default, instead of something that has to be reasoned
  about case by case. Source: a core tenet of modern infrastructure-as-code and operations tooling.
- **Explicit over implicit** — resolve a script's own path and working assumptions explicitly rather
  than relying on the caller's current working directory. Used for: making a script behave the same
  regardless of where it's invoked from, instead of silently depending on the caller's shell state.
  Source: general defensive-scripting guidance found throughout the shell style guide literature.
- **Single-purpose, composable scripts** — a thin script that does one thing well and composes with
  others beats one monolithic script that does everything. Used for: letting each piece be tested,
  understood, and reused on its own, instead of having to reason about one large script's every
  possible path at once. Source: the Unix philosophy (Doug McIlroy's "write programs that do one
  thing and do it well").
- **Static analysis via ShellCheck, with documented suppressions** — a real static analyzer catches
  classes of bugs (unquoted expansions, array/string mix-ups, unguarded destructive paths) that are
  easy to miss by eye; an inline suppression should always carry a one-line reason. Used for:
  catching a real class of bug mechanically, before it ships, instead of relying on a human
  reviewer to spot it by eye every time. Source: shellcheck.net; the Google Shell Style Guide
  recommends running it.
- **Minimal automated tests for scripts** — a static analyzer is not a test suite; frameworks like
  Bats or shunit2 let a script's own logic be exercised directly. Used for: catching a real
  regression in a script's own behavior, the same way a unit test does for application code — a
  linter alone can't verify the logic actually does the right thing. Source: general shell-testing
  practice, documented by both the Bats and shunit2 projects.
- **Structured, timestamped logging** — a log line with a real timestamp is what makes a later
  race-condition or ordering bug root-causeable at all. Used for: reconstructing the real sequence
  of events after the fact, when the bug itself was about *when* something happened relative to
  something else. Source: general operational/observability logging guidance.

## Documentation

### Architecture Decision Records (ADRs)

An ADR is a single, durable record of one architecturally significant decision. This group covers
both the shape of one record and how the collection of records evolves as decisions change over
time. Source: Michael Nygard, "Documenting Architecture Decisions" (2011), and the wider
Architectural Decision Records community convention (adr.github.io).

- **Context, Decision, Consequences** — a decision's context, what was decided, and its resulting
  consequences are recorded together in one entry. Used for: letting a later reader understand not
  just what was chosen but why, and what tradeoff was knowingly accepted — without that, a future
  change can silently re-break a problem the original decision already solved.
- **Review and supersede, never silently delete** — when a decision is revisited, a new record marks
  the old one as superseded rather than erasing it. Used for: preserving the reasoning trail, so
  it's clear a past decision was deliberately changed, not simply forgotten or contradicted by
  accident.

### Diátaxis — four documentation types

Diátaxis identifies four genuinely different reader needs, each served by its own kind of document
rather than one document trying to serve all four at once. Source: the Diátaxis documentation
framework (diataxis.fr).

- **Tutorials** — a lesson that takes a learner through a practical experience step by step. Used
  for: onboarding someone with no prior context, learning by doing under guidance.
- **How-to guides** — goal-oriented steps for solving one specific real-world problem. Used for:
  helping someone who already has the basics accomplish a concrete task they already know they want
  to do.
- **Reference** — accurate, complete technical description, free of narrative or interpretation.
  Used for: letting someone look up one exact fact (a parameter, a method's contract) quickly,
  without reading through prose to find it.
- **Explanation** — background and context that answers "why," not "how." Used for: helping a
  reader understand the reasoning and the bigger picture behind a design, which a reference or
  how-to guide deliberately leaves out.

### Other documentation practices

- **Single source of truth** — a fact lives in exactly one place; every other mention references it
  instead of restating it. Used for: guaranteeing that updating a fact once actually keeps every
  reference to it correct, instead of leaving stale copies elsewhere. Source: a direct application
  of the DRY principle to documentation.
- **Docs-as-code** — documentation is versioned and reviewed through the same process as the code it
  describes, not maintained on a separate, unreviewed channel. Used for: giving documentation the
  same review discipline, history, and traceability that code already gets. Source: a widely
  adopted modern technical-writing practice.
- **Progressive disclosure** — give a reader a short, immediately useful entry point before the full
  depth of detail, rather than requiring the whole document to be read to get started. Used for:
  letting a reader get productive quickly, while the deeper detail is still there for whoever
  actually needs it. Source: a general technical-writing and UX-writing principle.
- **Don't embed ephemeral state in current-state documentation** — a document describing what *is*
  true today should not carry a dated "resolved on X" narrative or a reference that will go stale
  the moment the thing it points to is renamed or archived. Used for: keeping a "current state"
  document trustworthy indefinitely, instead of accumulating outdated narrative a reader can't tell
  apart from what's still true. Source: general documentation-maintenance practice.
- **Automated staleness checks** — a generated or cross-referenced document is verified against its
  real source on a schedule or in CI, rather than relying on a human remembering to update it. Used
  for: catching documentation drift mechanically, at the moment it happens, instead of whenever
  someone next happens to notice it's wrong. Source: a standard docs-as-code CI practice.

## CI/CD

### Continuous Delivery fundamentals

These five practices form the core of what "Continuous Delivery" means as a discipline, as opposed
to just "there is a pipeline." Source: Jez Humble & David Farley, "Continuous Delivery" (2010), and
Martin Fowler's writing on Continuous Integration.

- **Fail fast, fail loud** — a quality gate should actually block a broken build, not just report on
  it after the fact. Used for: making sure a real problem stops the pipeline instead of quietly
  shipping through it.
- **Reproducibility** — a build or test run should produce the same result regardless of which
  machine it runs on, via pinned dependency versions and hermetic test infrastructure (e.g.
  containerized databases) rather than "works on my machine." Used for: making a failure
  investigatable by anyone, on any machine, instead of being tied to one specific environment's
  quirks.
- **Immutable artifact, build once** — build a single artifact and promote that same artifact
  through every later stage, instead of rebuilding it (and risking a different result) at each
  stage. Used for: guaranteeing the exact thing that was tested is the exact thing that ships,
  eliminating an entire class of "it passed CI but broke in prod" mismatch.
- **Shift-left / cost-ordered testing** — run cheap, fast tests first and reserve expensive ones
  (integration, end-to-end) for later or parallel stages. Used for: getting feedback on the common
  case of a broken change as fast as possible, instead of waiting for the slowest stage to find it.
  Source: the standard testing-pyramid model (Mike Cohn).
- **Cache dependencies, invalidate correctly** — reusing a dependency cache across runs saves real
  time, but only if the cache key changes whenever the dependency set actually changes. Used for:
  cutting pipeline time spent re-downloading the same unchanged dependencies on every single run.

### Pipeline reliability & observability

- **Pipeline observability** — a pipeline's own live status and run history should be inspectable
  directly, not inferred by polling a flat log file. Used for: letting anyone answer "what's
  happening right now, and what happened last time" without reconstructing it from raw logs.
  Source: general DevOps/CI-tooling practice.
- **Isolation between runs** — no state should survive from one pipeline run into the next in a way
  that could affect its outcome. Used for: preventing one run's leftover state from causing a false
  pass or a false failure in the next, unrelated run. Source: general CI/CD reliability practice.
- **Pipeline speed as a first-class metric** — a slow pipeline erodes the fast-feedback benefit CI
  exists to provide in the first place. Used for: treating pipeline duration as something to
  actively budget and defend, the same way test coverage or error rate already is. Source: broadly
  consistent with the DORA (DevOps Research and Assessment) metrics research, popularized in
  "Accelerate" (Forsgren, Humble, Kim).

### Security & governance

- **Shift-left security (dependency and secret scanning as a real gate)** — dependency/CVE scanning
  and secret-scanning are treated as a required pipeline stage, not an optional add-on. Used for:
  catching a known vulnerability or a leaked credential before it ever reaches a shared branch,
  instead of after. Source: the OWASP DevSecOps Guideline.
- **Branch protection with required status checks** — a pull request cannot merge until its defined
  checks pass. Used for: making a CI gate actually enforceable, instead of a check someone can
  choose to ignore before merging anyway. Source: a standard convention on hosted CI platforms
  (e.g. GitHub, GitLab).
