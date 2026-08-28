---
name: infra-doc-standards
description: File-level and per-function header conventions for infrastructure/tooling files -- bash/batch scripts, docker-compose*.yml, .properties (incl. .env, lombok.config), YAML config, .gitignore/.gitattributes, JavaScript, and Python files.
allowed-tools: Read Edit Write Bash
---

# Infra Doc Standards

File-level and per-function header conventions for infrastructure/tooling files — bash/batch
scripts, `docker-compose*.yml`, `.properties` — as distinct from `module-doc-standards` (the same
kind of file-level convention, applied to Java source files' Javadoc and `pom.xml` comments
instead) and from `infra-readme-standards` (a script-group directory's own `README.md` and
Flow-diagram conventions, once every file in it already has a complete header per this skill).

## ⛔ Files first, then README — highest priority in this document

Applies `.claude/rules.md`'s "One fact, one canonical home" rule's "Atomic unit first, then
directory-level index" principle to this skill's own domain, where the atomic unit is the whole
file. This section states this domain's specific ordering mechanics; it does not restate the
general principle itself.

Files are the highest-priority artifact. A file's own header must fully and precisely describe
everything that file does — every flag, every conditional branch, every real side effect — lean
and laconic but with no gap in coverage. Finish every file's header first; files are independent
of each other, so this can happen in any order or in parallel. Do not open or reference the
directory's `README.md` at all while doing this, not even to check for overlap.

Only once every file in the directory has a complete header does `README.md` get written or
touched. `README.md` may only ever contain information that is fundamentally about more than one
file at once — the sequence/interaction between files, why a step is grouped or blocks another,
what the whole chain produces end to end, a flow diagram of the real call chain, which
script-groups/files actually source a shared one. What any single file does, on its own, is never
README's job, at any level of brevity — not even a one-sentence gloss "for navigation." If a fact
is already covered by a file's own header, it must never appear in `README.md` in any form — not
restated, not reworded, not summarized. Before writing any README sentence, check: does an existing
file header already say this, or could it be answered by reading one file alone? If yes, drop the
sentence.

A directory's `README.md` describes only that directory's own level: its own files and how they
interact with each other. It never restates something a nested subdirectory's own `README.md`
already covers (a subdirectory speaks for itself), and never restates the wider context a parent
directory's own `README.md` provides.

A shared environmental constraint or gotcha that two or more otherwise-unrelated files
independently hit (not a call chain, not a caller/callee relationship — just a coincidence that
they need the same workaround) is not a "sequence/interaction between files" fact sanctioned for
`README.md`. Each affected file explains its own reason in its own header, exactly as if the
other files didn't exist. The test: if file A's header already explains its own reason and file
B's doesn't yet, the fix is never "write the combined explanation in `README.md` instead" — it's
"add the explanation to file B's own header, matching file A's existing pattern" (in scope) or
"flag it as a finding" (out of scope). A `README.md` paragraph that survives only because some
referenced file's header is still incomplete is itself evidence the paragraph doesn't belong.

This is the single governing statement of the file-vs-README duplication rule across both this
skill and `infra-readme-standards` — every other section touching the same topic (finding
placement here, the `Flow` section's own scope and the root `scripts/README.md`'s entry-point
descriptions in `infra-readme-standards`) defers to this rule rather than restating it.

## Base standard

Script-header documentation in this repo is grounded in the
[Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) — the most widely
cited authoritative convention for bash, and the only one found (2026-08-14 web search) with an
actual structured header spec rather than free-form prose advice. Two relevant parts of it:

- **File-level:** every file must have a top-level comment giving a brief overview of its
  contents, placed immediately after the shebang line.
