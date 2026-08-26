# improvement-173: Infra housekeeping — skill/command README audit + SonarQube/Dagu MCP server integration

**Type:** improvement — tooling/infra, design pending
**Module:** `.claude/skills/README.md`, `.claude/commands/README.md`, candidate new `.mcp.json`
  (repo root), candidate new `.claude/commands/*.md`,
  `backlog/issues/improvement-160-certification-coverage-map.md` (D2-2/D2-3/D2-4/D2-5,
  D3-3/D3-8/D5-7 rows)
**Priority:** high (Top)
**When:** independent, no blockers

## Current state

`.claude/skills/README.md` and `.claude/commands/README.md` are hand-maintained indexes — each row
reproduces a skill's `SKILL.md` frontmatter `description:` (or a command's own summary) verbatim.
Nothing mechanically checks these stay in sync as skills/commands get added, renamed, or reworded
— the same drift risk `.claude/nav/adr-index.md`'s generation script was built to close for
`DECISIONS.md`, but here still fully manual.

`improvement-160-certification-coverage-map.md` (Domain 2, D2-2/D2-3/D2-4/D2-5) already researched
three concrete, unimplemented MCP-server integrations: SonarQube's official `sonarqube-mcp-server`
(against the local instance at `localhost:9099`), Dagu's built-in MCP endpoint (`/mcp`, against the
already-running `ci-runner` container proxied at `localhost:8082`), and project-scoped `.mcp.json`
as the right config location for both. No `.mcp.json` exists at the repo root today (confirmed
directly) — every interaction with SonarQube/Dagu goes through `scripts/sonar.sh`/`scripts/ci.sh`
parsing scan output or polling `watch-run.py`, never a direct tool call into either system's own
state. The same tracking file also has rows already marked `done`/`done (partial)` (D3-3, D3-8,
D5-7) from a prior session, never re-verified against current code since.

## Why change

Two same-shaped gaps: (1) the skill/command README indexes can silently drift from the real files
they summarize; (2) the coverage map's own MCP-server ideas give Claude direct, structured
tool-call access to SonarQube/Dagu state instead of parsing HTML/log output or polling — real,
already-researched value left unimplemented.

## Expected benefit

- Skill/command README indexes verified accurate (or fixed).
- Direct MCP tool-call access to SonarQube quality-gate/issue state and Dagu DAG run state,
  replacing HTML-report parsing / `watch-run.py` polling.
- Already-`done` coverage-map rows re-confirmed or corrected, keeping that tracking file
  trustworthy.

## Approach

1. **README audit — done (2026-08-26), no drift found:** `.claude/skills/README.md`'s 5 entries all
   match their `SKILL.md` frontmatter `description:` exactly. `.claude/commands/README.md` lists
   all 12 real commands (none missing/extra); 9/12 descriptions are verbatim matches
   (`autopilot`/`feature`/`ci`/`playwright`/`record-decision`/`run-all-tests`/`screenshots`/`sonar`/
   `sync-docs`), 3/12 are shortened-but-accurate summaries (`build-and-test`/`deploy-and-run`/
   `new-domain` — omit flag-level detail like `--archunit-metrics`/`$ARGUMENTS`, nothing false).
   No fix needed — unlike `INFRASTRUCTURE.md`, these two index files were not actually stale.
2. **Coverage-map re-verification — done (2026-08-26):** D5-7 fully confirmed, no changes. D3-3 and
   D3-8's core findings still hold but both cited now-deleted/renamed files (`doc-standards` →
   split into `module-doc-standards`/`infra-doc-standards`; `.claude/skills/deep-review` → deleted,
   replaced by the `deep-review-orchestrator` agent per `improvement-171`; D3-8 also had a
   skill-name typo, `infra-doc-standards` instead of `infra-readme-standards`) — citations
   corrected in `improvement-160-certification-coverage-map.md`, `done`/`done (partial)` statuses
   unchanged.
