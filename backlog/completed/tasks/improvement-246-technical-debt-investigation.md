# improvement-246: Technical debt investigation of the project (ten hypotheses, read-only)

**Type:** improvement — a read-only investigation, no change until its report is decided
**Module:** the whole repository (`Dockerfile`, root and module `pom.xml`, `.claude/`, `scripts/ci/`, `platform-commons`, `marketplace-orchestrator`, `marketplace-rest-api`, `marketplace-mcp`)
**Priority:** 🟡 medium, at the top of the backlog at the user's request
**When:** independent, no blockers

## Current state

Checked on 2026-11-08 in the repository, before the investigation (the investigation itself has not run):

- H1: `Dockerfile` has no `COPY */pom.xml` line; it has `COPY . .` (line 19) in the `builder` stage, so the whole tree is copied. The premise of H1 does not hold as written.
- H2: the eight `DECISIONS.md` files have 0 `## ADR` headings and 8 to 16 lines each. `promotion-spring-boot-starter/DECISIONS.md` calls itself a generated index that lists decisions of other modules through their `**Also affects:**` tag, so the format is intended.
- H3: `.claude/rules.md` has 919 lines and 60,170 characters; 15 sections start with `> ## ⛔`.
- H4: a sentence-level comparison of the four skill files (81,469 bytes together) found 4 sentences longer than 70 characters that occur in two files, 887 characters in total; the check did not look for consecutive sentences.
- H5: `archunit-junit5` has a literal version `1.4.2` in `marketplace-app/pom.xml` and `marketplace-mcp/pom.xml`; the other versions in the module poms use `${...}` properties. The root `<dependencyManagement>` lists no `archunit-junit5`, `springdoc-openapi-starter-webmvc-ui`, `vaadin-dev` or `caffeine`. The root enforcer has `dependencyConvergence` (`pom.xml:241`).
- H6: the root `pom.xml` has `spring-boot-starter-parent` 4.1.1 and `vaadin.version` 25.3.2; root `CLAUDE.md` says Spring Boot 4.1.0 and Vaadin 25.2.3.
- H7: `maven-compiler-plugin` in the root `pom.xml` (lines 198 to 202) uses `<source>` and `<target>`, not `<release>`.
- H8: `.claude/nav/adr-index.md` has 283 decision rows, 33 of them with "superseded", "corrected" or "reversed" in the line (about 12 percent).
- H9: `scripts/ci/run.sh` has about 20 option lines (counted by a pattern, not read in full), and one of them is not named in any file of `backlog/completed`.
- H10 was not checked.
- Second prompt (deep verification of recent changes, below), checked on 2026-11-08: the names it uses exist in the code: `FeedbackRepository.deleteByEntity` (line 413), `FeedbackPort.deleteByEntity(EntityType, Long)`, `deleteForReasonExcept` (2 files), `EntityRef(EntityType, Long)` in `platform-commons`, `.dockerignore`, the `runtime` scope of the starters in `marketplace-orchestrator`, and `<exclude>org.ost:*-spring-boot-starter</exclude>` in all ten starters; `ContactRepository.deleteByEntity(EntityType, Long)` has the same shape as the feedback one. Its sections A and B overlap with the index audit already done in the database refactoring (six of seven removed indexes judged by the query code, one measured on a temporary table); its section G overlaps with H1 and H5 above.

## Why change

The user pasted an investigation prompt about the technical debt of the project and asked whether it makes sense; the checks above show which hypotheses hold as written (H2 fact, H3, H5 in part, H6, H7), which have a wrong premise (H1) and which are not yet checked.

## Expected benefit

A report with a status, evidence, root cause, impact, the smallest fix and its risk for each hypothesis, a summary table and the three fixes to do first.

## Approach

Run the investigation exactly as the prompt below describes: read-only, no commit, no build or test without separate permission, report in English, chat in Ukrainian. Hypothesis H1 starts from what `COPY . .` does and whether the build depends on `~/.m2`; H2 states that the eight files are generated indexes.

### The investigation prompt (as given by the user)

#### Role
You are an audit engineer. Investigate, confirm or refute concrete hypotheses about technical debt in this repository. Change nothing and commit nothing. The truth is in the code and configuration, not in .claude/rules.md/CLAUDE.md/DECISIONS.md (those are what is being checked).

#### Method
1. For each hypothesis read the real files with Read/Grep/Glob.
2. Every statement carries a file path and a line number. No "it seems", "probably".
3. If a hypothesis is not confirmed, say so; that is a valuable result.
4. For outside knowledge (version compatibility, framework behaviour) use WebSearch/WebFetch with a link to the primary source (release notes, Maven Central, the official compatibility matrix), not a blog.
5. Write nothing to files. Do not run build/test without separate permission; report first.

#### Hypotheses

H1. The Dockerfile does not build the whole reactor. File: `Dockerfile` (root). Compare `COPY */pom.xml` with `<modules>` in the root `pom.xml` and with the dependencies of `marketplace-orchestrator/pom.xml` + `marketplace-app/pom.xml`. Which modules are missing? Will the build fail, or quietly take stale JARs from `~/.m2`? Is the problem reproducible in a clean environment?

