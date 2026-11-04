# Plan for executors: architecture-map-services-card

Purpose: the result for the AI that executes it, with the detail each step's executor model needs
(exact content for a weaker model, exact files and behavior for a stronger one).

## Before any step

- The person's decisions in `plan-human.md` ("Decisions waiting for you") must be settled first. This
  plan is written with these placeholders:
  - D1 = (a): the OpenAPI JSON is mentioned on the Swagger card, so there are 6 services.
  - D2 = (a): every card has a "Started by" line, and the flow runner card adds an "Only when…" line.
  - D3: the card wording from step 6.
  - D4: the person's chosen option, applied in step 10.
  - D5 = (a): `INFRASTRUCTURE.md` stays as it is.
  - D6: `--project-directory` plus `name: sonar`.
  - D7: client tools are left unchanged.
  Where a decision comes out differently, the affected step says what changes.
- Project rules apply to every step: every change needs the user's approval under the Approval Rule
  in `.claude/rules.md`. Run `git add` after each file change and never commit. Code comments are one
  line, with no task or ADR numbers. All text is in English. Every new reader of `.env` strips `\r`,
  because the working tree has CRLF files.
- `.env` holds development credentials. Edit it only with exact old/new text, and never print the
  whole file to the chat or a log.
- New `.env` keys reuse names the scripts already know: `APP_PORT` and `SEAWEEDFS_CONSOLE_PORT`
  (`scripts/deploy-and-run/run.sh`, overridden by `scripts/ci/dagu/ci.yaml`'s e2e stack),
  `DAGU_UI_PORT` (`scripts/ci/dagu-rest-run-monitor.py`), and `SONAR_PORT` (new).
- Shell scripts follow scripts ADR-009's precedence: an explicitly exported variable wins, then
  `.env`, then the old literal as the last-resort default. Container-internal ports (`:9000`,
  `:9333` on the container side, `seaweedfs:9333`) stay literal.

## Steps

### Step 1: add the four ports to `.env`

- Depends on: nothing.
- Files: `/app/.env`
- Executor: haiku
- Input: the current `.env`. Its header comment starts with `# Description: Single source of truth
  for dev-only, non-secret values shared across`, and its last line is
  `PW_LINT_MAX_WARNINGS=<value>`.
- Exact change:
  1. Replace this header text:
     ```
     #   Testcontainers-based repository tests (AbstractPostgresIntegrationTest).
     ```
     with:
     ```
     #   Testcontainers-based repository tests (AbstractPostgresIntegrationTest), plus the host ports of
     #   the local web pages, read by scripts/sonar, scripts/ci and the architecture-map generator.
     ```
  2. Append at the end of the file, after the `PW_LINT_MAX_WARNINGS=` line, with a newline before:
     ```
     # Host ports of the local web pages; the architecture map's Services screen links to them.
     APP_PORT=8081
     SEAWEEDFS_CONSOLE_PORT=9333
     SONAR_PORT=9099
     DAGU_UI_PORT=8082
     ```
     Keep the file's existing line ending style (check with `file /app/.env`; if it reports CRLF,
     write CRLF lines).
- Verification: `grep -nE '^(APP_PORT|SEAWEEDFS_CONSOLE_PORT|SONAR_PORT|DAGU_UI_PORT)=' /app/.env`
  prints exactly 4 lines with 8081, 9333, 9099, 8082.
- Sources: https://12factor.net/config, https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/

### Step 2: deploy stack reads `APP_PORT` and `SEAWEEDFS_CONSOLE_PORT` from `.env`

- Depends on: 1.
- Files: `/app/scripts/deploy-and-run/docker-compose.seaweedfs.yml`, `/app/scripts/deploy-and-run/run.sh`
- Executor: haiku
- Input: the two files as they are.
- Exact change in `docker-compose.seaweedfs.yml`:
  1. `#   Master UI: http://localhost:9333. S3 API: http://localhost:${S3_PORT}.` becomes
     `#   Master UI: http://localhost:${SEAWEEDFS_CONSOLE_PORT}. S3 API: http://localhost:${S3_PORT}.`
  2. `# Env: S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET, S3_PORT -- all read from the repo-root .env.` becomes
     `# Env: S3_ACCESS_KEY, S3_SECRET_KEY, S3_BUCKET, S3_PORT, SEAWEEDFS_CONSOLE_PORT -- all read from the repo-root .env.`
  3. `# Outputs: advertisement-seaweedfs container (publishing S3_PORT -> 9000, 9333 -> 9333, named` becomes
     `# Outputs: advertisement-seaweedfs container (publishing S3_PORT -> 9000, SEAWEEDFS_CONSOLE_PORT -> 9333, named`
  4. `      - "9333:9333"` becomes `      - "${SEAWEEDFS_CONSOLE_PORT}:9333"`.
     Leave `-master=seaweedfs:9333` unchanged (container-internal).
