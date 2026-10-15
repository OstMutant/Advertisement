# improvement-204: Dependency and Java version upgrade audit

**Type:** improvement — infra/maintenance, dependency version audit
**Module:** root `pom.xml` (cross-cutting via dependencyManagement); `marketplace-rest-api` specifically for springdoc-openapi
**Priority:** 🟡 Top — user-requested Top placement, 2026-10-01; promoted to the very front of the Top tier, 2026-10-15 (confirmed independent of 186/138/198/191)
**When:** independent, no blockers

## Current state

Root `pom.xml` pins: Java 25 (LTS), Spring Boot parent 4.1.1, Vaadin 25.2.8, MapStruct 1.6.3,
lombok-mapstruct-binding 0.2.0, AWS S3 SDK 2.54.18, JetBrains annotations 26.1.0, jsoup 1.23.2,
springdoc-openapi 2.8.5, JaCoCo 0.8.15, Tika-core 4.0.0, Commons Text 1.15.0, Liquibase-core 5.0.4,
Commons Collections4 4.6.0, Commons IO 2.22.0. Lombok's version is not pinned directly — inherited
from the Spring Boot 4.1.1 BOM; confirmed directly via `mvn dependency:tree
-Dincludes=org.projectlombok:lombok` to resolve to exactly **1.18.46**. Additionally,
`scripts/deploy-and-run/docker-compose.minio.yml` (to be renamed `docker-compose.seaweedfs.yml`)
and `scripts/deploy-and-run/run.sh` are now in scope for this task, for a MinIO→SeaweedFS local
dev/CI storage-backend swap.

## Why change

Several dependencies are behind their latest release. More importantly, springdoc-openapi is
pinned to its 2.x line, which upstream documents as targeting Spring Boot 3 — this project runs
Spring Boot 4.1.1, which per the library's own compatibility statement needs the 3.x line instead.
Running a version line not meant for this Spring Boot major version is a latent correctness risk
for `marketplace-rest-api`'s OpenAPI docs endpoint, not just routine staleness.

Separately, MinIO Community Edition's GitHub repo was archived upstream on 2026-04-25 after a maintenance wind-down (license changed Apache 2.0 → AGPLv3 in 2025-05, Admin GUI removed, Docker Hub publishing stopped) — no further upstream security fixes will land on it. SeaweedFS is the chosen replacement: Apache 2.0 licensed (no copyleft/network-use clause), and far more actively maintained (release 4.48 on 2026-09-28, 36+ active committers in the last 12 months, 35.3k GitHub stars) than the AGPLv3-licensed Garage alternative that was also considered (release v2.3.0 on 2026-04-16, 83 contributors total, 3,603 stars).

## Expected benefit