H2. 8 DECISIONS.md without `## ADR-NNN:` headings. Files: `advertisement-spring-boot-starter`, `apikey-spring-boot-starter`, `feedback-spring-boot-starter`, `marketplace-mcp`, `marketplace-rest-api`, `promotion-spring-boot-starter`, `provider-profile-spring-boot-starter`, `user-spring-boot-starter`. Confirm the format is non-standard, show the first 10 lines of each, estimate how many ADR entities are really there and whether they can be mechanically brought to `## ADR-NNN:` without losing content. What % of ADR coverage does `adr-index.md` lose?

H3. `.claude/rules.md` has grown. Count: lines, approximate tokens, number of sections with ⛔, number of sections with "violated multiple times"/"violated repeatedly". Classify each ⛔ rule: (a) has a natural file trigger → can move to `.claude/rules/<domain>.md`; (b) has none → must stay eager. Give numbers: X% can move, Y% stays.

H4. Duplication in `.claude/skills/`. Files: `module-doc-standards`, `module-readme-standards`, `infra-doc-standards`, `infra-readme-standards`. Find paragraphs that repeat verbatim or almost verbatim (threshold: ≥2 consecutive sentences with the same meaning). Estimate how many tokens this costs when a skill loads and whether it breaks the own rule "one fact, one canonical home".

H5. Dependency convergence risks. Compare `<dependencyManagement>` in the root `pom.xml` with the actual `<version>` in `marketplace-app/pom.xml`, `marketplace-mcp/pom.xml`, `marketplace-rest-api/pom.xml`, `integration-tests/pom.xml`. Find versions pinned locally but not in the root (candidates: `archunit-junit5`, `springdoc-openapi-starter-webmvc-ui`, `vaadin-dev`, `jsoup`, `caffeine`). Does `dependencyConvergence` pass now? Is there a risk when a new module is added?

H6. Compatibility of Spring Boot 4.1.1 + Vaadin 25.3.2 + Java 25 + Jackson 2/3. Check through WebSearch/Maven Central: do these versions exist? Are Vaadin 25.3.2 and Spring Boot 4.1.1 compatible (official matrix)? Does Vaadin 25 support Java 25? Are there known Jackson 2.x + 3.x conflicts in `platform-commons`? Give links to primary sources.

H7. `maven-compiler-plugin` `<source>/<target>` instead of `<release>`. File: root `pom.xml`, the `maven-compiler-plugin` section. Explain the difference for Java 25, estimate the risk of leaving it vs moving to `<release>`.

H8. Quality of `adr-index.md` — Superseded chains. Count ADRs whose status contains "Superseded"/"Accepted — X superseded by Y"/"corrected"/"reversed". How many ADRs describe a state on a date, not the current state? Are there ADRs that can be deleted or merged without losing information? Estimate how much the index shrinks after cleaning.

H9. CI complexity. Files: `scripts/ci.sh`, `scripts/ci/run.sh`, `scripts/ci/dagu/ci.yaml`, `.claude/commands/ci.md`. Count: flags in `ci.sh`, steps in the DAG, volumes, containers, env variables. Compare with what is really used in `backlog/completed/` (grep for `/ci --<flag>`). How many flags were never used? Can it be simplified without losing functionality?

H10. Other debt. Go through `platform-commons`, `marketplace-orchestrator`, `marketplace-rest-api`, `marketplace-mcp` and record 1–3 concrete findings that are not in H1–H9. Do not invent: better 0 than a stretch.

#### Report format

For each hypothesis:

```
### HN. <name>
**Status:** confirmed / rejected / needs-further-check
**Evidence:** <path:line, quote>
**Root:** <why it happened — one sentence>
**Impact:** <what breaks or what it costs — concrete, with a number where possible>
**Fix:** <the smallest action, estimate: XS/S/M/L>
**Fix risk:** <what can break>
```

At the end — a summary table (H1–H10: status, effort, impact) and the top 3 to fix first.

#### Limits
- Read-only. No Write/Edit/Bash that changes files.
- No commits.
- Build/test only with separate permission.
- Report language: English (per `.claude/rules.md` Language); in chat — Ukrainian.

### The second investigation prompt: deep verification of recent changes (as given by the user, translated)

#### Deep Verification of Recent Changes: Subtle Bugs and Regressions

Run a deep verification of the latest changes in the repository. This is not a general code review and not a search for style improvements. The goal is to find subtle errors, hidden regressions and unaccounted consequences of the changes that may not show up in ordinary happy-path testing.

##### 1. Context and rules

