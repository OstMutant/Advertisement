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

## Note 2 (unrelated, resolved in place — see "Status: Done" below)

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
4. **Follow-up dead-code cleanup**, found by auditing what item 1's removal orphaned: item 1
   deleted the only real caller of `renderAdrList()`/`openAdrPopupForIntent()`, leaving both with
   zero callers; `adrFileLink()` in turn had zero callers left once those two were gone. All three
   deleted, plus 4 stale comments elsewhere in the file that referenced them
   (`sourceLink`/`exportModuleMarkdown`/`spiFileLink`/`openAdrPopupForAdr`'s own header comments).
   `scripts/architecture/DECISIONS.md` ADR-006 (the ADR that introduced `adrFileLink()`) got a
   dated **Amendment** recording why and when it was removed, instead of leaving its Decision
   section describing code that no longer exists; `docs/ai/adr-index.md` regenerated in the same
   operation per the standing rule.
5. **Follow-up dead-*data* cleanup** — same root cause as item 4, one layer deeper: the bash side
   still computed and embedded a full `"intent"` array (`{id, title, file}` per ADR) for every
   `SCRIPT_GROUP` node, but nothing on the client reads `.intent` for a `SCRIPT_GROUP` node anymore
   after item 1 (the only other consumer, `exportModuleMarkdown()`, is only reachable from
   `renderModule()`, which is never invoked for `SCRIPT_GROUP` ids — confirmed via `renderAdrs()`'s
   own comment: "Only a real MODULE node has a Module-detail page to link to"). Measured ~7.8KB of
   now-pointless JSON per regeneration across the 5 real `SCRIPT_GROUP` `DECISIONS.md` owners.
   Removed the `intent_json`/`"intent": $intent_json,` line from the `SCRIPT_GROUP` node-building
   loop only — `MODULE` nodes keep `.intent` (still consumed by `exportModuleMarkdown()`, reachable
   there). Verified `.decisions` (the field the ADR popups on the "ADRs" screen actually read via
   `openAdrPopupForAdr()`) is a separate field, untouched — popups keep working identically.

**Status: Done** — not a new ADR (the reasoning stands: this was still actively being reshuffled
across Notes 2-4, too dynamic to freeze as an architectural decision), and not relocated to a
separate backlog issue either — resolved in place, tracked here alongside Notes 3-4 below since
all of it is one continuous restructuring thread and every step is finished and verified.

## Note 3 (unrelated, resolved in place — see "Status: Done" below)

Follow-up to Note 2's new "Docker" group: add a second new group, **"Runtime"**, to the same
"Tooling & Pipelines" screen — a place for concise, hand-authored operational-topology facts (how
Claude Code itself is launched, where compilation happens, which container Node.js lives in, bind
mounts, sibling containers) instead of letting that knowledge live only in a chat transcript or an
unrelated issue file — via a real file, not a memory entry (this is what an earlier, now-removed
note in this same issue had originally flagged as needing a home; `runtime-notes.md` turned out to
already be that home once written, so the earlier note was deleted rather than relocated).

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

**Status: Done** — same reasoning as Note 2, resolved in place, no relocation.

## Note 4 (unrelated, resolved in place — see "Status: Done" below)

Follow-up to Note 3: move the "How this page is built" section (script self-documentation table +
rendering-library blurb) from the System screen to the bottom of the "Tooling & Pipelines" screen,
right after the new "Runtime" group — requested directly, same conversation.

1. Deleted the whole `<section class="block"><h3>How this page is built</h3>...</section>` block
   (the `MODEL.architectureToolingSelfDocs`-driven table + the Cytoscape/Mermaid rendering blurb)
   from `renderSystem()`.
2. Re-added the identical block at the end of `renderPipelines()`, immediately after
   `renderRuntimeSection()` — content itself unchanged, only its screen location moved.
3. Regenerated `architecture-model.json`/`architecture-map.html`; confirmed via grep the section
   now renders only inside `renderPipelines()`, with zero trace left in `renderSystem()`.
4. `scripts/architecture/DECISIONS.md` ADR-022 (the ADR that originally placed this section on the
   System screen) got a dated **Amendment** recording the new location, instead of leaving its
   Decision section describing a screen the content no longer lives on; `docs/ai/adr-index.md`
   regenerated in the same operation per the standing rule.

**Status: Done** — same reasoning as Notes 2/3, resolved in place, no relocation. The ADR-022
amendment itself is separate, permanent decisions-log maintenance (not something that ever needed
relocating, unlike the rest of this note).
