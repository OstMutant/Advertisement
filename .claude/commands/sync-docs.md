---
description: Find every document related to a change -- by the changed files and by the names the change touches -- correct it against the code, and report what was checked and what was not. Diff mode for uncommitted changes, a `<ref>` or a `task <id>`; `--full-audit`, `--module <name>` and `--package <name>` audit every claim regardless of any diff. Changes documentation and code comments only, never code logic, never commits.
argument-hint: "[<ref> | task <id> | --full-audit | --module <name> | --package <name>]"
---

# /sync-docs

Find every document related to a change, correct it against the code, and report what was checked and
what was not.

## Hard limits

- Documentation changes to match code, never the reverse. Never touch code logic; the edits are to
  documents and to comments, Javadoc, headers and changelog `remarks=`.
- Do not invent architecture: document only what exists in code. Mark anything code cannot prove as
  UNKNOWN.
- Edit no document or comment by hand before the user's direct yes (Approval Rule of
  `.claude/rules.md`), asked once per run, after the checks and before the first edit: Step 3 item 3,
  or Step A4 item 1. Running the generator or the ADR index script is not a hand edit.
- Never commit.

## Scopes

| Argument | Mode | Files in scope |
|---|---|---|
| none | diff mode | `git diff --name-only HEAD` (uncommitted changes, staged and unstaged) |
| `<ref>` (branch, tag or SHA) | diff mode | `git diff --name-only <ref>...HEAD` (committed changes only) |
| `task <id>` | diff mode | the union of `git show --name-only --format= <sha>` over every commit whose message names `<id>` (`git log --grep`) and `git diff --name-only HEAD`; with no such commit, only the uncommitted files, and say so |
| `--full-audit` | Full Audit Mode | every `README.md`, `DECISIONS.md`, `.claude/rules/*.md`, root `CLAUDE.md` and root `INFRASTRUCTURE.md`, every `*.java` (Javadoc) and every infra/tooling file (headers) |
| `--module <name>` | Full Audit Mode, one module | that module's `README.md`, `DECISIONS.md`, `.claude/rules/<name>.md`, `pom.xml` and every `*.java` under `<name>/src` |
| `--package <name>` | Full Audit Mode, one package | every `*.java` directly in the package's directory; its sub-packages only when that directory holds no `*.java` of its own, and then every sub-package is in scope (resolve the directory literally from the package name); no `README.md`, `DECISIONS.md` or `pom.xml` |

- "Run `/sync-docs` on module X" means `--module X`; "on package X" means `--package X`.
- `/sync-docs --module <name>` is running `module-doc-standards` and `module-readme-standards` over that
  module, as their "Applying this standard" sections define: every pre-existing line is checked, not
  only what a diff touched.
- Every file inside a named scope is checked exhaustively, never sampled; a diff scope starts from exactly and only the
  files it touched, and Steps 2 and 2b add the documents tied to them. A partly checked scope is not done.
- Diff mode re-checks only documents tied to the diff, so a document naming something an earlier commit
  renamed or removed stays stale until the next `--full-audit`. Run `--full-audit` periodically (before a
  release, every few weeks); it does not replace diff mode.

## Diff mode

### Step 1 — Changed files

Run the command of the scope (table above). The result is the changed files; deleted and renamed files
count, their old path is matched against the Step 2 table, and Step 3 verifies that no current document
still names it. Print the changed files.

### Step 2 — Targets

Every row whose pattern matches a changed file adds its targets. The skill named in a row governs that
target; how each target is checked and corrected is in Step 3.

| Changed file pattern | Targets |
|---|---|
| `**/*.java` (main or test), `**/pom.xml`, `**/db/*changelog/**`, `**/DECISIONS.md`, `backlog/**`, `.claude/commands/*.md`, `.claude/skills/*/SKILL.md`, `.claude/nav/flows.md`, `.claude/rules/*.md`, root `CLAUDE.md` | the generated map: `docs/architecture/data/architecture-model.json`, `docs/architecture/architecture-map.html`, `docs/architecture/data/arch-embed-index.md` |
| `**/*.java`, `**/pom.xml` | `.claude/rules/<module>.md` and `DECISIONS.md` of each changed module; `backlog/tasks/`; the module's own `README.md` (`module-readme-standards`) |
| `**/*.java` | that file's class-level and method-level Javadoc (`module-doc-standards`) |
| `**/pom.xml` | that file's file-level header and its touched `<dependency>`/`<exclusion>`/version-pin comments (`module-doc-standards`) |
| `**/db/*changelog/**` | the `remarks=` of every new or changed `<column>`/`<createTable>` (root `CLAUDE.md`) |
| `**/DECISIONS.md` | `.claude/nav/adr-index.md` |
| `scripts/**`, `docker-compose*.yml`, any other root-level infra/tooling file | root `INFRASTRUCTURE.md` (`app-readme-standards`); the `README.md` of the script group the file belongs to (`infra-readme-standards`) |
| any infra/tooling file: `scripts/**`, `docker-compose*.yml`, `.properties`/`.env`, YAML config, `.gitignore`/`.gitattributes`, bash/batch/JS/Python | that file's file-level and per-function header (`infra-doc-standards`) |