- Exact change in `run.sh`:
  1. `SEAWEEDFS_CONSOLE_PORT="${SEAWEEDFS_CONSOLE_PORT:-9333}"` becomes
     `SEAWEEDFS_CONSOLE_PORT="${SEAWEEDFS_CONSOLE_PORT:-${ENV_SEAWEEDFS_CONSOLE_PORT:-9333}}"`
  2. `APP_PORT="${APP_PORT:-8081}"` becomes `APP_PORT="${APP_PORT:-${ENV_APP_PORT:-8081}}"`
  3. Header, old:
     ```
     #   container, so a later build cannot replace the jar of the running application. Starts all services on port
     #   8081. A Liquibase checksum mismatch (a changeset edited in place instead of a new one added,
     ```
     new:
     ```
     #   container, so a later build cannot replace the jar of the running application. Starts all services, the
     #   app on APP_PORT. A Liquibase checksum mismatch (a changeset edited in place instead of a new one added,
     ```
  4. Header, old: `#                    tests run, no data wipe -- start all services on port 8081`
     new: `#                    tests run, no data wipe -- start all services, the app on APP_PORT`
  5. Header, old:
     ```
     #   S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET/S3_REGION fall back to the repo-root .env, then a
     #   hardcoded default.
     ```
     new:
     ```
     #   S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET/S3_REGION and the ports DB_PORT, SEAWEEDFS_PORT (.env key
     #   S3_PORT), SEAWEEDFS_CONSOLE_PORT and APP_PORT fall back to the repo-root .env, then a hardcoded
     #   default.
     ```
- Verification: `bash -n /app/scripts/deploy-and-run/run.sh`;
  `cd /app && docker compose --project-directory . -f scripts/deploy-and-run/docker-compose.seaweedfs.yml config | grep -B1 -A4 'target: 9333'`
  shows `published: "9333"`. `grep -n '9333' /app/scripts/deploy-and-run/docker-compose.seaweedfs.yml`
  shows only the container-side `:9333` and `seaweedfs:9333`.
- Sources: https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/

### Step 3: Dagu UI port from `.env`

- Depends on: 1.
- Files: `/app/scripts/ci/run.sh`, `/app/scripts/ci/dagu-rest-run-monitor.py`
- Executor: haiku
- Input: the two files as they are.
- Exact change in `scripts/ci/run.sh`:
  1. Replace the line `UI_PROXY_PORT=8082` with:
     ```
     DAGU_UI_PORT="${DAGU_UI_PORT:-$(grep -E '^DAGU_UI_PORT=' "$ROOT/.env" 2>/dev/null | cut -d= -f2- | tr -d '\r')}"
     UI_PROXY_PORT="${DAGU_UI_PORT:-8082}"
     ```
  2. Header, old:
     ```
     # Env: None read directly -- every flag above is translated into either a container-start env var
     #   (FORCE_TOOLS_REFRESH, passed via `docker run -e`) or a Dagu param (passed via
     #   `dagu start ... -- key=value`).
     ```
     new:
     ```
     # Env: DAGU_UI_PORT (default: the repo-root .env value) -- host port of the Dagu UI proxy. Every
     #   flag above is translated into either a container-start env var (FORCE_TOOLS_REFRESH, passed
     #   via `docker run -e`) or a Dagu param (passed via `dagu start ... -- key=value`).
     ```
  3. Header, old: `#   UI, reachable at http://localhost:8082 through the ci-runner-dagu-proxy sidecar (ci-runner`
     new: `#   UI, reachable at http://localhost:<DAGU_UI_PORT> through the ci-runner-dagu-proxy sidecar (ci-runner`
