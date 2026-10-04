# improvement-205: opencode-dev container — parallel Docker dev environment for the opencode CLI

**Type:** improvement — dev tooling, mirrors an existing pattern for a second CLI
**Module:** repo root (`Dockerfile.ai` renamed to `Dockerfile.claude`, new `Dockerfile.opencode`),
`scripts/claude.bat` (path update only), new `scripts/opencode.bat`, `INFRASTRUCTURE.md`; rename
ripple-references in `.claude/nav/flows.md`, `.claude/skills/app-readme-standards/SKILL.md`,
`scripts/collect-code.bat`
**Priority:** 🟡 Top — user-requested Top placement, 2026-10-02
**When:** independent, no blockers

## Current state

`claude-dev` is the only AI-developer Docker container in this repo: `Dockerfile.ai` (base
`eclipse-temurin:25-jdk-jammy` + Node.js 20 + maven/git/docker.io, `npm install -g
@anthropic-ai/claude-code`, `ENTRYPOINT ["claude"]`), started/reused via `scripts/claude.bat
<login> [--update] [--recreate]`, which mounts the project directory, a per-login
`%USERPROFILE%\.claude-config-<login>` auth folder, the host's `~/.m2`, and the Docker socket,
with `--network host`. Documented in `INFRASTRUCTURE.md`'s "AI Development Environment" table.

## Why change

The user wants to drive the same `/app` project with a second, open-source CLI agent
(`opencode`, npm package `opencode-ai`, command `opencode`, repo `sst/opencode` — the company
behind it renamed from SST to Anomaly in 2026 but the GitHub org is still `sst`; a separate,
unrelated older Go project also called `opencode` exists under the `opencode-ai` GitHub org and
is not this one) so they can use other model providers (e.g. Google AI Studio/Gemini, OpenRouter,
or any other listed on models.dev) for work they don't want to run through Claude. `opencode auth
login` handles provider credential setup interactively per models.dev's provider list.

opencode follows the XDG Base Directory spec, which splits its persisted state across **two**
separate directories, not one: data (incl. `auth.json`, the provider credentials) under
`$XDG_DATA_HOME/opencode` (default `~/.local/share/opencode`), and config (agents/commands/MCP
settings) under `$XDG_CONFIG_HOME/opencode` (default `~/.config/opencode`) — both overridable via
env var. This is unlike Claude's single `~/.claude` folder, and must be accounted for so a
`--recreate` doesn't silently drop one of the two.

## Expected benefit

A second, equally isolated container (`opencode-dev`) that can run alongside `claude-dev` against
the exact same mounted `/app` working tree (same code, same git history), with its own isolated
provider credentials — no manual one-off `docker run` needed, same reuse/`--recreate`/`--update`
ergonomics the user already has for `claude-dev`.

## Approach

Mirror `claude.bat`/`Dockerfile.ai` exactly, file-for-file, rather than inventing a new structure:

1. **Rename `Dockerfile.ai` → `Dockerfile.claude`** (explicit user request, done first so the new
   `Dockerfile.opencode` sits next to a correspondingly-named sibling, not an oddly-named `.ai`
   one). Update its own header's `Usage:` line (`docker build -f Dockerfile.ai ...` →
   `-f Dockerfile.claude`) and every other reference to the old filename:
   `scripts/claude.bat`'s `docker build -f Dockerfile.ai -t claude-j25-dev .` line,
   `INFRASTRUCTURE.md`'s `[Dockerfile.ai](Dockerfile.ai)` link, `.claude/nav/flows.md`,
   `.claude/skills/app-readme-standards/SKILL.md`, `scripts/collect-code.bat`.
2. **New `Dockerfile.opencode`** — same base image and apt/Node.js install block as
   `Dockerfile.claude`, `npm install -g opencode-ai` instead of `@anthropic-ai/claude-code`,
   `ENTRYPOINT ["opencode"]` instead of `["claude"]`. Node 20 satisfies opencode's own `>=18`
   requirement, no version bump needed. Same header-comment convention
   (Description/Usage/Uses/Env/Input/Outputs/Returns) as the file it mirrors.
3. **New `scripts/opencode.bat`** — copy of `claude.bat`'s structure: container name
   `opencode-dev`, image `opencode-j25-dev`, builds from `-f Dockerfile.opencode`, mounts
   `%CD%:/app` (same project), `%USERPROFILE%\.m2:/root/.m2`, Docker socket, `--network host`,
   `-it --rm`. Per-login state: **one** host folder
   (`%USERPROFILE%\.opencode-config-<login>`, created if missing, same as `claude.bat`'s own
   `CONFIG_DIR` step) mounted at `/root/.opencode-home`, plus two env vars pointing opencode's own
   two XDG directories at subpaths inside that one mount — avoids a two-separate-mounts split
   for what is otherwise a single per-login identity:
   ```bat
   -v "%CONFIG_DIR%:/root/.opencode-home" ^
   -e XDG_DATA_HOME=/root/.opencode-home/data ^
   -e XDG_CONFIG_HOME=/root/.opencode-home/config ^
   ```
   Same reuse-via-`docker exec`-unless-`--recreate` logic, same `--update` rebuild flag, as
   `claude.bat`.
4. **`INFRASTRUCTURE.md`** — add a second Container/Image/Mounts block for `opencode-dev` right
   after the existing `claude-dev` one, same table format, update the renamed `Dockerfile.claude`
   link in the existing block at the same time.
5. Provider/model setup itself (`opencode auth login`, choosing Google AI Studio/Gemini,
   OpenRouter, or any other models.dev-listed provider) is an interactive first-run step the user
   does themselves inside the running container — not something this task hardcodes.

**Verified, no gap:** Node ≥18 (image ships Node 20); `-it` present for the TTY opencode's
interactive auth/TUI needs; `--network host` already grants the outbound internet access
opencode's provider calls need, same mechanism Claude already relies on.

No design alternatives considered — this is a direct mirror of an existing, already-working
in-repo pattern, not a new design.

## Related

- `INFRASTRUCTURE.md`'s "AI Development Environment" section — existing `claude-dev` precedent.
- `Dockerfile.ai` / `scripts/claude.bat` — the files being mirrored (and partly renamed).