3. **SonarQube MCP server (D2-2) — done (2026-08-26):** `.mcp.json` added at the repo root
   (`sonarqube-mcp-server`, stdio via `npx -y sonarqube-mcp-server@latest`, real package/env vars
   confirmed via SonarSource's own docs: `SONARQUBE_URL`/`SONARQUBE_TOKEN`/`STORAGE_PATH`).
   `SONARQUBE_TOKEN` is `${SONARQUBE_TOKEN}` (env expansion, confirmed supported by Claude Code CLI
   specifically to keep secrets out of version control) — never hardcoded, since `run.sh`
   regenerates the real token automatically and a baked-in copy would both commit a secret and go
   stale. `scripts/sonar/README.md` documents exporting it from `sonar-project.properties`'s own
   value. `STORAGE_PATH` (`scripts/sonar/mcp-storage`) added to `.gitignore`. Not yet verified
   live (would need `SONARQUBE_TOKEN` exported + a Claude Code restart to pick up the new MCP
   server) — config written and JSON-validated, not exercised end to end.
   - **Scope clarification (2026-08-26):** MCP does not replace `/sonar`'s existing `Monitor`-based
     live progress tracking (`/tmp/sonar.log` tail, stuck/completion detection) — the MCP server
     talks to the already-running SonarQube server's own REST API, which has no visibility into a
     scan still in progress client-side (nothing to query until the scanner uploads its report at
     the end). The two are complementary, not alternatives: `Monitor` covers live progress during
     the run, MCP covers structured post-run data (quality gate status, issues list, metrics)
     instead of parsing the HTML report / hitting the dashboard URL by hand.
   - **Redesigned (2026-08-26) — session-wide `.mcp.json` replaced by an agent-scoped inline
     server:** a session-wide MCP server has two real problems for a token this volatile (SonarQube
     regenerates it whenever the container is recreated): (1) no way to restart just one MCP server
     without ending the whole Claude Code session (confirmed — `/mcp reconnect`/`claude mcp restart`
     are unimplemented feature requests, not real commands); (2) tool descriptions sit in the main
     conversation's context for the whole session whether used or not. Confirmed via
     [code.claude.com/docs/en/sub-agents](https://code.claude.com/docs/en/sub-agents): a subagent's
     own frontmatter can declare an inline `mcpServers` entry (same schema as `.mcp.json`) that
     "connects when the subagent starts... and disconnects when it finishes" — i.e. every dispatch
     is a fresh server launch, which is the closest real equivalent to "restart the MCP server" this
     CLI actually supports today. New plan: delete root `.mcp.json`; move the same server config
     into a new `.claude/agents/sonar/sonar-analyst.md` agent's own `mcpServers` frontmatter (queries
     quality-gate/issues/metrics, returns a structured report to whichever context dispatched it —
     same shape as `deep-review-orchestrator`). Also moving, in the same pass (agent organization,
     not part of the MCP redesign itself): the 3 existing review agents
     (`deep-review-orchestrator`/`solid-reviewer`/`dry-kiss-yagni-reviewer`) into
     `.claude/agents/review/` — Claude Code scans `.claude/agents/` recursively, identity comes from
     each file's own `name` frontmatter field, not its path, so this is a pure reorganization, no
     behavior change. Each new subfolder (`review/`, `sonar/`) gets its own `README.md`; the
     top-level `.claude/agents/README.md` updated for the new structure. `docs/architecture/scripts/generate-architecture-model.sh`
     re-run afterward to pick up the new folder structure into `architecture-map.html`'s cards.
   - **Token freshness (2026-08-26):** agent-scoping alone does not fix token staleness — the
     `${SONARQUBE_TOKEN}` env var is still fixed to whatever value existed when the Claude Code
     process itself started (`scripts/claude.bat`'s `docker run`), so an `export` in a different
     shell after that has no effect on an already-running session. Real fix: since an inline MCP
     server's `command` re-runs fresh on every agent dispatch (not once per session), point it at a
     new wrapper script instead of `npx` directly — the wrapper ensures the SonarQube container is
     up and the token is valid (regenerating via admin/admin if not) immediately before launching
     the real MCP process, so the token is guaranteed fresh on every single dispatch, not just at
     session start. This duplicates `scripts/sonar/run.sh`'s own existing "ensure server
     up"/"validate or regenerate token" logic if written standalone — instead, extract that logic
     into a shared `scripts/utils/ensure-sonar-token.sh` function, have `scripts/sonar/run.sh` call
     it instead of its own inline copy, and have the new wrapper script call the same function —
     one fact, one home, per this project's own DRY standard.
   - **Final design, implementing now (2026-08-26):** `scripts/utils/ensure-sonar-token.sh`
     (`ensure_sonar_token(compose_file, props_file, sonar_url)`, extracted from `scripts/sonar/run.sh`'s
     own server-up-wait + token-validate-or-regenerate block) is sourced by both
     `scripts/sonar/run.sh` (replacing its own inline copy) and a new
     `.claude/agents/sonar/ensure-token-and-launch.sh` wrapper. The agent's `mcpServers.command`
     points at this wrapper instead of `npx` directly — it calls `ensure_sonar_token`, reads the
     now-guaranteed-fresh token, exports it, then `exec npx -y sonarqube-mcp-server@latest`. Net
     effect: no manual `SONARQUBE_TOKEN` export needed at all, ever — `scripts/sonar/README.md`'s
     export-it-yourself instruction is now obsolete and gets removed.
   - **Verified live (2026-08-26) — real bugs found and fixed along the way:**
     1. First live dispatch returned zero MCP tools (0 tool_uses) — root cause: Claude Code only
        discovers new `.claude/agents/*.md` files and re-reads `mcpServers` frontmatter changes at
        session start, not mid-session. Required a full Claude Code restart (confirmed no lighter
        `/mcp reconnect` exists, per the research above) — the same limitation the agent-scoping
        redesign was meant to reduce the *frequency* of, not eliminate entirely for config changes.
     2. After restart, still zero tools. Manually running the wrapper script directly (bypassing
        Claude Code entirely) surfaced the real cause: `unknown shorthand flag: 'f' in -f` —
        `ensure_sonar_token`'s own `docker compose -f ...` call needs `ensure_docker_compose`
        (from `scripts/utils/ensure-docker-plugins.sh`) called first, which `scripts/sonar/run.sh`
        already does but the new wrapper never did. Fixed by adding the same
        `source .../ensure-docker-plugins.sh; ensure_docker_compose` call to the wrapper.
     3. Wrapper then progressed further but the actual `sonarqube-mcp-server` npm package printed
        `npm warn deprecated ... Package no longer supported`. Verified directly against the real
        GitHub README (`SonarSource/sonarqube-mcp-server`): **the npm package doesn't exist in the
        current official distribution at all** — the real, current method is a Docker image,
        `sonarsource/sonarqube-mcp`, run with `--network host` (same reasoning as the scanner
        container in `scripts/sonar/run.sh` — the MCP server runs in its own container and must
        reach the SonarQube server's published port at `localhost:9099`). Rewrote the wrapper's
        final `exec` from `npx -y sonarqube-mcp-server@latest` to `docker run --init -i --rm
        --network host ... sonarsource/sonarqube-mcp`; removed `STORAGE_PATH` (npm-specific, not
        used by the Docker image) from the agent's env block and its now-unneeded `.gitignore`
        entry.
     4. Manually ran the corrected wrapper directly — SonarQube ready, image pulled, MCP server
        started cleanly ("SonarQube MCP Server - Starting backend service", no errors). Required
        one more Claude Code restart (same mid-session-config-change limitation as step 1) before
        the agent could actually see the new tools.
     5. **Final dispatch succeeded** (2 real tool calls): returned actual `advertisement` project
        data — quality gate FAILED (3 new-code violations, the only failing condition), 15,766
        LOC, 0 bugs/vulnerabilities, 3 code smells, 1.5% duplication, A/A/A ratings, and a real
        (and correctly explained, not fabricated) 0.0% coverage reading. The whole chain — agent →
        wrapper script → Docker-based MCP server → real SonarQube data — is confirmed working end
        to end, not just configured.
4. **Dagu MCP server (D2-3) — implemented (2026-08-26), not yet verified live:** simpler than
   SonarQube — no wrapper script needed at all. Verified directly (`docs.dagu.sh/mcp/`): Dagu's
   MCP server is a plain Streamable HTTP endpoint (`http://localhost:18080/mcp`) on Dagu's own
   already-running web server, not a separate process — nothing for Claude Code to launch, so an
   HTTP-type `mcpServers` entry (`type: http`, just a `url`) is enough. No auth token either:
   `ci-runner` runs with `DAGU_AUTH_MODE=none` (`scripts/ci/Dockerfile`). Also corrected the
   original D2-3 idea's own assumption of needing the `localhost:8082` proxy — that proxy exists
   only so a real browser outside any Docker network can reach the Dagu UI; `ci-runner` and
   `claude-dev` both run `--network host`, so `18080` is directly reachable container-to-container
   without it. Exposes exactly 3 tools (`dagu_read`/`dagu_change`/`dagu_execute`, confirmed via
   the real docs) — already within the 4-5-tools-per-agent range, no `SONARQUBE_TOOLSETS`-style
   scoping needed. New `.claude/agents/dagu/dagu-analyst.md` is read-only by default (`dagu_read`
   only; `dagu_change`/`dagu_execute` require an explicit ask). Registered in `.claude/agents/README.md`
   and `.claude/nav/flows.md`'s subagents table.
   - **Verified live (2026-08-26) — a real, unfixable-by-us timing limitation found:** after a
     restart, the agent was recognized but had no `ci-runner` to query (never started this
     session). Gave it `tools: Read, Bash` plus body instructions to check `docker ps` and
     self-start via `bash scripts/ci.sh` if needed — confirmed the self-start itself works
     (`ci-runner` came up correctly, triggering a real DAG run as `scripts/ci.sh`'s own documented
     side effect). But confirmed directly: MCP server connections are established before an
     agent's own instructions run, so starting `ci-runner` mid-dispatch does not make the `dagu`
     MCP tools available *within that same dispatch* — a Claude Code characteristic, not something
     fixable from an agent definition. `dagu-analyst.md` now falls back to Dagu's own REST API
     directly (the same `/api/v1/dag-runs/<name>/<run-id>` endpoint `scripts/ci/dagu/pipeline-metrics.py`
     already uses) when this happens, rather than failing outright — confirmed working: the test
     dispatch returned real, non-fabricated run data (run `034DuHP1R8tv77rsOIAv8k`, status RUNNING,
     `build` step in progress) via this fallback. The real `dagu_read`/`dagu_change`/`dagu_execute`
     MCP tools themselves remain unverified live (would need `ci-runner` already running *before* a
     dispatch, untested so far this session) — the REST fallback path is what's actually confirmed
     working end to end.
   - **Dagu version bump tried (2.14.0 → 2.15.3), confirmed NOT the cause (2026-08-26):** on the
     lead that Dagu itself reported an available upgrade, bumped `ARG DAGU_VERSION` in
     `scripts/ci/Dockerfile`, rebuilt via `bash scripts/ci.sh --refresh-tools` (forces the cached
     `ci-tools-cache` volume to re-download the new binary), confirmed `ci-runner` came up on
     2.15.3 and had already been running for several minutes before the next `dagu-analyst`
     dispatch — the exact precondition the previous test lacked. Dispatched fresh: `dagu_read`/
     `dagu_change`/`dagu_execute` were still absent from the agent's toolset (only `Read`/`Bash`
     available), same as on 2.14.0. Confirms the connection failure is unrelated to the Dagu server
     version — a genuine Claude-Code-client-side limitation with inline HTTP-type `mcpServers` in
     agent frontmatter, not something fixable from this repo's side. REST-API fallback remains the
     documented, working path; no further version-bump attempts planned.
   - **`/ci --metrics` added (2026-08-26), same shape as `/sonar --metrics`:** persists a
     post-run `dagu-analyst` report to `scripts/ci/reports/dagu-metrics.md`. Also fixed a
     pre-existing, unrelated staleness found while editing the same file: `/ci`'s own steps still
     described the retired `progress.txt`-polling mechanism (`improvement-153` replaced it with
     Dagu's UI + `scripts/ci/watch-run.py` months ago) — rewritten to match the real current
     mechanism.
5. **New commands, if warranted — `/review` added (2026-08-26):** a thin wrapper over the
   `deep-review-orchestrator` agent (`Agent({subagent_type: "deep-review-orchestrator", prompt:
   "$ARGUMENTS"})`) — pure convenience, no new capability, per the user's own "would be cooler"
   request. Registered in `.claude/commands/README.md`, root `CLAUDE.md`'s command list, and both
   `.claude/nav/flows.md` tables (commands + subagents, the latter's `deep-review-orchestrator` row
   updated since it's no longer `Agent`-tool-only). `bash .claude/nav/scripts/check-flows-completeness.sh`
   confirms full coverage — also caught and fixed a pre-existing gap unrelated to today's work:
   `app-readme-standards` never had a `flows.md` row at all since it was first created.
   `sonar-analyst` deliberately did **not** get its own slash command — it's dispatched with a
   natural-language question as its prompt, which doesn't map cleanly onto a fixed `Usage:` line
   the way `deep-review-orchestrator`'s scope syntax does. Instead, wired into the existing
   `/sonar` command: step 6 always dispatches `sonar-analyst` after a scan to check for real
   BUG/CRITICAL/BLOCKER issues (a passing quality gate doesn't guarantee none exist, depending on
   which conditions the gate checks) and reports the answer in chat; a new `--metrics` flag
   additionally persists `sonar-analyst`'s full structured report (quality gate + summary metrics)
   to `scripts/sonar/report/metrics.md` — only meaningful right after a fresh scan. Considered and
   rejected: a separate `/sonar-metrics` command (redundant given metrics only make sense
   immediately after a scan `/sonar` already runs) and extending `/sonar` unconditionally without
   the bug-check step (would leave "gate passed" looking like "nothing wrong" when the two aren't
   the same claim).
6. **`INFRASTRUCTURE.md` restructure (`app-readme-standards` skill), plan agreed 2026-08-26:**
   - Root cause: `INFRASTRUCTURE.md` is hand-maintained prose and already drifted (its commands
     table is missing `/new-domain` and `/screenshots`, both real). It is read and rendered live
     at its existing spot in `docs/architecture/architecture-map.html`
     (`MODEL.rootInfrastructure`, `mdBlockToHtml()`) — confirmed this renderer already turns
     markdown links into clickable links and ` ```mermaid ` fenced blocks into live diagrams
     (`mermaid.run()`). **No new screen/section in `architecture-map.html`** — same file, same
     existing display spot, content restructured and kept fresh at the source.
   - New content order (top-down, general → detail, per the user's own stated convention): (a) AI
     development environment first — the `claude-dev`/`claude-j25-dev` container itself: how to
     bring it up (`scripts/claude.bat`), what it mounts, container/image names, network mode; (b)
     application startup — container names/ports/links for both the `deploy-and-run.sh` path and
     the IDE dev-mode path; (c) a table of every other local service (PostgreSQL
     `advertisement-db`, MinIO `advertisement-minio`, SonarQube `sonarqube`, Dagu
     `ci-runner`/`ci-runner-dagu-proxy`) with its local link and what it solves; (d) a short
     "start everything" quickstart block (one command per service); (e) a `mermaid` topology
     diagram (Claude container → scripts → services). The current `Helper Scripts`/
     `AI-Assisted Development Workflow`/`Database Scripts` sections are removed as standalone
     blocks — their content folds into (a)-(d) above, not duplicated separately.
     `Environment Variables` stays as its own section unchanged.
   - Dynamic parts (the services table, the command list) must be produced by a new function in
     `docs/architecture/scripts/generate-architecture-model.sh` that writes them directly into
     `INFRASTRUCTURE.md` on disk — reusing the existing `command_first_line()` (already parses
     every `.claude/commands/*.md`'s live description) plus new docker-compose-file parsing for
     container names/ports — not hand-typed again, so it cannot silently drift a second time.
   - Sequencing (user's explicit order): first update `app-readme-standards/SKILL.md` to describe
     this exact structure/generation convention, then implement the generator function, then let
     it produce `INFRASTRUCTURE.md`'s actual content, then record the generator change as an ADR
     in `docs/architecture/scripts/DECISIONS.md`, then regenerate the full map and verify visually
     that `INFRASTRUCTURE.md` still renders correctly (links clickable, diagram drawn) at its
     existing spot.
   - Also add to `app-readme-standards/SKILL.md`: editing `README.md`/`INFRASTRUCTURE.md` requires
     regenerating `architecture-map.html` in the same change (same shape as `.claude/rules.md`'s
     existing "any `DECISIONS.md` edit regenerates the ADR index" rule) — otherwise the map shows
     stale content since it renders these files live from whatever was on disk at last generation.
   - Adjacent gap noted, not yet fixed: root `CLAUDE.md`'s "Documentation Standards" section lists
     `module-doc-standards`/`module-readme-standards` but never mentions `app-readme-standards` —
     without that pointer, nothing tells an editor to consult this skill before touching
     `README.md`/`INFRASTRUCTURE.md` in the first place. Worth adding in the same pass.

   **Implemented (2026-08-26), final shape differs from the plan above:** the "generator function
   inside `generate-architecture-model.sh`" idea was explicitly rejected mid-implementation (would
   couple an unrelated generator to this file, and a real markdown-comment-marker approach for
   partial in-place regeneration isn't feasible against this project's own hand-rolled
   `mdBlockToHtml()` parser — a literal `<!-- marker -->` renders as visible escaped text, not a
   comment). Actual mechanism: `app-readme-standards/SKILL.md` gained a `## Steps` section (read
   `scripts/claude.bat`/`Dockerfile.ai`/every relevant `docker-compose*.yml`/`scripts/ci/run.sh`
   fresh each time, plus a `.bat`-sibling-pairing step and a "link every real file" step) — a
   procedure a human or Claude runs by hand, not an automated generator; a later request added a
   "validate first, only rewrite stale sections" mode on top. The "link real files" rule ended up
   general — moved to `.claude/rules.md`'s "Documentation Standards" section (covers
   `app-readme-standards` and `infra-readme-standards` both) instead of living in one skill only,
   after almost being written into the wrong skill first (`infra-readme-standards` briefly got it
   in error, then correctly). `INFRASTRUCTURE.md` itself was rewritten by hand following the new
   procedure (AI dev container card first, app startup, services table + Mermaid topology diagram,
   `.sh`/`.bat` quickstart table, Environment Variables unchanged) and re-verified/linked; the same
   link rule was then applied across all other `infra-readme-standards`-governed READMEs
   (`scripts/README.md` + 5 script-group READMEs + `playwright/README.md`) as a mechanical pass —
   not the skill's own heavier "full Flow-diagram regeneration + independent review agent" mandate,
   which was explicitly deferred, not done. `docs/architecture/scripts/generate-architecture-model.sh`
   was run afterward to pick up all of the above into `architecture-map.html` (55 nodes, no
   warnings). The "regenerate the map after editing README/INFRASTRUCTURE.md" rule and the
   `app-readme-standards`-in-`CLAUDE.md`-pointer gap noted above were both explicitly reverted/not
   done per direct user instruction mid-session — left as real, still-open gaps, not silently
   dropped.

Each step independently shippable — no requirement to do all in one pass.

## Post-close live verification (2026-08-26)

Live end-to-end test of `/sonar --metrics` in progress (real scan via `scripts/sonar.sh`, then the
always-on bug check + `--metrics`' persisted `scripts/sonar/report/metrics.md`, per `/sonar`'s own
steps 6-7). `/ci --metrics` planned as the next live test once this one finishes, to verify the
same pattern against `dagu-analyst`.

## Related

- `backlog/issues/improvement-160-certification-coverage-map.md` — D2-2/D2-3/D2-4/D2-5 (source
  research), D3-3/D3-8/D5-7 (rows to re-verify).
- `.claude/nav/adr-index.md` / `.claude/nav/scripts/generate-adr-index.sh` — precedent for the
  README-audit half.