### Step 2b — Related documents

A related document mentions what the change touched without being a target of Step 2.

1. For every name the diff adds, removes or renames (class, method, table, column, enum value, module,
   endpoint, script flag), search all current documents (`*.md`, script headers, `*.js`, `*.yml`, and the
   open tasks in `backlog/tasks/`; not `backlog/completed/`, not `backlog/research/`, not generated
   files). A name counts when it identifies something the diff changes (a path, flag, class, command or
   section it adds, removes, renames or redefines); a hit counts when the document states or depends on
   that thing, not when it only mentions the name in passing. Every hit is a related document and is
   checked like a target.
2. When the diff adds, removes or renames a module, check every document that enumerates the modules
   (each list, table and diagram) for completeness against the root `pom.xml` `<module>` entries. A name
   somewhere in a file does not prove that each of its lists is complete.
3. If no Step 2 row matched (the generated-map row counts as a match) and this step found no related
   document, print "No architectural changes detected." and the `Files in scope` lines of the Step 4
   template, then stop.
4. Print the targets and the related documents before Step 3.

### Step 3 — Update

1. Check every target and related document against the code by the rules below.
2. Each Javadoc, `pom.xml` header and infra header check goes to an Agent dispatch with `model: "haiku"`:
   one agent per module with its changed files, and one agent for a changed file outside any module,
   even for one file. Its prompt
   carries the file list, the checklist to apply (the skill named in Step 2 and the rules below), the
   output file `/tmp/sync-docs-audit-<agent-name>.md` or an equivalent file, with one line per finding
   `file:line -- MISSING|STALE|VALID -- evidence`, and that it edits nothing.
3. Show the planned corrections and ask (hard limits).
4. Apply the corrections:
   - Generated map: the three files in the Targets column of the first row of Step 2 are never edited by
     hand, and no markdown under `docs/architecture/` is hand-written content. The files the generator
     reads, including the `<!-- #arch-embed:KEY --> ... <!-- /#arch-embed -->` sections of `CLAUDE.md`
     and `.claude/rules/*.md`, are listed in the header of
     `docs/architecture/scripts/generate-architecture-model.sh` (the generator); editing a marked
     section and regenerating is the update of that content. The generator also reads line 5 of each
     `.claude/rules/<module>.md` as that module's one-line description.
   - Changelog: the `remarks=` is written in the changelog itself, never in a generated document.
   - `DECISIONS.md`: mark a realized open goal done, with the date; update the Status of a superseded
     entry; annotate an entry whose pattern no longer exists in code as superseded, never delete it; a
     new architectural pattern or constraint gets a new ADR only through `/record-decision`.
   - `.claude/nav/adr-index.md`: run `bash .claude/nav/scripts/generate-adr-index.sh`.
   - `.claude/rules/<module>.md`, and root `CLAUDE.md` whenever the diff touches something it names
     (its module layout, its reference implementations): factual corrections only, such as "What it
     owns" after classes were added or removed and stale package paths; never rewrite prose.
   - `backlog/tasks/`: create a task for a violation or open goal not yet tracked; close a task (move
     it to `backlog/completed/tasks/`) when the code confirms it resolved; update a task whose scope or
     constraints changed.
   - Root `README.md` and root `INFRASTRUCTURE.md`: follow `app-readme-standards` before editing either.
     `INFRASTRUCTURE.md` is updated when the diff changes what it documents. Root `README.md` has no
     generated architecture section; if one is ever added it links Diagrams › Module Dependencies of
     `docs/architecture/architecture-map.html` instead of embedding a diagram.
   - A module's own `README.md` (`module-readme-standards`) and a script group's own `README.md`
     (`infra-readme-standards`): accuracy only. It uses the section shape its standards skill defines,
     names every class, endpoint, package or script the diff touched or removed, and holds no stale
     reference. Fix that drift; never rewrite the whole file (Full Audit Mode corrects every claim it
     finds wrong, not only the drift of a diff).
   - Javadoc (`module-doc-standards`): class-level Javadoc is always required, no exception, even for a
     `record` or `enum`; check every class the diff touched, not only new ones. A missing one gets one
     line stating the real, non-obvious fact, never filler that restates the class name. Method-level
     Javadoc is one line or none (`.claude/rules.md`), verified against the method body.
   - `pom.xml` (`module-doc-standards`): the file-level header is required, no exception; a missing one
     gets one line `<!-- Description: ... -->` before `<project>`. Re-check every touched
     `<dependency>`/`<exclusion>`/version pin against the skill's "comment only the non-obvious" rule.
   - Infra/tooling file: its file-level and per-function header per `infra-doc-standards`.
