# improvement-181: Module-list config/auto-discovery for build scripts

**Type:** improvement — dev-experience/tooling, no live bug
**Module:** `scripts/build-and-test/build.sh`, `integration-tests/run.sh`,
`marketplace-app/src/test/java/org/ost/marketplace/architecture/ArchitectureRulesTest.java`,
root `pom.xml`
**Priority:** ⚪ lowest
**When:** independent, no blockers

## Current state

Adding a new Maven module currently requires manually updating a hand-maintained module-name list
in at least three separate non-`pom.xml` places, in addition to `pom.xml` itself:
- `scripts/build-and-test/build.sh`'s `UNIT_MODULES` string (plus a second, parallel `for m in ...`
  loop a few lines below it that copies Surefire reports) — a module left out here silently never
  has its tests executed by `--unit`, with no error or warning.
- `integration-tests/run.sh`'s `STARTER_MODULES` string, used for staleness auto-detection before
  reinstalling starter JARs.
- `ArchitectureRulesTest`'s `STARTER_PACKAGES` constant, plus separate inline
  `resideInAnyPackage(...)` package lists in at least two individual `@ArchTest` rules.

Confirmed directly (improvement-073, 2026-09-03): adding two new modules
(`apikey-spring-boot-starter`, `marketplace-rest-api`) required editing all of the above by hand;
the `build.sh` list was missed on the first pass, so the new module's own unit tests
(`ApiKeyAuthenticationFilterTest`, `ApiKeyControllerTest`) compiled successfully but were never
actually executed until the gap was noticed and fixed separately.

## Why change

Every one of these lists can silently drift from the real module set with no build failure to
catch it — a forgotten update doesn't error, it just quietly stops covering the new module (skipped
tests, skipped architecture checks, or a stale staleness-check). This is exactly the class of
problem `docs/architecture/scripts` and `scripts/sonar` already solve for their own module-list
needs (self-describing `pom.xml` properties / auto-validated against `pom.xml` before every run —
see `.claude/nav/adr-index.md`), just not yet applied to these three.

## Expected benefit

Adding a future module only requires updating the one place Maven already requires (root
`pom.xml`'s `<modules>`) — the scripts/test derive their module list from it automatically, so a
forgotten manual update can no longer silently skip a module's tests or checks.

## Approach

1. **Auto-discovery from `pom.xml`** — parse root `pom.xml`'s `<modules>` list at runtime in
   `build.sh`/`integration-tests/run.sh` instead of hardcoding the module name string, mirroring
   the existing `docs/architecture/scripts`/`scripts/sonar` precedent. `build.sh`'s `UNIT_MODULES`
   would need an additional marker to distinguish "has its own unit tests" modules from starter
   modules whose tests all live in `integration-tests` — e.g. a dedicated `pom.xml` property, same
   shape as the already-existing `<architecture.boundedContext>` property every starter's `pom.xml`
   already carries. For `ArchitectureRulesTest`'s `STARTER_PACKAGES`, the `boundedContext=starter`
   property would need to be readable from the test's own runtime classpath (e.g. baked into a
   properties resource at build time), since raw `pom.xml` isn't naturally on a test classpath.
   More robust, but real engineering effort and a new convention to design and document.
2. **Checklist only** — no structural change; add a single doc (e.g. an "adding a new module" note)
   listing every file that needs a manual touch. Cheap, but still relies on remembering to follow
   it — doesn't actually prevent the silent-skip failure mode confirmed above.

## Decision (2026-09-15) — Approach 1 (auto-discovery), plan below

Chosen over the checklist-only option per the standing "when a choice exists between a minimal and
an architecturally clean fix, choose the clean one" rule — a checklist doesn't actually prevent the
silent-skip failure mode this issue exists to close.

**Real, currently-active bug confirmed while researching this plan (not just historical):**
`html-sanitizer-lib/src/test/java/org/ost/sanitizer/HtmlSanitizerTest.java` exists but
`build.sh`'s `UNIT_MODULES` doesn't include `html-sanitizer-lib` — this test is never executed by
`--unit` today, right now, the exact failure mode this issue describes.