- Exact change in `scripts/ci/dagu-rest-run-monitor.py`:
  1. Replace the line `DAGU_UI_PORT = os.environ.get("DAGU_UI_PORT", "8082")` with:
     ```python
     REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


     def env_file_value(key):
         # Returns one key's value from the repo-root .env, or None when the file or the key is absent.
         try:
             with open(os.path.join(REPO_ROOT, ".env"), encoding="utf-8") as env_file:
                 for line in env_file:
                     name, sep, value = line.strip().partition("=")
                     if sep and name == key:
                         return value
         except FileNotFoundError:
             pass
         return None


     DAGU_UI_PORT = os.environ.get("DAGU_UI_PORT") or env_file_value("DAGU_UI_PORT") or "8082"
     ```
  2. Header, old: `# Env: DAGU_UI_PORT (default 8082, the proxy sidecar's published port) -- may be exported directly`
     new: `# Env: DAGU_UI_PORT (default: the repo-root .env value, the proxy sidecar's published port) -- may be exported directly`
- Verification: `bash -n /app/scripts/ci/run.sh`; `python3 -m py_compile /app/scripts/ci/dagu-rest-run-monitor.py`;
  `cd /app && python3 -c "import importlib.util,sys; s=importlib.util.spec_from_file_location('m','scripts/ci/dagu-rest-run-monitor.py'); m=importlib.util.module_from_spec(s); s.loader.exec_module(m); print(m.DAGU_UI_PORT)"`
  prints `8082`. If importing the module starts polling, skip this check and rely on py_compile.
- Sources: https://12factor.net/config

### Step 4: SonarQube compose and scripts read `SONAR_PORT`

- Depends on: 1.
- Files: `/app/scripts/sonar/docker-compose.sonar.yml`, `/app/scripts/sonar/run.sh`,
  `/app/scripts/utils/ensure-sonar-token.sh`, `/app/scripts/sonar/sonar-project.properties`
- Executor: haiku
- Input: the four files as they are.
- Exact change in `docker-compose.sonar.yml`:
  1. Old: `# Description: Defines the local SonarQube server container (port 9099) that`
     new: `# Description: Defines the local SonarQube server container (host port SONAR_PORT from the repo-root .env) that`
  2. Old: `# Usage: docker compose -f scripts/sonar/docker-compose.sonar.yml up -d -- not invoked directly in`
     new: `# Usage: docker compose --project-directory . -f scripts/sonar/docker-compose.sonar.yml up -d (from the repo root) -- not invoked directly in`
  3. Old: `#   SONARQUBE_VERSION=<tag> docker compose -f scripts/sonar/docker-compose.sonar.yml up -d`
     new: `#   SONARQUBE_VERSION=<tag> docker compose --project-directory . -f scripts/sonar/docker-compose.sonar.yml up -d`
  4. Old: `# Input: None (self-contained service definition).`
     new: `# Input: the repo-root .env (SONAR_PORT), found through --project-directory; the top-level name keeps the project, and so its volumes, named sonar.`
  5. Old: `# Outputs: a running `sonarqube` container; dashboard at http://localhost:9099 once up.`
     new: `# Outputs: a running `sonarqube` container; dashboard at http://localhost:${SONAR_PORT} once up.`
  6. Directly above the line `services:`, insert:
     ```
     name: sonar

     ```
  7. Old: `      - "9099:9000"` new: `      - "${SONAR_PORT}:9000"`
- Exact change in `scripts/sonar/run.sh`:
  1. Replace `SONAR_URL="http://localhost:9099"` with:
     ```
     SONAR_PORT="${SONAR_PORT:-$(grep -E '^SONAR_PORT=' "$ROOT/.env" 2>/dev/null | cut -d= -f2- | tr -d '\r')}"
     SONAR_URL="http://localhost:${SONAR_PORT:-9099}"
     ```
  2. Old: `  docker compose -f "$COMPOSE_FILE" pull -q`
     new: `  docker compose --project-directory "$ROOT" -f "$COMPOSE_FILE" pull -q`
  3. In the scanner call, after the line `  -Dsonar.projectBaseDir=/tmp/sonar-src \` insert the line
     `  -Dsonar.host.url="$SONAR_URL" \`
  4. In the Python heredoc, old:
     `url = "http://localhost:9099/api/issues/search?projectKeys=advertisement&ps=500&resolved=false&s=SEVERITY&asc=false"`
     new:
     `url = os.environ["SONAR_URL"] + "/api/issues/search?projectKeys=advertisement&ps=500&resolved=false&s=SEVERITY&asc=false"`
  5. Old: `docker exec -e SONAR_TOKEN="$SONAR_TOKEN" "$SCANNER_CONTAINER" python3 /tmp/sonar-gen-report.py`
     new: `docker exec -e SONAR_TOKEN="$SONAR_TOKEN" -e SONAR_URL="$SONAR_URL" "$SCANNER_CONTAINER" python3 /tmp/sonar-gen-report.py`
  6. Header, old: `# Env: None.` (the one in this file's header block)
     new: `# Env: SONAR_PORT (default: the repo-root .env value) -- host port of the SonarQube server.`
  7. Header, old: `# Outputs: analysis uploaded to http://localhost:9099/dashboard?id=advertisement (anonymous`
     new: `# Outputs: analysis uploaded to http://localhost:<SONAR_PORT>/dashboard?id=advertisement (anonymous`