5. Read every corrected document again after its edit. Every corrected claim names the file and line in
   code that confirms it; replacing a word without that check is not a correction.
6. Run `bash docs/architecture/scripts/generate-architecture-model.sh` last, once, after every other
   edit, when a changed file or a file edited in this step is one the generator reads (its header lists them;
   the Pattern column of the first row of the Step 2 table covers them).

### Step 4 — Report

Print exactly this template; write "none" for an empty part.

```
## /sync-docs report -- <scope>
Files in scope:     <file> -- verified against <source>        (one line per file; for a deleted file, the search showing that no current document names it)
Related documents:
| Document | Why related (Step 2 row or Step 2b name) | Verified against | Result |
Related, not checked: <document -- reason>
Updated:            <file> -- <corrected claim> -- confirmed by <file:line>
Tasks:              created <ids>; closed <ids>; updated <ids>
ADRs:               added <ids>; status changed <ids>
New violations:     <list>
Not checked:        <list>
```

## Full Audit Mode (`--full-audit`, `--module`, `--package`)

Steps 1 to 2b do not run: the scope is the file set of the scopes table, regardless of recent activity.
With `--module <name>` or `--package <name>`, Step A1 enumerates only that file set and Steps A2-A6 run
unchanged on it, with the same rigor; with `--package <name>` only the Javadoc checks of Step A2 apply. Step A0 always runs; Step A5 runs
only with `--full-audit`.

### Step A0 — Regenerate

1. Run `bash .claude/nav/scripts/generate-adr-index.sh`, then `git diff --stat .claude/nav/adr-index.md`;
   a change means a `DECISIONS.md` entry changed since the last regeneration.
2. Run the generator, so Step A2 reads a fresh `docs/architecture/data/architecture-model.json`.
3. Report the outcome of both in Step A6, either way.

### Step A1 — Enumerate targets

1. `find . -maxdepth 3 -iname "DECISIONS.md" -o -maxdepth 3 -iname "README.md"` (depth 3, not 2: nested
   tool folders such as `scripts/ci/` and `.claude/nav/scripts/` carry their own files one level deeper).
2. `ls .claude/rules/*.md`, plus root `CLAUDE.md` and root `INFRASTRUCTURE.md`.
3. Every `README.md` is governed by the skill for its location, never a generic convention: the repo
   root → `app-readme-standards`; a Java module → `module-readme-standards`; a script group or other tool
   folder (`scripts/<name>/README.md` and similar) → `infra-readme-standards`.
4. Every module without a `README.md`, a `DECISIONS.md` or a `.claude/rules/<module>.md` is flagged in
   the report, not skipped; a missing file alone is not necessarily a problem.
5. Every `*.java` file (`module-doc-standards`) and every infra/tooling file (`scripts/**`,
   `docker-compose*.yml`, `.properties`, YAML config, bash/batch/JS/Python; `infra-doc-standards`) is in
   scope, pre-existing lines included.
6. Print the number of files per kind and the modules flagged in item 4.

### Step A2 — Verify every claim

- First source: for ADR id and title, README content and entity, key-service, contract and table claims,
  read the module's node in `docs/architecture/data/architecture-model.json`; use grep or `Read` only
  for what it does not cover. Javadoc prose, infra headers and ADR hygiene are checked on the raw files.
- Each `DECISIONS.md` entry: check every class, method, field, config value, CSS class or file path it
  names in current source; never assume it from the ADR text. Classify the entry VALID (matches), STALE
  (a named thing was renamed, moved or removed), SUPERSEDED (a later ADR replaces it and its Status does
  not say so) or DONE-GOAL-NOT-MARKED (an open or future-work item that already shipped).
- Each `DECISIONS.md` file: no numbering gaps or duplicates, every entry has a `Status:` line, the Status
  vocabulary is consistent (`Accepted`, `Superseded`, `Deprecated`, with dates where useful).
