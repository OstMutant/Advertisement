# improvement-253: Dependency and tool version upgrades, in four batches

**Type:** improvement — maintenance and security hygiene; checked against the registries and release notes on 2026-11-11.
**Module:** root `pom.xml` (versions, plugins, Maven wrapper), `marketplace-app`, `html-sanitizer-lib`, `playwright` (`run.sh`, `package.json`), `scripts/` (image and tool pins), `.env`.
**Priority:** 🟡 medium-high — Spring Boot 4.1.2 brings newer Tomcat and the PostgreSQL driver; the rest is ordinary upkeep that grows harder the longer it waits.
**When:** independent, no blockers.

## Current state

Versions in the project against the latest stable one found (release candidates and betas left out). The "Verified" column says what was read, not assumed.

| What | In the project | Latest stable | Verified |
|---|---|---|---|
| Spring Boot | 4.1.2 (batch A done) | 4.1.2 (22 Oct 2026) | release notes read; it manages Tomcat 11.0.27 (was 11.0.24), PostgreSQL driver 42.7.14 (was 42.7.13), Lombok 1.18.48 (was 1.18.46), Liquibase 5.0.4 (was 5.0.3) |
| Vaadin | 25.3.5 (batch A done) | 25.3.5 | 25.3.5 notes: maintenance release |
| JaCoCo | 0.8.16 (batch A done) | 0.8.16 | notes read: official Java 27 support, ASM 9.11.1 |
| Liquibase pin in the root `pom.xml` | removed (batch A done), managed by Boot as 5.0.4 | 5.0.4 | Boot 4.1.2 manages the same version, so the pin becomes redundant |
| springdoc-openapi | 3.2.0 (springdoc 3.2.0 done) | 3.2.0 | notes read: built on Spring Boot 4.1.2; `PagedModel`/`PageMetadata` properties now required in the generated schema, Scalar 0.6, default API version handling changed |
| jsoup | 1.24.1 (batch B done) | 1.24.1 | notes read: HTML parsing aligned with the current HTML standard (tables, formatting elements, templates); opt-in selector "Web mode", which becomes the default in 1.25.1 |
| ArchUnit | 1.5.1 (batch B done) | 1.5.1 | notes read: Java 27 class files, caught exceptions count as dependencies (can add rule findings) |
| Tika core | 4.2.0 (batch B done) | 4.2.0 | change log read: most breaking items concern tika-server, pipes and parsers; `DefaultDetector` no longer lets a more specific magic result overrule it, which can change the detected content type; the only user is `AttachmentContentTypeValidator` |
| maven-compiler-plugin | 3.16.0 (batch B done) | 3.16.0 | versions only |
| maven-enforcer-plugin | 3.7.0 (batch B done) | 3.7.0 | versions only |
| Maven wrapper | 3.9.16 (batch A done) | 3.9.16 (3.10.0 also exists) | versions only |
| Playwright | 1.65.0 (batch C done) | 1.65.0, image `mcr.microsoft.com/playwright:v1.65.0-jammy` exists | notes read: Node 20 dropped, cookie matching follows RFC 6265, proxy credentials no longer sent to sites, text the browser does not render leaves aria snapshots |
| PostgreSQL image | `postgres:18-alpine` (floating) | 18.6-alpine | tag exists; a `docker pull` is enough |
| SonarQube server | 26.11.0.134175 (batch C done) | 26.11.0.134175 | notes read: 26.10 and older trust compromised eMudhra certificate authorities, 26.11 fixes it in the Docker image; 26.11 removes the Ruby, Rust and Scala analyzers and part of the advanced Java rules (their issues close), checks free disk space at start (stops below 10% free) and, since 26.10, raises the default heaps (web 1 GB, compute engine 1.5 GB) |
| `sonar-scanner-cli` | 12.4 (batch C done) | 12.4 | tag exists |
| SeaweedFS | 4.53 (batch C done) | 4.53 | tag exists |
| Dagu | 2.26.0 (batch C done) | v2.26.0 | notes of about 30 releases scanned: no breaking changes announced; 2.21.0 stops credential settings (auth secrets, `DAGU_ENCRYPTION_KEY`, ...) from reaching step environments; workspace settings deprecated for v3.0; some versions have no published notes |
| Temurin base image | `eclipse-temurin:25-jdk` / `25-jre` (floating) | rebuilt 29 Oct 2026 | image rebuild only |