- Exact change in `scripts/utils/ensure-sonar-token.sh`:
  1. After the line `  local compose_file="$1" props_file="$2" sonar_url="$3"` insert:
     ```
       # The compose file reads SONAR_PORT from the repo-root .env, two levels above its own folder.
       local project_dir
       project_dir="$(cd "$(dirname "$compose_file")/../.." && pwd)"
     ```
  2. Replace every `docker compose -f "$compose_file"` (3 occurrences) with
     `docker compose --project-directory "$project_dir" -f "$compose_file"`.
  3. Header, old: `# Input: None -- paths passed as arguments.`
     new: `# Input: the repo-root .env (SONAR_PORT, read by the compose file); other paths passed as arguments.`
- Exact change in `sonar-project.properties`: delete the line `sonar.host.url=http://localhost:9099`.
  `run.sh` now passes the URL with `-Dsonar.host.url`.
- If D6 = `--env-file`: skip edits 4, 6 and the `name: sonar` insert in the compose file. Write
  `--env-file "$ROOT/.env"` (run.sh) or `--env-file "$project_dir/.env"` (helper) in place of
  `--project-directory ...`, and state `--env-file` in the header Usage lines.
- Verification: `bash -n` on `run.sh` and `ensure-sonar-token.sh`;
  `cd /app && docker compose --project-directory . -f scripts/sonar/docker-compose.sonar.yml config | head -3`
  starts with `name: sonar`, and the same output contains `published: "9099"`.
  `grep -rn '9099' /app/scripts/sonar /app/scripts/utils/ensure-sonar-token.sh` prints only the
  `${SONAR_PORT:-9099}` fallback and any DECISIONS.md/README.md history lines.
- Sources: https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/,
  https://docs.docker.com/compose/how-tos/project-name/, https://docs.docker.com/reference/cli/docker/compose/

### Step 5: sonar-analyst MCP launcher reads `SONAR_PORT`

- Depends on: 1, 4.
- Files: `/app/.claude/agents/sonar/ensure-token-and-launch.sh`, `/app/.claude/agents/sonar/sonar-analyst.md`
- Executor: haiku
- Input: the two files as they are.
- Exact change in `ensure-token-and-launch.sh`:
  1. Replace `SONAR_URL="${SONARQUBE_URL:-http://localhost:9099}"` with:
     ```
     SONAR_PORT="${SONAR_PORT:-$(grep -E '^SONAR_PORT=' "$ROOT/.env" 2>/dev/null | cut -d= -f2- | tr -d '\r')}"
     SONAR_URL="${SONARQUBE_URL:-http://localhost:${SONAR_PORT:-9099}}"
     ```
  2. Header, old: `# Env: SONARQUBE_URL, SONARQUBE_TOOLSETS -- set by the agent's own mcpServers env block, forwarded`
     new:
     ```
     # Env: SONARQUBE_URL (default: localhost on SONAR_PORT from the repo-root .env), SONARQUBE_TOOLSETS --
     #   set by the agent's own mcpServers env block when present, forwarded
     ```
  3. Old: `# container's published port at localhost:9099 -- same reasoning as the scanner container in`
     new: `# container's published port on localhost -- same reasoning as the scanner container in`
- Exact change in `sonar-analyst.md`: delete the frontmatter line `        SONARQUBE_URL: "http://localhost:9099"`.
  Leave the body line with the dashboard URL as it is (documentation, decision D5).
- Verification: `bash -n` on the launcher; the frontmatter `env:` block still holds
  `SONARQUBE_TOOLSETS` and is valid YAML (`python3 -c "import yaml,sys; yaml.safe_load(open('/app/.claude/agents/sonar/sonar-analyst.md').read().split('---')[1])"`
  if PyYAML is present, otherwise check the indentation by eye).
- Sources: https://12factor.net/config

### Step 6: generator writes `services` and renders the Services screen

