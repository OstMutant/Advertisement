# improvement-166: collect-code.bat gains a --claude-only mode

**Type:** chore
**Module:** `scripts/collect-code.bat`
**Priority:** low
**When:** independent, no blockers

## Problem

`scripts/collect-code.bat` only supports one mode: bundle the entire project's source (every
`.java`/`.css`/`.yml`/... file plus `.claude/` rules/commands/skills) into `all-code.txt`. There is
no way to produce a smaller bundle scoped to just the Claude-facing operating context (`.claude/`
rules/commands/skills) plus the local memory snapshot now kept at `private/claude/memory/` (a
copy of Claude's own persistent cross-session memory, added so it's visible inside the project
instead of only at its actual system-level storage path).

## Fix

Add a `--claude-only` flag to `scripts/collect-code.bat`:
1. Parse `%~1` into a `CLAUDE_ONLY` flag right after `cd /d "%~dp0.."`.
2. Wrap the existing extension-scan block (`call :FindFiles "*.java"` through the root-file block)
   in `if "%CLAUDE_ONLY%"=="0" ( ... )` — skipped entirely in `--claude-only` mode.
3. The existing `.claude\rules.md`/`commands`/`skills` collection block stays unconditional (runs
   in both modes, already exactly what `--claude-only` mode needs).
4. Add a new block, gated `if "%CLAUDE_ONLY%"=="1" ( ... )`, that lists
   `private\claude\memory\*.md` directly via `dir /S /B` — bypassing `:FindFiles`'s own `\private\`
   exclusion (deliberate for the normal full-project bundle; this inclusion is intentional and
   mode-scoped, not a change to that default).
5. Output filename: `claude-context.txt` in `--claude-only` mode, `all-code.txt` otherwise
   (unchanged default) — keeps the two bundle kinds from silently overwriting each other.
6. The extension-count/root-file-check summary block is meaningless in `--claude-only` mode —
   gated behind `CLAUDE_ONLY=0`, replaced with a short claude-only-specific summary (count of
   `.claude\` files + memory files collected).
7. Header (`Usage`/`Outputs`) updated to document the new flag.

**Status:** implemented — 2026-08-21. First real Windows run found a bug: `since was unexpected at
this time.` — caused by `::`-style comments placed *inside* the new `if "%CLAUDE_ONLY%"=="0" ( ...
)` / `if "%CLAUDE_ONLY%"=="1" ( ... )` blocks. `cmd.exe`'s block parser scans a parenthesized
block's raw text for label/paren tokens before executing anything, without comment-awareness --
`::` is parsed as a label there (this is why the codebase's existing style note already avoids
`::` at all for cmd.exe's own forward-label-seek reason; the same fragility also applies inside
`( )` blocks specifically). Fixed: converted every comment now living inside one of these new
blocks to `REM` (safe inside blocks) and additionally removed all literal `(`/`)` characters from
those specific comments too, even where already paren-balanced within themselves -- the same raw
character-scan risk applies to a stray paren inside a `REM` line just as much as to `::`, so this
goes further than the immediate reported error to close the whole class of risk, not just the one
line that happened to trigger it.

Second real-run finding: `CLAUDE.md` files (root + all 13 per-module ones) were missing from
`claude-context.txt` -- in full-project mode they're picked up incidentally by the general `*.md`
`:FindFiles` call, but `--claude-only` mode skips that whole scan, so they need their own explicit
collection. Fixed: added `call :FindFiles "CLAUDE.md"` inside the `--claude-only`-gated block
(reuses `:FindFiles`'s existing target/node_modules/.git/etc exclusion list rather than
duplicating it), plus a matching `CLAUDE.md files (root + per-module)` summary line.

Verified — 2026-08-21. Confirmed working on the user's real Windows `cmd.exe`:
`scripts\collect-code.bat --claude-only` produces `claude-context.txt` containing `.claude/`
rules+commands+skills, all 14 `CLAUDE.md` files, and `private/claude/memory/`, no parser errors.

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