Already current: Spring AI 2.0.2, Liquibase through Boot, MapStruct 1.6.3, swagger-annotations 2.2.55, `eslint-plugin-playwright` 2.12.x.

Checked and deliberately left alone:
- **Java 27** (GA 15 Sep 2026): not an LTS release; the next LTS is Java 29. The groundwork exists (Boot 4.1.2 knows `JavaVersion.TWENTY_SEVEN`, JaCoCo 0.8.16 and ArchUnit 1.5 support it), so the move to 29 should be cheap.
- Spring Boot 4.2.0 (release candidate only), Spring AI 2.1.0 (release candidate only), MapStruct 1.7.0 (beta only), Maven 3.10.0.

Checked: the Vaadin build already runs on Node 24.21.0 (found in the `vaadin-cache` volume), which meets the Node 24 that Vaadin 25.3 asks for. Not established: how much the eMudhra notice matters for a local SonarQube container, the Node version inside the Playwright 1.65 image, and whether `scripts/ci/dagu/ci.yaml` relies on any Dagu credential variable inside a step.

## Why change

Newer patch releases carry fixes (Tomcat, the PostgreSQL driver) the project does not get while it stays on 4.1.1. Floating tags (`latest`, `18-alpine`, `25-jdk`) make a build differ from one day to the next and hide the day something changed. The longer minors wait, the larger the step and the harder it is to find which one broke what.

## Expected benefit

The build, the tests and the CI run on versions that are current, pinned where they were floating, and each step is a small commit that can be reverted alone.

## Approach

Four batches, each a separate commit, each followed by `bash scripts/build-and-test.sh --unit --integration --sandbox`; batches B and C also by the full Playwright `e2e --full --ux` run and `/ci`.

1. **A — patches (safe).** Spring Boot 4.1.2; Vaadin 25.3.5; JaCoCo 0.8.16; remove the now redundant Liquibase pin; Maven wrapper 3.9.16; `docker pull` of `postgres:18-alpine` (18.6) and a rebuild of the Temurin 25 images.
   Done 2026-11-12: `pom.xml` and `.mvn/wrapper/maven-wrapper.properties` changed as listed (the Docker image pulls of the floating tags were not part of it). `build-and-test.sh --unit --integration --sandbox` passed (695 unit tests, 995 integration tests, 0 failures) and the full local CI passed (all stages; e2e 68 passed with the known flaky first attempt of the setup test). The built jar holds Spring Boot 4.1.2, liquibase-core 5.0.4, Tomcat 11.0.27, PostgreSQL driver 42.7.14, Vaadin 25.3.5 (Flow 25.3.7), springdoc 3.1.1 and Spring AI 2.0.2 unchanged.
