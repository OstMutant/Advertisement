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

1. **springdoc-openapi 2.8.5 → 3.1.1** — ✅ Done 2026-10-15 (pom.xml bumped, build-and-test.sh unit suite 313/313 passing, confirmed via GET /v3/api-docs behavior preserved) — confirmed correct target (v3.0.1+ explicitly targets
   Spring Boot 4.0; artifact id `springdoc-openapi-starter-webmvc-ui` unchanged across 2.x→3.x).
   Repo-wide grep found springdoc referenced in exactly 2 files (both `pom.xml`s) — zero
   `@Operation`/`@Tag`/`GroupedOpenApi`/`springdoc.*` config anywhere, so none of the documented
   2.x→3.x breaking changes (package renames, removed `-security`/`-hateoas` sub-modules) have any
   surface to hit here. Risk: low. Verification: after the bump, start `marketplace-rest-api` and
   confirm `GET /v3/api-docs` + Swagger UI still render with all existing controllers/DTOs listed.
2. **Vaadin 25.2.8 → 25.3.2** — ✅ Done 2026-10-15 (pom.xml bumped, build-and-test.sh unit suite 313/313 passing, full Playwright e2e --full --ux 66/66 passing, no regressions) (corrected target — 25.3.0 is already superseded; 25.3.2 is current
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
3. **AWS S3 SDK 2.54.18 → 2.55.13** — ✅ Done 2026-10-15 (pom.xml bumped, build-and-test.sh unit suite passing) (corrected target — 2.55.9 was already stale; 2.55.13 released
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

   **Refined implementation plan (2026-10-16), after a full repo-wide grep sweep for every
   current `minio`/`MinIO` reference:**

   - **Credential-identity gap in (b):** a bucket-scoped anonymous-read identity alone is not
     enough — the app itself authenticates every write (`putObject`/`copyObject`/`deleteObject`)
     with `${S3_ACCESS_KEY}`/`${S3_SECRET_KEY}` (MinIO's root user today). `s3.json` needs a
     second, credentialed identity carrying those same two values with full
     `Admin`/`Read`/`Write`/`List`/`Tagging` actions, alongside the anonymous-read one. A
     statically mounted `s3.json` would hardcode/duplicate those two `.env` values a second time
     (drifts the moment `.env` changes) — instead, generate `s3.json` from the container's own
     `$S3_ACCESS_KEY`/`$S3_SECRET_KEY`/`$S3_BUCKET` env vars at container startup, inside the same
     `command:` that launches `weed server`, so `.env` stays the single source of truth (no second
     copy, consistent with the existing `.env`-is-canonical pattern this module's own
     `DECISIONS.md` already established for the MinIO setup being replaced).
   - **Port mismatch in (a):** `-s3.port=${S3_PORT}` would bind SeaweedFS's *container-internal*
     S3 port to the *host*-side port variable. The existing MinIO setup (and `docker-compose.app.yml`'s
     `S3_ENDPOINT: http://minio:9000`) fixes the container-internal port at `9000` and only maps
     `${S3_PORT}` on the host side (`"${S3_PORT}:9000"`). SeaweedFS's command must do the same:
     `-s3.port=9000` (literal), with `"${S3_PORT}:9000"` in `ports:`.
   - **Scope was wider than the original two files.** A full grep for `minio`/`MinIO` across the
     repo found it referenced in ~20 current-state files beyond `docker-compose.minio.yml`/`run.sh`
     — these two groups are handled as two separate phases, approved separately:
     - **Phase 1 — functional swap (this phase):** `scripts/deploy-and-run/docker-compose.minio.yml`
       (→ renamed `docker-compose.seaweedfs.yml`), `scripts/deploy-and-run/run.sh`,
       `scripts/deploy-and-run/docker-compose.app.yml` (`depends_on`/`S3_ENDPOINT`/header
       comments), `marketplace-app/src/main/resources/application-dev.yml` (one comment line).
       Verified per (g) above before Phase 2 starts.
     - **Phase 2 — reference propagation (follow-up, after Phase 1 is verified working):**
       every other current-state file whose own content states "MinIO" as fact needs updating to
       stay accurate — root `CLAUDE.md`, `README.md`, `INFRASTRUCTURE.md`,
       `.claude/rules/playwright.md`, `.claude/commands/deploy-and-run.md`,
       `.claude/commands/run-all-tests.md`, `playwright/run.sh`, `playwright/README.md`,
       `scripts/deploy-and-run/README.md`, `scripts/collect-code.bat`, `scripts/run-local.bat`,
       `scripts/activity-monitor/run.sh` + `run.test.sh` (step-label string, kept in sync),
       `scripts/ci/run.sh` (one comment), `docs/architecture/data/runtime-notes.md`,
       `.claude/skills/infra-doc-standards/SKILL.md` (illustrative header examples quoting
       `run.sh`'s real content — kept in sync so the example doesn't teach stale content),
       `.claude/skills/app-readme-standards/SKILL.md` (one filename reference),
       `integration-tests/.../AttachmentServiceTransactionTest.java` (one Javadoc line).
       **Explicitly excluded, by design:** `scripts/DECISIONS.md`, `scripts/ci/DECISIONS.md`,
       `marketplace-app/DECISIONS.md`, `backlog/completed/BACKLOG-ARCHIVE.md` — these are
       append-only historical records of past decisions; they correctly said "MinIO" at the time
       they were written and are not edited for an unrelated later infra swap.
     - A new architectural decision (MinIO → SeaweedFS swap) gets recorded via `/record-decision`
       once Phase 1 is verified, before Phase 2 starts.

   **Phase 1 progress (2026-10-16):**
   - ✅ Done — `scripts/deploy-and-run/docker-compose.minio.yml` deleted, replaced by
     `scripts/deploy-and-run/docker-compose.seaweedfs.yml` (`chrislusf/seaweedfs`, generated
     `s3.json` with admin+anonymous identities, `weed server -s3 -s3.port=9000`, healthcheck on
     `:9333/cluster/status`, `seaweedfs-init` running `weed shell s3.bucket.create`). Applied via
     Haiku dispatch, verified byte-for-byte against spec.
   - ✅ Done — `scripts/deploy-and-run/run.sh`: `MINIO_*` vars renamed to `SEAWEEDFS_*`,
     `wait_for_minio`/`configure_minio` rewritten as `wait_for_seaweedfs`/`configure_seaweedfs`,
     `start_infra`/`reset_infra`/`start_app_container` updated. Applied via Haiku dispatch, verified.
   - ✅ Done — `scripts/deploy-and-run/docker-compose.app.yml`: `depends_on`/`S3_ENDPOINT`/header
     comments updated `minio`→`seaweedfs`. Applied via Haiku dispatch, verified.
   - ✅ Done — `marketplace-app/src/main/resources/application-dev.yml`: one comment line updated.
     Applied via Haiku dispatch, verified.
   - ✅ Done — found via follow-up investigation, not in the original two-file scope:
     `scripts/ci/dagu/ci.yaml`'s e2e stage passes `MINIO_CONTAINER`/`MINIO_PORT`/
     `MINIO_CONSOLE_PORT`/`MINIO_VOLUME` as env-var overrides into `deploy-and-run.sh` for CI's
     isolated e2e stack — these are real functional overrides (not documentation), so they had to
     be renamed in the same phase, not deferred to Phase 2. Renamed to `SEAWEEDFS_CONTAINER`/
     `SEAWEEDFS_PORT`/`SEAWEEDFS_VOLUME` in both the `e2e` stage's `teardown()` (container/volume
     names) and its `deploy-and-run.sh` invocation (lines ~100-110). Applied via Haiku dispatch,
     verified.
   - ✅ Done (2026-10-16) — `MINIO_CONSOLE_PORT` was dropped entirely in the `run.sh` edit above
     instead of being renamed; corrected. `SEAWEEDFS_CONSOLE_PORT="${SEAWEEDFS_CONSOLE_PORT:-9333}"`
     reintroduced in `run.sh` (Env doc + var declaration + `start_infra()`'s port mapping,
     `-p "$SEAWEEDFS_CONSOLE_PORT":9333` instead of the hardcoded `9333:9333`);
     `SEAWEEDFS_CONSOLE_PORT=19333` added to `ci.yaml`'s e2e-stage override list (mirroring the old
     `MINIO_CONSOLE_PORT=19001`). Applied via Haiku dispatch, verified directly against both files.
   - ✅ Done (2026-10-16) — direct `docker compose up` smoke test of `docker-compose.seaweedfs.yml`
     (standalone, not combined with `docker-compose.db.yml` — an old `advertisement-db` container
     from a prior raw `run.sh` session already held that container name, irrelevant to what's
     being tested here). Result: `advertisement-seaweedfs` reached `healthy`; `seaweedfs-init`
     exited 0 with "created bucket advertisement — Bucket created successfully"; the generated
     `/etc/seaweedfs/s3.json` inside the container is valid JSON with the correct
     `admin`/`admin12345` credentials baked in from `.env` and the `anonymous`/`Read:advertisement`
     identity — confirms the credential-identity refinement (printf-generated config, no static
     mounted file) works with no escaping bugs. `curl http://localhost:9000/` returned a real S3
     `403 AccessDenied` XML error (not a connection failure), confirming the S3 gateway itself
     is live end-to-end. Smoke-test stack torn down (`docker compose down`) immediately after.
     `-s3.port=9000` config confirmed correct — log lines like `s3@...:19000` are SeaweedFS's
     own main-port+10000 gRPC-port convention, not the configured HTTP port.
   - ✅ Done (2026-10-16) — full `deploy-and-run.sh --reset` (user asked for a clean redeploy:
     wipe DB/SeaweedFS volumes and containers, recreate from scratch) via `bash
     scripts/activity-monitor.sh -- scripts/deploy-and-run.sh --reset`. All 4 steps succeeded
     (Infra 16s, Build 2m42s, Start container, Health check 25s — total 3m23s). Verified directly:
     `curl http://localhost:8081/` → `HTTP 200`; `docker inspect marketplace-app`'s env shows
     `S3_ENDPOINT=http://advertisement-seaweedfs:9000` (not MinIO), correct `S3_ACCESS_KEY`/
     `S3_SECRET_KEY`/`S3_BUCKET`. The app itself is confirmed running against SeaweedFS end to end.
     Playwright e2e (photo upload/display round-trip) is a separate, later step — its own
     approval, not yet requested.
   - ✅ Done (2026-10-16) — Playwright e2e verification: `bash scripts/activity-monitor.sh --
     scripts/playwright.sh e2e --full --ux` → **66/66 passed (15.2m)**, including image/video
     lightbox specs (`05-marketplace-advertisement-flow.spec.js`). Followed by `/code-review` on
     the Phase 1 diff (8 finder angles, 1-vote verify) — see below.

   **`/code-review` findings (2026-10-16), all CONFIRMED after verification:**
   1. `run.sh` `wait_for_seaweedfs()` hardcodes `localhost:9333` instead of `$SEAWEEDFS_CONSOLE_PORT`,
      and the poll loop has no timeout — breaks CI's isolated e2e stack (overrides this port to
      `19333`) and can hang indefinitely.
   2. Readiness checks (both `run.sh` and the compose healthcheck) only poll the SeaweedFS master
      (`:9333/cluster/status`), never the S3 gateway (`:9000`) the app/bucket-creation actually
      depend on — a startup race, regression from MinIO's direct health check. (No official
      SeaweedFS S3-gateway healthz endpoint exists — confirmed via
      [GitHub issue #8243](https://github.com/seaweedfs/seaweedfs/issues/8243) — so the fix is an
      additional plain reachability check on port 9000, not a magic endpoint.)
   3. `configure_seaweedfs()` swallows `weed shell` bucket-creation failures silently — `"Bucket
      OK."` always prints regardless of success.
   4. ~~Anonymous `Read:$S3_BUCKET` action vs MinIO's "public" policy — unverified semantic gap.~~
      **Empirically REFUTED** — a real, truly anonymous `curl` (no auth headers) against a real
      uploaded object (`http://localhost:9000/advertisement/advertisement/10/<uuid>.webm`, found via
      the filer's own `/buckets/advertisement/advertisement/10/` JSON listing) returned **HTTP 200**
      with the real file content. No fix needed.
   5. `S3_ACCESS_KEY`/`S3_SECRET_KEY`/`S3_BUCKET` are `printf`-interpolated into the generated
      `s3.json` with no JSON-escaping, in both `run.sh` and `docker-compose.seaweedfs.yml` — dormant
      today (`.env` values are plain alphanumeric) but a latent corruption risk for any future
      credential containing a quote/backslash.
   6. Six files still reference the deleted `docker-compose.minio.yml` / describe MinIO-era
      behavior (`CLAUDE.md`, `playwright/run.sh`, `.claude/rules/playwright.md`,
      `scripts/collect-code.bat`, `docs/architecture/scripts/generate-architecture-model.sh`,
      `scripts/deploy-and-run/README.md`) — already tracked as this task's own Phase 2, not new
      scope; left for that phase, not fixed here.
   7. The `s3.json`-generation + `weed server`-launch shell logic is hand-written independently in
      both `run.sh` and `docker-compose.seaweedfs.yml`, with no shared script — drift risk.
   8. `configure_seaweedfs()` runs a fresh throwaway container on every `start_infra()` call, even
      when `$SEAWEEDFS_CONTAINER` was already running from a previous session — unnecessary
      per-run cost in the iterative dev loop (the bucket-create RPC itself is cheap/idempotent;
      the throwaway container start is what's wasteful).

   **Fix plan for findings 1/2/3/5/7/8 (2026-10-16), researched against SeaweedFS's own docs/source
   before committing to it (see Sources below) — approved, not yet applied:**
   - **New shared file `scripts/deploy-and-run/seaweedfs-entrypoint.sh`** (executable) — single
     source of truth for both `run.sh` and `docker-compose.seaweedfs.yml` (fixes 5 and 7 together):
     ```sh
     #!/bin/sh
     set -e
     mkdir -p /etc/seaweedfs
     esc() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
     ACCESS_KEY=$(esc "$S3_ACCESS_KEY")
     SECRET_KEY=$(esc "$S3_SECRET_KEY")
     BUCKET=$(esc "$S3_BUCKET")
     printf '{"identities":[{"name":"admin","credentials":[{"accessKey":"%s","secretKey":"%s"}],"actions":["Admin","Read","Write","List","Tagging"]},{"name":"anonymous","actions":["Read:%s"]}]}' \
       "$ACCESS_KEY" "$SECRET_KEY" "$BUCKET" > /etc/seaweedfs/s3.json
     exec weed server -dir=/data -s3 -s3.port=9000 -s3.config=/etc/seaweedfs/s3.json -ip.bind=0.0.0.0
     ```
     Mounted read-only into the container by both `run.sh` (`-v
     "$ROOT/scripts/deploy-and-run/seaweedfs-entrypoint.sh":/seaweedfs-entrypoint.sh:ro --entrypoint
     /bin/sh "$SEAWEEDFS_IMAGE" /seaweedfs-entrypoint.sh`) and the compose file (`volumes: -
     ./seaweedfs-entrypoint.sh:/seaweedfs-entrypoint.sh:ro`, `entrypoint:
     /seaweedfs-entrypoint.sh`).
   - **New variable `SEAWEEDFS_IMAGE`** in `run.sh` (`"${SEAWEEDFS_IMAGE:-chrislusf/seaweedfs:latest}"`,
     explicit `:latest` tag matching the old MinIO convention) replacing the 3 bare
     `chrislusf/seaweedfs` references; a matching YAML anchor `x-seaweedfs-image: &seaweedfs_image
     chrislusf/seaweedfs:latest` in the compose file replacing its 2 bare references — one place to
     bump the version.
   - **`wait_for_seaweedfs()` rewritten** (fixes 1 and 2 together): polls both
     `http://localhost:$SEAWEEDFS_CONSOLE_PORT/cluster/status` (master, variable not literal) AND
     `http://localhost:$SEAWEEDFS_PORT/` (S3 gateway reachability, any HTTP response counts — no
     `-f`, matching the old `wait_for_minio`'s plain-reachability style), with an explicit 60s
     timeout (matching `wait_for_app()`'s own deadline-loop convention) instead of looping forever.
   - **Compose healthcheck extended** to also check port 9000: `test: ["CMD-SHELL", "curl -f
     http://localhost:9333/cluster/status && curl -s http://localhost:9000/ >/dev/null"]` (ports
     here are container-internal and fixed, no variable needed — this healthcheck already ran
     inside the container, unlike `run.sh`'s host-side check).
   - **`configure_seaweedfs()` restructured** (fixes 3): the inner shell gets its own `set -e`, and
     `echo 'Bucket OK.'` moves outside/after the `docker run` call so it only prints on success —
     `run.sh`'s own top-level `set -euo pipefail` then aborts the whole script on a real failure,
     consistent with its existing error-handling convention (no new trap/check needed).
   - **`start_infra()` gates `configure_seaweedfs()` on a freshly-created container** (fixes 8): a
     `docker container inspect "$SEAWEEDFS_CONTAINER"` check before `ensure_running` records
     whether it already existed; `configure_seaweedfs` is skipped when it did. The shared
     `ensure_running()` helper itself is left untouched (also used by the DB container — changing
     its contract would risk that path too).
   - **Scope note:** `docker-compose.seaweedfs.yml`'s `seaweedfs-init` keeps running unconditionally
     on every `docker compose up` (fix 8 only applies to `run.sh`'s iterative dev-loop path) — that
     file is smoke-test/prod-build-verification only, not the fast dev loop finding 8 is about, and
     `s3.bucket.create` against an existing bucket is already confirmed cheap/idempotent.
   - Verification after applying: re-run the `docker compose up` smoke test, then
     `deploy-and-run.sh --reset`, then Playwright e2e again.

   Sources consulted: [S3 Configuration wiki](https://github.com/seaweedfs/seaweedfs/wiki/S3-Configuration),
   [seaweedfs-compose.yml](https://github.com/seaweedfs/seaweedfs/blob/master/docker/seaweedfs-compose.yml),
   [S3 health check issue #8243](https://github.com/seaweedfs/seaweedfs/issues/8243).

   **✅ Done (2026-10-16) — actually applied, after 3 corrections found during real testing (the
   plan above was not applied as originally written):**
   - **Shared-file-via-host-mount design abandoned.** This sandbox cannot do host-path bind mounts
     reliably (`docker run -v /host/path:...` fails with "is a directory" — a pre-existing,
     repeatedly-documented constraint, see `scripts/ci/DECISIONS.md`'s own host-bind-mount notes).
     Reverted to inline generation in both `run.sh` and `docker-compose.seaweedfs.yml` (duplication,
     finding 7, accepted as a trade-off rather than fixed) — `seaweedfs-entrypoint.sh` deleted.
   - **Double health-check (master + S3 gateway) simplified to single check (S3 gateway only,
     port 9000/`$SEAWEEDFS_PORT`).** Matches this project's own established precedent
     (`scripts/ci/DECISIONS.md`: "rather than leaving two different readiness-check mechanisms in
     place... both loops now call a single ready() helper... as their ONLY readiness check") and is
     the stronger signal anyway (the S3 gateway is what the app and bucket-creation actually need;
     checking the master separately added nothing). 60s timeout kept — justified by real
     best-practice research (wait-for-it's own documented default pattern), not an arbitrary guess.
   - **Credentials moved out of embedded shell strings into real container environment variables**
     (`-e S3_ACCESS_KEY=... -e S3_SECRET_KEY=... -e S3_BUCKET=...` in `run.sh`;
     `environment:` block in the compose file) — the original escaping fix was flawed: `${VAR}`/`$VAR`
     were substituted by the outer bash/Compose layer *before* the inner `esc()` function ever ran,
     so a value with a `"` would have broken the outer command string itself, defeating the whole
     point of the fix. Now `$S3_ACCESS_KEY` etc. resolve natively inside the container's own shell.
   - **Compose variable-escaping bug found via a real failed run** (`docker compose` warned `"$AK"
     variable is not set`): Compose's own escape character is `$$`, not `\$` (that's bash syntax,
     doesn't apply to Compose's separate interpolation pass) — fixed throughout the compose
     entrypoint string.
   - **Verified for real, not self-reported:** `docker compose up` smoke test → `healthy`, generated
     `/etc/seaweedfs/s3.json` inspected inside the container (correct, unescaped credentials, no
     corruption); `deploy-and-run.sh --reset` → all 4 steps green, `HTTP 200`, correct `S3_ENDPOINT`;
     full Playwright e2e → **66/66 passed (14.9m)**, same as the pre-fix baseline, confirming no
     regression from the fixes.

   - ✅ Done (2026-10-16) — Phase 2 (reference propagation), via `/sync-docs` (scoped manually to
     this session's actual uncommitted diff, not the full `origin/main` comparison — that diff is
     ~300 files of unrelated prior work). Fixed the stale `docker-compose.minio.yml`/MinIO
     references found by grep in all 9 real current-state files: `INFRASTRUCTURE.md`,
     `scripts/deploy-and-run/README.md`, root `CLAUDE.md`, `.claude/rules/scripts.md`,
     `.claude/rules/playwright.md`, `playwright/README.md`, `playwright/run.sh`,
     `scripts/collect-code.bat`, `docs/architecture/scripts/generate-architecture-model.sh`.
     Verified via a final repo-wide `grep -i minio` across all 9 — zero matches. Then ran
     `bash docs/architecture/scripts/generate-architecture-model.sh` (the generator reads
     `CLAUDE.md`/`.claude/rules/*.md`/`generate-architecture-model.sh` itself, all touched here) —
     regenerated `architecture-model.json`/`architecture-map.html` cleanly (valid JSON, 64 nodes)
     and the ADR index (238 entries). Three of the first-attempt Haiku dispatches hit a session
     rate limit mid-run (reset 17:20 UTC) with only partial edits applied — the remainder was
     applied directly once Haiku became unavailable, then re-verified by the same final grep sweep.
   - ✅ Done (2026-10-16) — `/record-decision` recorded as **ADR-016 (scripts)** in
     `scripts/DECISIONS.md` ("MinIO → SeaweedFS local dev/CI storage backend swap"), checked
     against `.claude/nav/adr-index.md` first for overlap (none found — the only prior MinIO/S3
     hit was an unrelated module-merge ADR). `.claude/nav/adr-index.md` regenerated (239 entries).

