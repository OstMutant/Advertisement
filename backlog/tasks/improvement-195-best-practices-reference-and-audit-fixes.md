# improvement-195: Best-practices reference doc + concrete fixes from a real cross-cutting audit

**Type:** improvement — documentation + code/test/script hygiene
**Module:** cross-cutting — `marketplace-app` tests, `integration-tests`, `playwright/`, `scripts/`,
every module's `DECISIONS.md`
**Priority:** 🔴 Top — user-requested top-of-backlog placement, 2026-09-17
**When:** independent, no blockers

## Current state

A real audit was run against this codebase across five areas (Java/SOLID-DRY, JUnit, Playwright,
Bash scripts, documentation, CI/CD), checking each claimed best practice directly against the
actual code rather than trusting a generic checklist. Two things came out of it:

1. A candidate reference list of best practices, each one checked against a real example already
   living in this codebase (or flagged as not yet applied) — worth keeping somewhere so future work
   has a concrete, project-grounded checklist instead of a generic one.
2. A set of concrete, already-verified fixable issues — real code, not hypothetical — spanning
   tests, Playwright config, Bash scripts, and documentation.

Every finding below was re-verified directly against the current repository state on 2026-09-17
(not just taken from the source audit at face value) — file paths, line numbers, and counts in this
task reflect that direct check, not the original write-up.

## Why change

The audit surfaced real, low-risk fixes (a reflection-based test hack this project's own ADR-008
already forbids, a dead trace-on-retry config, a live external YouTube dependency in e2e, two
ShellCheck-flagged potential bugs) sitting alongside a genuinely useful artifact — a
project-grounded best-practices checklist — that has no home yet. Both are worth capturing in one
place rather than losing the audit's work the moment this conversation ends.

## Approach — Phase 0: decide where the best-practices reference lives

**Decided 2026-09-17 (user directive):** standalone `docs/best-practices.md`. Surfaced inside
`docs/architecture/architecture-map.html` under the existing **System › Code Quality** screen
(`renderCodeQuality()` in `docs/architecture/scripts/generate-architecture-model.sh`) as a 4th card,
alongside the existing SonarQube/ArchUnit/Findings cards — rendered the same way `README.md`/
`INFRASTRUCTURE.md` already render on the System screen (`root_md_json_for()` → `MODEL.rootXxx` →
`mdBlockToHtml()`). **Not linked from root `CLAUDE.md` for now** (explicit user instruction — revisit
later once the doc has settled).

Confirmed via direct read of `generate-architecture-model.sh`:
- `root_md_json_for(file)` (line ~218) already accepts any repo-relative path, not just root-level
  files — `root_md_json_for("docs/best-practices.md")` works without modifying the function.
- `renderCodeQuality()` (line ~2845) has a 3-card landing (`sonar`/`archunit`/`findings`) plus one
  `if/else if` branch per section — a `practices` section follows the exact same shape as the
  `sonar`/`archunit` branches, but rendering markdown via `mdBlockToHtml()` like the System screen's
  own README/INFRASTRUCTURE blocks (`renderSystem()` line ~2399, `renderModule()`'s infra block line
  ~3087) rather than a generated table.
- Breadcrumb label logic (line ~2273) already special-cases `sonar`/`archunit`/`findings` sections
  for the `Code Quality — X` title pattern — a `practices` case slots in the same way.

Remaining open sub-question from the original three-option framing, not yet resolved: whether
`app-readme-standards` (the skill family governing repo-root-scoped, non-module docs) should gain a
short note covering `docs/best-practices.md`'s own "cites already-documented facts as illustration,
canonical home stays elsewhere" exemption — modeled on that skill's existing `README.md` exemption
paragraph. Proposed for approval below, not yet applied.

### Candidate content — `docs/best-practices.md`