2. **B — minors with the changelog read first.** In this order: springdoc 3.2.0 (needs batch A; compare the generated OpenAPI document before and after, and look at `docs/api-collections`, because `PagedModel` properties become required); jsoup 1.24.1 (before it, pin the current sanitizer output with tests in `html-sanitizer-lib`, then run `jsoup.queryMode=check` to see which selectors would change under the Web mode of 1.25.1; users of jsoup are `HtmlSanitizer`, `OgMetaRequestListener`, `HtmlExcerptUtil` and the advertisement and provider-profile form handlers); ArchUnit 1.5.1 (new findings possible); Tika 4.2.0 (before it, pin the content types the current detector returns for the allowed attachment formats with tests in the attachment starter, because the detector ranking changed); maven-compiler-plugin 3.16.0; maven-enforcer-plugin 3.7.0.
   springdoc 3.2.0 done 2026-11-12: the OpenAPI document before and after has the same 51 paths, the same 45 schemas (none changed) and the same two security schemes; the only difference is the order of the two `oneOf` alternatives (`ValidationErrorResponse`, `ErrorResponse`) in the 400 responses of 69 operations. The full local CI passed. The `PagedModel` and Scalar changes of the release notes do not show up because the project uses neither.
   Batch B done 2026-11-12: jsoup 1.24.1, ArchUnit 1.5.1, Tika core 4.2.0, maven-compiler-plugin 3.16.0 and maven-enforcer-plugin 3.7.0 are in `pom.xml` next to springdoc 3.2.0. Before jsoup, `VisibleTextLengthTest` in `html-sanitizer-lib` pinned the visible-text length of 17 markup shapes; it passes unchanged on 1.23.2 and 1.24.1. The only jsoup calls are `Jsoup.parse(html).text()` (no selectors), so the 1.25.1 Web mode does not touch the project. The attachment content-type tests already cover the Tika detector for the allowed formats and stayed green on 4.2.0. `build-and-test.sh --unit --integration --sandbox` passed (735 unit tests, 995 integration tests, 0 failures) and the full local CI passed (all stages; e2e 68 passed with the known flaky first attempt of the setup test).
