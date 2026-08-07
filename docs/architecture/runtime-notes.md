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
script (`deploy-dev.sh`, `unit-tests.sh`, `integration-tests.sh`) invokes Maven itself — there is
no separate build container for that path. `deploy.sh`'s full image build instead hands a
`docker build` command to the mounted socket, which the host's real Docker Engine executes; the
resulting image and its container (`marketplace-app`) then run as a sibling, not nested inside
`claude-dev`.

**Node.js:** `scripts/architecture/*.js` (this generator's own JSON-producing helpers) run on the
Node.js install baked into the `claude-j25-dev` image — not installed on the Windows host, and
unrelated to `marketplace-app` itself (a pure Java/Spring application with no Node runtime
dependency).

**Sibling containers reachable via the mounted `docker.sock`** (managed from `claude-dev`, never
nested inside it): `marketplace-app`, `advertisement-db` (Postgres), `advertisement-minio`
(S3-compatible storage), `sonarqube` + `sonar-scanner`, and short-lived tooling containers spun up
per script run (the Playwright runner, the isolated CI runner).

**Reading one ADR without opening a whole `DECISIONS.md`:**
`node scripts/architecture/md-to-decisions-json.js --extract <module> <ADR-NNN>[,<ADR-NNN>...]`
prints the requested ADR(s) as raw markdown. Use this instead of `Read`-ing a whole
`DECISIONS.md` once `docs/ai/adr-index.md` has already narrowed down which id(s) are needed.
