# improvement-155: Repo-wide script documentation convention rollout — self-contained script header, README describes flow, architecture-map surfaces both live

**Type:** improvement — design done, real application rolled out to `scripts/sonar/` and
`scripts/build-and-test/` only so far; the rest is not started.
**Module:** every repo script (`scripts/**/*.sh`, `*.bat`, `docker-compose*.yml`, `*.properties`),
`.claude/skills/infra-doc-standards/SKILL.md`, `docs/architecture/scripts/generate-architecture-model.sh`
(`script_headers_json()`).
**Priority:** Top — explicit placement, ranked directly after `improvement-154`.
**When:** independent, no blockers.

Split out of `improvement-152` Part E once that issue's own Part A/D work was ready to close —
same "split once the parent issue's real work is done" pattern `improvement-152` itself was split
out of `improvement-151` under.

## Design (already shipped)

Lives in its own dedicated skill (`.claude/skills/infra-doc-standards/SKILL.md`, split out from
`doc-standards` once the two topics — Java-app docs vs. infra/script docs — turned out to need
separate ownership; `doc-standards` keeps a one-line "out of scope, see infra-doc-standards"
pointer, no duplicate content between the two). Covers: the 7-field file-level header
(`Description`/`Usage`/`Uses`/`Env`/`Input`/`Outputs`/`Returns`), per-function headers for
`source`d library files, `.bat` delegator forwarding (`same as <file>`), Dockerfile and
`.properties` field adaptations, and the `README.md` "Flow" section (Mermaid diagrams,
multi-entry-point handling, condition-tracing rule). Grounded in the Google Shell Style Guide and
ISO/ANSI flowchart notation, both confirmed via live web search, not assumed.
`docs/architecture/scripts/DECISIONS.md` ADR-032 covers the generator-side mechanism
(`script_headers_json()`, generalized from the original `docs/architecture/scripts`-only version)
and two real bugs found and fixed while verifying it (extractor over-scanning past the header
block; header must sit immediately after the shebang, before `set -e`). `scripts/sonar/DECISIONS.md`
ADR-008 covers a real, applied feature that came out of this same work: `run.sh` now
auto-validates `sonar-project.properties`'s module list against `pom.xml` before every run.

## Done, applied for real (only these two directories so far)

1. ✅ **`scripts/sonar/`** — all 4 files (`docker-compose.sonar.yml`, `run.bat`, `run.sh`,
   `sonar-project.properties`) carry real, finalized headers (7-field for `.sh`, delimiter-framed;
   6-field for `docker-compose.yml`; 3-field for `.properties`; `run.bat`'s `same as run.sh`
   forwarding template). `README.md` rewritten with the `## Flow` section (2 entry points
   converging into one Mermaid diagram, 3 decision diamonds tracing real conditional behavior —
   image recreate, DB-migration wipe, token regen, module-list auto-fix). Verified end to end:
   `bash scripts/sonar/run.sh --no-gate` still reaches `ANALYSIS SUCCESSFUL` after all header edits.
2. ✅ **`scripts/build-and-test/`** — headers and README Flow diagram applied as the underlying
   code evolved through this same session's build/test consolidation work.