3. **C — test infrastructure.** Playwright 1.65.0 together with the image `v1.65.0-jammy` and the `PLAYWRIGHT_VERSION` in `playwright/run.sh` (check specs that use aria snapshots or cookies, and that the image's Node is supported); SonarQube 26.11 first in this batch, because 26.10 and older trust compromised certificate authorities (how much that matters for a local container is not established; expect fewer Java issues, a startup disk check and larger default heaps; a server upgrade can reset its embedded data, see its own decision record), with a pin for `sonar-scanner-cli`; a pin for SeaweedFS instead of `latest`; Dagu 2.16.3 to 2.26.0 as its own step, after checking `ci.yaml` for credential variables that would stop reaching the steps. Whether to pin floating image tags at all is a decision for `/record-decision`; the choice made there decides which of these pins stay.
   Batch C done 2026-11-12: Playwright 1.65.0 (`PLAYWRIGHT_VERSION` in `playwright/run.sh` and the constant of the architecture-map screenshot script), SonarQube 26.11.0.134175-community, `sonar-scanner-cli` 12.4 and SeaweedFS 4.53 pinned (the existing decision records of `scripts` and `scripts/sonar` were rewritten, no new record), Dagu 2.26.0. Findings on the way: Dagu 2.26's scheduler stops when its watched DAG directory disappears and `ci.sh` wipes `/app` before every run, so the DAG file now lives in `/dagu-dags` (`DAGU_DAGS_DIR`, copied by the entrypoint and by `ci.sh` after every sync); one Docker layer-extraction failure on the scanner pull failed the Sonar stage, so `scripts/utils/docker-pull-retry.sh` retries the Sonar and deploy pulls up to three times; the Sonar script looked for `sonarqube:community` instead of the pinned server image and now reads the image from the compose file; the `wiki` exclusion of the architecture-map generator became unnecessary and was removed. `build-and-test.sh --unit --integration --sandbox` passed (995 integration tests, 0 failures in every module) and the third full local CI passed (all stages; the first two attempts failed on the Dagu directory and on the scanner pull). A separate `playwright e2e --full --ux` run was not made: the CI e2e stage runs the same script.
   Plan for batch C, approved 2026-11-12 (no new ADR; the existing records are rewritten to the current state): (1) `scripts/sonar/run.sh` pins the scanner as `SCANNER_IMAGE` (`sonar-scanner-cli:12.4`) and finds the server image through `docker compose config --images` instead of the stale `sonarqube:community` name; ADR-006 of `scripts/sonar` is rewritten to match. (2) SeaweedFS `4.53` in its compose file, one sentence added to ADR-016 of `scripts`. (3) Playwright 1.65.0, SonarQube 26.11.0.134175-community, Dagu 2.26.0 (first `ci.sh` run with `--refresh-tools`). (4) `/code-review`, then `build-and-test.sh --unit --integration --sandbox`, `playwright e2e --full --ux`, `/ci`, then `/sync-docs`. One test pass for the whole batch, no commits until asked.
4. **D — leftovers found while doing batch C.** Done 2026-11-12 (plan approved the same day): Dagu is installed into a folder named after `DAGU_VERSION` (`scripts/ci/docker-entrypoint.sh`) and `--refresh-tools`/`FORCE_TOOLS_REFRESH` are gone; the SeaweedFS tag is read from its compose file and the Playwright version from `playwright/run.sh`, and the two documents that named it point to the file; `scripts/utils/docker-pull-retry.sh` retries every registry fetch (all `docker pull` calls, `docker_pull_missing_bases <Dockerfile>` before every `docker build`, `docker_pull_missing_retry` before every `docker run` or `compose up` that pulls an image implicitly); every multi-line explanatory comment in the CI, Sonar, Playwright, deploy and screenshot scripts is one line, and the standing facts of the dropped ones sit in the function headers of `docker_cp_diag` and `log_orchestrator` or in the existing decision records; `postgres:18-alpine` was already current, `eclipse-temurin:25-jdk` and `25-jre` were pulled fresh and the `ci-runner` image was rebuilt by the full run. `/code-review` raised six findings: the CRLF read of the compose file, the `FROM` parser and the empty DB image check were fixed; the other three (Sonar pulls not retried, stale generated map, shared tools volume race) do not hold. No separate `build-and-test.sh` run, because the CI stages run it. Full `ci.sh --foreground --rebuild` passed (28 min 37 s, all 15 steps).
   After the last step: move this file to `backlog/completed/tasks/`, remove its `BACKLOG.md` row, add the `✅ Done` line to `BACKLOG-ARCHIVE.md`, add 1 to `**Applied:**` of every ADR in `adrs_referenced`, regenerate `.claude/nav/adr-index.md`.

## Related

- [improvement-133](../../tasks/improvement-133-deferred-oversized-review-findings.md) entry 46 listed the Boot and Vaadin patches; that line is now here.
- [improvement-086](improvement-086-postgres-major-version-bump.md) — the previous PostgreSQL image decision.
- [improvement-085](improvement-085-playwright-version-bump.md) — the previous Playwright bump and the checks it needed.
- `.claude/nav/adr-index.md` — no decision overlaps this task (searched on 2026-11-11).

## Operational notes
- token_cost_review: 160565
- token_cost_research: n/a
- token_cost_verification: 453902
- review_signal_ratio: 7 / 13
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: dependency and tool upgrade with a full test and CI run, CI regression from the new Dagu
- flows_chosen: build-and-test.sh, ci.sh, code-review, sync-docs
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 16
- haiku_decomposition_rework_needed: 4 / 16
- adrs_referenced: ADR-006 (scripts/sonar), ADR-016 (scripts), ADR-009 (scripts/ci)

### Agent calls
- version-bound Dagu install and removal of the refresh flag | subagent_type=general-purpose (haiku) | tokens=128112 | tool_uses=20 | duration_s=72 | mode=background | batch=parallel-d1
- one home for the SeaweedFS and Playwright versions | subagent_type=general-purpose (haiku) | tokens=83654 | tool_uses=14 | duration_s=48 | mode=background | batch=parallel-d1
- retry helpers and their call sites in scripts and playwright | subagent_type=general-purpose (haiku) | tokens=128441 | tool_uses=60 | duration_s=183 | mode=background | batch=parallel-d2
- retry call sites in docs and agent scripts | subagent_type=general-purpose (haiku) | tokens=111036 | tool_uses=24 | duration_s=99 | mode=background | batch=parallel-d2
- one-line comments in scripts/ci/run.sh and its Dockerfile | subagent_type=general-purpose (sonnet) | tokens=94682 | tool_uses=8 | duration_s=79 | mode=background | batch=parallel-d3
- one-line comments in scripts/sonar/run.sh | subagent_type=general-purpose (sonnet) | tokens=66691 | tool_uses=5 | duration_s=37 | mode=background | batch=parallel-d3
- one-line comments in playwright/run.sh | subagent_type=general-purpose (sonnet) | tokens=90342 | tool_uses=21 | duration_s=83 | mode=background | batch=parallel-d3
- one-line comments in the deploy and screenshot scripts | subagent_type=general-purpose (sonnet) | tokens=70479 | tool_uses=5 | duration_s=41 | mode=background | batch=parallel-d3
- code review of the batch D diff | subagent_type=code-review | tokens=86144 | tool_uses=6 | duration_s=72 | mode=background | batch=solo
- sync-docs audit of the batch D script headers | subagent_type=general-purpose (haiku) | tokens=167869 | tool_uses=20 | duration_s=140 | mode=background | batch=solo
- the Sonar scanner pin and server image check, ADR-006 rewrite | subagent_type=general-purpose (haiku) | tokens=96670 | tool_uses=11 | duration_s=51 | mode=background | batch=parallel-c1
- the SeaweedFS pin and ADR-016 sentence | subagent_type=general-purpose (haiku) | tokens=75676 | tool_uses=7 | duration_s=38 | mode=background | batch=parallel-c1
- the Playwright, SonarQube and Dagu version bumps | subagent_type=general-purpose (haiku) | tokens=81432 | tool_uses=12 | duration_s=81 | mode=background | batch=parallel-c1
- code review of the batch C diff | subagent_type=code-review | tokens=74421 | tool_uses=3 | duration_s=42 | mode=background | batch=solo
- sync-docs audit of the sanitizer test Javadoc | subagent_type=general-purpose (haiku) | tokens=69434 | tool_uses=4 | duration_s=39 | mode=background | batch=parallel-c2
- sync-docs audit of pom.xml and infra headers | subagent_type=general-purpose (haiku) | tokens=216599 | tool_uses=17 | duration_s=256 | mode=background | batch=parallel-c2
- the docker pull retry helper and its call sites | subagent_type=general-purpose (haiku) | tokens=116341 | tool_uses=18 | duration_s=58 | mode=background | batch=solo
- the six header and README corrections of sync-docs | subagent_type=general-purpose (haiku) | tokens=94999 | tool_uses=24 | duration_s=72 | mode=background | batch=solo
- the exact version edits of batch B, first dispatch | subagent_type=general-purpose (haiku) | tokens=66413 | mode=background | batch=solo
- the exact version edits of batch B, second dispatch | subagent_type=general-purpose (haiku) | tokens=67671 | mode=background | batch=solo
- the one-line springdoc version edit | subagent_type=general-purpose (haiku) | tokens=65940 | tool_uses=5 | duration_s=17 | mode=background | batch=solo
- the five exact version edits of batch A | subagent_type=general-purpose (haiku) | tokens=69799 | tool_uses=11 | duration_s=51 | mode=background | batch=solo

### Script/command runs
- scripts/ci.sh --foreground --rebuild (batch D) | duration_s=1714 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration --sandbox (batch C) | duration_s=550 | mode=background | result=pass
- scripts/ci.sh --foreground --refresh-tools (batch C, first attempt) | duration_s=143 | mode=background | result=fail
- scripts/ci.sh --foreground (batch C, second attempt) | duration_s=1857 | mode=background | result=fail
- scripts/ci.sh --foreground (batch C, third attempt) | duration_s=1771 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration --sandbox (batch B) | duration_s=549 | mode=background | result=pass
- scripts/ci.sh --foreground (batch B) | duration_s=1543 | mode=background | result=pass
- scripts/ci.sh --foreground (springdoc 3.2.0) | duration_s=1569 | mode=background | result=pass
- scripts/build-and-test.sh --unit --integration --sandbox | duration_s=584 | mode=background | result=pass
- scripts/ci.sh --foreground | duration_s=1558 | mode=background | result=pass
