# improvement-204: Dependency and Java version upgrade audit

**Type:** improvement — infra/maintenance, dependency version audit
**Module:** root `pom.xml` (cross-cutting via dependencyManagement); `marketplace-rest-api` specifically for springdoc-openapi
**Priority:** 🟡 Top — user-requested Top placement, 2026-10-01
**When:** independent, no blockers

## Current state

Root `pom.xml` pins: Java 25 (LTS), Spring Boot parent 4.1.1, Vaadin 25.2.8, MapStruct 1.6.3,
lombok-mapstruct-binding 0.2.0, AWS S3 SDK 2.54.18, JetBrains annotations 26.1.0, jsoup 1.23.2,
springdoc-openapi 2.8.5, JaCoCo 0.8.15, Tika-core 4.0.0, Commons Text 1.15.0, Liquibase-core 5.0.4,
Commons Collections4 4.6.0, Commons IO 2.22.0. Lombok's version is not pinned directly — inherited
from the Spring Boot 4.1.1 BOM (reported elsewhere as 1.18.46, not yet verified directly against
this repo's own effective resolved version).

## Why change

Several dependencies are behind their latest release. More importantly, springdoc-openapi is
pinned to its 2.x line, which upstream documents as targeting Spring Boot 3 — this project runs
Spring Boot 4.1.1, which per the library's own compatibility statement needs the 3.x line instead.
Running a version line not meant for this Spring Boot major version is a latent correctness risk
for `marketplace-rest-api`'s OpenAPI docs endpoint, not just routine staleness.

## Expected benefit

Dependencies on their vendor-intended compatible version line, latest security patches, and an
explicit, settled answer on whether a Java bump makes sense (it doesn't right now — see Approach).

## Approach

1. **springdoc-openapi 2.8.5 → 3.1.1** — the version line actually meant for Spring Boot 4.x per
   upstream docs; verify `marketplace-rest-api`'s OpenAPI docs endpoint still renders correctly
   after the bump (breaking changes likely between major versions). Highest-priority item here.
2. **Vaadin 25.2.8 → 25.3.0** (released 2026-09-22, confirmed directly) — minor bump, run the full
   Playwright suite after to catch any UI regression.
3. **Low-risk minor bumps**: AWS S3 SDK 2.54.18→2.55.9, JaCoCo 0.8.15→0.8.16, Tika-core 4.0.0→4.1.0.
4. **Java stays at 25** — already the current LTS. JDK 27 exists but is a non-LTS interim release
   with a short support window, not a recommended production target. No action beyond having
   checked.
5. **Needs direct re-verification before acting, not yet trustworthy as researched**: Commons IO's
   exact latest version (search was inconclusive); whether Lombok 1.18.46 (as bundled by the
   Spring Boot 4.1.1 BOM) has a real annotation-processor issue under Java 25 as one GitHub issue
   claimed — this project's tests currently pass, so unclear if the claim even applies here;
   confirm against this repo's own effective Lombok version before acting either way.
6. **Already current, no action**: Spring Boot (4.1.1), MapStruct (1.6.3),
   lombok-mapstruct-binding (0.2.0), JetBrains annotations (26.1.0), jsoup (1.23.2), Commons Text
   (1.15.0), Commons Collections4 (4.6.0), Liquibase-core (5.0.4).

## Related

- `improvement-203` entry #4 — MinIO Community Edition archived, Garage migration candidate
  (separate infra-currency finding from the same general check, kept in the deferred-findings
  bucket rather than its own task since it's a single, clearly-scoped swap).