3. ✅ **Generator regenerated and re-verified after both.** Surfaced two more real bugs in
   `script_headers_json()` while doing so (`Output`→`Outputs` rename never reached the parser's own
   regex; `Env`/`Returns` were never in its field list at all — see
   `docs/architecture/scripts/DECISIONS.md` ADR-032's third/fourth bug notes) — fixed and
   re-verified via screenshot: all 7 header columns render correctly, with each file-type's
   deliberately-omitted fields (e.g. `Returns` for Docker, 4 fields for `.properties`) rendering
   empty rather than absorbing a neighboring field's content.

**Still open on these two:** remove the `scripts/sonar`-specific illustrative example content from
`infra-doc-standards/SKILL.md` now that the real files carry the real headers (the skill's own copy
is now a `doc-standards`-style "one fact, one canonical home" duplicate) — needs explicit
confirmation before editing the skill again.

## Not yet started — the actual repo-wide rollout

Roll the same convention onto every other script directory:
- `docs/ai/scripts/` (moved from `scripts/ai/` this session — see `scripts/DECISIONS.md` ADR-002's
  annotated exception)
- `scripts/ci/`
- root `scripts/*.sh`/`*.bat`
- `playwright/` (see its own two-level design note below)
- the new `scripts/deploy-and-run/` (in progress, same session)

### `playwright/` — two-level directory structure (2026-08-19/20, done — superseded by a broader mechanism)

`playwright/` is the first `SCRIPT_GROUP_DIRS` candidate with real nested content the current
`-maxdepth 1` file-glob never reaches: `playwright/e2e/` (7 `.spec.js` + `_helpers.js`) and
`playwright/e2e/_flows/` (14 `.flow.js`, `require()`d by 2+ spec files — the JS analogue of the
existing "per-function header for files `source`d by other scripts" convention). `playwright/e2e/`
also has its own `README.md`, a test catalog — a genuinely different document shape from the
`## Flow` call-chain section the convention defines for a script directory's `README.md`.

**Design direction agreed in chat, not yet implemented:** card-then-drill-down, one level at a
time — not stacking `playwright`/`e2e`/`_flows` as multiple sections on one page. Clicking the
"Playwright" card on the Tooling & Pipelines list shows only `playwright/`'s own top-level files
(`run.sh`/`run.bat`/`playwright.config.js`/`reporter.js`) + its own `README.md`, plus one
subdirectory card ("e2e"). Clicking that card opens its own page: `playwright/e2e/`'s own files +
its own `README.md` (the existing test catalog, unchanged), plus one further subdirectory card
("_flows"). Clicking that opens `playwright/e2e/_flows/`'s own files, no further subdirectory
cards. Each level is `SCRIPT_GROUP_DIRS`'s existing flat, `-maxdepth 1`-per-directory shape —
nothing new needed for file discovery — the new part is a drill-down screen (generalizes the same
card→detail pattern already used for the System module screen and the Diagrams groupKey screen)
that resolves child cards purely from other `SCRIPT_GROUP` node ids sharing the parent id as a
prefix (`playwright/e2e` is a child of `playwright`; `playwright/e2e/_flows` is a child of
`playwright/e2e`) — no hardcoded "e2e"/"_flows" names in the renderer.

**Resolved, superseded by a broader mechanism (2026-08-19/20):** instead of a `playwright`-specific
two-level design, the actual implementation is the general-purpose `SCRIPT_TREE_ROOTS`/
`renderScriptTree()` drill-down (arbitrary depth, not fixed at two levels — `scripts/ci/dagu/` is a
real 3-deep case handled the same way). Both "still open" questions below resolved in the
opposite direction from what was proposed: `.spec.js`/`.flow.js` files got the **full** 7-field
header (not a lighter-weight adaptation), and the router state extends the existing `pipelines`
screen with a `path` array (not a new `screen` type).
- ~~Whether `.spec.js`/`.flow.js` files get a lighter-weight header-field adaptation~~ — got the
  full 7-field header instead.
- ~~Exact mechanics of the new drill-down screen/router state~~ — extends `pipelines` with `path`.

### Skill improvements raised while applying it to `scripts/ci/` (2026-08-18)

- The skill's own file-type coverage (script/`.bat`/Dockerfile/`.properties`) has no dedicated,
  strict comment-convention section for YAML files, Python files, or JavaScript files — all three
  were touched applying the skill to `scripts/ci/` (`ci.yaml`, `pipeline-metrics.py`) without one
  to follow, improvised ad hoc instead. Needs a real section per file type, not an improvisation
  each time a new language shows up.
  **YAML and JavaScript now have real research behind them** (2026-08-19, via `improvement-160`'s
  investigation log): YAML — no formal field-structured standard exists for Spring Boot
  `application.yml` specifically, but a real general convention does (top-of-file purpose/owner/
  context block, standard in Kubernetes manifests and Ansible playbooks); real constraint, YAML has
  no block-comment syntax, every header line needs its own `#`. JavaScript — JSDoc's
  `@fileoverview` is a real standard from the same authority already grounding this skill's bash
  convention (Google JavaScript Style Guide, same family as the Google Shell Style Guide),
  explicitly recommended "whenever a file consists of more than a single class definition" —
  stronger precedent than the YAML/Docker cases. Real files confirmed with zero coverage today:
  `scripts/ci/dagu/ci.yaml`, `marketplace-app/src/main/resources/application*.yml`,
  `playwright/e2e/*.spec.js`, `playwright/e2e/_flows/*.flow.js`, `playwright/e2e/_helpers.js`.
  **Done (2026-08-19):** `infra-doc-standards/SKILL.md` now has real `YAML files`, `JavaScript
  files`, and `Python files` sections (field-table/example each, same shape as the existing
  `.properties`/Dockerfile sections; the `Python` section grounded in PEP 257 plus the separately
  documented `#`-header-block convention, since PEP 257 itself targets docstrings for a different
  consumer than a script's own usage header). Applying any of the three sections to the real files
  listed above (or `scripts/ci/watch-run.py`/`scripts/ci/dagu/pipeline-metrics.py`, which already
  carry this exact header shape informally, ahead of the section that now documents it) is
  separate, not-yet-started work.
  Python still has no research done. See `improvement-160-certification-coverage-map.md`'s Domain 3
  rows for the structured version of this research.
- **Done (2026-08-20):** a reminder/rule that applying the skill to one directory should stay
  bounded to that directory, not follow every adjacent stale reference found elsewhere in the repo
  while working — `infra-doc-standards/SKILL.md` now has "Applying this skill to one directory
  never touches another".
- **Done (2026-08-20):** the reverse case — after a file is deleted, sweep the *in-scope*
  directory/directories for any remaining reference to it (README, other headers) before
  considering the pass complete — `infra-doc-standards/SKILL.md` now has "After deleting a file,
  sweep the in-scope directory for remaining references to it".
- **Done (2026-08-20):** organize the skill's file-type sections into a tree-shaped hierarchy —
  clarified via direct question that the goal is grouping by relatedness (file-type sections were
  physically adjacent already, but had no shared parent heading tying them together as a family,
  separate from the general `.sh`-header rules before them and the README rules after).
  `infra-doc-standards/SKILL.md` now has a `## File-type-specific header rules` parent heading with
  JavaScript/Python/`.bat`/Docker/`.properties`/YAML demoted to `###` children (their own nested
  examples demoted to `####` to preserve correct nesting). While doing this pass, also found and
  merged a real duplicate: two adjacent sections both titled "Applying this skill..." (workflow
  mechanics vs. scope boundary) — folded into one section under the original `⛔ Applying this
  standard` heading, keeping the stricter/more complete wording.

### Dead `.bat` delegators found — planned first step (2026-08-19, done)

Investigated whether `run.bat` files nested inside `scripts/*` subfolders are actually reachable,
since the top-level `scripts/<name>.bat` entry points were found to call `wsl bash
scripts/<name>/run.sh` **directly** (confirmed by reading `scripts/sonar.bat`/`scripts/playwright.bat`
themselves) — never routing through the subfolder's own `run.bat` at all. Cross-checked with a
repo-wide grep for any other reference to either path (docs, scripts, CI config): none found.

- **`scripts/sonar/run.bat`** — confirmed dead code, zero references anywhere except its own file
  and passing doc mentions. Only `scripts/sonar/` has this pattern — `build-and-test/`, `ci/`,
  `deploy-and-run/` have no subfolder-level `run.bat` at all, just `run.sh`, so `sonar/run.bat` is
  an inconsistent leftover, not the established shape. **Plan: delete.**
- **`playwright/run.bat`** — same dead-code situation (`scripts/playwright.bat` bypasses it too,
  confirmed by reading the file), but it carries one piece of content nothing else has: an inline
  `--help`/`/?` usage block with concrete example commands. Decided: delete as-is, no migration of
  that content elsewhere — the same information already lives in `playwright/README.md`/`CLAUDE.md`.
  **Plan: delete.**
- **`scripts/deploy-and-run/reset.bat`** — investigated as a possible third case, found to be
  **different, not redundant**: it's a genuinely distinct, already-documented standalone entry
  point (`README.md`, `scripts/README.md`, `scripts/deploy-and-run/README.md` all reference it) —
  truncate-only, no full deploy, unlike `deploy-and-run.bat --reset-only-db` which always runs the
  full deploy afterward. No top-level `scripts/reset.bat` exists to make this one redundant.
  **Plan: keep the nested `reset.sh`/`reset.bat` as the real logic, but add root-level
  `scripts/reset.sh`/`scripts/reset.bat` as thin delegators** — every other subfolder script
  (`sonar`, `build-and-test`, `deploy-and-run`, `playwright`, `ci`, `architecture-doc`) already has
  this exact paired root-entry-point shape (confirmed via `scripts/sonar.sh`:
  `bash "$ROOT/scripts/sonar/run.sh" "$@"`); `reset` is currently the one exception, invoked only by
  its full nested path. Aligns it with the established convention instead of leaving it as the odd
  one out.
- **After deleting the two dead files:** run the `infra-doc-standards` skill against
  `scripts/sonar/` and `playwright/` so it backfills/updates any documentation (README `## Flow`
  diagrams, architecture-map entries) that referenced the now-removed files.

**Done:** both dead files deleted (`scripts/sonar/run.bat`, `playwright/run.bat`); root-level
`scripts/reset.sh`/`scripts/reset.bat` added as thin delegators, aligning `reset` with every other
subfolder script's paired root-entry-point shape; `infra-doc-standards` re-run against
`scripts/sonar/`, `scripts/deploy-and-run/`, and `playwright/` afterward.

### Multi-level script-group documentation hierarchy (2026-08-19, done — superseded by a broader mechanism)

Evaluating a documentation shape for `scripts/README.md` itself (the root, "Level 1", above the
existing per-script-group "Level 2" `README.md` this skill already covers) — not yet drafted into
`SKILL.md`. Key points agreed so far:

- Level 1 lists every real entry point across the whole tree — both thin delegators into deeper
  logic and genuinely self-contained scripts — concise/abstract, never duplicating a file's own
  header `Description` (single source of truth; the architecture-map generator already surfaces
  the header directly). No Mermaid diagram at Level 1 — diagrams belong to whichever level actually
  owns the branching logic.
- Classifying an entry point (delegates elsewhere vs. self-contained) must come from reading the
  actual file, never inferred from naming or from whether a same-named subfolder exists — verified
  directly: one real entry point delegates into a directory with a different name than itself, and
  the mismatch would have been missed by a naming-based check alone.
- Levels 2/3/4 nest recursively (already-covered directory → its own further-nested directory),
  same rule applied at each level: no diagram unless that level genuinely owns branching logic.

**Real, verified inventory of every root `scripts/*.sh`/`*.bat` entry point** (each confirmed by
reading the actual file body, not assumed):
- Delegates into its own subfolder: `sonar`, `build-and-test`, `ci`, `deploy-and-run`,
  `run-all-tests`.
- Delegates into a sibling top-level directory outside `scripts/`: `playwright` (→ `playwright/`),
  `architecture-doc` (→ `docs/architecture/scripts/`, calling 3 separate files there).
  `architecture-doc` has no same-named subfolder of its own — confirms the "read the file, don't
  infer from naming" point above.
  `reset` (→ `scripts/deploy-and-run/`, a directory already owned by a different entry point).
- Genuinely self-contained, no delegation anywhere: `claude`, `clean`, `collect-code`, `run-local`.

**Superseded (2026-08-20):** rather than a fixed Level 1/2/3/4 scheme, the actual implementation
generalized this into the arbitrary-depth `SCRIPT_TREE_ROOTS`/`renderScriptTree()` mechanism on
`architecture-map.html` (see the "unified drill-down card" section below) plus a matching
`SKILL.md` rule ("The root `scripts/README.md`" / "Nested library/support folders" recurse to any
depth, not a fixed level count). `scripts/README.md` itself has since been rewritten to the "list
every entry point, concise/abstract" Level-1 shape this section describes.
- Not an entry point at all — a `source`-only shared library (already covered by this skill's
  existing "Per-function headers" section): `ensure-docker-plugins.sh`.

### `ensure-docker-plugins.sh` — move into a dedicated shared-library folder (2026-08-19)

Agreed: relocate `scripts/ensure-docker-plugins.sh` → `scripts/utils/ensure-docker-plugins.sh`.
Currently the only file of its kind (a cross-cutting library sourced by multiple different
script-groups, owned by none of them) sitting bare among real entry points at the `scripts/` root
— a dedicated folder makes the distinction visible and gives any future shared library a home.

**Plan:**
1. Move the file; update its 4 real sourcing references (`scripts/ci/docker-entrypoint.sh`,
   `scripts/deploy-and-run/reset.sh`, `scripts/deploy-and-run/run.sh`, `scripts/sonar/run.sh`) and
   `scripts/CLAUDE.md`'s 2 references to the old path.
2. Update `infra-doc-standards/SKILL.md`'s existing "Per-function headers" section — its own
   worked example already uses this exact file/function (`ensure_buildx`), path needs updating to
   the new location.
3. Extend `.claude/rules.md`'s "Script-group directory structure" rule with the shared-library-
   folder case (a library sourced by multiple different script-groups, owned by none of them, gets
   its own dedicated folder — distinct from a single script-group's own subdirectory, which is
   already covered).

**Done (2026-08-19):** all 3 steps applied — file moved, all 4 sourcing references and
`scripts/CLAUDE.md` updated, `SKILL.md`/`rules.md` both extended, new `scripts/utils/README.md`
written. `infra-doc-standards/SKILL.md` also gained a new `The root scripts/README.md` section
(the Level 1/2/3/4 hierarchy design discussed above). **Follow-up done (2026-08-20):** the root
`scripts/README.md` itself has since been rewritten to this shape (289 → 82 lines — one entry-point
table instead of duplicating every target file's own header content).

### `architecture-map.html`'s "Tooling & Pipelines" screen — collapse into one unified drill-down card (2026-08-19, done)

Goal: make the live architecture map reflect the multi-level hierarchy this issue already designed
into `infra-doc-standards/SKILL.md` — one true drill-down card starting at `scripts/`, instead of
one flat card per script-group. Investigated the real current implementation
(`docs/architecture/scripts/generate-architecture-model.sh` + `architecture-map.html`) before
planning — verdict: **feasible, no fundamental blocker**, but genuinely new code, not a pure reuse
of an existing pattern.

**Current real state (verified, not assumed):**
- 9 cards today: `ai-tooling`, `build-architecture-page`, `playwright`, `sonar`, `ci`, `build`
  (build-and-test), `deploy-and-run`, `run-all-tests`, `other-scripts` — one flat card per
  `SCRIPT_GROUP_DIRS` bash-side entry, matched to a `PIPELINE_GROUPS` JS-side entry by a `category`
  string.
- `script_headers_json()` (the header-extraction function) already accepts any path and needs no
  change — it's the file-listing step that's currently `-maxdepth 1` and would need to go
  recursive, with `SCRIPT_GROUP_FILE_ORDER` entries able to carry nested paths.
- The existing "Diagrams" screen's card→detail navigation is a close template (same breadcrumb/
  `view` state mechanism) but is hardcoded to exactly 2 levels (`groupKey` + `diagramIndex`) — true
  arbitrary-depth recursion (`scripts/` → `scripts/ci/` → `scripts/ci/dagu/`) needs a real
  extension (a `view.scriptPath` array + a `children` field per node), not a drop-in reuse.
- `renderScriptGroupSection()` (renders a directory's own file headers + README) is partially
  reusable as-is for whichever level a user has drilled into.

**Cards are folders only — files never navigate.** Only a real directory becomes a clickable card
that drills one level deeper. A file (e.g. `architecture-doc.sh`) never does — clicking it (or just
having it listed) only ever shows its own header content (`Description`/`Usage`/etc.) inline, in
whatever level's file listing it already appears in. A file whose `Description` happens to mention
it calls into a directory that's a different, separately-existing card (`architecture-doc.sh` →
`docs/architecture/scripts/`, which stays its own top-level `build-architecture-page` card) is not
a routing case to design around — that fact just reads as plain text in the file's own
`Description`, the same as any other file. No cross-card link/jump mechanism needed.

**Plan:**
1. Bash side: switch file-listing from `-maxdepth 1` to real recursion for the unified tree; add
   a `children` field per **directory** node (never a file node — files have no children) so the
   JS side can build subfolder cards without re-deriving the tree client-side.
2. When writing a file's own `Description` field, ground it in the real file content (not
   inferred from naming) — same discipline this issue's "root `scripts/README.md`" design already
   requires, just applied to the header itself now, not a routing decision.
3. JS side: extend `view` state to a path array instead of a flat `groupId`, add a genuine
   N-level drill-down renderer (modeled on the Diagrams screen's breadcrumb mechanism, but not a
   literal copy — that screen is hardcoded to exactly 2 levels). Drill-down applies only to
   directory nodes; a file node always renders as a plain, non-navigable row. **Render order at
   every level: folder-cards first, then the flat file list below them** — navigable options take
   visual priority over informational rows.
4. Recognize the "shared library folder" case (`scripts/utils/`) — file list + README only, no
   entry-point chain, no diagram, matching the `SKILL.md` rule already written.
5. Collapse `playwright`, `sonar`, `ci`, `build`, `deploy-and-run`, `run-all-tests`,
   `other-scripts` into one new unified card. Keep `ai-tooling` and `build-architecture-page` as
   their own separate top-level cards, unchanged.
6. Regenerate and visually verify (`bash scripts/architecture-doc.sh`, screenshot the new drill-down).

**Design confirmed with the user via an ASCII level-by-level walkthrough** (Level 1 `scripts/` →
Level 2 e.g. `sonar/`/`ci/`/`utils/` → Level 3 e.g. `ci/dagu/`; separately, the `playwright/`
branch down to Level 4 `playwright/e2e/_flows/`) — folder-cards first, files below, matches what's
described above.

**Implemented as designed, one real deviation on where the JS lives.** `architecture-map.html` is
not itself hand-maintained — the whole file (CSS, HTML skeleton, and every render function) is a
heredoc (`HTML_HEAD`/`HTML_TAIL`) written by `generate-architecture-model.sh` and rewritten in
full on every regeneration. All the JS/CSS changes described below therefore live inside that
heredoc in the bash script, not as a standalone edit to `architecture-map.html` (a first pass
edited the generated file directly, which was silently wiped by the very next regeneration —
caught before this write-up, fixed by moving the same edit into the heredoc instead).

Bash side (`docs/architecture/scripts/generate-architecture-model.sh`):
- `SCRIPT_GROUP_DIRS`/`SCRIPT_GROUP_CATEGORY` trimmed to just `docs/ai/scripts` and
  `docs/architecture/scripts` — the two cards this issue keeps untouched.
- New `SCRIPT_TREE_ROOTS=(scripts playwright)` and `SCRIPT_TREE_EXCLUDE_DIRS=(reports pw-report
  report node_modules)`.
- New `emit_script_tree_node()` (recursive): emits one `SCRIPT_GROUP`-shaped JSON object per
  directory, walking real child subdirectories (skipping excluded/hidden ones) into a `children`
  array of the same shape — directories only, files never get a `children` field. Reuses
  `script_headers_json()`/`readme_json_for()`/`decisions_json_for()`/`json_str_array()` unchanged,
  confirming they already worked on any path, not just depth-1. Only the two tree-root nodes
  (`scripts`, `playwright`) carry a `category` field ("Scripts") — nested nodes don't need one,
  they're only ever reached by walking a parent's `children`.
- `SCRIPT_GROUP_FILE_ORDER` extended with explicit orderings for `scripts` (root entry points),
  `scripts/ci`, `scripts/ci/dagu`, `playwright/e2e`, `playwright/e2e/_flows` — needed both for
  readability and because the extension-based fallback filter drops `.py` files
  (`watch-run.py`/`pipeline-metrics.py`), which would otherwise silently disappear from those two
  dirs' listings.

JS/CSS side (inside the same script's `HTML_HEAD`/`HTML_TAIL` heredoc, ends up in
`architecture-map.html`):
- `PIPELINE_GROUPS`/`PIPELINE_GROUP_ORDER` collapsed from 9 entries to 3: `ai-tooling`,
  `build-architecture-page`, `scripts-tree` (label "Scripts"). New `SCRIPT_GROUP_TREE_ROOTS =
  ["scripts", "playwright"]` constant mirrors the bash-side array.
- `renderPipelines()` gained one new branch: `view.groupId === "scripts-tree"` delegates to a new
  `renderScriptTree(g)` instead of the flat category-filter path every other card still uses.
- New `scriptTreeNodeAt(pathSegs)`: walks `SCRIPT_GROUP_TREE_ROOTS` down through a path array
  (`path[0]` is always a root id, e.g. `["scripts","ci","dagu"]`) to the live node.
- New `renderScriptTree(g)`: renders an in-card breadcrumb (own CSS class `.script-tree-crumb`,
  same visual shape as the page-level `#breadcrumb` but a separate DOM element, so drilling through
  folders doesn't grow the page-level `crumbStack` once per folder), then folder-cards for
  `node.children` (clickable, extend `view.path` by one segment), then the flat file list for the
  current level via the existing `renderScriptGroupSection()` — reused as-is, unchanged. No path
  (or an empty one) shows just the two root folder-cards, no file list. The page-level
  `crumbStack` records entry into "Scripts" as one hop total, same as Diagrams records entry into
  a diagram group as one hop, regardless of how deep `view.path` goes.
- `render()`'s dispatcher, `navigateBack()`, `navigateToCrumb()`, and all Diagrams-screen code are
  untouched, per the plan.

**Verified:** `bash scripts/architecture-doc.sh --no-screenshot` — clean exit, `Valid JSON`,
`architecture-model.json` / `architecture-map.html` reported up to date by the freshness check
(confirms deterministic regeneration). `architecture-model.json` has exactly 4 top-level
`SCRIPT_GROUP` nodes (`docs/ai/scripts`, `docs/architecture/scripts` — no `children` field, exactly
as before; `scripts` — 19 files, 6 folder children; `playwright` — 2 files, 1 folder child), nested
depth matches the real tree exactly (`scripts/ci` → `scripts/ci/dagu`; `playwright/e2e` →
`playwright/e2e/_flows`). Loaded the regenerated `architecture-map.html`'s embedded script into
Node (`vm.runInThisContext`, DOM/Cytoscape/Mermaid stubbed) and drove it end-to-end: the
path-less "Scripts" view renders exactly the 2 root folder-cards and no file list;
`path:['scripts','ci']` renders the `dagu` folder-card, the in-card breadcrumb, and `scripts/ci`'s
own file list (`ci.yaml` present); `ai-tooling`/`build-architecture-page` still render their exact
pre-existing content (Commands/Skills tables, the Cytoscape/Mermaid tech-stack note) untouched; the
top-level "Tooling & Pipelines" list screen renders exactly 3 cards (AI Tooling, Build architecture
page, Scripts).

**Bug found by clicking through the real UI (2026-08-19, fixed):** the path-less "Scripts" root
view rendered the physical `scripts` `SCRIPT_GROUP` node as its own clickable folder-card one level
below the "Scripts" pipeline card, instead of merging that node's own files/children directly into
the top-level view — so opening "Scripts" then clicking the "scripts" folder-card produced a
`Scripts › Scripts` breadcrumb instead of `Scripts › <subfolder>`. Fixed in `renderScriptTree()`:
the path-less branch now reads the `scripts` node's `children`/`files` directly (no intermediate
click-through) and appends `playwright` as one more sibling folder-card at that same level, rather
than nesting it under a "scripts" card of its own.

**Re-verified after the fix:** `bash scripts/architecture-doc.sh --no-screenshot --no-check`, clean
exit. Loaded the regenerated `architecture-map.html`'s embedded script into Node
(`vm.createContext`, DOM/Mermaid stubbed) and drove `renderScriptTree()` directly: the path-less
root view renders exactly 7 folder-cards (`build-and-test`, `ci`, `deploy-and-run`,
`run-all-tests`, `sonar`, `utils`, `playwright`) with the in-card breadcrumb reading only "Scripts";
`path:["sonar"]` renders breadcrumb `Scripts › sonar` (not `Scripts › Scripts › sonar`);
`path:["playwright"]` renders breadcrumb `Scripts › playwright` and correctly shows the `e2e`
folder-card.

**Plan — remove the in-card breadcrumb entirely, at every depth (2026-08-19, approved):**

1. The page already has its own breadcrumb above the card content (`System › Tooling & Pipelines ›
   Scripts`, the page-level `#breadcrumb`/`crumbStack`), plus a "Back" button
   (`backButtonHtml()`) already rendered at the top of `renderScriptTree()`'s output — both untouched
   by this change. The in-card `.script-tree-crumb` div (rendered inside `renderScriptTree()`, own
   CSS class, separate DOM element from the page-level breadcrumb) is therefore fully redundant at
   every depth, not just the root self-link case. Fix: delete the whole `.script-tree-crumb`
   construction block from `renderScriptTree()` — no breadcrumb rendering at any `path` depth,
   folder-cards + file list unchanged.
   File: `docs/architecture/scripts/generate-architecture-model.sh`, `renderScriptTree()` inside the
   `HTML_HEAD` heredoc. The `.script-tree-crumb` CSS rule (if it has no other consumer) is removed
   too, since nothing would render it anymore.
2. The "Scripts" card's own description line currently reads "scripts/ + playwright/ — every
   build/deploy/test/CI script, arbitrary-depth drill-down" — replace with a short 2-3 sentence
   description of what the section actually contains: "Every developer script for building,
   deploying, testing, and running CI lives here: local deploy/infra setup, Docker/Maven builds,
   SonarQube analysis, and the isolated Dagu-based CI runner. Playwright's end-to-end test suite
   sits alongside as its own folder. Drill into any folder for its own README and per-file
   headers."
   File: `docs/architecture/scripts/generate-architecture-model.sh`, line ~1956 — the `desc:` field
   of `PIPELINE_GROUPS["scripts-tree"]`.
3. Every level of the unified Scripts drill-down (root "scripts", and each subfolder card —
   "sonar", "ci", "ci/dagu", "playwright/e2e", etc.) currently renders a redundant heading block
   above its file list: `<h3>${n.id}</h3>` (e.g. "scripts", "scripts/sonar") + the node's
   `description` text (e.g. "Deployment") + a raw chip-row listing every one of that folder's own
   files as plain links — all of it duplicating information already visible from the page title,
   the breadcrumb, and the folder-card just clicked. Fix: suppress that heading+description+
   chip-row block for every node reached through the unified tree (`renderScriptTree()`'s calls
   into `renderScriptGroupSection()`), at every depth — not just the root. The per-file structured
   headers, `README`, and (for `scripts/ci`) the "Last CI run" block that already render right
   after this section are untouched and keep rendering as-is.
   File: `docs/architecture/scripts/generate-architecture-model.sh` — `renderScriptGroupSection(n)`
   (~line 2565) gets an option to skip its own leading heading block; `renderScriptTree()` passes
   that option on every call. The two untouched flat `SCRIPT_GROUP` cards (`docs/ai/scripts`,
   `docs/architecture/scripts`) keep calling `renderScriptGroupSection()` without the option — their
   own heading/description/chip-row stays exactly as today, out of scope for this change.

**Points 2-3 implemented and verified (2026-08-19):** `bash scripts/architecture-doc.sh
--no-screenshot --no-check`, clean exit. Loaded the regenerated `architecture-map.html` into Node
(`vm.createContext`, DOM/Mermaid stubbed) and drove `renderScriptTree()` directly: the root
"Scripts" card's own description now reads the new 2-3 sentence text (point 2); neither the root
nor `path:["sonar"]` render an `<h3>` heading/chip-row block anymore (point 3), while the per-file
"Script headers" blocks still render normally. `renderScriptGroupSection(n, skipHeading)` — the
flat `docs/ai/scripts`/`docs/architecture/scripts` cards call it without the new second argument,
unaffected.

**Five real bugs found by clicking through the live UI (2026-08-19, fixed):**
1. `navigate()` unconditionally pushed the current `view` onto `crumbStack` on every call, so
   drilling deeper into the unified Scripts tree (same `screen`/`groupId`, only `path` changing)
   grew the page-level breadcrumb by one more "Scripts" per depth level (`System › Tooling &
   Pipelines › Scripts › Scripts › Scripts`). Fixed: `navigate()` now treats a same-`screen`
   (`"pipelines"` only, never matches the `diagrams` screen's own `groupKey`/`diagramIndex`
   identity), same-`groupId` call as a path change, not a new hop.
2. `script_headers_json()`'s closing-delimiter regex (`^─+$`) only matched the Unicode box-drawing
   character `.sh`/`.js`/`.py` headers use, never the plain ASCII dashes `.bat` headers use per
   this repo's own `.bat`-must-stay-ASCII rule — so every `.bat` file's closing header delimiter
   line leaked into its last field's value as literal text (e.g. `Returns: same as
   scripts/sonar/run.sh. ---------------------------------------------------------------------------`).
   Fixed: `^[─-]+$`.
3. `.card-grid` had `gap: 12px` between cards but no `margin-bottom`, while `section.block` (the
   next element down) has `margin-bottom: 16px` — the visible gap after a whole folder-card row
   was smaller than the gap between individual cards in that row. Fixed: `.card-grid` now also has
   `margin-bottom: 12px`.
4. The Scripts card's own description rendered unconditionally at every drill depth, repeating the
   same whole-tree blurb below every folder's own file list. Fixed: only rendered when
   `path.length === 0` (root).
5. The `scripts/ci` node's "Last CI run" block always rendered its own heading even with no data
   (`--with-ci-metrics` never run), showing an empty "No data" placeholder. Fixed: the whole
   section (including the heading) only renders when `MODEL.ciPipelineMetrics` is actually present.

**Re-verified after all five fixes:** `bash scripts/architecture-doc.sh --no-screenshot
--no-check`, clean exit. Node (`vm.createContext`) simulation of a real click path (open
Tooling & Pipelines → open Scripts → drill into `sonar` → drill into `ci/dagu`) shows
`crumbStack` staying at exactly `["Tooling & Pipelines"]` throughout, never accumulating repeated
"Scripts" entries; `screen-desc` present at the root, absent at any deeper path; a real diagram
group's own crumb-push behavior (`diagrams` screen, unrelated to this fix's `"pipelines"` scoping)
confirmed unchanged. `sonar.bat`'s parsed `Returns` field reads clean (`"same as
scripts/sonar/run.sh."`, no trailing separator text). `scripts/ci`'s rendered section omits the
"Last CI run" heading entirely while `MODEL.ciPipelineMetrics` is `null`.

**Two more real bugs found by clicking through the live UI after the five above (2026-08-19,
fixed):**
6. The `navigate()` fix for bug 1 made the current-view breadcrumb segment (e.g. "Scripts" at any
   drill depth) render as a non-clickable `<span class="current">`, since it no longer lives in
   `crumbStack` at all -- there was no way back to the tree's own root via the breadcrumb once
   drilled even one folder deep. Fixed: `renderBreadcrumb()` renders the current-view segment as a
   clickable link back to `path: []` whenever the current view is a tree-drilling view
   (`screen === "pipelines"` with a real `groupId` and non-empty `path`); unchanged (still a plain
   `<span>`) for every other screen.
7. `script_headers_json()`'s Python line-classifier only recognized `#`, `//`, and `REM`-prefixed
   comment lines -- it had no handling at all for JS/JSDoc block-comment style (`/* ── Header ──
   ... */`), so every JS file using that convention (all 24 files under `playwright/e2e/`,
   `playwright/playwright.config.js`) silently produced an empty header, invisible in the
   "Script headers" section regardless of how complete the real file header was. Fixed: added
   `/*`-open and ` *`-continuation line handling (stripping the comment-prefix characters, plus a
   trailing `*/` on the closing delimiter line) before the existing field-parsing loop, which
   needed no changes itself.

**Re-verified after both fixes:** `bash scripts/architecture-doc.sh --no-screenshot --no-check`,
clean exit (after fixing a real bash-double-quote-vs-embedded-Python-string collision the first
attempt at bug 7's fix introduced -- caught immediately by the script's own Python
`IndentationError`, not silently). Node simulation confirms the "Scripts" breadcrumb segment is a
real `<a>` linking back to `path: []` when several levels deep; `architecture-model.json` now
carries real parsed headers for `playwright/playwright.config.js` (previously absent) and all 8
files under `playwright/e2e/` and all 16 under `playwright/e2e/_flows/` (previously an empty
`headers` array for both directories).

**Eighth bug found by real UI clicking, plus a real-browser re-verification requirement (2026-08-19,
fixed):** the Node-`vm` simulation used for bugs 1-7 drove `navigate()` directly and never actually
rendered/clicked the page — it missed that bug 6's fix left two things broken: the page breadcrumb
never showed which folder was open (`crumbLabelFor()` for a tree-drilling view ignores `path`
entirely, so the line read a static "Scripts" no matter how deep), and the "← back" button
(`navigateBack()`) always exited the whole Scripts tree in one click instead of going up one folder
at a time, since `crumbStack` itself no longer grows per folder level (by bug 1's own design).
Fixed: `renderBreadcrumb()` now appends one clickable segment per `view.path` entry after "Scripts"
(last one plain/current, matching every other screen's convention) whenever the current view is a
tree-drilling view; `navigateBack()` pops one `path` segment first when `path.length > 0`, only
falling through to the ordinary `crumbStack`-based jump once back at the tree's own root.

**Re-verified via a real headless Chromium session** (not simulation) — `docker cp` the regenerated
`architecture-map.html` into the already-running `pw-runner` container, drove it with
`@playwright/test`'s own `chromium.launch()` against the real `file://` page, clicking through real
DOM elements: entering Scripts → breadcrumb `Scripts`; clicking the `sonar` folder-card →
breadcrumb `Scripts›sonar`; one "← back" click → breadcrumb back to `Scripts` (not out of the tree
entirely), root folder-cards visible again. A second run went two levels deep (`ci` → `ci/dagu`)
and confirmed three consecutive "← back" clicks land at exactly `Scripts›ci`, then `Scripts`, then
`Tooling & Pipelines` — one real level per click, matching expectation precisely at every step.

**Follow-up enhancement (2026-08-20): depth also shown in the screen title itself.** The user
requested the `h2.screen-title` (which stayed a static "📜 Scripts" at every depth) also reflect
current depth, not just the breadcrumb above it, so the current location is visible at a glance
without reading the breadcrumb separately. Fixed: `renderScriptTree()` appends
` — ${path.join(" › ")}` to the title whenever `path.length > 0` (e.g. "📜 Scripts — ci › dagu"),
matching the breadcrumb's own separator style. Re-verified via the same real headless-Chromium
`pw-runner` session: root title reads "📜 Scripts"; after drilling into `ci`, "📜 Scripts — ci";
after `ci/dagu`, "📜 Scripts — ci › dagu", with the breadcrumb showing the matching
`Scripts›ci›dagu` at the same moment.

### `infra-doc-standards`/`doc-standards` — added missing skill frontmatter (2026-08-19, done)

Neither skill had any YAML frontmatter at all (not just missing `argument-hint`/`allowed-tools` —
zero fields of any kind). Verified the real frontmatter syntax against the official reference
table before writing (space-/comma-separated or YAML-list for `allowed-tools`; `argument-hint`
valid for project skills, its "unexpected key" error only applies to the external Agent Skills
packaging path). Both skills now have `name`/`description`/`allowed-tools`. No `argument-hint` on
either — confirmed neither uses `$ARGUMENTS`, so the field would be moot for these two.

### `infra-doc-standards` ran for real against `scripts/sonar/`, `scripts/deploy-and-run/`, `playwright/` — including nested folders (2026-08-19, done)

Backfilled docs for the `run.bat`/`reset.bat` deletions and new root `scripts/reset.sh`/`.bat` pair
(`scripts/sonar/README.md` needed no change — it never documented `run.bat` as an entry point;
`playwright/README.md`, `scripts/deploy-and-run/README.md`, root `README.md`, `scripts/README.md`
all updated). Extended to nested folders per the user's explicit request: `scripts/ci/dagu/` got a
new short README (no `## Flow`, matches the shared-library-folder shape); all 24 real files in
`playwright/e2e/` (`_helpers.js`, 7 spec files, 16 `_flows/*.flow.js`) got the JavaScript-section
header treatment, including per-function JSDoc headers on every externally-used exported function
— done via a background agent, `git diff` confirmed zero non-comment lines touched.

**Independent review** (per the skill's own process — a fresh agent with no prior context)
verified all 24 JS files against real current content. First attempt failed on an infrastructure
session-limit, not a real finding (0 tokens spent); retried successfully. Found and fixed 4 real
gaps: `attachment.flow.js`/`auth.flow.js` had inaccurate `Input` fields (missing real direct
consumers), `05-seed-filter-sort-pagination.spec.js`'s `Env` field omitted `PW_SCREENSHOTS` (a real
`process.env` read), and `timeline.flow.js` — the most significant gap — had zero per-function
JSDoc headers on any of its 13 externally-used exported functions, now fixed.

**Adjacent finding, fixed as part of the same pass:** the review surfaced 5 code comments (not 3 as
first suspected — a follow-up grep found a 5th) citing issue numbers
(`improvement-126`/`-008`/`-101`/`-020`/`-056`/`-061`) across `playwright/e2e/`, violating
`.claude/rules.md`'s "never mention an issue/ticket number inside a code comment" rule — all
trimmed to number-free, one-line comments preserving the real WHY.

**Dead exports — actually deleted (2026-08-19), not just flagged, per `CLAUDE.md`'s "if you are
certain something is unused, delete it completely" rule:** `advertisement-filter.flow.js`'s
`runFillAllFiltersFlow` (also removed an incidental `page.waitForTimeout()` call that went with
it, itself a standing rule violation), `category.flow.js`'s
`deselectCategoryInAdForm`/`runDeleteCategoryFlow`, and `filter.flow.js`'s `closeQueryPanel` — all
confirmed zero real external consumers via whole-tree grep before deletion, each file's header/
`module.exports` updated to match, `node --check` clean on all three afterward.
`audit.flow.js`'s `runOpenSettingsFlow`/`runCloseSettingsFlow`/`runVerifyEntityActivityFlow` were
**not** touched — verified they have real internal callers within their own file (used, just never
imported elsewhere), a different case from the four above, which had zero callers anywhere.

## Original problem statement

While fixing `scripts/sonar`'s README/comments, a top-of-file "Usage:" comment got trimmed down to
a bare pointer at `README.md` ("see README.md 'How to run'") under the "one line or none"
code-comment rule. Rejected: a script's own top comment must be self-contained — what it does,
what parameters are valid, what result to expect — not a redirect to another file. This surfaced a
real, repo-wide gap: the original `docs/architecture/scripts/*.sh`/`*.js` header convention
(`Description:`/`Uses:`/`Input:`/`Output:` — see `docs/architecture/scripts/DECISIONS.md` ADR-022)
was (a) undocumented as a standing rule anywhere outside that one generator function's own code,
(b) hardcoded to only scan that one directory, never applied elsewhere, and (c) had no field for
valid CLI parameters/flags.

### Research grounding (2026-08-14, web search)

Checked against real bash-scripting convention before finalizing: the
[Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) — the most widely
cited authoritative bash convention — requires a top-of-file header comment (brief overview) and,
for functions, a structured block: `Description`, `Globals`, `Arguments`, `Outputs`, `Returns`.
This validates both the general shape (structured header block, not prose) and specifically the
missing field this issue added — Google's `Arguments` is exactly the `Input:`/`Uses:` fields'
missing sibling for "what CLI flags does invoking this take."

### Decision — three-layer documentation, one canonical home each

1. **Script's own top-of-file header — self-contained, mechanically parsed.** Every script gets a
   structured header, extending the original 4-field convention with a 5th field: `Description:` /
   `Usage:` (valid CLI parameters/flags) / `Uses:` / `Input:` / `Output:`. A deliberate, explicit
   exception to the "one line or none" code-comment rule (a structured metadata block, not prose
   rationale).
2. **README.md (per script-group directory) — describes the *flow between* scripts, not each
   script's own behavior again.** One script's output frequently feeds another's input (e.g.
   `scripts/sonar.sh` → `scripts/sonar/run.sh`; `scripts/ci.sh` → `scripts/ci/run.sh` →
   `scripts/build-and-test.sh`/`deploy-and-run.sh`+`playwright/run.sh`/`sonar.sh`). README's job is
   to document that chain — what calls what, in what order, what the end-to-end result is —
   referencing each script's own header instead of restating its Description/Usage/Output.
3. **`architecture-map.html`'s Tooling & Pipelines cards — renders both live, no third copy.**
   `architecture_tooling_self_docs_json()` (renamed/generalized, no longer hardcoded to one
   directory) scans every `SCRIPT_GROUP_DIRS` entry's own files for this header, parses the 5
   fields, and renders a per-file table on that group's own card. The README's flow/chain content
   renders alongside via the already-built `readme_json_for()` + `mdBlockToHtml()` path — two
   distinct sections on one page, sourced from two distinct files, no restatement between them.

Per `.claude/rules.md`'s "state the abstract principle, not a case study" rule, the concrete
5-field spec and the "header vs. README vs. architecture-map" layering lives in
`.claude/skills/doc-standards/SKILL.md`, not restated as a case study anywhere else.

## Related

- `improvement-152` Part E — the section this issue was split out of.
- `docs/architecture/scripts/DECISIONS.md` ADR-022 (the original 4-field convention), ADR-032
  (the generalized `script_headers_json()` mechanism and the bugs found while building it).
- `scripts/sonar/DECISIONS.md` ADR-008 (the real feature — module-list auto-validation — that came
  out of applying this convention to `scripts/sonar/`).
- `scripts/DECISIONS.md` ADR-002 (annotated exception covering the `scripts/architecture` →
  `docs/architecture/scripts` and `scripts/ai` → `docs/ai/scripts` moves referenced throughout this
  issue's file paths).
