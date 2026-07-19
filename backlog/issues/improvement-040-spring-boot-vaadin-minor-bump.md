# improvement-040: routine minor/patch dependency bumps (Spring Boot, Vaadin, jsoup, AWS SDK, jetbrains-annotations)

**Type:** improvement — dependency maintenance.
**Module:** root `pom.xml` (`spring-boot-starter-parent` version, `vaadin.version` property,
`jsoup.version`, `aws-s3-sdk.version`, `jetbrains-annotations.version` properties)
**Priority:** low-medium — minor/patch version bumps, not security-critical; routine maintenance
**When:** independent — re-check current available versions before starting if significant time has
passed since the scan below (this issue has already gone stale once, see history)

## Problem

Re-scanned directly via `./mvnw versions:display-parent-updates` / `display-property-updates` /
`display-dependency-updates -DallowMajorUpdates=true -DallowMinorUpdates=true
-DallowIncrementalUpdates=true` (2026-07-19 — supersedes the original 2026-07-04 scan, which had
already gone stale by the time this issue was first migrated on 2026-07-13):

| Dependency | Current | Available | Kind |
|---|---|---|---|
| `spring-boot-starter-parent` | 4.0.6 | 4.1.0 | minor |
| `vaadin.version` | 25.1.5 | **25.2.4** (not `25.2.1` — moved further since the original scan) | minor |
| `org.jsoup:jsoup` | 1.22.1 | 1.22.2 | patch |
| `software.amazon.awssdk:s3` (`aws-s3-sdk.version`) | 2.44.4 | 2.48.3 | minor |
| `org.jetbrains:annotations` (`jetbrains-annotations.version`) | 24.1.0 | 26.1.0 | minor |

Everything else checked directly and confirmed already at latest: `liquibase-core` (5.0.3),
`commons-io` (2.22.0), `commons-text` (1.15.0), `archunit-junit5` (1.4.2, cross-checked against
Maven Central directly — 1.4.2 really is current as of 2026-07-19), and `testcontainers`
(Spring-Boot-BOM-managed, no update flagged).

**`org.mapstruct:mapstruct` 1.6.3 → 1.7.0.Beta2 found but explicitly NOT included** — a
pre-release (`.Beta2`), not a stable release; do not bump to a beta in this pass.

**Java stays at 25 — no action.** Java 25 is the current LTS (released Sept 2025, supported under
NFTC until Sept 2028). Java 26 exists (non-LTS, released March 2026) but is not a stability-first
choice for a production app on the standard 2-year LTS cadence; the next LTS is Java 29 (expected
Sept 2027). Nothing to bump here — noted only so this doesn't get re-investigated as if it were an
open question.

## Suggested fix

One PR, one full e2e run — all five are "just bump the version, verify nothing regressed," no
distinct migration steps expected for any of them individually:

- Bump `spring-boot-starter-parent` to `4.1.0` and `vaadin.version` to `25.2.4` together (the
  original plan's reasoning still applies: Vaadin's Spring Boot integration is version-sensitive,
  bump together not separately).
- Bump `jsoup.version` to `1.22.2`, `aws-s3-sdk.version` to `2.48.3`,
  `jetbrains-annotations.version` to `26.1.0` in the same PR — all patch/minor, low risk, no known
  breaking changes to check for at this size of bump.
- Ignore transitive noise from the Spring Boot BOM unrelated to this app (Kotlin, Elasticsearch,
  Pulsar, Cassandra, etc. — not used directly, confirmed by the same scan pulling in dozens of
  irrelevant transitive suggestions).
- Re-run the three `mvnw versions:*` commands above immediately before starting, since this issue
  has now gone stale twice already — do not trust the table above blindly if meaningful time has
  passed.
- Full Playwright e2e (`--full --ux`) + unit-tests + integration-tests after the bump — Vaadin
  bumps in particular have previously needed a full UI regression pass, not just a compile check.

## Process addition (still relevant, unautomated)

Run `./mvnw versions:display-property-updates` (+ `display-parent-updates` +
`display-dependency-updates`) monthly, or wire it into CI (improvement-028) once that exists, so a
security-relevant dependency can't silently fall years behind the way owasp-java-html-sanitizer
had before that bump was caught. This issue itself is the second demonstration of why: it went
stale from a single 9-day gap the first time, and a further 6 days the second.

## Related

- `backlog/process-improvements.md` Part 1, item 2 — original source item; the owasp-sanitizer
  portion is already done (see `backlog/completed/issues/`).
- `backlog/issues/improvement-028-minimal-ci-pipeline.md` — where the monthly dependency-check
  process addition would actually get automated.
- [improvement-085](improvement-085-playwright-version-bump.md) — Playwright's own version is
  tracked separately (9 minor versions behind, pinned to a Docker image tag, different risk shape
  than these library bumps).
- [improvement-086](improvement-086-postgres-major-version-bump.md) — PostgreSQL major version is
  also tracked separately (infra/data-compatibility risk, not a library recompile).