- Compare the current HEAD with the previous state of the changes.
- Check not only the changed lines but all dependent places: callers, SQL, DTOs, Ports, API, UI, tests, Liquibase, configuration.
- Do not propose abstract best practices.
- Do not invent problems without evidence.
- Do not change the code. This is an audit only.
- Do not refactor and do not widen the scope.
- `marketplace-orchestrator` is a deliberate architectural decision. Do not propose moving, dismantling or redesigning it.
- Do not duplicate known backlog items. Check the existing tasks and ADRs before forming new recommendations.
- If confirming a finding needs a test run, SQL or `EXPLAIN`, run it if possible. If not, state clearly what remained unchecked.

##### 2. Priority areas

**A. SQL and data integrity.** Check:
- whether the removed indexes match real queries, filters, sorting and JOINs;
- whether selectivity or the execution plan of queries got worse;
- whether composite indexes work correctly given the column order;
- whether an index was removed that a query uses through another combination of conditions;
- whether full table scans appear on large tables;
- whether the new DELETE/UPDATE queries match the real relations between records;
- whether data still used by other records can be deleted;
- whether there are hidden assumptions of 1:1 or 1:N not enforced by UNIQUE/FK constraints;
- whether NULL, empty collections, duplicates and missing records are handled correctly;
- whether the semantics of filtering, sorting or pagination changed.

Check the new `FeedbackRepository.deleteByEntity()` in particular: the order of deleting related records; the behaviour of a data-modifying CTE in PostgreSQL; deleting `feedback_content` if other records can reference it; the behaviour when there is no feedback; the behaviour with partly present related data; atomicity and rollback; possible race conditions with parallel operations. Do not count a problem as confirmed only because it is theoretically possible; check the actual constraints, callers and data model.

**B. Liquibase and compatibility of existing databases.** Check every changelog change:
- whether already applied changesets were edited instead of new ones being created;
- what exactly `validCheckSum ANY` does and whether it masks a divergence between schemas;
- whether existing installations get the same changes as a clean installation;
- whether old columns and indexes are really removed;
- whether a new database is created correctly while an old one stays with the old schema;
- whether the order of the migration changesets is safe;
- whether data is lost during migration;
- whether a migration can be run again or after a partial failure;
- whether changelog, Java model and SQL repository agree.

Check separately the removal of `assigned_by`, `valid_from` / `valid_until`, and of the promotion, feedback, provider profile and taxon indexes. For each change say whether it is correct for: 1. a clean database; 2. a database where the previous changesets are already applied; 3. a database with real data.

**C. Promotion: the change of semantics.** Check the whole flow after `valid_from` / `valid_until` were removed: creating, updating and deleting a promotion placement; determining the promoted status; filtering and sorting; the API response; the UI display; the sitemap; the provider profile read flow; external consumers of the DTO/API. Find out: whether expiration is really not needed anywhere else; whether old records with a non-null `valid_until` remain; whether the behaviour of old records changed; whether every place that used to take expiration into account now has equivalent logic; whether a difference appeared between the UI, the REST API and the sitemap. Check `findPromoted()` and `deleteForReasonExcept()` together with the existing indexes, especially the efficiency of the `entity_type + reason` conditions.

**D. Transactions and concurrency.** Check the new and changed operations for: partial execution; a missing transaction where one is needed; wrong transaction boundaries; calls to another module inside a transaction; a rollback that does not cover all changes; deadlocks; race conditions; lost updates; wrong behaviour with parallel delete/update. Check in particular the integration of `FeedbackAccessService.deleteByEntity()` into `ProviderProfileSaveService.delete()`: whether one transaction covers all needed changes; whether orphan records remain; whether the optional feedback module behaves correctly; whether the order of operations and the audit logging are kept.

**E. Cross-module contracts.** Check the changes of Ports, DTOs and service contracts: whether a new needless dependency appeared; whether the same information is passed in several ways; whether enrichment is duplicated; whether needless queries between modules appeared; whether behaviour changed when an optional module is absent; whether the signatures of Port, implementation and callers agree; whether the module isolation rules are broken. Consider `FeedbackPort.deleteByEntity(EntityType, Long)` separately: whether the signature fits the current idea of typed references; whether using the existing `EntityRef` is appropriate; whether a new local format for passing a composite identity is created; whether the change would really reduce code and coupling and not just add an abstraction. Do not propose a global registry or new tables only to replace two arguments by one. First assess the existing types and real scenarios.

**F. Promotion enrichment and performance.** Check: whether repeated queries were really removed; whether promotion placements are loaded again in the same scenario; whether all placements are loaded when only the ids of the current page are needed; whether a hidden N+1 appeared; whether the volume of data passed between layers grew; whether pagination got worse; whether the number of SQL queries changed in the REST/UI/sitemap scenarios. Do not propose caching or asynchrony without a measured problem.

**G. Docker and Maven.** Check the new Dockerfile: whether `COPY . .` works correctly with the real `.dockerignore`; whether needless files get into the build context; whether secrets, local configs or build output are copied; whether the Maven reactor build is broken; whether `-pl marketplace-app -am` works; whether the efficiency of the Docker layer cache is lost; whether the Maven cache mount is really used; whether the build behaviour changed against the old version. Check also the change of dependency scope in `marketplace-orchestrator`: compile/runtime/test classpaths; production packaging; Spring Boot auto-configuration discovery; integration tests; running the application built as a JAR; whether differences appeared between the IDE and the Maven build. Check the wildcard exclusion `org.ost:*-spring-boot-starter`: whether it excludes dependencies that were not meant to be excluded.