**Major simplification found during research — no new `pom.xml` property needed anywhere,**
contrary to the original "Approach 1" sketch above, which assumed one would be required:

1. `build.sh` (lines 112-121) **already implements this exact pattern** for a different list
   (`TARGET_CLASSES_MODULES`), with its own comment explaining why: "Module list derived from root
   pom.xml... instead of hand-maintained -- a separately hardcoded copy here silently drifted out
   of sync before." `UNIT_MODULES` (line 174) and its parallel `for m in ...` report-copy loop
   (line 189) are the two remaining hardcoded lists in the *same file* that never got the same fix.
2. Confirmed directly (`find <module>/src/test/java -name '*.java'`) which of the 13 non-
   `integration-tests` modules have their own unit tests: `query-lib`, `html-sanitizer-lib`,
   `marketplace-orchestrator`, `marketplace-rest-api`, `marketplace-app` (5) — every
   `*-spring-boot-starter` module plus `platform-commons` has zero (matches this project's own
   "domain starters carry no test code, `integration-tests` owns it" convention). This "has its own
   `src/test/java/**/*.java`" check is a clean, mechanical proxy for "should `--unit` run it" — no
   new pom.xml property needed.
3. Confirmed directly (`find <starter>/src/main/java/org/ost -maxdepth 1`) that every one of the 7
   starter modules has exactly one subdirectory there, and it matches
   `ArchitectureRulesTest.STARTER_PACKAGES` exactly — including the one case that breaks a naive
   "strip `-spring-boot-starter`, remove dashes" string rule:
   `provider-profile-spring-boot-starter` → `org.ost.provider`, not `org.ost.providerprofile`. The
   real package name is only discoverable from the filesystem, not derivable from the module name
   by a fixed transform — but it *is* cleanly discoverable that way, purely at test run time, no
   pom.xml/properties-resource involved.

### Part A — `scripts/build-and-test/build.sh`: fix `UNIT_MODULES` (real, active bug)

- `run_unit_tests()`'s `UNIT_MODULES="query-lib,marketplace-app,marketplace-orchestrator,marketplace-rest-api"`
  (line 174) → computed the same way `TARGET_CLASSES_MODULES` already is (lines 120-121): parse
  `pom.xml`'s `<modules>` list via `sed`/`grep` (no python3 — this script runs inside the minimal
  JDK-only build container, per its own header), keep only modules where
  `find "$ROOT/$m/src/test/java" -name '*.java'` finds at least one file.
- Keep a separate `ALL_UNIT_MODULES` (the full auto-discovered set) distinct from `UNIT_MODULES`
  (possibly narrowed to one module by `$UNIT_TEST_ARG`) — the existing report-copy loop at line 189
  (`for m in query-lib marketplace-app marketplace-orchestrator marketplace-rest-api`) always
  iterates the *full* set today regardless of narrowing, matching current behavior exactly; only
  its hardcoded module list becomes `$ALL_UNIT_MODULES` space-separated.