- Each `README.md`: check every concrete claim (class, package path, command, table, port, config value,
  dependency version) against code. When it and the module's `.claude/rules/<module>.md` disagree, code
  decides; report every case where the rules file is the stale one.
- Each `.claude/rules/<module>.md` and root `CLAUDE.md`: check every concrete claim the same way (names,
  paths, "What it owns" lists, cross-module dependency claims, config values, naming examples), above
  all claims about other files or modules.
- Each `*.java` file, per the pre-write checklist of `module-doc-standards`: a class-level Javadoc exists;
  every method Javadoc describes what its body does, verified by reading it, not its callers; no comment
  cites a task or ticket number (remove it and route the rationale per that skill's table). Each
  infra/tooling file: file-level and per-function headers present and accurate (`infra-doc-standards`).
  Classify each MISSING, STALE or VALID.
- Each `pom.xml` (`--full-audit` and `--module`): the file-level header and the dependency, exclusion and
  version-pin comments, per `module-doc-standards`; classify MISSING, STALE or VALID.
- With `--full-audit` only: check the "Built-in Claude Code skills" table of `.claude/nav/flows.md` for
  completeness against the skills listed as available in the current session.

### Step A3 — Parallelize

1. Split the checks across parallel agents by size: one per about 500-1000 lines of `DECISIONS.md`; one
   for all `README.md` files; one for all `.claude/rules/*.md` plus root `CLAUDE.md`; one per module (or
   per few modules of comparable file count) for its `*.java` Javadoc and its infra/tooling files.
2. Dispatch every agent with `model: "haiku"`, Javadoc prose accuracy included. Its prompt carries the
   file list, the Step A2 checks for that file kind, the output file `/tmp/sync-docs-audit-<agent-name>.md` or
   an equivalent file, with one line per finding (file, line, claim, verdict, evidence), and that it edits nothing.
3. The coordinator reads each agent's output file, not its reply, for Step A4 and Step A6; Steps A2
   and A3 print nothing themselves.

### Step A4 — Apply fixes

1. Ask once (hard limits), then apply Step 3 items 4 to 6: the corrections, the re-read after every
   edit, and the generator run last, once, when an edited file is one the generator reads (Step 3 item 6).
2. When code does what an ADR rejected (a reversed decision, not a naming drift), annotate the ADR to
   current reality and flag it in the report; reverting code is a separate decision for the owner of
   that code.

### Step A5 — Aggregate operational notes

1. Parse every `## Operational notes` block in `backlog/completed/tasks/` and `backlog/tasks/` as
   `key: value` lines, not prose; the keys are defined by "Final reports record real operational data"
   in `.claude/rules.md`. No audit date is recorded, so every block counts.
2. Aggregate: token cost by purpose (`token_cost_review`, research, verification), sum and average, `n/a`
   excluded from the average, not counted as zero; `context_loading_matched` yes/no/n/a tally grouped by
   `context_loading_task_type`; `flows_matched` yes/no/n/a tally; `haiku_decomposition_used` yes/no tally
   and the rework rate of `haiku_decomposition_rework_needed` (sum of corrections over sum of dispatches,
   `n/a` excluded).
3. The result is the evidence the governing rule of `.claude/nav/` asks for before navigation content is
   added or trimmed. Too few blocks to say anything is reported plainly as a finding.

### Step A6 — Report

The Step 4 template, plus:

```
ADR verdicts:       <DECISIONS.md> -- VALID <n>; <entry> -- <verdict> -- <reason>   (one block per file)
Document verdicts:  <README.md | CLAUDE.md | .claude/rules/<module>.md> -- the same shape
Javadoc/headers:    <module> -- MISSING <n>, STALE <n>, VALID <n> (Javadoc); MISSING <n>, STALE <n>, VALID <n> (infra headers)
Missing files:      <module> -- no README.md | DECISIONS.md | .claude/rules/<module>.md
Code left unfixed:  <file:line -- stale code or reversed decision>
Regeneration:       ADR index <changed|unchanged>; generator <outcome>
Operational notes:  <Step A5 aggregate>
Built-in skills:    <flows.md table complete | missing: <skills>>   (--full-audit only)
```

## Documentation Rules

- The hard limits at the top of this file apply to every documentation edit.
- Diagrams use Mermaid.
- Every conclusion references real class names or file paths.
- ADR format: `.claude/commands/record-decision.md` step 4.
- Task format: `.claude/commands/new-task.md` step 3.
- Never duplicate a task; update the existing one.
