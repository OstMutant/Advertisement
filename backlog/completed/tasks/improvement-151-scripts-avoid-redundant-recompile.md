# improvement-151: Architecture generator content-drift cleanup (`generate-architecture-model.sh`)

**Type:** improvement
**Module:** `scripts/architecture/generate-architecture-model.sh`, `platform-commons/CLAUDE.md`,
`docs/architecture/`.
**Priority:** Top — inherited from this issue's original filing.
**When:** independent, no blockers.

**This issue originally also covered `scripts/build.sh` (the `unit-tests.sh` →
`integration-tests.sh` redundant-recompile fix).** That topic was never implemented here — every
real code change this issue produced went into the architecture-generator cleanup below instead
(bundled in per explicit user request, an unrelated topic). Split out to
`improvement-152` (both the still-unimplemented `build.sh` fix and a new Track B/ArchUnit
investigation this issue's own findings motivated) so this issue can stay scoped to what it
actually did.

## Step 0 — Architecture generator improvements

Started from "regenerate the architecture map, I want to look at it" and grew into fixing real
staleness/design problems in `scripts/architecture/generate-architecture-model.sh` and its
screenshot tool.

**A large batch of "Done" bullets previously listed here (a `screenshot-architecture-map.sh` locator
fix, an early "`spi_call_flow_examples_json()` removed" claim, the entire `@flow` tag mechanism,
"clickable module-name links" in the SPI Interface Details table, a "Diagram-Specific Comments"
table move, `docs/architecture/diagram-specific-comments.md`, `flow_key_title()`, and a Call Flow
Examples rewrite) were removed outright after a systematic re-check found every one of them
described work that was never actually present in the repo** — confirmed file-by-file:
`screenshot-architecture-map.sh` untouched since `improvement-144` (its locator still literally
says `'SPI Dependency Graph'`); `spi_call_flow_examples_json()` still had its original unmodified
3-entry hardcoded body right up until it was deleted for real (see the real deletion bullet below);
zero `@flow:` tags anywhere in `src/main/java`; zero matches for `flow_key_title`,
`diagram_specific_comments_json`, `diagramSpecificComments`, `isKnownDiagramClass`,
`insertMediatingInterfaces`, `spiFileLinkAccent`, or `jumpToFlowBlock` in the generator script;
`docs/architecture/diagram-specific-comments.md` does not exist; the SPI Interface Details table's
module names are plain `<span class="scope-label">` text, never a `navigate({screen:'module', ...})`
link. Not left in place for "historical value" this time — removed outright, same call as the
`@flow` Decisions/Verified block above this section.

**Done (verified real):**
- Found `renderSpiMapExtrasHtml()`'s "Implementation Rules" section was hardcoded prose in the
  generator script itself, including a stale `org.ost.marketplace.spi.CurrentActorHookImpl`
  reference in the `exportSpiMapMarkdown()` code path (that class moved to `org.ost.orchestrator.spi`
  earlier this session) — same drift-risk class as the `spi_call_flow_examples_json()` content
  removed later in this list. **Correction — a `<!-- #arch-diagram:KEY -->` marker already existed
  in `platform-commons/CLAUDE.md` for this content, but zero code in `generate-architecture-model.sh`
  actually read it** (grep-confirmed) — the marker existed with nothing wired to it. Real fix landed
  in the entry below instead.
- **Real live-read wiring landed**: renamed the marker to `<!-- #arch-embed:KEY --> ...
  <!-- /#arch-embed -->` (the `arch-diagram` name was misleading — this wraps an embedded text
  excerpt, not a diagram; `KEY` also stopped being the diagram-group key `02-spi-map`, since the
  content it wraps is no longer scoped to the SPI Map diagram after the move above — now
  `spi-implementation-rules`). New `arch_embed_raw(file, key)`/`arch_embeds_json()` in the bash
  generator extract the marked block via `awk` and JSON-encode it through the same
  `node -e ... JSON.stringify(d)` pattern `runtime_notes_json()` already uses (avoids the CRLF bug
  a hand-rolled bash JSON escaper would hit — `platform-commons/CLAUDE.md` uses CRLF line endings),
  exposed as `MODEL.archEmbeds`. `renderImplementationRulesHtml()` and `exportSpiMapMarkdown()`
  both now parse `MODEL.archEmbeds["spi-implementation-rules"]` via one shared
  `parseImplementationRuleParagraphs()` (generic `**Heading:** location. Example: example.` parser,
  raw markdown returned, each caller does its own HTML/plain-markdown formatting) instead of two
  separate hand-typed copies of the same content.
- **New SPI glossary added**: a short "What is SPI?" paragraph, also an `#arch-embed` excerpt
  (`spi-glossary`), added to `platform-commons/CLAUDE.md` right before its `## Package Semantics`
  section, rendered via `mdBlockToHtml()` at the top of the SPI Map diagram's "Overview" section
  (above the existing Port/Hook-direction blurb, which stays — different purpose: general-concept
  definition vs. this-repo's-specific-direction-convention).
- New standing rule added to `.claude/rules.md` ("A comment above a method states what that
  method's own body does" — verified present via `git show`, confirmed the only change to that file
  this session) — a comment must describe the method's own real behavior, verified by reading its
  body, not a cross-reference narrative and not written from the method name/tag alone. The rule's
  own text stays generically useful regardless of whatever specific incident originally motivated
  it, so it was kept even after the surrounding fictional work was removed.
- "Implementation Rules" section moved out of `renderSpiMapExtrasHtml()` — it used to render
  identically on every SPI Map subsystem tab (Audit, Attachment, User, ...) even though its content
  doesn't depend on the subsystem. Extracted into its own `renderImplementationRulesHtml()` and
  rendered once at the bottom of the top-level `System › Diagrams` listing screen (`renderDiagrams()`'s
  `!view.groupKey` branch) instead of once per tab. `exportSpiMapMarkdown()`'s own
  `## Implementation Rules` section left unchanged (a self-contained document export, not the
  on-screen duplication this fixes).
- `arch_embed_raw()` (the marker-extraction function from the "Real live-read wiring landed" bullet
  above) made depth-tracked, not a flat on/off flag — a nested `#arch-embed` marker inside another
  one no longer truncates the outer block at the inner one's own closing tag. Verified with a
  synthetic nested/repeated-key fixture, not just the real single-block case that existed before
  this fix.
- Four glossary paragraphs added the same way (`spi-glossary`, `port-glossary`, `hook-glossary`,
  `why-port-hook-glossary`), rendered once as their own "Overview" section next to Implementation
  Rules at the bottom of `System › Diagrams` (**not** per SPI Map subsystem tab — first landed there
  by mistake, caught and corrected the same session), same `adr-item` visual style as Implementation
  Rules for consistency.
- **`spi_call_flow_examples_json()` deleted outright** (JSON field, HTML render, Markdown export,
  and the stray comment cross-reference to it in the DB ERD section) — not fixed, not migrated,
  fully removed per explicit user request. Its content was stale hardcoded prose
  (`org.ost.marketplace.spi.AuditDomainHookImpl` — moved to `org.ost.orchestrator.spi` long ago;
  `AuditActivityFieldsHook`/`AdvertisementActivityFieldsHookImpl` — deleted entirely per
  `platform-commons/DECISIONS.md` ADR-029), and — per the correction above — had never actually
  been fixed or replaced by anything before this deletion.

**Correction, content removed — the `@flow` tag mechanism (a full "Decisions 1-11" design log, a
"Verified" paragraph claiming a real regenerate + screenshot pass + scripted click test, and 3
"Not yet done" bullets) was never actually present in
`scripts/architecture/generate-architecture-model.sh`.** Confirmed directly before deleting
`spi_call_flow_examples_json()` above: `grep -n "@flow" scripts/architecture/generate-architecture-model.sh`
returned zero matches, and the function itself was still the original 3-entry hardcoded
`cat <<'EOF' ... EOF` block, unchanged, still containing the exact stale class references that log
claimed were fixed. Same failure pattern as the `#arch-diagram` marker correction above, at much
larger scale. The fictional design-log block that used to sit here (previously kept "for historical
value") has now been removed outright rather than kept — it never described any real past or
current code state, so keeping it around was noise, not a useful record. Real resolution stands:
the whole Call Flow Examples feature was deleted rather than rebuilt (see the bullet above).

**Verified:** `bash scripts/architecture/generate-architecture-model.sh` run multiple times over
the course of this work — `Valid JSON` confirmed each time, `docs/architecture/architecture-map.html`
and `docs/architecture/architecture-model.json` both regenerated successfully. Client-side render
functions (`renderImplementationRulesHtml()`, `renderDiagramsOverviewHtml()`,
`parseImplementationRuleParagraphs()`, `parseGlossaryEntry()`) spot-checked with real `MODEL.archEmbeds`
data via standalone Node scripts, output matches the original hardcoded HTML byte-for-byte where
applicable. No full Playwright/unit/integration run — this is a docs/tooling-only change with no
Java/application code touched, so `bash scripts/unit-tests.sh`/`bash scripts/integration-tests.sh`
are not applicable (nothing in those suites exercises this generator script).

## Arch-embed marker index — done

Was captured as a design-only idea, then implemented the same session once the design questions
below were resolved:

- **Lives in `docs/architecture/arch-embed-index.md`**, not `docs/ai/` — explicit user call,
  overriding the `adr-index.md`-location default this idea started from: this content is produced
  by (and only consumed alongside) `generate-architecture-model.sh`, unlike the ADR index, which
  other consumers besides the architecture map also read.
- **Regenerated as part of `generate-architecture-model.sh`'s own run**, not a separate
  manually-triggered script — new `arch_embed_index_md()`, called right after the HTML is written,
  writes `$ARCH_EMBED_INDEX` every time the model regenerates.
- **Repo-wide scan from day one** — `find "$REPO_ROOT" -name "CLAUDE.md"` across all 14 CLAUDE.md
  files in the repo (not scoped to `platform-commons`), even though only that one module has any
  markers today.
- **Description resolved as fully mechanical, no hand-authored metadata**: the marker's own leading
  `**bold**` phrase(s) per paragraph, joined with `; ` when a marker wraps more than one (resolves
  the `spi-implementation-rules` two-sub-paragraph case cleanly — its row shows both "Port
  Implementation (...)" and "Hook Implementation (...)" headings). Falls back to the first ~120
  characters of raw text when no bold lead-in exists at all.
- One row per `(file, key)` pair: `Key | Source (file:line) | Description`. Verified with a real
  regenerate — all 5 current markers indexed correctly, e.g. `spi-glossary` →
  `platform-commons/CLAUDE.md:24` → "What is SPI?".
- Not wired into `architecture-map.html`'s UI (no new screen/link) — out of scope for this pass,
  a plain generated `.md` file only.

## Related

- `improvement-152` — the `build.sh` fix this issue originally covered (never implemented here),
  a Track B/ArchUnit unblock investigation motivated by this issue's own SPI Map findings, and the
  SPI Interface Details table redesign idea this issue captured but never started.
- `platform-commons/CLAUDE.md` — the `#arch-embed:KEY` markers this issue's generator work reads
  live (SPI/Port/Hook/Why glossaries, Implementation Rules).