- Lines 176-180's `UNIT_TEST_ARG` single-module check (`if [ "$UNIT_TEST_ARG" = "query-lib" ] ||
  ...`) becomes a membership test against the computed list instead of 4 hardcoded string
  comparisons (e.g. `echo ",$ALL_UNIT_MODULES," | grep -q ",$UNIT_TEST_ARG,"`).
- Fixes the real `html-sanitizer-lib` gap as a direct, immediate, verifiable consequence — no
  separate fix needed for it.

### Part B — `integration-tests/run.sh`: fix `STARTER_MODULES`

- Line 103's hardcoded `STARTER_MODULES="platform-commons advertisement-spring-boot-starter ..."`
  → the inverse of Part A's filter: parse `pom.xml`'s `<modules>`, exclude `integration-tests`
  itself, keep only modules where `find .../src/test/java -name '*.java'` finds **nothing**. Same
  derivation source as Part A (both read from the one `pom.xml` module list), just the opposite
  predicate — this list and `UNIT_MODULES` are complementary sets by construction once both are
  computed this way, so they can never silently drift apart from each other.
- This script runs on the host (not the minimal build container, confirmed via
  `integration-tests/CLAUDE.md`), so `python3` would be available here if ever needed — kept to the
  same portable `sed`/`grep` approach anyway, for consistency with Part A rather than mixing tools.

### Part C — `ArchitectureRulesTest.java`: derive `STARTER_PACKAGES` from the filesystem (higher risk, most verification needed)

- Replace `private static final List<String> STARTER_PACKAGES = List.of("org.ost.audit", ...)`
  (line 42-44) with a computed value: list sibling directories of `marketplace-app` (i.e. `..` from
  this module's own working directory, which Maven always sets to the module's own directory
  regardless of how `mvn test` is invoked — `-pl`-scoped or not) whose name ends with
  `-spring-boot-starter`; for each, list the one subdirectory under `src/main/java/org/ost/` and
  prefix it `org.ost.`.
- Needs `java.nio.file.Files`/`Path`/`Paths` (already simple JDK APIs, no new dependency).
- **Real risk, not hypothetical:** a bug in this discovery logic that returns an empty or wrong
  list wouldn't fail loudly — several `@ArchTest` rules using `STARTER_PACKAGES` would just
  trivially pass on an empty set (nothing to check), silently disabling real architecture
  enforcement instead of erroring. Mitigation: add one plain `@Test` (not `@ArchTest`) asserting
  the discovered list has exactly 7 entries and contains every currently-expected package name —
  a safety net that fails loudly if discovery ever breaks or a starter's package changes shape.
- Verify by deliberately breaking a rule temporarily (e.g. a throwaway Vaadin import in a starter
  class) after this change and confirming `starters_must_not_depend_on_vaadin` still catches it —
  not just that the suite compiles and passes on already-clean code.

### Verification (2026-09-15) — real results, not just compiled/assumed

1. **`bash scripts/build-and-test.sh --unit --no-integration`** — ran twice. First run: real,
   previously-hidden `HtmlSanitizerTest.sanitize_formattingLinksBlocksAndPre_arePreserved` failure
   surfaced (see "Real bug found during verification" below) — this alone is direct proof
   `html-sanitizer-lib` is now actually executed by `--unit` for the first time. Second run (after
   fixing that test): host-side `scripts/build-and-test/reports/surefire/html-sanitizer-lib/` now
   exists with a fresh Surefire report — `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`. Every
   other module in the auto-discovered set (`query-lib`, `marketplace-orchestrator`,
   `marketplace-rest-api`, `marketplace-app`) also confirmed 0 failures via their own fresh
   Surefire reports; `integration-tests` confirmed excluded (not present in `ALL_UNIT_MODULES`).
2. **`bash integration-tests/run.sh --sandbox smoke`** — real run, staleness check correctly
   derived the 8-module set (`platform-commons` + all 7 starters, confirmed via the real
   "Installing fresh starter JARs: ..." log line matching exactly), triggered a real reinstall,
   then `PostgresContainerSmokeTest`: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`, overall
   `===== PASSED =====`. A minor cosmetic bug found and fixed along the way: `tr '\n' ' '` left a
   trailing space (→ trailing comma in the `-pl` argument) — Maven tolerated it, but switched to
   `paste -sd' '` (matching the comma-list pattern already used for `UNIT_MODULES`) for cleanliness.
3. **`ArchitectureRulesTest`** ran as part of verification step 1's `marketplace-app` test run:
   `Tests run: 20, Failures: 0, Errors: 0, Skipped: 0` (19 `@ArchTest` rules + the new safety-net
   `@Test`) — real proof `discoverStarterPackages()` correctly resolves the reactor root and finds
   real starter packages in the actual Maven test execution context, not just compiles.
