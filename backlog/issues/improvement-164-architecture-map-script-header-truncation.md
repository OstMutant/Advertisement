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
