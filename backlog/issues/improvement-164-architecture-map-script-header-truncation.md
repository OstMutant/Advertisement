# improvement-164: architecture-map.html script-header display is truncated and loses formatting

**Type:** bug
**Module:** `docs/architecture/scripts/generate-architecture-model.sh`
**Priority:** medium
**When:** independent, no blockers

## Problem

`architecture-map.html`'s script-header display (`script_headers_json()`,
`docs/architecture/scripts/generate-architecture-model.sh` ~line 1104) has two independent, confirmed
root causes:

1. **Truncation** (line 1123): `lines = fh.readlines()[:20]` reads only a file's first 20 lines
   before parsing Description/Usage/Uses/Env/Input/Outputs/Returns out of them. Confirmed real:
   `scripts/deploy-and-run/run.sh`'s own header spans 47 lines — everything past line 20 (part of
   `Env`, all of `Input`/`Outputs`/`Returns`) is never even read, regardless of what the
   field-parsing logic below it does.
2. **Formatting loss** (line 1166 + CSS): a continuation line is joined onto its field with a
   single space (`fields[current] += ' ' + l.strip()`), collapsing the source file's real
   multi-line/per-flag layout into one run-on sentence. Even if line breaks were preserved here,
   `.header-entry-field` (~line 1897) has no `white-space: pre-wrap` — a browser collapses
   whitespace/newlines by default, so the HTML would still render as one line either way.

## Suggested fix

**Truncation:** not a delimiter-driven read (the closing `# ────...────` marker
`infra-doc-standards/SKILL.md` defines is not present in every file's header, so parsing "read
until that exact line" would silently break on any file without it). The *existing*
field-terminating logic a few lines below (`elif current and (l.strip() == '' or
re.match(r'^[─-]+$', l.strip())): break`) already stops correctly on either a blank line **or** a
dash-delimiter line — it already handles both "has the delimiter" and "just ends at a blank line"
shapes.

Confirmed no artificial cap is needed at all, not even a raised one: `fh.readlines()` already
reads the entire file into memory before any slice is applied, so a cap saves no memory — it only
limits how many lines the first (comment-stripping) loop examines, and that loop already has its
own natural stop (`else: break` on the first non-comment-prefixed line). Combined with the
field-parsing loop's existing blank-line/delimiter stop, every header shape
`infra-doc-standards/SKILL.md` describes (with or without the closing delimiter pair) already
terminates correctly with no line-count cap at all. Fix: drop the slice entirely —
`lines = fh.readlines()[:20]` → `lines = fh.readlines()`.

**Formatting loss:**
1. Line 1166: join continuation lines with `\n` instead of `' '`, preserving the source file's real
   per-line layout in the extracted field value.
2. `.header-entry-field` CSS (~line 1897): add `white-space: pre-wrap` so those preserved line
   breaks actually render in the browser instead of collapsing back to one line.

**Status:** implemented and user-verified — 2026-08-21. All three edits applied
(`docs/architecture/scripts/generate-architecture-model.sh`: `readlines()` slice dropped entirely,
`\n`-joined continuations, `white-space: pre-wrap` added). Confirmed both at the data level (a real
regeneration shows `scripts/deploy-and-run/run.sh`'s full `env`/`usage`/`outputs` content, no
longer cut off at line 20, with real `\n` line breaks preserved) and visually, by the user, on the
rendered `architecture-map.html` page.

## Preliminary fix — architecture-doc.sh tar-pipe permission errors + verbose logging

Distinct bug found while starting work on this issue, fixed first since it affects every run of
the same generation pipeline: `bash docs/architecture/architecture-doc.sh` prints two `tar:
Cannot open: Permission denied` lines (for `architecture-map.html` and
`docs/architecture/data/architecture-model.json`) during the source-upload step, plus
`tar: Exiting with failure status due to previous errors`. Not fatal — generation still succeeds,
since `generate-architecture-model.sh` never reads either file as input, only writes them — but
noisy and confusing.

**Root cause:** both files were left on disk with mode `600` (owner-only read), unlike every
sibling generated file in the same directory (`644`) — confirmed directly (`stat -c '%a %U:%G %n'`
on both). A `tar -c` run as a normal (non-root) user cannot open a `600` file owned by a different
UID, which is exactly the reported symptom. Also confirmed: `architecture-doc.sh` never checks
the exit status of the upload tar-pipe at all (`set +e` before it, no `$?` check after) — a
separate, unrelated gap: a genuinely broken upload would currently fail silently too.

**Fix** (`docs/architecture/architecture-doc.sh`):
1. Exclude the two generator-output files from the source-upload tar (lines 99-101) — they're
   pure outputs the container regenerates anyway, never inputs, so there's no reason to upload
   them (or ever try to open them) in the first place.
2. After each `docker cp` pulls a result back out (lines 107-112), `chmod 644` it on the host —
   makes the fix robust regardless of what mode the file had inside the container, not just a
   workaround for this one observed cause.
3. Add `echo` progress lines bracketing every phase, so a run's console output states which step
   is currently executing: parsed-options summary right after arg parsing; image
   up-to-date/skipped case (the one branch with no existing echo); removing leftover container;
   starting fresh container; uploading source; upload complete / running generation; generation
   exit code; copying results back; permissions normalized; removing container; running the
   screenshot pass.

**Status:** implemented and verified — 2026-08-21 (`docs/architecture/architecture-doc.sh`:
tar-pipe exclude for the two output files, `chmod 644` after each `docker cp`, and progress
echoes bracketing every phase). Verified with a real `bash docs/architecture/architecture-doc.sh
--no-check --no-screenshot` run: completed exit 0, no `Permission denied`, both output files
confirmed `644` on disk afterward (previously `600`).

## Follow-up found during user's own visual check — scripts/logs/ shows up as a Tooling & Pipelines node

`scripts/logs/` (per-script runtime log directory, e.g. `scripts/logs/build-and-test/`,
`scripts/logs/playwright/`, `scripts/logs/sonar/` — added by `improvement-163` to separate raw
process logs from structured `reports/`) is gitignored (`.gitignore` line 47, same
`/scripts/*/reports/`-style entry as every other generated-output directory) but is **not** in
`generate-architecture-model.sh`'s own `SCRIPT_TREE_EXCLUDE_DIRS` (line 123:
`SCRIPT_TREE_EXCLUDE_DIRS=(reports pw-report report node_modules)`) — the list that keeps
generated/report output out of the System › Tooling & Pipelines › Scripts tree. `reports`/
`pw-report`/`report` are already excluded for exactly this reason; `logs` is the same shape
(dynamic, gitignored, no real script files with headers inside it) and needs the same treatment.