4. **Real-bug proof superseded the originally-planned synthetic "add a 15th module" test** — a
   real, pre-existing module (`html-sanitizer-lib`) getting picked up automatically, with zero
   manual edits to any of the 3 files this issue touches, having been silently skipped for its
   entire existence until this fix, is stronger evidence than a synthetic module would have been.
   Not run separately.
5. **Deliberate ArchUnit temporary-violation check (Part C's own extra safety check).** First
   attempt — a bare unused `import com.vaadin.flow.component.Component;` in `ApiKey.java` — failed
   to prove anything twice over: `apikey-spring-boot-starter` has no Vaadin dependency at all, so it
   didn't even compile; and separately, an unused import creates no real bytecode reference, so even
   if it had compiled, ArchUnit's `dependsOnClassesThat()` (a bytecode-level check) would have seen
   nothing. Corrected approach: temporarily added `vaadin-core` as a real compile dependency to
   `apikey-spring-boot-starter/pom.xml` (resolved via the already-imported root `vaadin-bom`, no
   version needed) and a real reference (`private static Component throwawayVaadinReference() {
   return null; }`) to `ApiKey.java`. Re-ran `ArchitectureRulesTest`: `Tests run: 21, Failures: 1` —
   `starters_must_not_depend_on_vaadin` failed with the real ArchUnit violation message naming
   `org.ost.apikey..` and `com.vaadin..`, the other 20 tests still passed. Confirms
   `discoverStarterPackages()` produces a `STARTER_PACKAGES` list the rule actually enforces against,
   not one that silently passes on nothing. Both temporary files (`pom.xml`, `ApiKey.java`) reverted
   immediately after via `git checkout --` — confirmed clean via `git status --short`.
6. **Full `bash scripts/build-and-test.sh --unit --integration --sandbox`** (Definition of Done) —
   real run, 3m48s, exit 0. Aggregated every Surefire report across all 6 modules that produced one:

   | Module | Classes | Tests | Failures | Errors | Skipped |
   |---|---|---|---|---|---|
   | query-lib | 4 | 43 | 0 | 0 | 0 |
   | html-sanitizer-lib | 1 | 6 | 0 | 0 | 0 |
   | marketplace-app | 7 | 71 | 0 | 0 | 0 |
   | marketplace-orchestrator | 12 | 116 | 0 | 0 | 0 |
   | marketplace-rest-api | 12 | 100 | 0 | 0 | 0 |
   | integration-tests | 67 | 430 | 0 | 0 | 0 |
   | **Total** | **103** | **766** | **0** | **0** | **0** |

   All 766 tests across 103 test classes passed — 0 failures, 0 errors, 0 skipped.

### Real bug found and fixed during verification (not part of the original plan)

`HtmlSanitizerTest.sanitize_formattingLinksBlocksAndPre_arePreserved` asserted that
`<a href="https://example.com">link</a>` passes through `HtmlSanitizer.sanitize()` byte-for-byte
unchanged. Real output (confirmed via the actual Surefire failure diff, not assumed):
`<a href="https://example.com" rel="nofollow">link</a>` — `Sanitizers.LINKS` (OWASP
java-html-sanitizer) adds `rel="nofollow"` to every link, standard documented behavior, not a bug
in `HtmlSanitizer` itself. The test's assumption was simply never checked before — this exact test
had never actually run until this issue's own fix wired `html-sanitizer-lib` into `--unit`. Fixed
the test's expected value to match the real, correct sanitizer output.

## Related

- Surfaced while implementing the REST-API module split — see
  `backlog/completed/tasks/improvement-073-rest-endpoint-infrastructure-test-seeding.md` (closed).
- `.claude/nav/adr-index.md` — `docs/architecture/scripts` and `scripts/sonar` already solve this
  same class of problem for their own module lists.
- `docs/architecture/scripts/DECISIONS.md` ADR-034 — the Database ERD's `remarks=`-marker
  convention this same "no further hardcoded list" pattern was later applied to.