**H. Hidden functional regressions.** For every changed behaviour find: all callers; all API/UI entry points; negative scenarios; boundary values; scenarios with missing or stale data; scenarios with parallel requests; compatibility with existing data. Check the removal of fields from the DTO/API and the changes of promotion behaviour with special care: not only the internal callers but also the public contracts.

##### 3. Test review

Review the matching unit, Testcontainers and Playwright tests. Do not only check that tests exist: check whether they would really have found the defects of the sections above. Find: tests that check only the happy path; tests that fix the state themselves before the assertion; missing rollback/concurrency/integration scenarios; tests that no longer match the new behaviour; cases where a mock hides an SQL or transaction problem. For SQL, CTEs, constraints and Liquibase prefer a real PostgreSQL through Testcontainers, not H2 or a mock. If you run tests, give the exact commands and results. Do not claim that a test passed if it was not actually run.

##### 4. Result format

No general description of the changes. A list of concrete findings is needed. For each finding: 1. Name of the problem. 2. Severity: Critical / High / Medium / Low. 3. Status: Confirmed / Likely / Needs verification. 4. File and the exact method/SQL/changeset. 5. What exactly is wrong. 6. The concrete scenario in which the problem occurs. 7. The consequence for data, performance or behaviour. 8. The minimal fix. 9. The test that will prove the problem is fixed.

Split the result into: Confirmed bugs (proven errors); Potential regressions (reasoned risks that need checking); Missing tests (important scenarios without test coverage); Performance concerns (only those with a concrete basis); Verified safe changes (changes that were checked and create no problems).

##### 5. Final conclusion

At the end give: the top 5 problems by priority; what must be fixed before the merge; what can be left as it is; which changes need a check on real data; which new backlog items are really needed; which are already covered by existing tasks or ADRs. For each recommendation say whether it is a bug fix, a data migration, a performance optimization, test coverage or an optional improvement.

**The main thing:** look not for what could be done differently but for what in the current changes can really break, worsen the work or create a hidden problem. Every conclusion must rest on concrete code, SQL, schema or a test. If the evidence is not enough, say so.

## Investigation report 1: technical debt (run 2026-11-09, read-only)

No build or test was run; two items stay `needs-further-check` because they need Maven (permission asked separately). Corrections to "Current state" above: `.claude/rules.md` has 16 sections with the warning sign (15 blockquote headings plus the heading on line 1), not 15; `adr-index.md` has 282 rows, not 283.

### H1. The Dockerfile does not build the whole reactor
**Status:** rejected
**Evidence:** `Dockerfile:19` `COPY . .`; `Dockerfile:31` `./mvnw package -pl marketplace-app -am`. `marketplace-app/pom.xml` depends on `marketplace-orchestrator`, `marketplace-rest-api`, `marketplace-mcp` and `platform-commons`; `marketplace-orchestrator/pom.xml:31-77` pulls all 10 starters at `runtime` scope, and `-am` builds every reactor module a module depends on. `integration-tests` is test-only and not built, on purpose. `~/.m2` is only a cache mount (`Dockerfile:29`); reactor modules come from the reactor, not from `.m2`.
**Root:** the premise is wrong; `COPY . .` is the fix for an earlier missing-module bug (commit `069d3eca`).
**Impact:** none. A clean-environment build was not run.
**Fix:** none. **Fix risk:** n/a.

### H2. Eight `DECISIONS.md` files without `## ADR-NNN:` headings
**Status:** rejected as debt (the fact is confirmed)
**Evidence:** each file has 8-16 lines and calls itself a generated index with 1-3 links to ADRs that live in `marketplace-app`, `platform-commons` and `docs/api-collections` (17 links, 13 distinct ADRs). They own 0 ADRs.
**Root:** pointer-only files generated from the `**Also affects:**` tag, by design.
**Impact:** `adr-index.md` loses 0% coverage: the home ADRs are indexed (282 rows) and these files are listed under "Pointer-only files". Rewriting them to `## ADR-NNN` would only duplicate.
**Fix:** none. **Fix risk:** n/a.