- Depends on: 1, 4 (the sonar project key line stays in `sonar-project.properties`).
- Files: `/app/docs/architecture/scripts/generate-architecture-model.sh` (only this file)
- Executor: sonnet
- Input: the generator. Places to read first: the header (lines 1-27); `REPO_ROOT` (line 30);
  `json_escape()` (around line 96); the SonarQube block with `SONAR_URL="http://localhost:9099"` and
  `SONAR_PROPS` (around line 1168); the model emission block (`echo "  \"rootReadme\": ..."`, around
  line 1915); the CSS `.card` rules (around line 2112); `crumbLabelFor()`, `navigate()`,
  `backButtonHtml()`, `renderSystem()` (around lines 2300-2445); `sourceLink()` (around line 3051);
  `render()` (around line 4298); `renderAdrs()` as the model for a simple screen.
- Behavior to build (model side, bash):
  1. A helper that returns one key's value from `$REPO_ROOT/.env`: match `^KEY=`, take everything
     after the first `=`, strip `\r`. If the key is missing or the value is not all digits, the
     generator stops with a clear error on stderr naming the key. Read `.env` only, never the
     caller's environment, so the output is the same on the host and in ci-runner, where e2e port
     overrides are exported.
  2. `SONAR_URL` is built from that helper's `SONAR_PORT`. Nothing else in the SonarQube block
     changes.
  3. One static table in the generator, one line per service, holding: id, name, the `.env` port
     key, path, started-by command, condition (empty when none), description. Ports never appear
     as numbers. The SonarQube path is `/dashboard?id=<key>`, with `<key>` read from
     `sonar.projectKey=` in `SONAR_PROPS` (same grep/`\r` handling as the existing token read). Rows
     (D3 placeholder wording):

     | id | name | port key | path | startedBy | condition | description |
     |---|---|---|---|---|---|---|
     | app | Marketplace app | APP_PORT | `/` | `scripts/deploy-and-run.sh` | (none) | The Vaadin web application. |
     | swagger | Swagger UI | APP_PORT | `/swagger-ui/index.html` | `scripts/deploy-and-run.sh` | (none) | Interactive docs of the external REST API; the raw OpenAPI JSON is at /v3/api-docs. |
     | api-flows | API flow runner | APP_PORT | `/dev-tools/api-flows/` | `scripts/deploy-and-run.sh --dev-tools` | Only when the app was started with --dev-tools | Developer-only page that runs the REST API flows. |
     | sonarqube | SonarQube | SONAR_PORT | `/dashboard?id=<sonar.projectKey>` | `scripts/sonar.sh` | (none) | Static-analysis dashboard of the last scan. |
     | dagu | Dagu (local CI) | DAGU_UI_PORT | `/` | `scripts/ci.sh` | (none) | Run history, logs and live status of the local CI pipeline. |
     | seaweedfs | SeaweedFS master UI | SEAWEEDFS_CONSOLE_PORT | `/` | `scripts/deploy-and-run.sh` | (none) | Status page of the local S3-compatible photo storage. |

     If D1 = (b), add a row `openapi | OpenAPI JSON | APP_PORT | /v3/api-docs | scripts/deploy-and-run.sh | (none) | Machine-readable OpenAPI description of the REST API.`
     and drop the second sentence from the Swagger description.
  4. A `services_json` function emitting a JSON array, in table order, of
     `{"id", "name", "description", "url", "startedBy", "condition"}`. `url` is
     `http://localhost:<port><path>`, and `condition` is a string or `null`. Escape every string with
     `json_escape`. Emit it as a top-level model key, `"services": $(services_json),`, on the line
     before `"nodes": [`.
  5. Header: add the repo-root `.env` (host ports of the local web pages) to `# Input:`. `# Env:`
     stays `None.`.