**Fix:** add `logs` to `SCRIPT_TREE_EXCLUDE_DIRS` at line 123:
`SCRIPT_TREE_EXCLUDE_DIRS=(reports pw-report report logs node_modules)`.

**Status:** implemented and user-verified — 2026-08-21. Regenerated via a real
`architecture-doc.sh` run: node count dropped 46 → 41 (the removed `scripts/logs/*` tree entries),
and `architecture-model.json` no longer contains any `scripts/logs` tree-node path (the two
remaining `scripts/logs` mentions are plain prose inside unrelated headers' `Outputs`/`Usage`
field text, not nodes). Confirmed visually by the user on the rendered page.

## Follow-up requested by user — prune System › Tooling & Pipelines down to just the cards

User confirmed the System › Tooling & Pipelines › Scripts sub-view looks correct, then asked
(2026-08-21): on the parent System › Tooling & Pipelines screen itself (`renderPipelines()`,
`docs/architecture/scripts/generate-architecture-model.sh` ~line 2709, the view before drilling
into any card), remove everything except the cards.

Current content of that screen, read from the code (not yet changed):
1. `<h2>` title "Tooling & Pipelines" + one `screen-desc` paragraph.
2. `card-grid` — the 3 cards themselves (`AI Tooling`, `Build architecture page`, `Scripts`) via
   `PIPELINE_GROUP_ORDER`/`PIPELINE_GROUPS`.
3. A `Docker` section below the grid (`renderDockerSection()`, ~line 3900) — real, mechanically-
   extracted tables of every `Dockerfile`/`docker-compose*.yml` in the repo (build stages, compose
   service names), not duplicated on any other screen.
4. A `Runtime` section below that (`renderRuntimeSection()`, ~line 2857) — hand-authored
   operational-topology prose from `docs/architecture/data/runtime-notes.md`, also not rendered
   anywhere else on the page.