### H3. `.claude/rules.md` has grown
**Status:** confirmed
**Evidence:** 919 lines, 60,170 bytes (about 15k tokens, bytes/4 estimate). 16 sections carry the warning sign (`rules.md:1`, and 15 `> ## ⛔` headings from line 34). "Happened multiple times / repeatedly" appears twice (lines 69, 141). Of the 16: 12 have a natural file trigger (75% by count, about 71% of the ⛔ text) and 4 must stay eager: "re-read all rules", "never commit", "code quality", "mechanical work goes to Haiku". Outside the ⛔ block: the UI pattern sections (Overlay, View, Query Layer, Service headers, DTO constants, Form handler, Breadcrumb, Reference implementations) are about 9.2k characters (15%) and already have a path-scoped home `.claude/rules/marketplace-app.md`; the `Operational notes` block is about 7.2k characters (12%) and is needed only when a task is created or closed. Together about 27% of the file can move without losing content. The file also breaks its own "abstract principle, not a case study" rule at lines 348, 360-361 and 428 ("confirmed directly", a named real file).
**Root:** every lesson learned was appended to the always-loaded file.
**Impact:** about 4k tokens in every session for rules that apply to one module or one moment.
**Fix:** S - move the UI patterns to `marketplace-app.md`, the `Operational notes` format to the commands that create and close tasks.
**Fix risk:** path-scoped rules load on a `Read`, not on a `Write` (see `.claude/rules/README.md`), so a brand-new file written before any read in that module would miss the rule.

### H4. Duplication in `.claude/skills/`
**Status:** confirmed, small
**Evidence:** the four skills together are 81,739 bytes (`infra-doc-standards` alone 34 KB). A fuzzy sentence match (similarity above 0.8, sentences over 60 characters) found 20 similar pairs and 6 runs of two or more consecutive sentences, about 2,260 characters (about 560 tokens, 2.8%). Largest: "Applying this standard" in `module-readme-standards/SKILL.md:128` and `infra-readme-standards/SKILL.md:210` (5 sentences, 665 characters); then the "Atomic unit first" intro of the two doc skills, the "Aggregated cross-file facts" paragraph and the deferred-findings paragraph. The earlier 887-character figure used a stricter exact-sentence match.
**Root:** each domain skill was written by copying its sibling.
**Impact:** the cost is paid only when two skills are loaded together; the "one fact, one canonical home" rule governs facts, and these are parallel procedures, so the breach is weak.
**Fix:** S - one shared procedure referenced by both.
**Fix risk:** each skill stops being self-contained when loaded alone.

### H5. Dependency convergence risks
**Status:** confirmed in part; `dependencyConvergence` passing now is needs-further-check
**Evidence:** `archunit-junit5` has a literal `1.4.2` in `marketplace-app/pom.xml:117-118` and `marketplace-mcp/pom.xml:55-56` and is not managed in the root. `jsoup` is managed in the root yet `${jsoup.version}` is repeated in `marketplace-app` and `html-sanitizer-lib`; `vaadin-dev` carries `${vaadin.version}`, and the 25.3.2 `vaadin-bom` does not manage it (checked in the BOM file), so that version is needed - the first draft of this report said otherwise. `caffeine` has no version in any pom (Spring Boot BOM) - refuted. `springdoc` is a root property used by one module - no risk. The root `dependencyManagement` mixes four literals (`commons-text`, `liquibase-core`, `commons-collections4`, `commons-io`) with properties; `owasp-java-html-sanitizer` has a literal in `html-sanitizer-lib`. The enforcer rule is inherited by every module (`pom.xml:232-241`, plugin 3.6.3). The root comment about aligning `swagger-annotations` "for the dependency-convergence rule" shows it was made to pass once; no run now.
**Root:** versions are pinned where first needed.
**Impact:** a new module is covered by the rule automatically; a divergent transitive version fails the build at once, not silently.
**Fix:** XS - `archunit-junit5` into the root `dependencyManagement`; drop the repeated `jsoup` versions. Check with `mvn enforcer:enforce` (needs permission).
**Fix risk:** minimal.