Dependencies on their vendor-intended compatible version line, latest security patches, and an
explicit, settled answer on whether a Java bump makes sense (it doesn't right now — see Approach).

## Approach

1. **springdoc-openapi 2.8.5 → 3.1.1** — confirmed correct target (v3.0.1+ explicitly targets
   Spring Boot 4.0; artifact id `springdoc-openapi-starter-webmvc-ui` unchanged across 2.x→3.x).
   Repo-wide grep found springdoc referenced in exactly 2 files (both `pom.xml`s) — zero
   `@Operation`/`@Tag`/`GroupedOpenApi`/`springdoc.*` config anywhere, so none of the documented
   2.x→3.x breaking changes (package renames, removed `-security`/`-hateoas` sub-modules) have any
   surface to hit here. Risk: low. Verification: after the bump, start `marketplace-rest-api` and
   confirm `GET /v3/api-docs` + Swagger UI still render with all existing controllers/DTOs listed.
2. **Vaadin 25.2.8 → 25.3.2** (corrected target — 25.3.0 is already superseded; 25.3.2 is current
   as of 2026-10, 25.4 targets Q4 2026). Real breaking changes between 25.2.8 and 25.3.2: `Image`
   now extends `HtmlComponent` instead of `HtmlContainer` (`.add()`/`.remove()`/`.removeAll()`/
   `.setText()`/`.getText()` removed from it); Spring Security now answers `401` instead of
   `403`/redirect for sub-resource requests under session timeout; `VaadinServlet.
   serveStaticOrWebJarRequest()` deprecated for removal (only matters if any custom static-file
   handling exists); 156 `VaadinIcon` constants deprecated/renamed (build warnings only, not
   removed). Pre-bump: grep the repo for any `Image` component `.add()/.setText()/.getText()`
   calls or custom `StaticFileHandler`/`serveStaticOrWebJarRequest` overrides (none expected,
   confirm). Post-bump verification: full `playwright.sh e2e --full --ux`, paying attention to
   session-timeout/sub-resource-request flows (new 401 behavior) and icon rendering.
3. **AWS S3 SDK 2.54.18 → 2.55.13** (corrected target — 2.55.9 was already stale; 2.55.13 released
   2026-10-08 is current). Routine patch-line bump, no breaking changes found, low risk.
4. **Java stays at 25** — already the current LTS. JDK 27 exists but is a non-LTS interim release
   with a short support window, not a recommended production target. No action beyond having
   checked.
5. **Already current, no action — re-verification complete**: Spring Boot (4.1.1), MapStruct
   (1.6.3), lombok-mapstruct-binding (0.2.0), JetBrains annotations (26.1.0), jsoup (1.23.2),
   Commons Text (1.15.0), Commons Collections4 (4.6.0), Liquibase-core (5.0.4). Also confirmed
   settled by direct re-verification: **JaCoCo 0.8.15** is already latest (the previously-floated
   "0.8.16" does not exist as a real release, only a `0.8.16-SNAPSHOT`); **Tika-core 4.0.0** is
   already latest (the previously-floated "4.1.0" does not exist); **Commons IO 2.22.0** is
   already latest (published 2026-04-19). **Lombok 1.18.46 / Java 25 annotation-processor claim**
   — real issue (JDK23+ changed javac's default annotation-processor discovery, breaking Lombok's
   old implicit-discovery mechanism), but already mitigated here: root `pom.xml` (lines 177-192)
   already explicitly registers Lombok via `annotationProcessorPaths` in `maven-compiler-plugin`,
   which is exactly the documented fix. A separate real Lombok 1.18.46 bug (issue #4072,
   `ClassNotFoundException: EndPosTable`) only affects JDK 27, irrelevant since Java stays at 25.
6. **MinIO (local dev/CI storage backend) → SeaweedFS** — replaces the archived MinIO, phased,
   with concrete syntax now confirmed:
   a. Image: `chrislusf/seaweedfs` (the official image — confirmed no `seaweedfs/seaweedfs` Docker
      Hub image exists, that name only hosts the GitHub source repo). Swap `minio/minio:latest` +
      `minio/mc:latest` init container in `docker-compose.minio.yml` (renamed to
      `docker-compose.seaweedfs.yml`) for a single `chrislusf/seaweedfs` container running
      `weed server -dir=/data -s3 -s3.port=${S3_PORT} -ip.bind=0.0.0.0` (all-in-one
      master+volume+filer+S3-gateway in one process). Mount a named volume at `/data` (parity
      with MinIO's `minio_data`).
   b. Public-read bucket access via a mounted `s3.json` passed with `-s3.config=/etc/seaweedfs/
      s3.json` (parity with `mc anonymous set public`):
      `{ "identities": [ { "name": "anonymous", "actions": ["Read:${S3_BUCKET}"] } ] }`.
   c. Healthcheck against the master's cluster-status endpoint — no `mc`-equivalent CLI ships in
      the image: `["CMD", "curl", "-f", "http://localhost:9333/cluster/status"]` (fall back to
      `wget --spider` if the image lacks curl — verify when writing the compose file).
   d. Bucket-creation init step (parity with `mc mb --ignore-existing`):
      `echo "s3.bucket.create -name ${S3_BUCKET}" | weed shell -master=seaweedfs:9333`.
   e. No application code change needed — confirmed: `S3StorageService`/`AttachmentS3Config`
      (attachment-spring-boot-starter) use the AWS SDK's generic `S3Client` with
      `forcePathStyle(true)` and plain `putObject`/`copyObject`/`deleteObject`/`listObjectsV2`
      calls — SeaweedFS's S3 gateway implements the same S3 API surface.
   f. Scope confined to local dev/CI tooling only — confirmed prod never runs this container;
      `docker-compose.app.yml`'s commented Neon/Supabase Storage block shows prod always points
      at an external S3-compatible provider.
   g. Verify via a direct `docker compose up` smoke test of the new compose file first (the
      `s3.json` path flag name and `weed shell -master` address format weren't independently
      cross-verified against this exact image version's own CLI help output), then a full
      Playwright e2e run (image upload/display round-trip) + `scripts/ci.sh`'s e2e stage.