User clarified (2026-08-21): keep the `<h2>` title + `screen-desc` paragraph + card-grid exactly
as-is; delete the Docker and Runtime sections entirely (chose "delete outright" over "turn into 2
more cards" when offered both options).

**Full technical scope** (tracing every place this data flows, not just the 2 render calls, per
the "surface adjacent quality issues" rule — leaving the underlying data-generation code in place
after its only renderer is deleted would be dead weight in every future `architecture-model.json`
regeneration):

Bash side (`docs/architecture/scripts/generate-architecture-model.sh`):
1. `DOCKER_FILES` array + its leading comment (~line 1481-1493).
2. `docker_files_json()` function (~line 1494-1513).
3. Runtime-notes comment + `RUNTIME_NOTES_FILE` + `runtime_notes_json()` function (~line
   1515-1524).
4. The two `"dockerFiles"`/`"runtimeNotes"` JSON-emission lines in the top-level MODEL object
   (~line 1624-1625).

JS side (same file, embedded `<script>` block):
5. Inside `renderPipelines()`: the `<h3 class="group-heading">Docker</h3>` + `renderDockerSection()`
   call and `<h3 class="group-heading">Runtime</h3>` + `renderRuntimeSection()` call (~line
   2725-2729).
6. `renderRuntimeSection()` function + its leading comment (~line 2854-2861).
7. `renderDockerSection()` function + its leading comment (~line 3897-3919).

CSS: `.group-heading` / `.group-heading:first-of-type` rules (~line 1923-1924) — become unused
once both `<h3 class="group-heading">` usages are removed (no other `<h3>` on the page uses this
class).

**Not deleted:** `docs/architecture/data/runtime-notes.md` itself (the hand-authored source file)
stays — only the generator's reading/rendering of it is removed. The user asked to stop showing it
on this screen, not to delete the file; it becomes unread by the generator but still exists as a
real file, in case a future screen wants it again.

**Status:** implemented, not yet user-verified — 2026-08-21. All 8 pieces removed (bash
`DOCKER_FILES`/`docker_files_json()`/`RUNTIME_NOTES_FILE`/`runtime_notes_json()`/JSON-emission
lines, JS `renderDockerSection()`/`renderRuntimeSection()`/their `renderPipelines()` call sites,
CSS `.group-heading`), plus 2 stale comments elsewhere in the file that referenced
`runtime_notes_json()` by name as precedent reasoning, rewritten to state the reasoning directly
instead of pointing at a removed function. `bash -n` syntax check passed. Regenerated via a real
`architecture-doc.sh` run (exit 0, node count unchanged at 41 since Docker/Runtime were never
their own tree nodes) — confirmed zero remaining occurrences of `dockerFiles`/`runtimeNotes`/
`group-heading` in both the regenerated `architecture-map.html` and `architecture-model.json`.
Rendered page not yet visually re-checked by the user.

## Related follow-up — split infra-doc-standards into header-conventions and README-conventions skills

Raised by the user (2026-08-21) while still reviewing this same page's generation tooling: is
`.claude/skills/infra-doc-standards/SKILL.md` too large? Kept in this issue rather than a separate
one — this is exactly why 164 was kept open instead of closed after the Docker/Runtime cleanup.

**Confirmed real, not just a hunch:** 714 lines / 48KB — 6-8x its sibling skills
(`doc-standards/SKILL.md` 115 lines, `deep-review/SKILL.md` 88 lines). This is a real, recurring
cost: the skill auto-triggers on every script/tooling file edit (`docs/ai/flows.md`: "About to
write or edit a script/tooling file... → `infra-doc-standards` skill"), so the full 714 lines load
into context on every such invocation — even the common case of editing one file's own header,
which never needs the README/Mermaid/ISO-5807 content the same file also carries. No blatant
content duplication found (7 file-type sections each state only their own delta from the base
7-field shape; ~21% of the file is code-fence examples, not prose bloat) — the real issue is two
genuinely separable concerns bundled together, firing at different times.

**Fix — split into two skills:**

`.claude/skills/infra-doc-standards/SKILL.md` (kept, trimmed to ~490 lines, exactly current lines
1-493) — everything about file-level and per-function headers: "One fact, one canonical home" (kept
in full here — the governing principle both header and README placement defer to), "Base standard"
+ deviations, "The file-level header (target shape)" + example, all header-specific rules
(`Usage`'s no-flags line, `Outputs`/`Returns` staying separate, explicit "None", non-obvious side
effects, container/image names, "no real file as pointer" discipline, `Env` caller-vs-user,
`UPPER_CASE` constants, `Description` staying lean, where a finding gets recorded), "Per-function
headers", and "File-type-specific header rules" (JS/Python/`.bat`/Dockerfile/`.properties`/YAML —
the largest single chunk, ~200 lines).

`.claude/skills/infra-readme-standards/SKILL.md` (new, ~220 lines, exactly current lines 494-714)
— everything about a script-group's own `README.md`: "README — what the tool is", "README 'Flow'
section" + Mermaid/ISO-5807/direction-choosing sub-rules, "The root `scripts/README.md`", "Nested
library/support folders", "⛔ Applying this standard — what 'run the skill over a directory' means"
(moved here since README is explicitly the *last* step of that combined workflow), "sweep for
stale refs after delete", "⛔ Aggregated cross-file facts get verified by search", "Independent
review". References `infra-doc-standards`'s "One fact, one canonical home" section by name instead
of restating it (the same discipline this skill itself teaches, applied to itself).

**Cross-references needing a fix during the move** (both currently say "above", which becomes
wrong once split across two files):
- Line 47-50 (kept file): "every other section touching the same topic ... defers to this rule
  rather than restating it" — currently claims those sections are "in this document"; needs to say
  "across both this skill and `infra-readme-standards`" instead.
- Line 617 (moving section, root `scripts/README.md` bullet): "(see 'One fact, one canonical home'
  above)" → "(see `infra-doc-standards`'s 'One fact, one canonical home' rule)".
- Line 661 (moving section, "Applying this standard"): "follow 'One fact, one canonical home'
  above" → "follow `infra-doc-standards`'s 'One fact, one canonical home' rule".

**Other files needing updates in the same change:**
1. `docs/ai/flows.md` — split the existing infra-doc-standards trigger row into two, add the new
   skill's own navigation row (per `.claude/rules.md`'s "a new project-local command/skill file
   adds its own navigation row in the same operation").
2. `.claude/skills/doc-standards/SKILL.md` line 22 — currently points to `infra-doc-standards` for
   infra files generally; split the reference so a README-specific question also names
   `infra-readme-standards`.
3. `docs/architecture/scripts/DECISIONS.md` line 1455 — mentions `infra-doc-standards/SKILL.md` by
   path; check whether it needs a pointer to the new skill too once the split lands (found via
   grep, not yet read in full).

Historical references to `infra-doc-standards` inside `backlog/completed/issues/*.md` and
`BACKLOG-ARCHIVE.md` are append-only history — not touched, per the "no retroactive scrubbing"
rule.

**Status:** implemented, not yet user-verified — 2026-08-21. `.claude/skills/infra-doc-standards/SKILL.md`
trimmed to 496 lines (header/per-function-header conventions only, intro paragraph updated to
name the new sibling skill); `.claude/skills/infra-readme-standards/SKILL.md` created (237 lines,
README/Flow/Mermaid/ISO-5807 conventions, own frontmatter + intro). Both stale "above" cross-refs
to "One fact, one canonical home" fixed to name `infra-doc-standards` explicitly now that they
cross a file boundary; the file-vs-README governing-statement paragraph (originally lines 47-50)
updated to say "across both this skill and `infra-readme-standards`" instead of "in this
document". `docs/ai/flows.md`'s single trigger row split into two (one per skill), with the split
rationale noted inline. `doc-standards/SKILL.md` line 22 updated to name both skills. Also found
and fixed while implementing: `docs/architecture/scripts/DECISIONS.md` ADR-032 named the old
single-skill path — updated to describe the split, `Verified: 2026-08-21` stamped (opportunistic,
per `.claude/rules.md`), and `docs/ai/adr-index.md` regenerated in the same change. Grep-verified
zero remaining stale references anywhere in `.claude/skills/`/`docs/ai/flows.md` to the old
single-file structure. Not yet functionally re-tested (no way to invoke a skill and confirm its
new auto-trigger description matches from within this session) or visually reviewed by the user.

## Operational notes
- token_cost_review: n/a
- token_cost_research: n/a
- token_cost_verification: n/a
- review_signal_ratio: n/a
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: n/a
- flows_chosen: n/a
- flows_matched: n/a

### Script/command runs
- docs/architecture/architecture-doc.sh --no-check --no-screenshot (verify preliminary tar/permission fix) | duration_s=n/a | mode=background | result=pass
- docs/architecture/architecture-doc.sh --no-check --no-screenshot (verify header-truncation/formatting fix) | duration_s=n/a | mode=background | result=pass
- docs/architecture/architecture-doc.sh --no-check --no-screenshot (verify scripts/logs/ exclusion fix) | duration_s=n/a | mode=background | result=pass