### H6. Spring Boot 4.1.1, Vaadin 25.3.2, Java 25, Jackson 2 and 3
**Status:** rejected (documentation drift confirmed)
**Evidence:** Spring Boot 4.1.1 exists, latest 4.1.x is 4.1.2 ([Maven Central metadata](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-starter-parent/maven-metadata.xml)). Vaadin 25.3.2 exists, latest 25.3.5 ([Maven Central metadata](https://repo.maven.apache.org/maven2/com/vaadin/vaadin-bom/maven-metadata.xml)). Vaadin 25.3 needs Java 21 or later, Spring Boot 4.1 or later, Node 24 or later ([compatibility page](https://vaadin.com/docs/v25/compatibility)); Java 25 satisfies "21 or later" but the page does not name it. Spring Boot 4.1 supports Java up to 27 ([system requirements](https://docs.spring.io/spring-boot/system-requirements.html)). Boot 4.1.1 manages both Jackson BOMs (2.21.5 and 3.1.5, `spring-boot-dependencies-4.1.1.pom:82-83`). Only `jackson-annotations` keeps the old package in Jackson 3 ([migration guide](https://github.com/FasterXML/jackson/blob/main/jackson3/MIGRATING_TO_JACKSON_3.md)); the guide warns that old-package databind annotations are silently ignored - the code has none (`com.fasterxml.jackson.databind.annotation/ser/deser` imports: 0). `platform-commons/pom.xml:22-37` declares both lines on purpose. Root `CLAUDE.md` says Spring Boot 4.1.0 and Vaadin 25.2.3, the pom has 4.1.1 and 25.3.2.
**Root:** versions are repeated in prose that nobody updates.
**Impact:** two patch releases behind; stale numbers in `CLAUDE.md`.
**Fix:** XS - remove the version numbers from `CLAUDE.md` (the pom is their home).
**Fix risk:** none.

### H7. `<source>/<target>` instead of `<release>`
**Status:** rejected as a problem; dead configuration confirmed
**Evidence:** `pom.xml:201-202`. `spring-boot-starter-parent-4.1.1.pom:16` sets `maven.compiler.release=${java.version}` and the root sets `java.version=25` (`pom.xml:44`), so the plugin's `release` parameter is already filled. Not proven: that with both set the plugin passes only `--release` (the plugin's goal page states no precedence).
**Root:** the lines predate the Boot parent's setting.
**Impact:** none for Java 25 (source, target and release are all 25); the difference appears only with a JDK newer than the target.
**Fix:** XS - delete the two lines; confirm with `mvn -X compile` (needs permission).
**Fix risk:** minimal.

### H8. Quality of `adr-index.md`
**Status:** confirmed, moderate
**Evidence:** 282 rows, 57,726 bytes (about 14k tokens). 14 rows have status "Superseded" (5.0%); 12 are "Accepted (done <date>)"; 10 are "Accepted (... portion reversed/corrected)": 36 rows (12.8%) describe a state on a date. 33 rows mention superseded, corrected or reversed anywhere. Four of the 14 are one chain in `scripts/ci`. `Applied` is filled in 58 rows, `Verified` in 7.
**Root:** statuses are edited in place instead of the index hiding history.
**Impact:** a superseded ADR cannot be deleted from `DECISIONS.md` (history, and other ADRs point to it); only the index can drop them, saving about 2.9 KB (5%).
**Fix:** S - the generator omits "Superseded" rows and keeps a count.
**Fix risk:** history search through the index is lost.

### H9. CI complexity
**Status:** confirmed in part
**Evidence:** `scripts/ci/run.sh` 592 lines with 19 flags (lines 279-297); `scripts/ci/dagu/ci.yaml` 284 lines with 13 steps, 13 parameters, 5 env variables; 6 named volumes (`ci-m2-cache`, `ci-dagu-home`, `ci-tools-cache` in `run.sh`; `ci-maven-cache`, `ci-vaadin-cache`, `ci-test-reports` in `ci.yaml`) and 2 persistent containers. In the backlog files 18 of 19 flags appear in a CI context; used once: `--api-flows`, `--docs-only`, `--rebuild`, `--playwright-args`; never: `--teardown-e2e-after` (still named in `.claude/rules/scripts.md:259` and `scripts/ci/README.md:102`). "Unused" means "not mentioned in the backlog". The header text at `run.sh:36-39` says the flag is "on by default" while the README says the stacks stay up by default. Two separate Maven caches exist (`ci-m2-cache` at `run.sh:373,390`, `ci-maven-cache` at `ci.yaml:47`); whether they are redundant was not checked.
**Root:** one flag per stage and per skip.
**Impact:** a misleading header line; otherwise the flags are used.
**Fix:** S - fix the header wording; merge the three `--no-*` skip flags into one mechanism.
**Fix risk:** the CLI changes.

### H10. Other debt
**Status:** 1 finding (nothing invented)
**Evidence:** `attachment-spring-boot-starter` (`AttachmentAutoConfiguration.java:3-4`, `AttachmentSnapshotRepository.java:4`) and `marketplace-app` (`JacksonConfig.java:3`) import `com.fasterxml.jackson.databind.ObjectMapper`, but their poms declare no Jackson; only `audit`, `platform-commons` and `user` do. They get it through `platform-commons`, whose own source imports only Jackson annotations from the 2.x line (`platform-commons/pom.xml:29-32`).
**Root:** the dependency sits in the shared kernel instead of with its users.
**Impact:** removing it from `platform-commons` would break `attachment` and `marketplace-app`; it works against starter independence.
**Fix:** S - declare `jackson-databind` 2.x in the real users, remove it from `platform-commons`; check with `mvn dependency:analyze` (needs permission).
**Fix risk:** compile errors in modules not inspected (`integration-tests` also imports it).
Rejected: the logic in the `TaxonPort` default method is tested (`TaxonPortResolveCategoryAndCityFilterTest`); the two-port rule is enforced by `ArchitectureRulesTest`, and `EntityExistenceService` is a documented exception.

### Summary

| # | Status | Effort | Impact |
|---|---|---|---|
| H1 | rejected | - | - |
| H2 | rejected | - | 0% index coverage lost |
| H3 | confirmed | S | about 27% of the file, about 4k tokens per session |
| H4 | confirmed | S | about 560 tokens, only when two skills load together |
| H5 | confirmed (convergence: needs-check) | XS | one literal, two repeated versions |
| H6 | rejected (drift in `CLAUDE.md`) | XS | two patch versions behind |
| H7 | rejected (dead lines) | XS | none |
| H8 | confirmed | S | index smaller by 5% |
| H9 | confirmed in part | S | one misleading header, one flag never used |
| H10 | 1 finding | S | a dependency in the wrong module |

Top 3 to fix first: (1) H3, move the UI patterns and the `Operational notes` format out of the always-loaded file; (2) H10, move the Jackson 2.x databind dependency to its real users; (3) H5, H6 and H7 as one pom and documentation cleanup.

## Investigation report 2: deep verification of recent changes (run 2026-11-09, read-only)

Scope: commits `4b89a829` (database refactoring: promotion dates, unused columns and indexes, Dockerfile and orchestrator scope already in place) and `dfe14feb` (deleting a provider profile deletes its reviews). One `EXPLAIN` was run on a throwaway database in the dev PostgreSQL container (PostgreSQL 15, dropped afterwards). No Maven, no test run.

### Confirmed bugs
None.

### Performance and regression findings

**1. Removing `idx_advertisement_title` makes sorting by title scan the whole table at scale.** Severity: Medium. Status: Confirmed (measured).
- Where: `advertisement-spring-boot-starter/.../01-advertisement-schema.xml` (index removed); the sort is user-reachable at `AdvertisementRepository.java:89` and `AdvertisementSortMeta.java:13`. The task's own table says the index "can only help ORDER BY title. Not measured."
- Scenario: `WHERE deleted_at IS NULL ORDER BY a.title LIMIT 20`, 1M advertisements, 10% soft-deleted, random titles:

| Query | With index | Without index |
|---|---|---|
| sort by title, first page | 0.4 ms | 230 ms (Parallel Seq Scan) |
| sort by title descending, offset 1000 | 10.5 ms | 212 ms |
| `ILIKE '%ab%'` filter plus sort by title | 1.6 ms | 396 ms |

- Consequence: invisible at today's volume; at 1M rows every title-sorted page is a full scan. Default sorting by date is not affected.
- Minimal fix: restore the index, or record that title sort is not needed at the expected size (XS).
- Test that proves it: repeat the `EXPLAIN` on a realistic volume (a plan check, not a unit test).

**2. Existing databases keep what the changelogs no longer create.** Severity: Low. Status: Confirmed.
- Where: `promotion`, `taxon`, `attachment`, `advertisement`, `provider-profile`, `feedback`, `contact` changelogs, edited in place; all ten changesets carry `validCheckSum ANY` (added to `promotion` in this change), the recorded pre-launch policy.
- Clean database: correct. Database with the old changesets applied: keeps `valid_from` (`NOT NULL DEFAULT NOW()`), `valid_until`, `assigned_by`, the seven removed indexes and the GIN index; the code works on both because no insert names a removed column and `valid_from` has a default. Database with real data: no row can have a non-null `valid_until` (nothing in the code writes it), so promoted status does not change.
- Consequence: schema divergence on development databases only.
- Minimal fix: a baseline changelog before the first release (data migration).

**3. A review written while its profile is being deleted can stay behind.** Severity: Low. Status: Needs verification.
- Where: `FeedbackRepository.deleteByEntity`, called from `ProviderProfileSaveService.delete`; no foreign key to the profile (by decision).
- Scenario: another transaction inserts a review for that profile after the delete statement took its snapshot; the review and a recomputed aggregate row stay without a profile.
- Not checked: whether `FeedbackWriteService` re-checks that the profile exists inside its own transaction. Test: two parallel transactions.

**4. Content-row ownership is not constrained.** Severity: Low. Status: Likely.
- Where: `feedback.content_id` and `feedback_comment.content_id` have no `UNIQUE`. If two rows ever shared a content row, the delete would fail on the foreign key and roll back whole; no silent data loss.

**5. Promotion lookups (not changed by the diff).** Status: Needs verification, no measurement.
- `enrichWithPromotion` for one profile loads all placements of the entity type; with the "promoted only" filter both the page and the count read them (`PromotionCatalogService.scopeFor`). The size depends on how many profiles pass the top-rated rule.

**6. Docker build context.** Severity: Low. Status: Likely, not measured.
- `COPY . .` (`Dockerfile:19`) invalidates the Maven layer on any changed file anywhere in the tree; the `.m2` and `.vaadin` cache mounts still save downloads but not compilation. `.dockerignore` does not exclude `private/` (git-ignored, one non-Markdown file reaches the builder stage). Nothing but the JAR reaches the final image.

### Missing tests
- No test that deleting a profile with a stale version, or a failure in a later step, leaves its reviews in place (the atomicity the change relies on). Test: call `ProviderProfileSaveService.delete` with an old version, assert the reviews, replies, ratings and aggregate still exist.
- No parallel-transaction test for finding 3.
- The repository and orchestrator tests that exist use a real PostgreSQL and cover: every row of the entity removed, other entities untouched, an entity without feedback, the feedback module absent.

### Verified safe
- The data-modifying statement is one statement; the intra-module foreign keys (`fk_feedback_content`, `fk_feedback_rating_feedback`, `fk_feedback_comment_content`, `fk_feedback_comment_feedback`) are checked at the end of the statement, so removing parents and children together is valid; an entity without feedback deletes zero rows.
- Profile, contact, reviews, promotion placements and the audit entry are in one `TransactionTemplate` unit; the sitemap is invalidated after it.
- An unknown id or a stale version makes `ProviderProfileRepository.delete` throw `StaleWriteException` (0 rows updated) before any feedback is touched, so the unauthenticated-path concern does not apply.
- Feedback module absent: `FeedbackAccessService.deleteByEntity` uses `ifAvailable` and does nothing.
- The other five removed indexes match the queries: `ad_kind` and `kind` have three values and only an `IN` filter (no sort by advertisement kind); `idx_feedback_comment_feedback` duplicates the `UNIQUE` constraint on `feedback_id`; `idx_promotion_placement_type_reason` is covered by the prefix of the unique index `(entity_type, entity_id, reason)` on a small table; no query reads `taxon_translation` by locale and name; no JSONB operator reads `changes_summary` (only `changes_summary::text`).
- Promotion after the date columns were removed: UI, REST, MCP and sitemap read one source, `promotedPlacements()`; `getFiltered` reads the placements once for both the scope and the marks; `promotionValidUntil` is referenced nowhere outside the backlog and generated files.
- `FeedbackPort.deleteByEntity(EntityType, Long)` follows the dominant Port shape (about 19 signatures use the pair, 2-3 use `EntityRef`); `ContactPort.delete` versus `FeedbackPort.deleteByEntity` is a small naming difference only.
- `-pl marketplace-app -am` builds what the app needs; the wildcard `org.ost:*-spring-boot-starter` in the ten starters excludes only artifacts of group `org.ost` with that suffix.

### Conclusion
Top 5: (1) the title index, a performance optimization; (2) the atomicity test, test coverage; (3) old databases keep removed columns and indexes, a data migration before the first release; (4) the review-versus-delete race, needs verification; (5) the Docker build context, an optional improvement. Nothing blocks a merge. Needs real data: finding 1 with real titles and volumes, finding 3 with a parallel test. New backlog items that are really needed: one for the title index decision, one for the atomicity test; the rest goes to the deferred-findings bucket. Not found in the backlog while preparing this report (the backlog was not searched item by item): findings 2 and 6.

Outside both prompts, deduced and not run: `ComponentFactory<XPort>` beans are declared only by the starter that provides the Port, so removing an optional starter likely fails Spring startup; a test that starts the context with each optional starter absent would show it.

## Implementation log (2026-11-09)

Done after the reports, at the user's request:
- Task 251 (profile delete atomicity test) and task 252 (application starts without each optional starter) are filed and ranked in the backlog.
- Pom and documentation cleanup (H5, H6, H7, H9 wording): `archunit-junit5` is managed in the root `pom.xml` (`archunit.version`) and the two literal versions are gone; the repeated `jsoup` versions are removed; `<source>/<target>` are removed from the compiler plugin (the Boot parent already sets `maven.compiler.release`); the version numbers are removed from `CLAUDE.md`; the `--teardown-e2e-after` header in `scripts/ci/run.sh` states the real default.
- Jackson 2.x `jackson-databind` moved from `platform-commons` to the modules that import it (`attachment-spring-boot-starter`, `marketplace-app`, `integration-tests`); `platform-commons` keeps only the shared annotations.
- `idx_advertisement_title` is back in the advertisement changelog (edited in place, as the changelog policy allows before launch); a database created since the removal keeps lacking it until it is recreated.
- `.claude/rules.md` shrank from 919 lines and 60,170 bytes to 646 lines and 45,949 bytes: the Overlay, View, Query Layer, Form Handler, Breadcrumb and Reference sections moved into `.claude/rules/marketplace-app.md`; the `Operational notes` format moved into the new `.claude/rules/backlog-tasks.md` (loaded when a task file is read); the four commands and agents that pointed at `rules.md` for that format now point at the new file.
- Not done: the Docker context items, the review-versus-delete race and the skill duplication stay as reported.

## Related

- [improvement-244](../../tasks/improvement-244-research-wiki-or-keep-architecture-control-plane.md): research on the documentation tools.
- [improvement-243](improvement-243-sort-status-line-and-where-1-1.md): holds the decision about an index with `created_at` first on `audit_log`, which section A of the second prompt can check on real data; the two tasks stay separate.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: a pasted investigation prompt, then fixes chosen from its report
- flows_chosen: /new-task
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none

### Script/command runs
- scripts/ci.sh (full, 9 stages) | duration_s=1455 | mode=background | result=pass
- scripts/ci.sh (full, 9 stages) | duration_s=0 | mode=background | result=n/a (cut off when the session ended, containers exited with 255)
- scripts/ci.sh (full, 9 stages) | duration_s=1404 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration --sandbox | duration_s=0 | mode=background | result=n/a (stopped, the full CI covered it)