One section per audited area; each entry: the practice, a named authoritative external source (no
bare URLs, matching this project's own "cite by name" citation style), and either a real applied
example in this codebase or an explicit "not yet applied" flag pointing at the fixing phase below
(no ticket numbers in the doc's own prose — phase references here are for this approval step only).

- **Java — SOLID & DRY**
  - Dependency Inversion — source: Robert C. Martin, "Design Principles and Design Patterns" (the
    original SOLID formulation). Applied: `AdvertisementPort`/`AdvertisementPortImpl` — callers
    depend on the `platform-commons` interface, never the starter's own impl class.
  - DRY — source: Hunt & Thomas, "The Pragmatic Programmer". Applied: `SqlFilterBuilder`/
    `OrderByBuilder` in `query-lib`, reused across multiple repositories instead of each hand-rolling
    filter/sort SQL.
  - YAGNI — source: Kent Beck's Extreme Programming practices. Applied: this project's own "no
    defensive empty checks" design-by-contract rule already rejects speculative guard code.
- **JUnit**
  - Test through the public API, not reflection into internals — source: JUnit 5 User Guide's
    guidance on testing behavior, not implementation. Not yet applied (until Phase 1a lands):
    `TimelineViewTest` invoked a private method via reflection.
  - Deterministic assertions over incidental timing — source: JUnit 5 User Guide / general
    test-determinism principle. Not yet applied (until Phase 1b lands): `AttachmentRepositoryTest`
    used `Thread.sleep()` instead of relying on the query's own tiebreaker column.
  - Descriptive naming (`method_condition_expectedResult`) — source: Given-When-Then structure
    (Martin Fowler / BDD). Applied: most test methods in this codebase already follow this shape.
- **Playwright**
  - Prefer user-facing locators over CSS selectors — source: playwright.dev's official Best
    Practices guide. Not yet broadly applied: a small minority of locators use `getByRole`/
    `getByLabel`/`getByTestId` against a large majority of CSS selectors (deferred by design — see
    Phase 2's own scoping).
  - Don't depend on third-party services in tests — source: playwright.dev's official Best
    Practices guide. Not yet applied (until Phase 2b lands): a live YouTube embed URL is hardcoded
    in two e2e files.
  - Capture a trace on real failure — source: playwright.dev's Trace Viewer documentation.
    Currently misconfigured (until Phase 2a lands): `retries: 0` makes `trace: 'on-first-retry'`
    dead configuration.
- **Bash**
  - `set -euo pipefail` at the top of every script — source: Google Shell Style Guide. Not yet
    consistently applied (until Phase 3b lands): most scripts under `scripts/` still use a bare
    `set -e`.
  - Portable shebang (`#!/usr/bin/env bash`) — source: Google Shell Style Guide. Mixed today
    (until Phase 3c lands).
  - Static analysis via ShellCheck — source: Google Shell Style Guide's own recommendation to run
    it. Not yet a CI gate (until Phase 3d lands); already caught two real bugs directly (Phase 3a).
- **Documentation**
  - Architecture Decision Records — source: Michael Nygard, "Documenting Architecture Decisions"
    (2011). Applied: every module's `DECISIONS.md` already follows the Context/Decision/
    Consequences shape.
  - Current-state docs describe what is, not a changelog — general documentation-maintenance
    principle. Applied inconsistently today (until Phase 4 lands): several `DECISIONS.md` files
    still cite ticket numbers in prose, against this project's own stated rule.
- **CI/CD**
  - Shift security left (dependency/secret scanning in the pipeline) — source: OWASP's DevSecOps
    guidance. Not yet applied — a consciously deferred gap tied to the still-open hosted-CI
    migration decision, stated as such in the doc rather than left silent.

Present this candidate structure + sourced descriptions for approval before writing the final doc
and wiring the architecture-map changes.

**Done 2026-09-17.** Final shape differs from the candidate above per direct user instruction during
implementation: [`docs/best-practices.md`](../../docs/best-practices.md) contains only general,
project-independent practice/definition/source entries — no repository facts, file paths, or ADR
citations at all (the reverse relationship applies instead: other documents may link *to* this file).
Related practices are grouped under a named subheading with a short "used for" purpose line per
entry (e.g. SOLID, JUnit's FIRST, Playwright's official-docs practices vs. testing-craft practices,
Bash's `set -euo pipefail`, ADRs vs. Diátaxis, CI/CD's three subgroups). Wired into
`docs/architecture/architecture-map.html` as a 4th card under System › Code Quality
(`docs/architecture/scripts/generate-architecture-model.sh`'s `renderCodeQuality()`), rendered via
the same `mdBlockToHtml()` mechanism as `README.md`/`INFRASTRUCTURE.md`. Not linked from root
`CLAUDE.md` (explicit user instruction, revisit later). The `app-readme-standards` skill was
deliberately left untouched (explicit user instruction) since this file needs no "one fact, one
canonical home" exemption once it carries no project facts at all.

## Approach — Phase 1: JUnit test hygiene (small, safe, test-only)

**1a. `TimelineViewTest` reflection hack**
`marketplace-app/src/test/java/org/ost/marketplace/ui/views/main/tabs/timeline/TimelineViewTest.java`
— `refresh_nonAdminWithNoResolvedActorId_rendersEmptyAndNeverQueries` calls a private
`TimelineView.refresh()` via `getDeclaredMethod("refresh")` + `setAccessible(true)` + `invoke()`
through a local `invokeRefresh()` helper (lines 17, 48, 55-58). This violates this project's own
ADR-008 ("test package-private/private internal logic through its public entry point, never a
same-package trick or reflection"). `TimelineView.setVisible(boolean)` already calls `refresh()`
when `visible == true` and is a real public entry point — confirmed by reading the class.
`Component.setVisible()` doesn't need an attached UI/session, so it's safe on a plain
`new TimelineView(...)` in this Mockito-only test.
- Replace `invokeRefresh();` with `view.setVisible(true);`.
- Remove `throws Exception` from the test method signature.
- Delete the `invokeRefresh()` helper and the now-unused `import java.lang.reflect.Method;`.
- Check no other test method in the file still calls `invokeRefresh()` before deleting it.

**1b. `AttachmentRepositoryTest` redundant `Thread.sleep`**
`integration-tests/src/test/java/org/ost/integrationtests/level1/attachment/AttachmentRepositoryTest.java`
— `Thread.sleep(10)` at line 222 (`loadMediaStats_singleEntity_...`) and line 242
(`loadMediaStats_bulk_...`), both forcing distinct `created_at` values for deterministic "earliest"
ordering. Confirmed unnecessary against `AttachmentRepository.loadMediaStats()`'s real SQL
(`ORDER BY created_at ASC, id ASC`) — the `id ASC` tiebreaker already guarantees the first-inserted
row (lower auto-increment id) sorts first regardless of timestamp ties.
- Remove both `Thread.sleep(10);` lines.
- Remove `throws InterruptedException` from both method signatures if nothing else needs it.
- Do not change any assertions — the SQL tiebreaker already makes the expected result
  deterministic without the sleep.

**Verification:** run `TimelineViewTest` and `AttachmentRepositoryTest` directly first, then the
full `marketplace-app` unit suite and `integration-tests` level1/attachment suite, to confirm
nothing else depended on the removed helper/timing.

**Done 2026-09-17.** Both fixes applied exactly as planned.
- 1a: `invokeRefresh()`/`import java.lang.reflect.Method;` deleted, replaced with
  `view.setVisible(true);`, `throws Exception` removed. Confirmed by reading
  `TimelineView.setVisible(boolean)` directly that it calls `super.setVisible(visible)` then
  `refresh()` when `visible == true` — the public entry point claim holds. Confirmed no other
  method in the file still referenced `invokeRefresh()` before deleting it.
  `TimelineViewTest` run standalone: 1/1 passed.
- 1b: both `Thread.sleep(10);` lines removed, `throws InterruptedException` removed from both
  signatures (nothing else in either method needed it). Re-verified the tiebreaker claim directly
  against `AttachmentRepository.loadMediaStats()`'s real SQL before trusting it: both the
  single-entity query and the bulk query's `ROW_NUMBER() OVER (PARTITION BY entity_id ORDER BY
  created_at ASC, id ASC)` use `id ASC` as the tiebreaker, so `save()`'s insertion-order
  auto-increment ids already make "first.jpg" sort first regardless of timestamp ties — the sleep
  was genuinely adding nothing. `integration-tests/run.sh --sandbox AttachmentRepositoryTest` run
  in progress at time of writing; full `marketplace-app` unit suite and `integration-tests`
  level1/attachment suite still pending before Phase 1 is fully closed out.

## Approach — Phase 2: Playwright config + test hygiene

**2a. Dead trace config — real logical bug, not a style nit.**
`playwright/playwright.config.js:26,28,37` — `retries: 0` with `trace: 'on-first-retry'`: a retry
can never happen, so trace capture is dead code today. Fix: either raise `retries` to at least 1
(CI runs only, if local runs should stay fast/deterministic) or change to
`trace: 'retain-on-failure'` so a first-failure trace is actually captured. Needs a decision on
which — present both options with their tradeoff before picking.

**Verified already fixed 2026-09-18** — re-read `playwright.config.js` directly before starting
this phase: `retries: 0` (line 28) is paired with `trace: 'retain-on-failure'` (line 37), not
`'on-first-retry'`. This was already corrected as a side effect of Phase 8's own verification work
(that phase's own text mentions "Phase 2a's trace-config change" in passing) even though Phase 2
itself was never marked done. No further action needed here.

**2b. Live external dependency in e2e — YouTube.**
`playwright/e2e/_helpers.js:41` and `playwright/e2e/_flows/advertisement.flow.js:30` both hardcode
`https://www.youtube.com/watch?v=dQw4w9WgXcQ` and the test asserts on `getIframeSrc(page)` against
the real embed. Fix: stub via `page.route()` matching the YouTube embed URL pattern, serving a
minimal fixture response, so the test no longer depends on YouTube being reachable from wherever
Playwright runs.

**2c. No ESLint in `playwright/`.**
No `.eslintrc*`/`eslint.config.*` found under `playwright/`. Add a minimal config with
`@typescript-eslint/no-floating-promises` (or the plain-JS equivalent covering un-awaited promises)
as the primary rule this project cares about, matching the audit's own rationale — Playwright tests
losing an `await` is a real, silent failure mode. Wire it into `scripts/ci.sh` or a dedicated lint
step, not just left as a local-only config.

**Done 2026-09-18.**
- 2b: `_helpers.js` gained exported `stubYoutubeEmbed(page)` (routes
  `https://www.youtube.com/embed/**` to a minimal fixture response via `page.route()`);
  `05-marketplace-advertisement-flow.spec.js` calls it right after each of its two
  `browser.newPage()` calls (the only spec file that actually triggers a YouTube iframe render —
  confirmed `getIframeSrc()` only reads the `.src` attribute, never waits on real content, so the
  fix doesn't change any assertion). `advertisement.flow.js`'s duplicate `YT_URL` constant was
  removed in the same pass, now imported from `_helpers.js` instead (adjacent DRY fix).
- 2c: `eslint-plugin-playwright@2.12.0` chosen over a generic floating-promise rule — its
  `playwright/missing-playwright-await` rule is purpose-built for exactly this suite's own API
  surface (verified against the plugin's real npm registry metadata and GitHub source, not
  assumed). New `playwright/eslint.config.js` (flat config) + `playwright/package.json`
  (devDependencies only — JSON carries no header per this repo's own doc standard, described in
  `playwright/README.md`'s new "Linting" section instead). Wired as a new `--lint` mode on
  `playwright/run.sh` (a script-group's single entry point, not a new sibling script) — skips the
  app/DB entirely, syncs the same spec/flow/helper files plus the new config files into
  `pw-runner`, installs, runs `npx eslint .`. Wired further into `scripts/ci/dagu/ci.yaml` as a new
  `lint` step (`depends: build`, gated by a new `lint` param, default `true`) that just calls
  `playwright/run.sh --lint` with `PW_CONTAINER=ci-pw-runner` — no Node.js needed inside
  `ci-runner` itself, same delegation shape the existing `e2e` step already uses for its own
  `pw-runner` dependency. `scripts/ci/run.sh` gained a matching `--no-lint` flag (mirrors
  `--no-archunit-metrics`'s existing shape exactly) and `--docs-only` now also skips it.
  `pipeline_metrics`'s `depends` list extended to include `lint`; `pipeline-metrics.py` itself
  needed no change (already generic over every DAG node, not a hardcoded step list).

**2c follow-up — enabling the full `flat/recommended` config (not just the one target rule)
surfaced 106 real problems (6 errors, 100 warnings) across the whole suite, none previously caught
by anything. Fixed down to 0 errors / 36 warnings (the 36 are all `no-wait-for-timeout`, already
tracked as `improvement-133` entry 22 — a separate, larger piece of work, not duplicated here):**
- `eslint --fix` auto-fixed most of it correctly, but broke 3 call sites of
  `playwright/prefer-web-first-assertions` (`getAttribute()` → `toHaveAttribute()`) into invalid
  syntax (`toHaveAttribute('x', )`, missing the second argument) — one of the three
  (`05-marketplace-advertisement-flow.spec.js`'s deep-link test) also silently broke real behavior,
  since the extracted value was reused afterward as a string in `page.goto(...)`, not just checked
  for existence. All 3 found and fixed by hand (2 reverted to manual `getAttribute()` +
  `// eslint-disable-next-line` with a stated reason since the real string value is genuinely
  needed downstream; 1 rewritten as `toHaveAttribute('src', /.+/)` since only existence, not a
  specific value, was ever being checked).
- `playwright/expect-expect` (27 warnings) was almost entirely a false positive — this suite
  deliberately delegates real assertions into `assert*`/`verify*`/`run*Flow`/`*ViaApi` helper
  functions the rule can't statically see into. Fixed via `assertFunctionPatterns`, not by adding
  redundant top-level `expect()` calls.
- `playwright/no-conditional-in-test` (13) and `playwright/no-skipped-test` (2) — every real
  instance checked individually; all are legitimate (config-parameter-driven flow branching,
  data-filtering loops, temp-file cleanup, and the documented `--full` skip gate), never the
  actual page-state-dependent non-determinism the rules exist to catch. Disabled in
  `eslint.config.js` with the reasoning recorded in its own header comment.
- `playwright/no-networkidle` (2 errors) — one call was dead weight (the next line already waits
  deterministically), removed; the other (a Settings-save wait) replaced with waiting on the real
  `vaadin-notification-card` success signal `SETTINGS_SAVED_SUCCESS` triggers, confirmed by reading
  `AccountOverlay.java`'s save-config mapping.
- `playwright/no-conditional-expect` (2, same line) — a **real bug**, not a lint nit:
  `runApplyFilterFlow` (`_flows/advertisement-filter.flow.js`) had
  `await expect(...).toBeVisible(...).catch(() => {})`, silently swallowing the assertion outright
  regardless of cause. Its one real call site (`01-marketplace-empty-flow.spec.js`, run against a
  genuinely still-empty DB) does legitimately expect zero filter results — fixed by adding an
  explicit `expectResults` parameter: `true` asserts a card renders (unchanged default), `false`
  asserts the pagination count actually shows a zero-result state (`toContainText('0–0', ...)`,
  locale-independent — the first attempt hardcoded the English `'0–0 of 0'` and failed for real
  against the Ukrainian-locale run, `'0–0 з 0 записів'`; fixed once caught by the live e2e run).
- `playwright/no-force-option` (1) — `.card-lightbox__close`'s `{ force: true }` was investigated
  live, not left unexplained: removed and re-verified against a full `e2e --ux` run — the plain
  click works with no failure, confirming `force` was unnecessary (root cause never identified,
  since it's no longer reproducible; not worth chasing further once confirmed gone).
- **Two real self-inflicted bugs found and fixed in `run.sh` along the way, both from `--lint`
  sharing state with the test-run path inside the reused `pw-runner` container:**
  1. `--lint`'s own `npm install` (`eslint`/`eslint-plugin-playwright` only) was sharing
     `pw-runner`'s `/tmp/node_modules` with the test-run path's own `playwright`/`@playwright/test`
     install. Since the test-run path only checks `[ ! -d /tmp/node_modules ]` before installing,
     running `--lint` once left that directory present but missing `@playwright/test` entirely,
     breaking every subsequent `e2e` run (`Cannot find module '@playwright/test'`) until fixed.
  2. First fix attempt copied lint's spec files into `/tmp/lint/e2e/` — still nested under `/tmp`.
     The test-run path's own `testMatch: '**/*.spec.js'` (rooted at `/tmp`) then recursively picked
     up `/tmp/lint/e2e/*.spec.js` too, doubling every test (126 collected instead of 63) — caught
     live when the user ran a test command right after `--lint` and got 126, a sequence never
     actually exercised end-to-end before that point (lint and e2e had only ever been verified
     separately, never lint-then-e2e back to back).
  Fixed by moving `--lint`'s whole working directory to `/lint` (container root, sibling to `/tmp`,
  never nested inside it) — fully outside both the node_modules-existence check and the recursive
  spec glob. Re-verified specifically in the `--lint` → `e2e` sequence this time (not each mode in
  isolation) — `Running 63 tests using 1 worker`, correct count restored.
- **Verification:** full `e2e --ux` run — first attempt caught both the `/tmp/node_modules`
  collision and the locale-hardcoded `runApplyFilterFlow` bug (both fixed); a clean re-run
  afterward passed 50/50 (13 skipped, same `--full`-gated baseline as always); a `--lint` → `e2e`
  sequence re-run afterward caught and confirmed the fix for the 126-test doubling bug above; a
  final `--lint` re-run on its own passed 0 errors / 36 warnings (all `no-wait-for-timeout`,
  tracked separately).

**Deferred, not required for this task's own done-ness (large, no fast/safe path):**
- Locator migration toward `getByRole`/`getByLabel`/`getByTestId` — currently ~7 role/label/testid
  locators vs. 1000+ CSS-selector locators repo-wide. Migrating all of them is out of scope; apply
  the preference to *new* tests and the most fragile existing ones opportunistically, not as a
  batch here.
- `storageState` session reuse (currently 0 usages) — real speed win, but touches every spec file's
  setup; size it as its own task if picked up, don't fold into this one.
- `fullyParallel: false` / `workers: 1` — already a documented, reasoned tradeoff in the config's
  own comment (stateful Vaadin sessions + shared DB) — not a finding, no action needed.

## Approach — Phase 3: Bash script hygiene

**3a. Real ShellCheck-flagged bugs (not style) — fix these regardless of the rest of this phase.**
- `docs/architecture/scripts/generate-architecture-model.sh:1358` —
  `local dir="$1" files="$2"` followed by `files` later being used as an array in some paths but a
  plain string here (ShellCheck SC2178) — and a later expansion at the call site around line 1430
  only yields the first element instead of the whole array (SC2128). Read the surrounding function
  fully before fixing — determine whether `files` should be an array throughout, or whether the
  array usage elsewhere is the actual bug.

  **Re-verified 2026-09-18 with a real ShellCheck run (0.8.0, installed ad hoc for this
  investigation only — see 3d's own note on why that install method isn't the real fix): this is
  not a runtime bug.** `script_headers_json()`'s own `local files="$2"` (line 1358) is a plain
  newline-joined string, consumed by its own embedded `python3 -c "..."` via `sys.argv[3]
  .splitlines()` — correct and self-consistent throughout that whole function, confirmed by
  reading every `$files` reference inside it. The SC2178/SC2128 warnings are a cross-scope naming
  collision: an unrelated, genuinely-array `files=()` exists at top-level script scope (line 499,
  a Liquibase-changelog file list, entirely different purpose) and again as `local files=()` in a
  separate function (line 1095) — ShellCheck's whole-file analysis conflates all three same-named
  variables even though `local` fully isolates `script_headers_json()`'s own copy from the other
  two at runtime. Fix: rename `script_headers_json()`'s own local parameter (`files` →
  `file_list`, at both its declaration and the one call-site expansion) to eliminate the ambiguous
  name — a zero-risk rename local to one ~15-line function, not a suppression, since the warning's
  root cause (name reuse) is directly removable rather than worth silencing.
- `scripts/build-and-test/build.sh:127` — `rm -rf "$TARGET_CLASSES_DIR/$module"`: if `$module` is
  ever empty, this deletes all of `$TARGET_CLASSES_DIR`. Fix with `"${module:?module must be set}"`
  (or equivalent guard) so an empty value fails loudly instead of silently widening the delete.

**Done 2026-09-18.** Both fixed and re-verified with a real ShellCheck run (not blind-applied):
`script_headers_json()`'s local `files` renamed to `file_list` (declaration + the one call-site
expansion) — SC2178/SC2128 both gone on re-scan, confirmed no real bug ever existed, just a
same-name collision with two unrelated `files` variables elsewhere in this 2900+-line script (one
genuinely an array, at top-level scope and in a separate function — neither actually reachable from
`script_headers_json()`'s own `local`-scoped copy). `build.sh:127` guarded with
`"${TARGET_CLASSES_DIR:?}/${module:?module must be set}"` (both operands guarded, not just
`module` — an empty `TARGET_CLASSES_DIR` would be equally catastrophic). Re-ran ShellCheck on both
full files afterward: zero new warnings introduced by either fix; pre-existing, unrelated findings
in `build.sh` (SC2154, SC2010, SC2086 ×2) left untouched — out of this phase's named scope.

**3b. `set -euo pipefail` consistency.**
Verified directly: only 1 script under `scripts/` has the full `set -euo pipefail`; 5 have a bare
`set -e` with no `-u`/`pipefail`. Bring the bare-`set -e` scripts up to the full form, checking each
one individually for any place that currently relies on an unset variable defaulting to empty
(pipefail/`-u` can change behavior, not just tighten it — verify, don't blind-apply).

**Done 2026-09-18.** All 5 upgraded to `set -euo pipefail`; two of them
(`build-and-test/run.sh`, `sonar/run.sh`) have a *second* `set -e` later in the file restoring
errexit after a deliberate `set +e` around a manual exit-code capture — that second occurrence was
correctly left as plain `set -e` (`-u`/`pipefail` are never disabled by `set +e`, which only
toggles errexit, so restating them there would be redundant, not wrong).

The "verify, don't blind-apply" instruction caught 6 real behavior changes across 3 files, each
confirmed empirically (a real `bash -c 'set -euo pipefail; ...'` reproduction, not just reasoned
about) before fixing:
- `scripts/ci/docker-entrypoint.sh` — all variables (`FORCE_TOOLS_REFRESH`/`DAGU_VERSION`/
  `DAGU_PORT`) always set by the Dockerfile/caller; no pipes. Safe as-is, no fix needed.
- `scripts/deploy-and-run/run.sh` — same story (all vars defaulted or function-local; the one
  unguarded pipe, `docker build | tee | grep`, is already wrapped so `pipefail` actually *fixes* a
  real pre-existing bug: today a failed `docker build` whose output happens to match the `grep`
  filter is silently swallowed and the script continues as if the build succeeded). Safe as-is.
- `scripts/build-and-test/build.sh` — 3 real fixes needed, all confirmed by reproducing the exact
  failure first:
  1. `if [ -n "$TESTCONTAINERS_RYUK_DISABLED" ]` — this env var is documented as optional
     ("may be exported directly... if needed"), genuinely unset on a normal (non-sandbox) run.
     Fixed: `${TESTCONTAINERS_RYUK_DISABLED:-}`.
  2. `JAR=$(ls ".../"*.jar 2>/dev/null | grep -v ... | head -1)` — reproduced directly: under
     `pipefail`, an empty `ls` glob match aborts the script immediately via this assignment, before
     the existing `if [ -n "$JAR" ]` guard ever runs — breaking every cold-start build (no jar yet).
     Fixed by appending `|| true`, confirmed the repro now completes normally.
  3. Two best-effort diagnostic listings (`grep -rl "FAILED\|ERROR" .../*.txt | sed ... ` in both
     the unit and integration failure-reporting branches) — reproduced the same way: `pipefail`
     turns "no file happened to contain the literal string FAILED/ERROR" into a script-aborting
     error instead of just printing nothing. Fixed both with `|| true`.
  Other pipes in this file (pom.xml module-list extraction, JaCoCo version extraction) were left
  as-is deliberately — failing loudly there is correct, since those inputs are project-controlled
  and always expected to succeed; that's pipefail doing its actual job, not a regression.
- `scripts/build-and-test/run.sh` — 3 more optional-env-var reads needed the same `:-` treatment
  (`GITHUB_ACTIONS`, and — a subtler case — `TESTCONTAINERS_RYUK_DISABLED`/
  `INTEGRATION_TESTS_POSTGRES_FIXED_PORT` a second time, this time inside a `[ -n "$VAR" ] && ...`
  short-circuit rather than an `if`: the `-e`-exemption for `if`/`&&` conditions does **not** extend
  to `-u`'s unbound-variable check, which fires on any expansion regardless of context). The
  `unit=`/`integration=` property reads and the `tar | docker run` pipe (already wrapped in its own
  `set +e`/`PIPESTATUS[0]` handling, independent of `pipefail`) needed no change.
- `scripts/sonar/run.sh` — no fixes needed: `NO_GATE`/`PULL_LATEST` are pre-initialized to empty
  strings, `CONTAINER_STATUS` is always assigned via `$(... || true)` before use, the
  sonar-scanner's own `set +e`/`PIPESTATUS[0]` block is pipefail-independent, and the one
  `grep '^sonar.token='` read is guaranteed to succeed by the preceding `ensure_sonar_token` call.

All 5 files re-syntax-checked with `bash -n` after their edits.

**Real regression found and fixed while verifying 3a-3d end-to-end, 2026-09-18:** Phase 3c's own
`sed -i` shebang rewrite silently stripped the executable bit off-disk on every one of the 20
files it touched (a `sed -i` side effect on this system — the temp-file-then-rename it does
internally doesn't reliably preserve file mode). This first surfaced as a real, reproduced failure
(`Permission denied`) when actually running `scripts/activity-monitor.sh -- scripts/ci.sh ...` —
`activity-monitor.sh`'s own wrapping mechanism executes the target script directly (not via `bash
<script>`), so it's the one caller that actually depends on the executable bit; every other
documented usage in this repo already prefixes `bash`, which is why this went unnoticed until an
actual end-to-end run was attempted. Investigating further: `git status`/`git diff` showed nothing
wrong at all, because this repo's `core.fileMode` is `false` — `git ls-files -s` was needed to see
the real tracked mode, which showed the block: all 20 files were tracked as `100644` in git, not
just on disk, going back to *before this session even started* (confirmed via `git ls-tree` on the
very first commit at conversation start) — a latent, pre-existing git-tracked-mode bug that
`sed -i`'s disk-level side effect happened to expose for the first time, not something this
session newly introduced by itself. Fixed via `git update-index --chmod=+x` (bypasses
`core.fileMode`, unlike a plain `chmod` + `git add`) on all 20 files, verified `git ls-files -s`
now reports `100755` for each. A repo-wide sweep (`git ls-files '*.sh'` cross-checked against
tracked mode) found the identical latent bug in 15 more `.sh` files never touched this session
(e.g. `integration-tests/run.sh`, `docs/architecture/scripts/generate-architecture-model.sh`,
`.claude/nav/scripts/*.sh`) — currently harmless (nothing has stripped their on-disk bit), flagged
to the user as a candidate for the same fix rather than silently expanded into this task's own
scope. **Fixed on request the same session** — all 15 raised to `100755` via the identical
`git update-index --chmod=+x` treatment. Repo-wide sweep (`git ls-files '*.sh'` cross-checked
against tracked mode, every file) confirms zero `.sh` files remain at `100644` anywhere in the repo.

**Full end-to-end re-verification after the fix:** a real `scripts/ci.sh --unit --no-docs
--foreground` run (not a simulation) — confirmed `ci-runner` rebuilds successfully with
`shellcheck` newly baked into its image, and the actual live Dagu run shows `lint` and
`shellcheck` both passing (`✅ lint (23s)`, `✅ shellcheck (0s)`), `unit`/`archunit_metrics` also
passing, `integration`/`e2e`/`sonar`/`docs` correctly skipped (not requested), and the run's own
overall `CI run` step reporting success.

**3c. Shebang consistency.**
Verified: 19 scripts use `#!/bin/bash`, 6 use `#!/usr/bin/env bash`. Standardize on
`#!/usr/bin/env bash` (portable — resolves via `PATH` rather than assuming `/bin/bash`'s exact
location). Mechanical, low-risk; do in one pass.

**Done 2026-09-18.** Re-verified counts directly before touching anything (they'd drifted since
the original audit — 20/9 actual, not 19/6, from scripts added in the meantime): 20 files
converted. One candidate from the initial `grep -l` sweep, `scripts/activity-monitor/run.test.sh`,
turned out to be a false positive — its own line-1 shebang was already `#!/usr/bin/env bash`; the
grep matched a `#!/bin/bash` string appearing elsewhere in the file (a test fixture generating a
fake shebang'd script), not its own header. The actual fix (`sed` restricted to line 1 specifically)
left it untouched, confirmed via an empty diff. Verified afterward: `bash -n` syntax-checked on a
spot sample, `head -1` re-swept across all candidates confirms zero files still start with
`#!/bin/bash`.

**3d. ShellCheck as a real CI gate.**
No `shellcheck` invocation found anywhere under `scripts/ci/`. Add it as a step in
`scripts/ci.sh`'s pipeline (the Docker-based CI infrastructure already exists — this is one more
stage, not new infrastructure). Any pre-existing suppression needed must carry a
`# shellcheck disable=SCxxxx` with a one-line reason, per this project's own documentation-quality
bar — never a blanket/unexplained suppression.

**Must run inside `ci-runner`'s own Docker image (`scripts/ci/Dockerfile`), never rely on a
host-installed `shellcheck` binary** — this project's own standing convention is every tool runs
inside its own container (`build-and-test`'s build container, `sonar`'s scanner container,
`playwright`'s `pw-runner`, the whole `ci-runner` design itself) specifically so results stay
reproducible and the host stays clean. `apt-get install -y shellcheck` was run directly in this
sandbox during 3a's own investigation above purely to get a real diagnostic before touching code —
a one-off, throwaway install for that investigation only, explicitly not the real implementation
this phase needs (flagged by the user mid-investigation, not something to repeat unprompted).

**Done 2026-09-18.** `shellcheck` added to `scripts/ci/Dockerfile`'s `apt-get install` line (baked
into the `ci-runner` image itself — no other container to delegate to, unlike `lint`'s Node.js
dependency living in `pw-runner`). New `shellcheck` step in `scripts/ci/dagu/ci.yaml`
(`depends: build`, gated by a new `shellcheck` param, default `true`) runs
`find scripts playwright docs/architecture/scripts -name '*.sh' ... | xargs shellcheck
--severity=error`. **`--severity=error` only, per explicit user decision** — a warning/info/style
finding (this repo currently has a handful, e.g. `build.sh`'s pre-existing SC2154/SC2010/SC2086,
deliberately left untouched by 3a's own scope) never fails this gate, only a real
correctness-shaped bug does. `scripts/ci/run.sh` gained a matching `--no-shellcheck` flag (mirrors
`--no-lint`'s shape exactly); `--docs-only` now also skips it. `pipeline_metrics`'s `depends` list
extended to include `shellcheck`.

**Verified before wiring it in as a gate, not after:** ran the exact `find | xargs shellcheck
--severity=error` command locally (with `shellcheck` installed ad hoc, same one-off install as 3a)
against every `.sh` file this step will scan — real exit code `0` today, confirming the gate won't
immediately fail on day one from pre-existing warning-level findings.

## Approach — Phase 4: Documentation — enforce `improvement-141`'s own rule against current docs

Root `CLAUDE.md`/`.claude/rules.md` already states current-state docs must never cite an
`improvement-NNN` number or embed dated "resolved" narrative. Verified directly: 6 current
`DECISIONS.md` files still contain `improvement-NNN` references in prose:
`.claude/DECISIONS.md`, `docs/architecture/scripts/DECISIONS.md`, `integration-tests/DECISIONS.md`,
`marketplace-app/DECISIONS.md`, `marketplace-orchestrator/DECISIONS.md`, `scripts/ci/DECISIONS.md`.

- For each file, read every `improvement-NNN` occurrence in context and rewrite it to state the
  fact/decision itself without the ticket citation (the pattern `improvement-141`'s own fix already
  established) — never delete the substance, only the forward-link.
  A rewrite from an actual instance found in the audit: "closing off this specific recurring class
  of hardcoded-list drift (see `backlog/completed/BACKLOG-ARCHIVE.md`'s `improvement-181`...)"
  becomes a plain statement of what the fix was and why, with no ticket pointer at all.
- Do this file by file, presenting the before/after for each occurrence before writing — this is
  editing already-Accepted ADR text, not new content, so accuracy after the rewrite matters more
  than speed.

**Done 2026-09-22.** All 23 occurrences across the 6 files rewritten file by file, each before/after
presented for approval first (one occurrence turned out missed on the first pass —
`docs/architecture/scripts/DECISIONS.md`'s `--extract` mode entry under "Open goals" — caught and
fixed on the final repo-wide sweep). Final `grep -rl "improvement-[0-9]" --include="DECISIONS.md"`
across the whole repo returns nothing.

**Follow-up requested mid-task, not in the original plan:** since removing a `DECISIONS.md` →
backlog-ticket citation deletes the *inbound* pointer, the *outbound* direction (the backlog task
citing the ADR it produced) was checked for all 23 removed citations and added wherever missing —
backlog task files are historical and explicitly exempt from the ticket-citation ban, so this
direction is the correct place for that traceability to live going forward. 15 of the underlying
backlog files needed a new reference added (`improvement-181`→ADR-034, `improvement-142`→ADR-020,
`improvement-147`→ADR-025, `improvement-136`→ADR-001/ADR-003, `improvement-124`→ADR-003/ADR-075,
`improvement-169`/`171`/`111`/`172`→`.claude/DECISIONS.md` ADR-002, `improvement-183`→
`integration-tests/DECISIONS.md` ADR-009, `improvement-152`→`scripts/ci/DECISIONS.md` ADR-008,
`improvement-188`→ADR-084, `improvement-179`→ADR-075/ADR-076); 4 already had the correct reference
in place (`improvement-170`→ADR-033, `improvement-144`→ADR-024, `improvement-193`→ADR-082,
`improvement-037`→ADR-038 already covered the one real cross-reference that citation carried).

**Not required, optional/lower-value:** standardizing every ADR to use a literal "Rejected
alternatives" heading — verified only ~13% of ADRs use that exact heading, though many more discuss
alternatives inline within "Context" (which is arguably fine, not obviously a defect). If picked up
at all, treat as its own small follow-up, not blocking this task.

## Approach — Phase 5: CI/CD — security scanning gap

Verified: no dependency/CVE scanning (Trivy/Snyk/Dependabot/Grype/OWASP dependency-check) and no
secret-scanning gate exists anywhere in the repo. This is consistent with the project's own already-
documented decision to stay on a local Dagu-based CI runner before migrating to a hosted CI
(`improvement-028`, still open) — GitHub-hosted CI would bring secret scanning/Dependabot largely
for free, so this gap is a natural consequence of that deferral, not an independent oversight.

- Do not silently leave this unstated. Add one explicit line to wherever Phase 0's best-practices
  doc ends up, naming this as a consciously-deferred gap tied to `improvement-028`, the same way
  this project already treats other acknowledged gaps.
- Optional low-cost first step, only if picked up: `mvn dependency-check:check` (OWASP
  Dependency-Check) or `trivy fs .` as one more opt-in flag on `scripts/ci.sh`, mirroring how
  `--sonar`/`--no-gate` already work as flags on that script. Not required for this task's own
  done-ness — flag it as a natural next step in the best-practices doc instead if not implemented
  now.

**Decided 2026-09-18 (user directive):** this optional dependency/secret-scanning step will not be
picked up as part of this task — `improvement-028`'s own hosted-CI migration remains the deferred,
tracked place for eventually closing this gap.

## Approach — Phase 6: Java/SOLID-DRY — extract duplicated failure-rate-limiter

The original "Current state" audit covered Java/SOLID-DRY as one of its five areas, but no fix
phase above addressed it — this phase fills that gap with a real, verified finding surfaced during
this same task's work, added to this issue per explicit user instruction rather than a new task
file.

**Finding, verified directly against current code (2026-09-17):**
`marketplace-app/src/main/java/org/ost/marketplace/services/auth/AuthService.java` (`login()`) and
`user-spring-boot-starter/src/main/java/org/ost/user/services/UserService.java` (`register()`) each
hand-roll an identical Caffeine-backed sliding-window failure counter: a
`Cache<String, AtomicInteger>` (`expireAfterWrite(15 min)`, `maximumSize(10_000)`), the same
`get(key, _ -> new AtomicInteger(0))` → threshold-check → `throw TooManyAttemptsException` shape,
differing only in the threshold constant name, the message text, the cache key composition
(IP+email vs. clientIp alone), and whether a success path calls `.invalidate(key)` (`login()` does;
`register()` deliberately doesn't — registration has no retry-then-succeed flow to reset). This is
genuine structural duplication of one well-defined generic algorithm, not two coincidentally similar
domain rules — a real DRY case, not a premature-abstraction risk.
`TooManyAttemptsException` (both already throw it) already lives in
`platform-commons/src/main/java/org/ost/platform/core/TooManyAttemptsException.java` — a precedent
for cross-cutting rate-limiting infrastructure living directly in `org.ost.platform.core` (not
namespaced under one subsystem the way `YoutubeUtil` is under `platform.attachment.util`, since this
concern belongs to neither `user` nor `marketplace-app` alone). `platform-commons/pom.xml` has no
Caffeine dependency today (verified); `marketplace-app`/`marketplace-orchestrator`/
`user-spring-boot-starter` already declare it unversioned, relying on the Spring Boot BOM
`platform-commons` already imports.

**6a. New shared class**
`platform-commons/src/main/java/org/ost/platform/core/FailureRateLimiter.java` — a plain class
(not a Port/Hook/DTO) wrapping one Caffeine `Cache<String, AtomicInteger>`:
- Constructor `FailureRateLimiter(int maxAttempts, @NonNull Duration window)`.
- `checkAllowed(@NonNull String key, @NonNull String message)` — throws `TooManyAttemptsException`
  if the key is already at/over threshold.
- `recordFailure(@NonNull String key)` — increments the counter.
- `clear(@NonNull String key)` — invalidates the counter (only called by a caller with a
  reset-on-success flow, e.g. login; registration will not call it).

**6b. `platform-commons/pom.xml`** — add the `com.github.ben-manes.caffeine:caffeine` dependency,
no `<version>` (Spring Boot BOM already manages it), next to the existing
`jakarta.validation-api`/`lombok`/`spring-data-commons` block.

**6c. `AuthService.login()`** — replace the hand-rolled `Cache<String, AtomicInteger> loginAttempts`
field with `private final FailureRateLimiter loginLimiter = new FailureRateLimiter(MAX_LOGIN_ATTEMPTS, Duration.ofMinutes(15));`,
replace the get/threshold-check block with `loginLimiter.checkAllowed(key, "Too many failed login attempts, try again later");`,
replace `loginAttempts.invalidate(key);` with `loginLimiter.clear(key);`, replace
`attempts.incrementAndGet();` (in the `BadCredentialsException` catch) with
`loginLimiter.recordFailure(key);`. The existing `log.warn(...)` call currently sits inside the same
`if` block, before the `throw` — since `checkAllowed()` now throws internally, move that `log.warn`
call to just before the `checkAllowed()` call, otherwise it would never execute. Remove the
now-unused Caffeine/`AtomicInteger` imports.

**6d. `UserService.register()`** — same shape: replace `registerAttempts` with
`private final FailureRateLimiter registerLimiter = new FailureRateLimiter(MAX_REGISTER_ATTEMPTS, Duration.ofMinutes(15));`,
replace the get/threshold-check block with
`registerLimiter.checkAllowed(clientIp, "Too many failed registration attempts, try again later");`,
replace `attempts.incrementAndGet();` in the `DuplicateKeyException` catch block with
`registerLimiter.recordFailure(clientIp);` — **the existing `throw ex;` right after it in that same
catch block must stay**, this refactor only replaces the counter call, not the re-throw. Do **not**
add a `.clear()` call anywhere in `register()` — it has no success-path reset today and this refactor
must not change that observable behavior. Remove the now-unused Caffeine/`AtomicInteger` imports.

**Verification:** run the existing `AuthServiceTest` and the `integration-tests` `UserServiceTest`
unchanged first — both already simulate N failed attempts via a bounded loop and assert
`TooManyAttemptsException` at the threshold, so their assertions should pass with zero test changes
since observable behavior (threshold=5, 15-minute window, message text) is unchanged. Then run the
full `marketplace-app` unit suite and the relevant `integration-tests`/`user-spring-boot-starter`
suites. No other production code changes needed — this is a pure extract-shared-class refactor.

**Done 2026-09-17.** All four sub-steps applied exactly as planned, with one real bug caught and
fixed during implementation: an initial edit accidentally moved `AuthService`'s
`log.warn("Login blocked...")` to run unconditionally on every `login()` call instead of only on
the actual threshold-exceeded path (since `checkAllowed()` now throws internally instead of the
caller checking a boolean first). Fixed by wrapping the `checkAllowed()` call in a
`try/catch (TooManyAttemptsException ex)` that logs then rethrows — confirmed correct by reading the
resulting test log directly: `"Login blocked"` now appears exactly once, immediately after the 5th
`"Login failed"` line, not on every call. `UserService.register()` needed no such wrapping since its
original code never logged on the blocked path.
- `AuthServiceTest`: 7/7 passed.
- `integration-tests` `UserServiceTest`: 10/10 passed.
- Full `marketplace-app`/`query-lib`/`marketplace-orchestrator`/`marketplace-rest-api` unit suite:
  71/71 passed, including `ArchitectureRulesTest` (20/20) — confirms the new
  `platform-commons/org.ost.platform.core.FailureRateLimiter` class and its Caffeine dependency
  don't violate this project's own module-boundary rules.

## Approach — Phase 7: Playwright test isolation — open discussion, not decided, not scheduled

**Raised 2026-09-17, deliberately deferred — a discussion topic, not an implementation item.** While
deciding Phase 2a's trace-config fix, the fact that `playwright.config.js` already runs
`fullyParallel: false`/`workers: 1` specifically because "Vaadin + shared DB — parallel runs cause
race conditions" (own code comment), combined with `playwright/e2e/README.md`'s own statement that
"tests are serial and ordered — each spec depends on state left by the previous one," was flagged as
worth a real conversation: this project's e2e suite does not follow the official Playwright "isolate
tests by default" best practice (already listed as its own entry in
[`docs/best-practices.md`](../../docs/best-practices.md)'s Playwright section), and that has a real
consequence surfaced during this same task — a failed test cannot be safely retried (Phase 2a's
`retries: 1` option was rejected specifically because a retry wouldn't start from a clean state).

Not analyzed yet: whether this is worth changing (e.g. per-spec-file database reset, isolated
browser contexts/storage state per test instead of per suite), what it would cost given the current
Vaadin-session/shared-DB constraint, or whether the current serial-and-ordered design is a
deliberate, acceptable tradeoff that should just be documented more prominently rather than changed.
Pick this up as its own conversation when ready — do not start implementation from this entry alone.

## Approach — Phase 8: real bug found via Playwright verification — stale `<base href>` after client-side navigation

**Found and fixed 2026-09-18, while verifying Phase 2a's trace-config change with a real `e2e --ux`
run.** Not part of the original audit — a genuine, reproducible production bug surfaced by actually
running the test suite, not by static review.

**Finding:** `AdvertisementOverlay.openForView()`/`ProviderProfileCatalogOverlay.openForView()` push
a shareable URL (`/ads/{id}`, `/providers/{id}`) into the browser's history via
`UI.getCurrent().getPage().getHistory().pushState(...)` every time an advertisement/provider
overlay opens in view mode — normal usage, not just deep-linking. Since this is a client-side
history push (no real page reload), the page's `<base href>` tag — set once, correctly, at the
original full-page bootstrap — never gets updated to match the new, deeper URL. Any Vaadin-generated
relative resource URL computed afterward (confirmed concretely: a file upload's target URL) then
resolves against the stale base and gets a path segment wrong, hitting a path Spring/Vaadin doesn't
recognize and returning `403`.

**First diagnosis was wrong, corrected after re-verification:** an initial fix in
`MainView.java` (resyncing the URL once after consuming a one-time deep-link forward) did not
address the real trigger and a second full `e2e --ux` run reproduced the identical failure —
confirmed via the Playwright trace's network log (`Referer: .../ads/2`, upload URL wrongly
prefixed `/ads/VAADIN/dynamic/resource/...` → `403`) that the actual cause was the general
`pushState` call in the overlay classes, not the narrower deep-link-forward path `MainView.java`
touches only once at startup.

**Fix:** new `marketplace-app/src/main/java/org/ost/marketplace/ui/views/utils/BrowserHistoryUtil.java`
— `pushStateWithBaseSync(String path)` pushes the history entry and, in the same call, updates
`<base href>` via `executeJs` to the correct relative depth for the new path (same formula Vaadin's
own server-side bootstrap uses, confirmed by comparing real `<base href>` values returned for
`/`, `/ads`, and `/ads/2`). Replaces all 5 raw `UI.getCurrent().getPage().getHistory().pushState(...)`
call sites across `AdvertisementOverlay.java` (2) and `ProviderProfileCatalogOverlay.java` (3).
`MainView.java`'s earlier resync-on-deep-link-consumption call is left in place (harmless, still
correct for that one case) rather than reverted.

**Verification:** unit suite 71/71 (including `ArchitectureRulesTest`) after the fix. Full
`e2e --ux` run first showed 4 new, unrelated-looking failures (different spec files, an unrelated
`entity-activity-overlay` close timeout) — re-ran the identical suite a second time with zero code
changes between runs to test reproducibility: the second run passed 50/50 (13 skipped, same as
`--full`-gated spec 06 always shows) with zero failures. This confirms the 4 failures are **not
deterministically caused by this fix** (same code, clean pass). The originally failing test
(`moderatorEn edits EN advertisement — ... add and replace media`) passed cleanly in both runs,
confirming the actual bug fix itself works.

**Open, unresolved: the real cause of the 4 first-run failures is not established.** An initial
"Docker resource contention from long-idle leftover containers (`ci-runner`/`sonarqube`, up for
days)" theory was proposed and acted on (those containers stopped/removed) — but this was **never
verified with real data** (no `docker stats`/load measurement was taken at the actual failure
moment, and the containers were destroyed before that could be checked), and was explicitly
rejected as the explanation on review. This project's own `scripts/ci/DECISIONS.md` ADR-005 records
a real, previously-confirmed precedent for Playwright timeout flakiness under concurrent
dev-stack+e2e-stack+SonarQube load on this same sandbox — a plausible contributing factor, not a
confirmed one for this specific run. Flagged here rather than left silent: if the same
unrelated-looking failure pattern (`entity-activity-overlay` close timeout, or similar) recurs on a
future verification run, investigate the actual failing action's own code path directly instead of
reaching for the environment-contention explanation again unverified.

## Approach — Phase 9: Java/SOLID-DRY — extract duplicated category/city taxon-filter resolution

**Raised 2026-09-18, a second Java/SOLID-DRY finding in the same area Phase 6 already covers**
(the original audit's Java/SOLID-DRY area named more than the one failure-rate-limiter case).

**Finding, verified directly against current code:** `AdvertisementService`
(`advertisement-spring-boot-starter`) and `ProviderProfileService`
(`provider-profile-spring-boot-starter`) each carry four private methods
(`resolveCategoryAndCityFilter`/`resolveCategoryFilter`/`resolveCityFilter`/
`resolveTaxonIdFilter`, ~25 lines) that are byte-for-byte identical except for the hardcoded
`EntityType.ADVERTISEMENT` vs `EntityType.PROVIDER_PROFILE` and the filter DTO type
(`AdvertisementFilterDto` vs `ProviderProfileFilterDto`). Confirmed both DTOs expose the exact same
field shape (`Set<Long> categoryIds`, `Long cityTaxonId`), so one shared signature covers both
without an adapter.

**Decided approach — `default` method on `TaxonPort` itself, not a new class.** Three alternatives
were considered and ruled out before settling on this:
- A new plain class in `platform-commons` (mirroring Phase 6's `FailureRateLimiter` precedent) —
  workable, but adds a class, a name, and manual per-service instantiation for no real gain once
  the interface-default option below was found.
- `marketplace-orchestrator` — architecturally wrong, not just bigger: `marketplace-orchestrator`
  already depends on both starters, so a starter depending back on the orchestrator would invert
  the project's own three-layer dependency direction.
- Implementing it in `taxon-spring-boot-starter`'s own `TaxonPort` implementation class instead of
  the interface — would force `advertisement-`/`provider-profile-spring-boot-starter` to add a
  hard compile-time dependency on a sibling starter's concrete class, violating "no direct imports
  between sibling modules" and breaking `TaxonPort`'s existing optional-degradation contract
  (`ComponentFactory<TaxonPort>` / `findIfAvailable()`).

A `default` method directly on `TaxonPort` (`platform-commons`) avoids all three problems: zero new
classes, zero new inter-module dependencies (both `ComponentFactory` and `TaxonPort` already live
in `platform-commons`), and matches an existing precedent — `AuditActivityEnrichHook` already uses
a `default` method on a `platform-commons` SPI interface.

**Done (2026-09-18):**
1. Added `default Optional<Set<Long>> resolveCategoryAndCityFilter(@NonNull EntityType entityType,
   Set<Long> categoryIds, Long cityTaxonId)` plus a `private Optional<Set<Long>>
   resolveTaxonIdFilter(EntityType entityType, Set<Long> taxonIds)` helper to `TaxonPort`
   (`platform-commons/src/main/java/org/ost/platform/taxon/spi/TaxonPort.java`), delegating to the
   already-existing `findEntityIdsWithAnyTaxon`.
2. `AdvertisementService`: deleted all four private methods, replaced both call sites with a
   `resolveTaxonFilter(filter)` wrapper delegating to
   `taxonPortFactory.findIfAvailable().flatMap(p -> p.resolveCategoryAndCityFilter(EntityType.ADVERTISEMENT, filter.getCategoryIds(), filter.getCityTaxonId()))`.
3. `ProviderProfileService`: identical change, `EntityType.PROVIDER_PROFILE`.
4. `AdvertisementServiceCategoryFilterTest`/`ProviderProfileServiceTest` rewritten to stub
   `TaxonPort#resolveCategoryAndCityFilter` directly instead of `findEntityIdsWithAnyTaxon` — they
   now cover only each service's own delegation, not the AND-combine logic itself. New
   `TaxonPortResolveCategoryAndCityFilterTest` (`integration-tests/.../level1/taxon/`) covers the
   default method's own AND-combine logic directly, no Spring context — a `Mockito.mock(TaxonPort.class,
   CALLS_REAL_METHODS)` executes the real default-method body while only
   `findEntityIdsWithAnyTaxon` needs stubbing (6 test cases, `Tests run: 6, Failures: 0, Errors: 0`).
5. `platform-commons/DECISIONS.md` ADR-032 recorded: `TaxonPort.resolveCategoryAndCityFilter` as a
   default method — a narrow, bounded exception to platform-commons' "no business logic" rule.
   `.claude/rules/platform-commons.md` updated with a second, separate narrow-exception paragraph
   documenting the general shape this ADR licenses (a `*Port` default method composing only that
   same interface's own other abstract methods — no external calls, no state, no side effects).
   `.claude/nav/adr-index.md` regenerated.
6. High-effort `/code-review` run against the full Phase 9 diff (8 finder angles → dedup → 5
   independent verifies): 1 CONFIRMED finding (the original `TaxonPortResolveCategoryAndCityFilterTest`
   used a 14-method-`UnsupportedOperationException`-stub anonymous `TaxonPort` — fixed via
   `CALLS_REAL_METHODS` per above), 2 REFUTED and dropped (a missing `@NonNull` — null is
   semantically valid for `cityTaxonId`/`categoryIds`; inlining the `resolveTaxonFilter` wrapper —
   contradicts this project's own "2+ reuse sites → extract" convention, since both
   `getFiltered`/`count` call it).

**Verification:** `scripts/build-and-test.sh --no-unit --integration --integration-test
TaxonPortResolveCategoryAndCityFilterTest` — `BUILD SUCCESS`, 6/6 passing.

## Expected benefit

A project-grounded best-practices reference that future work (human or AI) can check claims
against instead of a generic checklist, plus a handful of concrete, already-verified bugs/gaps
fixed: a forbidden reflection-test pattern, a dead trace config, a live external test dependency,
two real ShellCheck-flagged risks, inconsistent script hardening, and stale ticket references in
current documentation.

## Related

- `marketplace-app/DECISIONS.md` — ADR-008 (test-through-public-entry-point rule that Phase 1a
  enforces).
- [improvement-091](../completed/tasks/improvement-091-loadmediastats-nondeterministic-main-attachment.md) —
  confirmed as the file that added the `id ASC` tiebreaker Phase 1b relies on.
- [improvement-028](tasks/improvement-028-minimal-ci-pipeline.md) — the hosted-CI migration Phase 5's
  security-scanning gap is a natural consequence of deferring.
- `04-provider-profile-flow.spec.js`'s 34 `waitForTimeout` calls — this citation previously (wrongly)
  claimed the violation was already tracked by `improvement-063`; confirmed 2026-09-18 that `063`
  never mentioned this file at all and has since been closed as invalid for an unrelated reason. Now
  tracked as [improvement-133](tasks/improvement-133-deferred-oversized-review-findings.md) entry 22
  instead — not duplicated into this task's Phase 2.
- `.claude/rules.md` — "No task/ticket numbers... in current-state documentation" (the rule Phase 4
  enforces against real current violations).