- Behavior to build (page side, the HTML/JS heredoc):
  1. `renderSystem()`: a sixth card after Code Quality, same markup as its siblings
     (`card special-card`), `onclick="navigate({screen:'services'})"`, title `🔗 Services`,
     description built from `MODEL.services.length`, for example
     `${MODEL.services.length} local web pages: app, API docs, SonarQube, CI, storage`.
     Replace the three-line comment above `renderSystem()` (it says "just the 3 entry-point cards"
     and cites an ADR number) with one line that does neither, for example
     `// ── System screen: entry-point cards only; module browsing lives under Diagrams › Module Dependencies (see .claude/nav/adr-index.md). ──`.
  2. `crumbLabelFor()`: `services` returns `Services`. `render()`: `services` dispatches to a new
     `renderServices()`.
  3. `renderServices()`, modelled on `renderAdrs()`: `backButtonHtml()`, `<h2 class="screen-title">Services</h2>`,
     and a `screen-desc` saying these are the local web pages of the running stack, each opening in
     a new tab, and that a page answers only while the script that starts it is running. Then a
     `card-grid` with one `<a class="card" href="<url>" target="_blank">` per service, holding the
     name (`card-title`), the description, the URL in `<code>`, `Started by: <code>startedBy</code>`,
     and the condition line only when `condition` is not null (D2 = (a)). No nested `<a>` inside a
     card. Escape every value with `esc()`. End with
     `<div class="empty-hint">Ports: ${sourceLink(".env")}</div>`.
  4. CSS: one rule so an anchor card looks like the other cards:
     `a.card { display: block; color: inherit; text-decoration: none; }`, next to the existing
     `.card` rules.
- Constraints: no new files, dependencies or flags; no live status or fetch calls from the page; no
  change to any other screen; one-line comments only; keep the existing code style (template
  literals, `esc()`, `navigate()`).
- Verification: `bash -n` on the generator. Full regeneration happens in step 9.
- Sources: https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/rel/noopener,
  https://springdoc.org/, https://gethomepage.dev/configs/docker/ (rejected variant, for contrast)

### Step 7: screenshot script covers the Services screen

- Depends on: 6.
- Files: `/app/docs/architecture/scripts/screenshot-architecture-map.sh`
- Executor: haiku
- Input: the file as it is.
- Exact change:
  1. Old: `#   08-backlog}.png (gitignored -- ephemeral verification artifacts, not committed).`
     new: `#   08-backlog,09-services}.png (gitignored -- ephemeral verification artifacts, not committed).`
  2. Old: `  await shot(page, '01-system'); // must show only 3 cards -- no map, no domain-grouped module list`
     new: `  await shot(page, '01-system'); // entry-point cards only -- no map, no domain-grouped module list`
  3. After the block
     ```
       await page.locator('.special-card:has-text("Backlog")').click();
       await page.waitForTimeout(300);
       await shot(page, '08-backlog');
     ```
     insert:
     ```

       await goSystem(page);
       await page.locator('.special-card:has-text("Services")').click();
       await page.waitForTimeout(300);
       await shot(page, '09-services');
     ```
  4. Old: `SHOT_NAMES=(01-system 02-diagrams-list 03-diagram-module-deps 04-module-detail 04b-back-to-diagram-via-breadcrumb 05-diagram-spi 06-diagram-spi-zoomed 07-pipelines 08-backlog)`
     new: the same line with ` 09-services` added before the closing `)`.
- Verification: `bash -n` on the script. Running it happens in step 9.
- Sources: none beyond the project.

### Step 8: documentation

- Depends on: 4, 6.
- Files: `/app/docs/architecture/README.md`, `/app/.claude/rules/scripts.md`,
  `/app/.claude/skills/app-readme-standards/SKILL.md`
- Executor: haiku
- Input: the three files as they are.
- Exact change in `docs/architecture/README.md`:
  1. Old:
     ```
     2. **System** screen — entry points to Tooling & Pipelines, Backlog, Diagrams, ADRs, and Code
        Quality, plus the project README inline at the bottom.
     ```
     new:
     ```
     2. **System** screen — entry points to Tooling & Pipelines, Backlog, Diagrams, ADRs, Code
        Quality, and Services (one link per local web page), plus the project README inline at the
        bottom.
     ```
  2. Old: `- A running SonarQube server (`localhost:9099`) for code-quality metrics, and the last`
     new: `- A running SonarQube server (host port `SONAR_PORT` in the repo-root `.env`) for code-quality metrics, and the last`
  3. After the line `- `pom.xml` files for every module listed in the root `pom.xml`'s `<modules>` block.` insert:
     `- The repo-root `.env` for the host ports the Services screen links to.`
