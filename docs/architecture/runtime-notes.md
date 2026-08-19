**Where Claude Code runs:** every `Read`/`Edit`/`Bash` action happens inside the `claude-dev`
container (image `claude-j25-dev`, started via `claude --resume`) — not on the Windows host
directly, and not inside any of this project's own containers (`marketplace-app`,
`advertisement-db`, etc.), which sit alongside it as siblings, not nested inside it.

- `claude-dev`'s `hostname` is `docker-desktop` — Docker Desktop assigns this to containers it
  manages under its WSL2 backend, so seeing `docker-desktop` in a shell prompt is not evidence of
  running on the Docker Desktop VM itself, just this container's assigned hostname.
- Bind mounts: the repo itself (host `Advertisement` folder) is mounted to `/app`, the host's
  Maven cache to `/root/.m2` (shared cache across rebuilds), the host's Claude config folder to
  `/root/.claude` (session/config persistence), and `/var/run/docker.sock` to
  `/var/run/docker.sock` — the socket mount lets `claude-dev` drive sibling containers through the
  host's real Docker Engine, without being a Docker-in-Docker setup.

**Compiling and running the app:** `mvn`/`java` execute directly inside `claude-dev` whenever a
script (`integration-tests/run.sh`, `sonar/run.sh`'s own compile step) invokes Maven itself —
there is no separate build container for that path. `scripts/build-and-test.sh` and `deploy.sh`'s
full image build instead hand off to a container/the mounted Docker socket respectively; the
resulting containers (the build-and-test container, `marketplace-app`) then run as siblings, not
nested inside `claude-dev`.

**Node.js:** `docs/architecture/scripts/*.js` (this generator's own JSON-producing helpers) run on the
Node.js install baked into the `claude-j25-dev` image — not installed on the Windows host, and
unrelated to `marketplace-app` itself (a pure Java/Spring application with no Node runtime
dependency).

**Sibling containers reachable via the mounted `docker.sock`** (managed from `claude-dev`, never
nested inside it): `marketplace-app`, `advertisement-db` (Postgres), `advertisement-minio`
(S3-compatible storage), `sonarqube` + `sonar-scanner`, and short-lived tooling containers spun up
per script run (the Playwright runner, the isolated CI runner).

**Architecture map tooling** (`docs/architecture/scripts/`, plus `docs/ai/scripts/generate-adr-index.sh`) —
the scripts that build and verify `architecture-map.html`:

- `generate-architecture-model.sh` — regenerates `architecture-model.json` +
  `architecture-map.html`. Run manually: `bash docs/architecture/scripts/generate-architecture-model.sh
  [--with-sonar] [--with-archunit] [--with-adr-details]` — all three off by default (opt-in: live
  SonarQube fetch, `ArchitectureMetricsExport` ArchUnit numbers, full embedded ADR text). No
  Docker, no sandbox-specific handling.
- `check-architecture-model-freshness.sh` — read-only CI gate, no args; regenerates into a temp
  copy, diffs against the committed files, restores them either way.
- `md-to-decisions-json.js` (Node) — `--stdout <module>` (used internally by the generator);
  `--extract <module> <ADR-NNN>[,<ADR-NNN>...]` prints one/a few ADRs as raw markdown — use this
  instead of `Read`-ing a whole `DECISIONS.md` once `docs/ai/adr-index.md` has already narrowed
  down which id(s) are needed.
- `liquibase-schema-to-json.js` (Node) — parses Liquibase changelog XML for the Database ERD;
  invoked internally by the generator, not normally run standalone.
- `screenshot-architecture-map.sh` — headless-Playwright screenshots of every screen, no args;
  needs Docker (spins its own `arch-map-shot` container) — same sandbox constraint as Playwright
  itself: use `docker cp`, never a `-v` volume mount.
- `generate-adr-index.sh` / `check-adr-index-freshness.sh` (`docs/ai/scripts/`) — rebuild/verify
  `docs/ai/adr-index.md`; rerun the generator after any `DECISIONS.md` edit (standing rule), no
  args either way.