- **Function-level:** every function's header comment describes its API behaviour via five
  labeled fields — `Description`, `Globals` (global variables read/modified), `Arguments`,
  `Outputs` (what it writes to stdout/stderr), `Returns` (exit status, if not just the last
  command's).

## Where we deviate from Google, and why

| Google | Ours | Why |
|---|---|---|
| Structured block only for functions; file-level is one free sentence | Structured block at the **file** level | Most of our scripts are standalone CLI entry points, not function libraries — the file itself is the unit of invocation |
| `Arguments` | `Usage` | Merges invocation syntax + flag meaning in one field (split into a per-flag list when there are 3+ flags — see example below) |
| — | `Uses` (added) | Google's guide has no notion of a script orchestrating docker/mvn/node/python — ours routinely does |
| — (`Globals` exists only in Google's *function*-level block, no file-level equivalent) | `Env` (added, file level) | Not a rename — Google never addresses this at file level. Our scripts rarely have shell-global state, but frequently read overridable `VAR:-default` environment variables, so we add a field for it |

## Where we follow Google as-is

| Google | Ours | Why |
|---|---|---|
| `Outputs` and `Returns` as two separate fields | Same — `Outputs` (what it writes) and `Returns` (exit codes) stay split | No reason to merge them; Google already got this right |
| Visual delimiter framing the header block | `# ── Header ── ... # ──` (our own marker style, same idea) | Bounds the block explicitly instead of relying on context — the two real parsing bugs this session both trace back to that ambiguity |

## The file-level header (target shape)

```
# ── Header ──────────────────────────────────────────────────────────────────
# Description: what this file does, one short paragraph.
# Usage: how it's invoked. "None" if it takes no arguments. 3+ flags -> one flag per line.
# Uses: tools/runtimes it invokes (bash, docker, mvn, node, ...). "None" if none.
# Env: overridable environment variables, with defaults. "None" if it reads none.
# Input: what it reads (files, APIs, other scripts' output). "None" if none.
# Outputs: what it produces (files written, side effects) -- no exit codes here.
# Returns: exit codes and what each one means.
# ────────────────────────────────────────────────────────────────────────────
```

The `# ── Header ──` / `# ──` pair bounds the block explicitly, so a parser (or a human) never has
to guess where it ends from context (a blank line, the next line of code, or nothing at all).

### Example — `scripts/deploy-and-run.sh`

```bash
#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Full prod deploy -- builds Docker image from scratch, starts all services on port 8081.
# Usage: bash scripts/deploy-and-run.sh [--reset] [--restart-infra] [--file] [--no-cache] [--reset-only-db] [--prune-all]
#   --reset          wipe DB/MinIO volumes, then rebuild
#   --restart-infra  restart infra containers only (no rebuild)
#   --file           filtered console output + full log to /tmp/deploy.log
#   --no-cache       force rebuild ignoring Docker layer cache
#   --reset-only-db       truncate app tables (reset-clean.sql) before starting the app
#   --prune-all      also prune stopped containers/volumes HOST-WIDE (see .claude/nav/adr-index.md)
# Uses: bash, docker buildx, docker compose.
# Env: NETWORK (default advertisement), DB_CONTAINER (advertisement-db), MINIO_CONTAINER
#   (advertisement-minio), APP_CONTAINER (marketplace-app), APP_IMAGE (marketplace-app), DB_PORT
#   (5432), MINIO_PORT (9000), MINIO_CONSOLE_PORT (9001), APP_PORT (8081) -- all overridable, used
#   by scripts/ci.sh for its isolated e2e stack.
# Input: repo source, .env (POSTGRES_IMAGE, S3_* fallback defaults).
# Outputs: running marketplace-app container on APP_PORT.
# Returns: 0 on success, non-zero on build/startup failure.
# ────────────────────────────────────────────────────────────────────────────
```

Why this example: `Usage` breaks into one flag per line here rather than staying a single sentence
— chosen because the file takes 6 flags, past the "3+ flags" threshold where a single-sentence
`Usage` line would force a reader to parse a wall of `[--flag]` brackets instead of scanning a list.

## `Usage`'s no-flags invocation must be stated explicitly when every flag is optional

When every flag in a script's `Usage` is optional (no required positional argument), the field
must open with one line stating what the bare invocation does, before the per-flag breakdown --
never left only implied by `Description`. A reader scanning `Usage` for "what do I get if I just
run this" should never have to cross-reference a separate field to find out.

Example — `scripts/deploy-and-run.sh`:
```
# Usage: bash scripts/deploy-and-run.sh [--reset] [--restart-infra] [--file] [--no-cache] [--reset-only-db] [--with-tests] [--from-scratch] [--prune-all]
#   (no flags)       reuse scripts/build-and-test.sh's shared jar -- no Docker image built, no
#                    tests run -- start all services on port 8081
#   --reset          wipe DB/MinIO volumes, then rebuild
#   ...
```

Why this rule: matches the existing "3+ flags -> one flag per line" threshold's own reasoning --
once a flag list is long enough to need one-per-line already, the plain invocation's own behavior
is exactly the fact most likely to get lost in it without its own explicit line.

### `Outputs`/`Returns` stay two separate fields (per Google) — example, `check-architecture-model-freshness.sh`

```
# Outputs: an ERROR line naming which file is stale, if any -- no file is written, the committed
#   files are always restored afterward.
# Returns: 0 = up to date, 1 = stale.
```

Why this example: chosen over merging both into one field because this script's two facts answer
genuinely different reader questions — "what does it print" (`Outputs`) vs. "what does its exit
code mean for my CI gate" (`Returns`) — collapsing them would force a reader hunting for the exit
code to first parse past the unrelated ERROR-message description.

### Explicit "None" instead of omitting a field — example, `screenshot-architecture-map.sh`

```
# Description: Screenshots every screen of architecture-map.html...
# Usage: None -- always run with no arguments.
# Uses: bash, a headless Playwright browser...
```

Why this example: `Usage` states `None` explicitly rather than being left out, chosen because an
absent field reads as "not documented yet" while `None` reads as "checked, confirmed empty" — the
distinction matters precisely because this skill's own rule elsewhere requires every field present.

## Non-obvious operational side effects belong in the header, not just `DECISIONS.md`

A field's job isn't just "the mundane primary behavior" — if a script has a real, non-obvious
operational side effect (auto-resets a server setting every run, self-heals a config drift,
silently wipes something under a condition), that fact belongs in the relevant field's own text
(usually `Outputs`, sometimes `Env`) — not just buried in `DECISIONS.md`, where a reader has to
already know to look. The header is what someone sees first; the adr-index pointer convention for
the full reasoning is covered by "Header fields follow the same 'no real file as pointer'
discipline" below.

## Explicit container/image names belong in the Description field

When a script gives its own Docker container or image a fixed, meaningful name (rather than
leaving Docker to assign a random one), that name goes in the header's `Description` field, not
just in the script body — so a reader can `docker exec`/`docker logs`/`docker inspect` into a
running instance directly, without first opening the script to find out what it's called.

## Header fields follow the same "no real file as pointer" discipline as README/SKILL/rules/commands

A header field (`Description`/`Usage`/`Env`/`Outputs`, etc.) never names a real file elsewhere in
the repo as a pointer — the same discipline `.claude/rules.md`'s "name a real file only when
unavoidable" rule already applies to `SKILL.md`/commands/`rules.md`/`README.md`. The one sanctioned
exception, matching `.claude/rules.md`'s ADR-citation rule: a generic "see `.claude/nav/adr-index.md`"
(never a specific `ADR-NNN` number) — no other real file name belongs in a header field.

## `Env` field distinguishes "set automatically by a caller" from "set directly by you"

When a script is invoked by another script/container-run rather than typed directly by a human,
its `Env` field must mark each variable as one of two kinds, not describe them all the same way:
- **Set automatically by whatever invokes this file** (translated from that caller's own flags,
  passed via `-e VAR=value` on `docker run` or similar) — never exported directly by a user.
- **May be exported directly in the calling shell** — a user genuinely might set this themselves.

Mixing both kinds under one undifferentiated list (as if all of them were things a user types)
produces exactly the confusion this rule exists to prevent: a reader can't tell "do I type
`export X=...` myself, or does something else do this for me automatically."

## `UPPER_CASE` for constants (including CLI-flag results) and exported env vars; `lower_case` only for genuinely local/loop state

Per the Google Shell Style Guide's own "Constants and Environment Variable Names" section:
constants and anything exported to the environment are `UPPER_CASE`. This explicitly includes a
variable set once from a CLI flag and never reassigned afterward — the Guide's own example ("some
things become constant at their first setting, for example via getopts... it's OK to set a
constant in getopts or based on a condition, but it should be made readonly right after") treats a
parsed-flag variable as a constant from that point on, same as a value that was fixed from the
start. `lower_case` belongs to the Guide's separate "Variable Names" section — genuinely local,
potentially-reassigned state such as a loop counter or an accumulator, not a value that's fixed
once parsing completes.

In practice this means most of a typical script's top-level variables (paths, image/container
names, flags derived from CLI arguments) legitimately stay `UPPER_CASE` — matching this repo's
existing convention throughout `scripts/`. This is not a license to rename them.

## `Description` stays lean — flag-specific detail belongs in `Usage`/`Env`, not duplicated here

`Description` states the file's core purpose in one short paragraph — what it fundamentally does,
not a blow-by-blow of every flag's own conditional behavior (that's `Usage`'s job for CLI flags,
`Env`'s job for environment variables). Cramming per-flag detail into `Description` too duplicates
what `Usage`/`Env` already say, and makes the one field meant to answer "what is this file, in
short" hard to actually read at a glance.

When a file's own real behavior is too complex to state precisely in one short paragraph,
`Description` leads with a lean, one-sentence gist, then continues with the fuller detail in that
same field — never split into a short version living in the file's header and a separate summary
living in a README instead. A reader always gets the brief version from the same place as the full
one: the file's own header.

## Where a real finding about a script or directory gets recorded

A significant fact discovered while actually running or investigating a script (not obvious from
reading the code alone — an environment quirk, a real risk, why something behaves the way it
does) gets written into whichever file the finding is actually about, not left only in a chat
transcript or a backlog issue — placement follows "Files first, then README" above.

A finding captured only in a backlog issue during investigation is a legitimate intermediate step
(the issue is where it's confirmed and worded first) — but once confirmed, it belongs in the file
it actually describes, not left permanently in the issue as the only record.

## Per-function headers — only for files that are `source`d by other scripts

Google's structured block is really meant for functions. Most of our scripts don't export
functions other scripts call — but a few genuinely are small libraries, `source`d by several other
scripts. For a file like this, each function meant to be called externally gets its own
Google-style block, in addition to the file's own header:

```bash
#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Idempotent check-and-install for Docker CLI plugins this sandbox doesn't ship by
#   default (buildx, compose v2). Source this file, call the function you need.
# Usage: source scripts/utils/ensure-docker-plugins.sh; then call ensure_buildx / ensure_docker_compose.
# Uses: bash, curl.
# Env: None.
# Input: None.
# Outputs: installs a plugin into ~/.docker/cli-plugins/ if missing.
# Returns: 0 always (installs on demand, no-ops if already present).
# ────────────────────────────────────────────────────────────────────────────

#######################################
# Ensures `docker buildx` is available, installing it if missing.
# Arguments: None.
# Outputs: progress messages to stdout.
# Returns: 0 always.
#######################################
ensure_buildx() {
  ...
}
```

A purely internal/private function (never called from outside its own file) does not get one of
these — a one-line comment is enough, per `.claude/rules.md`'s "one line or none" rule.

Why this example: `ensure_buildx` gets a per-function header while `ensure-docker-plugins.sh`'s own
internal helpers don't, because this specific function is called from outside the file
(`scripts/sonar/run.sh` and others `source` it and call it directly) — the per-function block exists
for the caller who never opens this file, not as a blanket rule for every function in a library file.

## File-type-specific header rules

The base 7-field header shape (`Description`/`Usage`/`Uses`/`Env`/`Input`/`Outputs`/`Returns`)
applies as-is to `.sh`/bash files, per everything above. Each file type below states only where
and why it deviates from that base shape.

### JavaScript files (`.js`)

Grounded in JSDoc's `@fileoverview` convention from the
[Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html) — the same
authority family as the Google Shell Style Guide this skill's bash convention is already grounded
in, explicitly recommended whenever a file consists of more than a single class definition. A
runnable/`require()`d JavaScript file is the same shape as a `.sh` file (real logic, not passive
config) — same 7-field structured header, in a `/* ... */` block instead of `#`-prefixed lines (JS
block comments span multiple lines natively, unlike YAML).

```javascript
/* ── Header ──────────────────────────────────────────────────────────────────
 * Description: what this file does, one short paragraph.
 * Usage: how it's invoked/required. "None" if it's a library only, never run directly.
 * Uses: notable dependencies. "None" if none.
 * Env: overridable environment variables, with defaults. "None" if it reads none.
 * Input: what it reads (files, other modules' exports). "None" if none.
 * Outputs: what it produces (side effects, exported values) -- no exit codes here.
 * Returns: exit codes, if this file is a runnable entry point. "N/A" for a pure library module.
 * ──────────────────────────────────────────────────────────────────────────── */
```

Per-function headers apply the same as `source`d bash library files (see "Per-function headers"
above): a helper file `require()`d by 2+ other files gets its own header per exported function, not
just the file-level block.

### Python files (`.py`)

Python's own [PEP 257](https://peps.python.org/pep-0257/) governs module/function/class
docstrings (`"""triple-quoted"""`) — aimed at a different consumer (introspection, `help()`,
generated API docs), not a script's own usage instructions. A `#`-prefixed header comment block
at the top of the file, right after the shebang, is a separately documented, common convention
for exactly the script-level purpose this skill's header serves — same shape as `.sh`, same
7-field header.

```python
#!/usr/bin/env python3
# ── Header ──────────────────────────────────────────────────────────────────
# Description: what this file does, one short paragraph.
# Usage: how it's invoked. "None" if it takes no arguments.
# Uses: notable dependencies. "None" if none.
# Env: overridable environment variables, with defaults. "None" if it reads none.
# Input: what it reads. "None" if none.
# Outputs: what it produces -- no exit codes here.
# Returns: exit codes and what each one means.
# ────────────────────────────────────────────────────────────────────────────
```

Per-function headers apply the same as `source`d bash library files (see "Per-function headers"
above) for a Python module imported by other scripts rather than run directly.

### Windows (`.bat`) delegator files — `same as <file>` forwarding

Most `.bat` files in this repo are pure delegators to a sibling `.sh` (invoke it through WSL, add
nothing of their own). For these, fields whose value is identical to the target `.sh`'s own header
get a short `same as <file>` instead of restating the content — only fields with something genuinely
`.bat`-specific (a narrower flag set, Windows-only usage tips) keep their own full text.

#### Example — `scripts/deploy-and-run.bat`

```bat
@echo off
REM ── Header ──────────────────────────────────────────────────────────────────
REM Description: Full prod deploy -- builds Docker image from scratch, starts all services on port 8081.
REM Usage:
REM   scripts\deploy-and-run.bat                    -- full image rebuild + start
REM   scripts\deploy-and-run.bat --no-cache         -- force rebuild ignoring Docker layer cache
REM   scripts\deploy-and-run.bat --reset            -- wipe DB/MinIO volumes, then rebuild
REM   scripts\deploy-and-run.bat --restart-infra    -- restart infra containers only (no rebuild)
REM   scripts\deploy-and-run.bat --reset-only-db         -- truncate app tables before starting the app
REM Uses: WSL (wsl bash scripts/deploy-and-run.sh).
REM Env: same as scripts/deploy-and-run.sh.
REM Input: None.
REM Outputs:
REM   Filtered console output (WSL/Git Bash): bash scripts/deploy-and-run.sh 2>&1 | tee /tmp/deploy.log | grep -E "BUILD|ERROR|Started|Waiting|ready|FAILED"
REM   Stream full app log after deploy: docker logs -f marketplace-app
REM Returns: same as scripts/deploy-and-run.sh (0 = success, non-zero = build/startup failure).
REM ────────────────────────────────────────────────────────────────────────────
```

Why this example: `Usage`/`Outputs` keep full text here instead of `same as scripts/deploy-and-run.sh`
because this `.bat` genuinely behaves differently at the invocation surface (Windows path syntax,
WSL/Git-Bash-specific piping for filtered console output) — `Env`/`Returns` do collapse to `same as`
because those facts are identical to the `.sh` sibling with nothing Windows-specific to add.

#### Trivial delegators (no unique content of their own)

For a `.bat` that's just `@echo off` + one `wsl bash <sibling>.sh %*` line — every field is
`same as <sibling>.sh` except `Description`/`Uses`:

```bat
@echo off
REM Description: Windows entry point -- delegates to <sibling>.sh via WSL.
REM Usage: same as <sibling>.sh.
REM Uses: WSL (wsl bash <sibling>.sh).
REM Env: same as <sibling>.sh.
REM Input: same as <sibling>.sh.
REM Outputs: same as <sibling>.sh.
REM Returns: same as <sibling>.sh.
```

### Docker files (`Dockerfile`, `docker-compose*.yml`)

A Dockerfile isn't invoked directly with its own flags the way a script is — it's built via
`docker build`, so two of the fields carry a different meaning. `Returns` is the one field whose
value never changes across any Dockerfile — a type-level constant stated once here, not a per-file
fact worth repeating on every Dockerfile's own header.

| Field | Meaning for a script | Meaning for a Dockerfile | Always the same value? |
|---|---|---|---|
| `Description` | what the script does | what image it builds and why | No -- varies per file |
| `Usage` | CLI flags | the `docker build -f <path> -t <tag> .` invocation (plus real `--build-arg`s, if any) | No -- varies per file |
| `Uses` | tools/runtimes it invokes | base image(s) (`FROM ...`), build tools used inside | No -- varies per file |
| `Env` | overridable env vars it reads | the Dockerfile's own `ARG`/`ENV` instructions -- the native equivalent | No -- varies per file |
| `Input` | files/APIs it reads | what it `COPY`s in (source tree, `pom.xml`, ...) | No -- varies per file |
| `Outputs` | files written, side effects | the resulting image (name/tag) and what's inside (exposed port, entrypoint) | No -- varies per file |
| `Returns` | exit codes | build success/failure belongs to the `docker build` command, not the file | Yes -- always `N/A`, field omitted |

**Multi-stage builds** don't get a full 7-field block per stage -- one file-level header covers the
whole file, and each `FROM ... AS <stage>` line gets a one-line comment stating that stage's role,
per the general "one line or none" rule.

#### Example — `scripts/ci/Dockerfile`

```dockerfile
# syntax=docker/dockerfile:1
# ── Header ──────────────────────────────────────────────────────────────────
# Description: CI-runner image -- one container the user interacts with (scripts/ci.sh ->
#   scripts/ci/run.sh), Docker-outside-of-Docker: the host's docker.sock is mounted into it at
#   `docker run` time so it can create/tear down its own isolated ci-* sibling containers without
#   touching the persistent dev stack. Full design rationale: see .claude/nav/adr-index.md.
# Usage: docker build -f scripts/ci/Dockerfile -t ci-runner .
# Uses: eclipse-temurin:25-jdk base image.
# Env: DAGU_VERSION, DAGU_HOME, DAGU_PORT, DAGU_AUTH_MODE (all declared via ENV, not
#   caller-overridable at build time).
# Input: repo source (mvnw, pom.xml).
# Outputs: ci-runner image -- see scripts/ci/docker-entrypoint.sh for the in-container
#   orchestration logic.
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk
```

Why this example: `Env` lists `DAGU_VERSION`/`DAGU_HOME`/`DAGU_PORT`/`DAGU_AUTH_MODE` as
non-caller-overridable, chosen specifically because a Dockerfile's `ENV` instructions are baked
into the image at build time — unlike a script's `Env` field (which usually distinguishes
caller-set vs. user-exportable), a Dockerfile's `Env` field only ever has one kind of entry, so no
such split applies here. `Returns` is omitted entirely, not just left "None" — chosen because build
success/failure belongs to the `docker build` command itself, never a fact this file's own header
could state (see the field-meaning table above).

### `.properties` files (and the same shape for `.env`/`lombok.config`)

A `.properties` file is passive configuration, not something invoked — `Uses`/`Input`/`Outputs`/
`Returns` are dropped entirely: their value never changes across any `.properties` file (`None`/
`None`/`None`/`N/A` respectively, for the reasons in the table below) — a type-level constant
stated once here, not a per-file fact worth repeating on every `.properties` file's own header.
Only `Description`/`Usage`/`Env` actually vary per file, so those are the only three fields such a
file's header carries. A root `.env` (`KEY=VALUE`, read via `${VAR}` substitution) and
`lombok.config` (`key = value`, read by the Lombok annotation processor at compile time) are the
same passive `KEY=VALUE` shape — same three-field table, not a separate section.

| Field | Meaning for a script | Meaning for a `.properties` file | Always the same value? |
|---|---|---|---|
| `Description` | what the script does | what it configures, for which tool | No -- varies per file |
| `Usage` | CLI flags | not invoked -- state which file/flag actually reads it (e.g. `-Dproject.settings=...`) | No -- varies per file |
| `Env` | overridable env vars it reads | `${VAR}` substitution, if the file genuinely uses any | No -- varies per file |
| `Uses` | tools/runtimes it invokes | it's passive data | Yes -- always `None`, field omitted |
| `Input` | files/APIs it reads | this file *is* the input | Yes -- always `None`, field omitted |
| `Outputs` | files written, side effects | static config, produces nothing on its own | Yes -- always `None`, field omitted |
| `Returns` | exit codes | it's never executed | Yes -- always `N/A`, field omitted |

### Generated JSON data files

JSON has no comment syntax -- it cannot carry a header at all. A JSON file's description lives
in its directory's own README.md instead, always -- see docs/architecture/data/README.md for a
real example.

### `.gitignore` / `.gitattributes` files

A different passive shape from `.properties` — not `KEY=VALUE`, a list of glob patterns (plus, for
`.gitattributes`, a trailing attribute per pattern). Git reads either file automatically; neither
is ever directly invoked. No file-level structured header — a pattern list gains nothing from a
`Description`/`Usage`/`Env` block repeating "this is read automatically by git" on every one. What
actually carries information is a one-line `#` comment above each logical group of patterns (e.g.
`# IDE files`, `# per-module build output`) — the same "one line or none" comment discipline as
everywhere else in this skill, applied per group rather than per individual pattern.

### YAML files (excluding `docker-compose*.yml`, covered above)

No formal field-structured standard exists for a passive YAML configuration file — but the general
convention (standard practice in Kubernetes manifests and Ansible playbooks) is a top-of-file
comment block stating purpose and context. YAML has no block-comment syntax — every header line
needs its own `#`, the same mechanical shape as this skill's bash headers.

Whether `Uses`/`Input`/`Outputs`/`Returns` apply depends on what the file actually is, not a fixed
rule for every YAML file: a genuinely passive config file (read as data, never itself processed as
a workflow) drops all four and follows the same three-field (`Description`/`Usage`/`Env`) shape and
table as `.properties` above. A YAML file an engine actively processes as a workflow (produces real
outputs, has its own success/failure outcome) keeps the full field set instead, following the
script-header shape.

#### Example — a passive profile config

```yaml
# Description: Dev-profile overrides -- localhost Postgres/MinIO, seed data enabled.
# Usage: read automatically by the app on startup when the dev profile is active.
# Env: None.
spring:
  ...
```

Why this example: only three fields appear (`Description`/`Usage`/`Env`) instead of the full
7-field shape, chosen because this file is read passively at startup, never itself executed or
processed as a workflow — the `Uses`/`Input`/`Outputs`/`Returns` fields would all be constant
`None`/`N/A` boilerplate repeated on every such file (see the `.properties` table above for the
same reasoning applied there first).

