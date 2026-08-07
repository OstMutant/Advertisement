# improvement-145: On-demand ADR full-text extraction tool — the "L3" token-efficiency gap

**Type:** improvement — new small tool, no UI change.
**Module:** `scripts/architecture/md-to-decisions-json.js`, `docs/ai/README.md`.
**Priority:** 🔴 highest (explicit user request — first to execute).
**When:** independent, no blockers.

## Problem

`scripts/architecture/DECISIONS.md` has a long-standing "Open goals" entry ("AI-layer L3
(Rule/Intent) artifact") identifying this exact gap, never implemented. Investigated directly
before filing this issue, not assumed:

- `docs/ai/adr-index.md` already exists and works well — a flat, mechanically generated list
  (id/module/status/title) so Claude never has to grep every `DECISIONS.md` blind. `docs/ai/
  context-loading.md` already prescribes "filter adr-index.md by module first" for the relevant
  task types.
- The gap is **after** that step: once Claude knows it needs, say, `ADR-042` and `ADR-057` from a
  specific module's `DECISIONS.md`, there is no cheap way to read just those two — only the whole
  file (via `Read`) or a manual grep-for-line-number-then-offset/limit dance. Real file sizes,
  confirmed by direct measurement, not estimated:
  ```
  marketplace-app/DECISIONS.md        3580 lines / 72 ADRs
  scripts/architecture/DECISIONS.md   1549 lines / 23 ADRs
  platform-commons/DECISIONS.md        804 lines / 27 ADRs
  ```
  Reading a 3580-line file to get 2 ADRs (~100-150 lines of actual relevant text) is a real,
  measurable waste, not a hypothetical one.

## Suggested fix

No new generated static file (would go stale, need its own freshness gate). Instead: extend the
already-existing `scripts/architecture/md-to-decisions-json.js` (it already parses `## ADR-NNN:
Title` blocks into `{id, title, status, body}` objects for the `--stdout` mode the generator
itself uses) with a new **on-demand extraction mode**:

```
node scripts/architecture/md-to-decisions-json.js --extract <module> <ADR-NNN>[,<ADR-NNN>...]
```

- Reads the real `<module>/DECISIONS.md` at call time (always fresh — no staleness possible, no
  freshness gate needed, unlike a generated file).
- Filters the already-parsed `.adrs` array down to just the requested id(s).
- Prints **raw markdown** (the ADR's own `## ADR-NNN: Title` heading + full body), not JSON — JSON
  would just be escaping overhead Claude has to mentally undo; the whole point is a lean read.
- Reuses 100% of the existing parsing logic in this file — zero duplicated markdown-parsing code.

## Not in scope

- No change to `adr-index.md`/`context-loading.md` — they already correctly point at this gap
  (context-loading.md's "filter adr-index.md by module first" guidance stays exactly as-is; this
  tool is the next step after that guidance, not a replacement for it).
- No new CI freshness gate — an on-demand extraction tool that reads the real file at call time
  cannot go stale by construction, unlike a generated snapshot.
- No change to the human-facing `architecture-map.html` ADR popup (`openAdrPopupForIntent`/
  `openAdrPopupForAdr`) — that already reads full ADR bodies embedded in `architecture-model.json`
  for a person clicking through the UI; this issue is specifically about Claude's own token cost
  when working in this repo, a different consumer with a different cost profile.

## Related

- `scripts/architecture/DECISIONS.md` "Open goals" — the "AI-layer L3 (Rule/Intent) artifact" entry
  this issue directly addresses (mark done there once implemented, per this project's own
  "Open goals" convention).
- `docs/ai/README.md` — document the new extraction mode there once built, in the existing
  file/why-it-exists/where-it-fits/when-to-consult/how-it-stays-fresh table format.

## Note (unrelated, temporary holding spot — relocate before closing this issue)

Execution-environment topology, established by direct inspection in an unrelated conversation,
parked here only because there was no better home yet:

- All bash tool calls run inside the `claude-dev` container (image `claude-j25-dev`,
  `docker inspect claude-dev` confirms `Id: a14fde8b6bf7...`).
- `claude-dev`'s `Config.Hostname` is `docker-desktop` — this is why `hostname` inside the shell
  prints `docker-desktop` rather than the container name or ID; it is not evidence of running on
  the Docker Desktop VM itself.
- `claude-dev`'s bind mounts: `D:\Ost\dev\Advertisement` → `/app` (the repo), `C:\Users\maxym\.m2`
  → `/root/.m2`, `C:\Users\maxym\.claude-config-ost.mutant.mil@gmail.com` → `/root/.claude`, and
  `//var/run/docker.sock` → `/var/run/docker.sock`.
- The mounted `docker.sock` is why `docker ps` from inside `claude-dev` lists sibling containers
  (`marketplace-app`, `advertisement-db`, `advertisement-minio`, `sonarqube`, `sonar-scanner`,
  `arch-map-shot`) — those are not nested inside `claude-dev`, they're managed as siblings through
  the same Docker daemon socket.
- Node.js (`v20.20.2`, `/usr/bin/node`) used by `scripts/architecture/md-to-decisions-json.js` is
  installed inside the `claude-j25-dev` image itself, not on the Windows host.

Needs a real home before this issue closes — candidates: a `reference`-type memory entry (matches
this repo's existing `reference_db_access.md`/`reference_playwright_ui_testing.md` pattern),
and/or a short section in `scripts/CLAUDE.md`.

## Note 2 (unrelated, temporary holding spot — relocate before closing this issue)

`architecture-map.html` (`scripts/architecture/generate-architecture-model.sh`) cleanup, requested
directly and executed in the same conversation:

1. **Removed duplicated ADR listings from the "Tooling & Pipelines" screen.**
   `renderScriptGroupSection(n)` used to render each script group's own ADR list
   (`n.intent`/`renderAdrList(n)`) inline on the Tooling & Pipelines page — duplicating what the
   dedicated "ADRs" card/screen (`renderAdrs()`, reading `MODEL.allAdrs`, itself sourced from
   `docs/ai/adr-index.md`) already shows, grouped by module, for every `SCRIPT_GROUP` dir with a
   real `DECISIONS.md` (`scripts`, `scripts/architecture`, `scripts/ci`, `scripts/sonar`,
   `playwright`). The ADR block was deleted from `renderScriptGroupSection`; the ADRs screen
   remains the single place these are shown.
2. **Moved the standalone "🐳 Docker" System-level card into a new "Docker" group inside "Tooling &
   Pipelines"**, structured the same way as the existing "AI Tooling" group heading:
   - Deleted the `<div class="card special-card" onclick="navigate({screen:'docker'})">...</div>`
     card block from `renderSystem()`'s card grid.
   - Deleted the `if (v.screen === "docker") return "Docker";` line from `crumbLabelFor()` and the
     `else if (view.screen === "docker") renderDocker();` dispatch line from `render()`.
   - Renamed `renderDocker()` to `renderDockerSection()`, dropped its own
     `backButtonHtml()`/`<h2 class="screen-title">Docker</h2>` (no longer a standalone screen), and
     call it from `renderPipelines()` under a new `<h3 class="group-heading">Docker</h3>` heading,
     after the existing "AI Tooling"/"Other Scripts" groups. Content itself (Dockerfiles table +
     docker-compose stacks table, sourced from `MODEL.dockerFiles`) is unchanged.
   - `MODEL.dockerFiles`/`docker_files_json()` (the underlying data source) untouched — only the
     screen it's rendered on changed.
3. Regenerated `docs/architecture/architecture-model.json` / `architecture-map.html` via
   `bash scripts/architecture/generate-architecture-model.sh` after the edit, and spot-checked the
   output for leftover `screen:'docker'`/`renderDocker(` references.

Also needs a real home before this issue closes (this is `architecture-map.html` structure, not
ADR-extraction-tool content). **Reconsidered:** not a new ADR — the System/Tooling & Pipelines
card layout is still actively being reshuffled in the same conversation (this Docker move, the
Runtime group, then "How this page is built" moving again right after), too dynamic to freeze as
an architectural decision yet. A regular backlog issue (once this whole thread of card-layout
changes settles) is the right home instead, not a memory entry like Note 1 above.

## Note 3 (unrelated, temporary holding spot — relocate before closing this issue)

Follow-up to Note 2's new "Docker" group: add a second new group, **"Runtime"**, to the same
"Tooling & Pipelines" screen — a place for concise, hand-authored operational-topology facts (how
Claude Code itself is launched, where compilation happens, which container Node.js lives in, bind
mounts, sibling containers) instead of letting that knowledge live only in a chat transcript or an
unrelated issue file (i.e., closing the gap Note 1 flagged, via a real file this time, not a
memory entry).

Decisions made (via `AskUserQuestion`, both confirmed by the user):
- New file: `docs/architecture/runtime-notes.md` — deliberately not named `flows.md`/anything
  reusing "flows", since `docs/ai/flows.md` already owns that word for an unrelated meaning
  ("situation → which command/skill handles it").
- Parsing: raw markdown, rendered via the already-existing client-side `mdBlockToHtml()` (same
  renderer ADR bodies use) — no new parser. Written in the same "**Label:**" bold-paragraph +
  bullet-list style ADR bodies already use (not `##` headings — `mdBlockToHtml()` has no heading
  support, only paragraph/list/table blocks).
- Group heading: **"Runtime"** (matches the file name, one word like the sibling "Docker" group).

Implementation plan:
1. Write `docs/architecture/runtime-notes.md` with the container-topology facts established
   earlier this same conversation (`claude-dev` container / `claude-j25-dev` image, its
   `hostname: docker-desktop` quirk, its real bind mounts, the mounted `docker.sock` and what
   sibling containers it drives, where `mvn`/`node` actually execute).
2. `scripts/architecture/generate-architecture-model.sh`: add a `runtime_notes_json()` bash
   function (reads the file, JSON-escapes via a `node -e 'JSON.stringify(...)'` one-liner — the
   existing `json_escape()` strips newlines, unsuitable for multi-paragraph content, same
   reasoning already documented above `FULL_DECISIONS_MODULES` for why ADR bodies go through Node
   instead) and emit it as a new top-level `"runtimeNotes"` MODEL field (`null` if the file is
   missing), next to the existing `"dockerFiles"` field.
3. Add `renderRuntimeSection()` (client-side) — feeds `MODEL.runtimeNotes` through the existing
   `mdBlockToHtml()`, wrapped in a `<section class="block">`, plus a `sourceLink()` back to the
   real file. Call it from `renderPipelines()` right after the "Docker" group:
   `<h3 class="group-heading">Runtime</h3>` + `renderRuntimeSection()`.
4. Regenerate `architecture-model.json`/`architecture-map.html` via
   `bash scripts/architecture/generate-architecture-model.sh`; spot-check the new "Runtime" group
   renders real content and the source link resolves.

User also floated (not yet decided/built): this same `runtime-notes.md` content might later be
copied or adapted into `docs/ai/` as an "operational notes" file for Claude's own context-loading
purposes — noted here for later discussion, out of scope for this pass.

Also needs a real home before this issue closes (same reconsideration as Note 2 — not a new ADR,
the Tooling & Pipelines/System card layout is still actively moving in this same conversation;
a regular backlog issue once it settles is the right home, paired with Note 2's content since
both describe the same restructuring thread).

Also folded into this same pass: `scripts/architecture/DECISIONS.md` ADR-022 (the original "How
this page is built" decision) got a dated **Amendment** paragraph noting the section's new
location (bottom of Tooling & Pipelines, not the System screen) — an update to an *existing* ADR
to keep it accurate, not a new ADR; `docs/ai/adr-index.md` regenerated in the same operation per
the standing rule. This one doesn't need relocating — amending an existing ADR to stay accurate
is real, permanent decisions-log maintenance, unlike Notes 1-3 above.