- Exact change in `.claude/rules/scripts.md`:
  1. Old (the code line under "### Start server manually (if needed)"):
     `docker compose -f scripts/sonar/docker-compose.sonar.yml up -d`
     new: `docker compose --project-directory . -f scripts/sonar/docker-compose.sonar.yml up -d`
  2. Old (line starting the variable list's last line): ``${S3_PORT}` from the repo-root `.env` — the single source of truth for these values, also read as``
     new: ``${S3_PORT}`/`${SEAWEEDFS_CONSOLE_PORT}` from the repo-root `.env` — the single source of truth for these values, also read as``
  3. After the line `not just this sandbox.` insert a blank line and:
     ```
     `scripts/sonar/docker-compose.sonar.yml` reads `${SONAR_PORT}` from the same `.env` the same way and
     pins its project name with a top-level `name: sonar`, so `--project-directory` keeps its volumes.
     ```
- Exact change in `.claude/skills/app-readme-standards/SKILL.md`, old:
  ```
  2. Read `scripts/deploy-and-run/docker-compose.db.yml`, `docker-compose.seaweedfs.yml`,
     `docker-compose.app.yml`, `scripts/sonar/docker-compose.sonar.yml`, and `scripts/ci/run.sh` for
     every service's current container name and port — feeds sections 2-3.
  ```
  new:
  ```
  2. Read the repo-root `.env` for the host ports it holds, and
     `scripts/deploy-and-run/docker-compose.db.yml`, `docker-compose.seaweedfs.yml`,
     `docker-compose.app.yml`, `scripts/sonar/docker-compose.sonar.yml`, and `scripts/ci/run.sh` for
     every service's current container name and any port not in `.env` — feeds sections 2-3.
  ```
- If D5 = (b): an added sonnet sub-step rewrites `INFRASTRUCTURE.md`'s startup table and Local
  Services table so each address names its `.env` key and points to the map's Services screen. It
  follows the app-readme-standards procedure and applies the same rule to the prose copies listed in
  `plan-human.md` decision 5. Under (a), `INFRASTRUCTURE.md` needs no edit: its numbers equal the
  `.env` values.
- Verification: `git diff --stat` shows only these three files;
  `grep -n 'SONAR_PORT' /app/docs/architecture/README.md /app/.claude/rules/scripts.md` finds the new lines.
- Sources: https://docs.docker.com/compose/how-tos/project-name/

### Step 9: regenerate the map and check it

- Depends on: 1-8.
- Files: generated: `/app/docs/architecture/data/architecture-model.json`,
  `/app/docs/architecture/architecture-map.html`, `/app/.claude/nav/adr-index.md` (regenerated by
  the generator, unchanged unless step 10 already ran).
- Executor: haiku (runs commands only, decides nothing)
- Input: the repo after steps 1-8.
- Exact change: run `bash /app/docs/architecture/architecture-doc.sh`. It regenerates, runs the
  reproducibility check, and takes screenshots. Then run the check below from `/app`. Use host
  `node` if present, otherwise
  `docker run --rm -v /app:/app -w /app node:22-alpine node -e '<same script>'`.
  ```
  node -e '
  const fs = require("fs");
  const env = {};
  for (const l of fs.readFileSync(".env", "utf8").split(/\r?\n/)) { const m = l.match(/^([A-Z0-9_]+)=(.*)$/); if (m) env[m[1]] = m[2]; }
  const s = JSON.parse(fs.readFileSync("docs/architecture/data/architecture-model.json", "utf8")).services;
  const ports = ["APP_PORT", "SEAWEEDFS_CONSOLE_PORT", "SONAR_PORT", "DAGU_UI_PORT"].map(k => env[k]);
  if (s.length !== 6) throw new Error("expected 6 services, got " + s.length);
  for (const x of s) { const p = new URL(x.url).port; if (!ports.includes(p)) throw new Error(x.name + ": port " + p + " is not a .env port"); }
  const html = fs.readFileSync("docs/architecture/architecture-map.html", "utf8");
  for (const t of ["navigate({screen:\x27services\x27})", "function renderServices"]) if (!html.includes(t)) throw new Error("map is missing " + t);
  console.log("services ok: " + s.map(x => x.url).join(" "));
  '
  ```
  (Expect 7 when D1 = (b).)
- Verification: `architecture-doc.sh` exits 0, and the freshness check inside it reports up to
  date. The node check prints `services ok:` with 6 URLs on ports 8081, 8081, 8081, 9099, 8082,
  9333. Open `docs/architecture/scripts/architecture-map-screenshots/09-services.png` (Read tool)
  and confirm 6 cards, the flow runner's condition line, and the `.env` source line;
  `01-system.png` shows 6 System cards. Then
  `bash /app/docs/architecture/scripts/check-architecture-model-freshness.sh` exits 0.
- Sources: none beyond the project.

### Step 10: decision records

- Depends on: 1-9, and decision D4.
- Files: `/app/scripts/DECISIONS.md` and/or `/app/docs/architecture/scripts/DECISIONS.md`,
  `/app/.claude/nav/adr-index.md`, the task file's `## Operational notes`
- Executor: opus, in the main thread only. `/record-decision` presents a draft and waits for the
  user, so it cannot run in a sub-agent.
- Input: scripts ADR-009 (`.env` as the home of DB/S3 values, fallback precedence) and
  docs/architecture/scripts ADR-003 (System page holds only entry-point cards), read through
  `.claude/nav/adr-index.md`.
- Change, per D4:
  - Ports. D4 ports (a): extend ADR-009's decision text so it also covers the service host ports
    (`APP_PORT`, `SEAWEEDFS_CONSOLE_PORT`, `SONAR_PORT`, `DAGU_UI_PORT`), the generator as a `.env`
    reader, and the sonar compose `name: sonar` plus `--project-directory`. Write it as the current
    decision, not as an appended "Amendment" section. D4 ports (b): `/record-decision scripts — Host
    ports of local web services live in the repo-root .env` and mark ADR-009
    `Superseded by ADR-NNN`.
  - System card. D4 card (a): `/record-decision docs/architecture/scripts — System page holds only
    entry-point cards` (count-free, keeps ADR-003's still-valid "module browsing under Diagrams"
    decision) and mark ADR-003 `Superseded by ADR-NNN`. (b): reword ADR-003's title and first
    Decision sentence to drop "3". (c): no change.
  - Then `bash .claude/nav/scripts/generate-adr-index.sh`, regenerate the map once more (step 9's
    command), and stamp `**Verified:** <today>` on ADR-009 and ADR-003 if their claims still hold.
- Verification: `grep -n 'ADR-009 (scripts)\|ADR-003 (docs/architecture/scripts)' /app/.claude/nav/adr-index.md`
  reflects the new title/status; the freshness check exits 0 again.
- Sources: none beyond the project.

### Step 11: verification of the whole

- Depends on: 1-10.
- Executor: haiku (runs scripts and reports results; on any failure it stops and reports the error
  lines). Follow `.claude/rules.md` "Scripts": background the wrapped scripts through
  `bash scripts/activity-monitor.sh -- <script>` and watch `tree.txt`.
- Checks, in order:
  1. Syntax: `bash -n` on every changed `.sh`;
     `python3 -m py_compile scripts/ci/dagu-rest-run-monitor.py`.
  2. Compose resolution (from `/app`):
     `docker compose --project-directory . -f scripts/sonar/docker-compose.sonar.yml config` shows
     `name: sonar` and `published: "9099"`;
     `docker compose --project-directory . -f scripts/deploy-and-run/docker-compose.seaweedfs.yml config`
     shows `published: "9333"`.
  3. SonarQube keeps its data:
     `docker compose --project-directory . -f scripts/sonar/docker-compose.sonar.yml up -d`, then
     `docker inspect sonarqube --format '{{index .Config.Labels "com.docker.compose.project"}}'`
     prints `sonar`, and `docker volume ls -q --filter name=sonar_sonarqube_data` prints the old
     volume. Then `curl -s http://localhost:9099/api/system/status` returns `"status":"UP"` (it may
     take a minute).
  4. Deploy: `bash scripts/activity-monitor.sh -- scripts/deploy-and-run.sh --dev-tools`. Then
     `curl -s -o /dev/null -w '%{http_code}\n'` against `http://localhost:8081/`,
     `http://localhost:8081/swagger-ui/index.html`, `http://localhost:8081/dev-tools/api-flows/`
     and `http://localhost:9333/`. All return 200, or a 3xx redirect to a page that does.
  5. Definition of Done:
     `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`
     passes. No Java changed, but `.env` is read by integration-tests' `SharedEnvConfig`.
  6. Optional, slower: `bash scripts/sonar.sh --no-gate` (the scanner reaches the server through
     `-Dsonar.host.url`, and the HTML report is written), and
     `bash scripts/ci.sh --rebuild --unit` followed by
     `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8082/` (the Dagu proxy republished from `.env`).
  7. Leftovers:
     `git grep -nE '"9099:|"9333:9333"|UI_PROXY_PORT=8082|localhost:9099/api|DAGU_UI_PORT", "8082"'`
     returns nothing. `git status --short` lists only the files named in steps 1-10 and the
     regenerated outputs.
- Sources: https://docs.docker.com/reference/cli/docker/compose/
