---
name: app-readme-standards
description: Conventions for the repo root's two markdown files -- README.md (marketing/git-visibility landing page) and INFRASTRUCTURE.md (technical infra overview). Sibling to module-readme-standards and infra-readme-standards, scoped to the repo root only.
allowed-tools: Read Edit Write
---

# App README Standards

Conventions for the repo root's own markdown files — `README.md` and `INFRASTRUCTURE.md` — as
distinct from `module-readme-standards` (a single module's own `README.md`) and
`infra-readme-standards` (a script-group directory's own `README.md`). No sibling `app-doc-standards`
exists: the repo root has no `.java`/`pom.xml`/Liquibase files of its own to govern.

## `README.md` — marketing/git-visibility, exempt from "one fact, one canonical home"

`README.md` is the page GitHub renders when anyone lands on this repository — its job is to be an
appealing pitch (what this project is, what it does, why it's interesting), not a strict technical
reference. It is a deliberate, explicit exception to `.claude/rules.md`'s "One fact, one canonical
home" rule: content here may legitimately restate a fact that also has a canonical home elsewhere
(e.g. an "Architectural Principles" section overlapping root `CLAUDE.md`'s "Architecture
Guidelines") when restating it serves the pitch — accuracy still matters, but avoiding restatement
does not override making the page compelling to a newcomer.

## `INFRASTRUCTURE.md` — technical infra overview, follows the standard discipline

`INFRASTRUCTURE.md` is the root-level technical counterpart — governed by the same "One fact, one
canonical home" / "Atomic unit first, then directory-level index" discipline as every other
technical document in this repo. It gives a reader landing at the repo root the infrastructure
landscape — without restating what `scripts/README.md`, `playwright/README.md`, or any other
directory's own `README.md` already states in full. Reference those files, don't restate their
content.

It is read and rendered live inside `docs/architecture/architecture-map.html`
(`MODEL.rootInfrastructure`, via `mdBlockToHtml()`) at its own existing spot — never a separate
screen/section of its own. That renderer already turns markdown links into clickable links and
` ```mermaid ` fenced code blocks into live-rendered diagrams — no additional wiring needed there
once the file's own content is correct.

### Structure — general to specific, top-down

1. **AI development environment** — the Claude Code dev container itself (name, image, network
   mode, mounts, how to bring it up) comes first: the highest-priority reader of this file is
   Claude itself, working inside that container.
2. **Application startup** — container names/ports/links for every way to start the app.
3. **Local services table** — one row per other locally-running service (database, storage,
   static analysis, CI), each with its local link and what it solves.
4. **Quickstart** — one command per service, to bring everything up from nothing.
5. **A `mermaid` topology diagram** — Claude container → scripts → services.
6. **Environment Variables** — its own section, unchanged by the structure above.

No standalone "Helper Scripts"/"AI-Assisted Development Workflow"/"Database Scripts" sections —
that content folds into sections 1-4 above, next to the service/command it actually describes,
never duplicated as a separate flat list.

The slash-command list is never restated here at all: `.claude/commands/*.md` already has one
canonical home for that, `architecture-map.html`'s own Tooling & Pipelines screen — section 1
above only references it, per "one fact, one canonical home."

## Steps — updating `INFRASTRUCTURE.md`

Follow these every time `INFRASTRUCTURE.md` is edited — re-read the real sources fresh each time
rather than trusting what the file currently says, since it has already drifted once (a stale
commands table missing two real commands). No generator script does this automatically; staying
current is this procedure, run by whoever (human or Claude) is editing the file.

1. Read `scripts/claude.bat` + `Dockerfile.ai` for the AI dev container's current name, image,
   network mode, and mounts — feeds section 1.
2. Read `scripts/deploy-and-run/docker-compose.db.yml`, `docker-compose.minio.yml`,
   `docker-compose.app.yml`, `scripts/sonar/docker-compose.sonar.yml`, and `scripts/ci/run.sh` for
   every service's current container name and port — feeds sections 2-3.
3. Do not enumerate `.claude/commands/*.md` here — section 1 only references
   `architecture-map.html`'s Tooling & Pipelines screen (see "one fact, one canonical home" above).
4. For every entry-point command shown (sections 1-4), check `scripts/` for a `.bat` sibling of
   the same name (`ls scripts/*.bat`) — if one exists, show both the `.sh`/bash invocation and the
   `.bat` one, never only the `.sh` side. A tool that is Windows-only (e.g. `scripts/claude.bat`,
   no `.sh` sibling) stays `.bat`-only.
5. Compare each of the 6 structure sections (per "Structure" above) against what steps 1-2 just
   found. A section that already matches reality is left unchanged — do not rewrite it just
   because the skill ran. Rewrite only the sections that are stale or missing.

No generator writes `INFRASTRUCTURE.md` — the procedure above (steps 1-5) is what keeps it
current.

## Where this gets invoked

Anyone (human or Claude) writing or editing root `README.md` or root `INFRASTRUCTURE.md` directly.
