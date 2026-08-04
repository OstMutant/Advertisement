# improvement-140: Documentation shrink pass — finish the deferred dedup, replace hedges with real fixes

**Type:** process/documentation-infrastructure — a measurement-driven shrink pass, distinct from
`improvement-137` (which added structure: the `doc-standards` skill + a first dedup pass). This
issue's job is to make the *volume* go down and finish what `improvement-137` explicitly deferred.
**Module:** cross-cutting — same surfaces as `improvement-137`: `CLAUDE.md` (13 files),
`README.md` (15 files), `docs/architecture/*.md`, `docs/ai/*.md`, `backlog/BACKLOG.md`.
**Priority:** 🔴 top — highest priority per explicit user request (2026-08-04), ranked **above**
`improvement-138` (both Track A and Track B) — 138 would read/visualize `docs/architecture/*`,
which this issue is cleaning up; doing 138 first means it builds a layer on top of bloat instead
of on top of something lean.
**When:** Ready to execute — independent, no blockers. Run before `improvement-138`.

## Problem

Direct user observation, confirmed by the numbers: `improvement-137` was framed as a "dedup
cleanup" but its own commit (`27003007`) shows **+2127 / -233 lines, net +1894** — the pass grew
the documentation surface rather than shrinking it. Root causes, identified directly from that
session's own work rather than guessed:

1. **New infrastructure is legitimate one-time cost** (~250 lines: `doc-standards/SKILL.md`,
   `check-hardcoded-counts.sh`, ADR-002, the `improvement-139` issue file) — not the problem.
2. **Disclaimers instead of fixes.** Several stale facts were wrapped in prose explaining *that*
   they're stale instead of being fixed or removed — e.g. `docs/architecture/02-spi-map.md` got a
   9-line "known stale as of 2026-08-04" banner instead of a corrected diagram; `docs/architecture/
   README.md` got multiple "not re-verified this pass" hedges instead of the actual (cheaply
   computable) numbers. The hedge costs as much text as a real fix and doesn't solve anything.
3. **Confirmed remaining duplicates, found during `improvement-137`'s own discovery pass but
   explicitly deferred, never fixed:**
   - "No circular/cyclic dependencies (clean DAG)" — canonical home `01-module-dependencies.md:72`,
     independently restated in `06-coupling-analysis.md`, `07-risk-report.md`, `08-scorecard.md`
     (2 places), and `docs/architecture/README.md` (4 places).
   - "marketplace-app depends on all starters" — canonical `01-module-dependencies.md:74`, restated
     in `03-bounded-contexts.md`, `06-coupling-analysis.md`, `07-risk-report.md`, `08-scorecard.md`,
     `README.md`.
   - "Only marketplace-app imports Vaadin, starters have none" — canonical
     `06-coupling-analysis.md:66-75`, restated in `08-scorecard.md` (2 places), `README.md`
     (2 places).
   - "AccessEvaluator violation resolved (ADR-016)" — canonical `06-coupling-analysis.md:5-11`,
     restated across `07-risk-report.md` (8 places) and `08-scorecard.md` (7 places); `README.md`'s
     own copies were fixed this session, but 06/07/08's internal restatements were not touched.
   - "Optional audit/attachment dependencies not guarded (MEDIUM)" — canonical
     `07-risk-report.md:122-126`, restated in `08-scorecard.md` (4 places) and `README.md`
     (2 places).
   - `UserPort`'s 4-way split — stated independently in `platform-commons/CLAUDE.md`,
     `user-spring-boot-starter/CLAUDE.md`, `user-spring-boot-starter/README.md`, and
     `docs/architecture/02-spi-map.md` (which doesn't even mark it as one logical split, just lists
     4 unrelated-looking ports).
   - `query-lib`'s `SqlFilterBuilder`/`OrderByBuilder` one-line API descriptions — near-identical
     text in both `query-lib/README.md` and `query-lib/CLAUDE.md`.
   - "SPI implementations: `AttachmentPort`/`AttachmentAuditPort`" (and the audit-starter
     equivalent) — restated in each starter's `README.md`, `CLAUDE.md`, and
     `docs/architecture/02-spi-map.md`.
4. **`backlog/BACKLOG.md`'s "At a glance" section is an ever-growing narrative wall** — every
   completed issue gets a paragraph here *and* a fuller entry in `BACKLOG-ARCHIVE.md`, so the same
   history is told twice, once briefly and once fully, and the brief version never gets trimmed
   back down once the issue is archived.

**Baseline measurement (taken 2026-08-04, before this issue's work starts):**

| Surface | Lines |
|---|---|
| `CLAUDE.md` (13 files) | 1,400 |
| `README.md` (15 files) | 1,875 |
| `docs/architecture/*.md` (9 files) | 2,588 |
| `docs/ai/*.md` (3 hand-maintained files, excludes generated `adr-index.md`) | 135 |
| `.claude/commands/*.md` (12 files) | 741 |
| `.claude/rules.md` | 468 |
| `backlog/BACKLOG.md` | 227 |
| **Total** | **7,434** |

`DECISIONS.md` files and individual `backlog/issues/*.md`/`backlog/completed/issues/*.md` files are
excluded from this baseline — both are intentionally append-only historical records, not
current-state documentation, so "shrinking" them isn't the goal (per `doc-standards/SKILL.md`'s own
stated exclusions).

## Suggested fix

1. **Finish the deferred duplicate list above** — for each one, per `doc-standards/SKILL.md`'s
   ownership table: pick the single most-specific canonical file (already identified above for
   each), collapse every other occurrence to a one-line reference. Extend the ownership table
   itself with a new row for "cross-cutting audit/coupling/risk findings" (canonical home: the
   `06-coupling-analysis.md`/`07-risk-report.md`/`08-scorecard.md` file whose topic matches the
   finding) since this fact-type wasn't covered yet.
2. **Replace every hedge added during `improvement-137` with a real fix**, per file:
   - `docs/architecture/02-spi-map.md`: fix the actual diagram/tables (remove
     `AttachmentMediaChangeHook`, rename `AttachmentAuditHook`→`AttachmentAuditPort`, add the
     `UserPort` 4-way split and `ProviderProfilePort`) instead of the staleness banner.
   - `docs/architecture/README.md`: recompute "Total Java Files", "Largest Module", "Largest File"
     (each a single `find`/`wc -l` command) instead of "not re-verified this pass".
   - `docs/architecture/03-bounded-contexts.md`/`04-database-erd.md`: regenerate the domain
     list/ERD to include `taxon`/`provider-profile`, rather than leaving `README.md`'s "due for a
     refresh" pointer as the only acknowledgment.
3. **Consolidate verbatim, context-free "starter-wide" constraints repeated across every starter's
   `CLAUDE.md`** — confirmed via direct grep (2026-08-04), not hypothetical:
   - `"Starters own their own Liquibase changelogs — never merge into a shared file."` — appears
     word-for-word in all 6 starters' `CLAUDE.md` (`advertisement`, `attachment`, `audit`,
     `provider-profile`, `taxon`, `user`).
   - `"No Vaadin dependency. No UI code here."` — appears word-for-word in all 6 (some then add a
     module-specific clause after it, e.g. `attachment`'s "UI components ... live in
     `marketplace-app`" — that module-specific tail stays local, only the identical opening
     sentence moves).
   State each once in root `CLAUDE.md`'s "Module Layout"/"Architecture Guidelines" section; each
   starter's `CLAUDE.md` keeps only its module-specific tail, if any. This is a gap in
   `doc-standards/SKILL.md`'s own fact-vs-constraint test worth fixing in the skill itself: a
   constraint identical word-for-word across every module with zero module-specific content is
   functionally a system-wide fact and should follow the fact rule (one canonical home), not the
   constraint rule (local repetition allowed) — add this clarification to the skill's "Facts vs.
   constraints" section as part of this item.
4. **Diagram+table redundancy in `01-module-dependencies.md`** — the mermaid graph and the
   markdown table encode the same edges twice. Keep the table as primary (it carries the
   compile/runtime scope distinction the diagram can't show); trim the diagram to what it uniquely
   adds (the visual DAG shape) rather than fully restating every edge already in the table, or
   drop one of the two representations if review shows neither carries unique value on its own.
5. **Legend/prefix line in `02-spi-map.md`'s SPI tables** — every row currently spells out the full
   package (`org.ost.platform.attachment.spi`, etc.) instead of stating the shared prefix once at
   the top of each subsystem's table and using the leaf segment in rows.
6. **Compress narrative-style `DECISIONS.md` entries to terse fact statements** — some entries
   (e.g. `platform-commons/DECISIONS.md` ADR-025 Item 20) narrate the full course of a
   self-correction ("first drafted as X, a review caught Y, corrected to Z because W") across
   15-20 lines when the durable fact is "corrected: was X, now Z, because W" in 3-4 lines. Same
   information, less prose — a writing-style pass, not a structural one, and it does not conflict
   with `DECISIONS.md`'s append-only/no-pruning rule (nothing is removed, just said more tersely).
   Scope this to entries reviewed while doing items 1-2 above — not a full separate audit of all
   174 ADRs.
7. **Trim `backlog/BACKLOG.md`'s "At a glance"** to a short pointer per completed item (one line:
   "✅ done, see BACKLOG-ARCHIVE.md") instead of a restated paragraph — the full narrative already
   lives in `BACKLOG-ARCHIVE.md`.
8. **Re-measure against the baseline table above** at the end; report the delta in this issue's
   `## Operational notes` block (net lines added/removed, not just "done").
9. **Out of scope for this issue, note as a possible future follow-up only if the user wants it**:
   a standing/CI-visible doc-size-trend tracker. Not required to close this issue — this issue is
   a one-time shrink, not the ongoing-monitoring mechanism discussed in conversation.

## Additional scope item — DECISIONS.md pruning (added 2026-08-04, needs a decision before execution)

User proposal, not yet decided or executed — added here per explicit request to capture it in this
issue rather than act on it now.

**The idea:** go through every module's `DECISIONS.md` and remove entries that are stale/superseded
and now conflict with a newer decision, instead of leaving both the old and new text in the file
forever. The historical record isn't lost — it stays recoverable via the corresponding
`backlog/completed/issues/*.md` file (which this project already archives permanently and never
deletes), so `DECISIONS.md` would hold only the decisions still actually in force, not the full
history of every decision ever made about a topic.

**This directly reverses a policy stated earlier in this same issue and in
`doc-standards/SKILL.md`:** `DECISIONS.md` is currently documented as "append-only history — write
what happened, accurately; optimizing an ADR for brevity over completeness is the wrong trade," and
this issue's own "Out of scope" section (below) currently lists `DECISIONS.md` as unchanged. The
existing precedent in this repo for a superseded decision is to **annotate it in place** ("ADR-044
... superseded by ADR-070" — see `user-spring-boot-starter/CLAUDE.md`), not delete it. Before this
item can be executed, that tension needs an explicit resolution, not a silent override:
- Does "prune" mean **delete the superseded ADR's text entirely** from `DECISIONS.md` (relying on
  `backlog/completed/issues/` and git history for recovery), or **shrink it to a one-line pointer**
  ("ADR-044: superseded by ADR-070, see completed/issues/improvement-XXX.md for the original text")?
  The user's phrasing ("можна для історії залишати в ішшюсах") suggests full removal from
  `DECISIONS.md`, but this needs confirming before touching any file.
- Not every ADR traces back to a `backlog/completed/issues/*.md` file 1:1 — several were recorded
  directly via `/decision` with no corresponding issue ever filed. For those, deleting the
  `DECISIONS.md` entry would lose the only record entirely (git history aside). Needs a rule for
  this case: skip pruning ADRs with no backing issue file, or write one retroactively before
  pruning.
- Scope of "stale/conflicting": an ADR whose `Status:` already says superseded/rejected is an easy,
  unambiguous case; an ADR that's merely *old* but still accurately describes current behavior is
  not a pruning candidate — this needs the same fact-vs-history judgment `doc-standards/SKILL.md`
  already applies elsewhere, not a blanket "delete anything old."

**Not part of this issue's completion bar** until the above is resolved with the user — flagged
here so it isn't silently dropped, per this project's standing "surface it, don't drop it" rule.

## Testing strategy

- `bash scripts/ai/check-adr-index-freshness.sh`, `check-flows-completeness.sh`,
  `check-hardcoded-counts.sh` must all still pass after every file touched.
- `bash scripts/unit-tests.sh` once at the end (documentation/script-only change, same sanity-check
  rationale as `improvement-137`).

## Out of scope

- `DECISIONS.md` files — append-only, unchanged, per the shrink goal not applying to historical
  records, **except** for the pruning idea captured in "Additional scope item" above, which is not
  authorized to execute yet (needs the open questions there resolved first).
- `backlog/issues/*.md`/`backlog/completed/issues/*.md` — unchanged; these remain the permanent
  historical record regardless of what happens with `DECISIONS.md`.
- Any code change — documentation-only, same as `improvement-137`.
- Building a new ongoing doc-size-tracking mechanism — noted above as a possible separate future
  issue, not part of this one's completion bar.

## Execution outcome (2026-08-04)

All 8 in-scope suggested-fix items completed; the "Additional scope item" (`DECISIONS.md`
pruning) was left unauthorized/untouched as documented, per its own open questions.

- **Item 1 (dedup):** DAG/no-cycles, "marketplace-app depends on all starters", "Vaadin only in
  marketplace-app", AccessEvaluator-resolved, and optional-deps-not-guarded facts collapsed to
  one-line pointers in `06-coupling-analysis.md`/`07-risk-report.md`/`08-scorecard.md`/
  `docs/architecture/README.md`, each now pointing at its single canonical file. `UserPort`'s
  4-way split now stated as one logical split (not 4 unrelated ports) in `02-spi-map.md` and root
  `CLAUDE.md`. `query-lib`'s API tables deduplicated between `README.md` (canonical) and
  `CLAUDE.md` (now references it). SPI-implementation lines deduplicated the same way for
  audit/attachment/user/advertisement starters' `README.md` vs `CLAUDE.md`.
- **Item 2 (real fixes, not hedges):** `02-spi-map.md` rewritten — removed the staleness banner,
  removed `AttachmentMediaChangeHook`, renamed `AttachmentAuditHook`→`AttachmentAuditPort`, added
  the `UserPort` 4-way split and `ProviderProfilePort`. `docs/architecture/README.md`'s "Key
  Metrics" table recomputed with real numbers (314 Java files, 17 SPI interfaces, largest module
  marketplace-app/174 files, largest file `I18nKey.java`/438 lines) instead of "not re-verified
  this pass". `03-bounded-contexts.md` gained a full Provider Profile domain section + context-map
  diagram entry. `04-database-erd.md` gained `user_preferences` and `provider_profile` tables
  (ERD diagram + full schema sections) and corrected `user_information` (removed
  `locale`/`settings`, which moved out per ADR-070; added `deleted_at`/`deleted_by`).
- **Item 3 (verbatim constraint consolidation):** "No Vaadin dependency. No UI code here." and
  "Starters own their own Liquibase changelogs" moved to root `CLAUDE.md`'s Architecture
  Guidelines (new guideline 7); removed from all 6 starter `CLAUDE.md`s, keeping only each
  starter's module-specific tail. `doc-standards/SKILL.md`'s "Facts vs. constraints" section
  gained the clarification this item asked for (identical-everywhere constraint = a fact).
- **Item 4/5:** `01-module-dependencies.md` gained a one-line note clarifying the diagram is
  DAG-shape-only and the table is authoritative for scope (kept both — diagram carries real visual
  value, decided against dropping it). `02-spi-map.md` gained a package-prefix legend per
  subsystem heading instead of repeating the full package on every row.
- **Item 6:** `platform-commons/DECISIONS.md` ADR-025 item 20 (the exact example named in this
  issue) compressed from an 18-line self-correction narrative to a 7-line terse fact statement.
  `docs/ai/adr-index.md` regenerated in the same operation per `.claude/rules.md`.
- **Item 7:** `BACKLOG.md`'s "At a glance" section rewritten from a 54-line narrative wall into a
  short "Completed" list (one clause per item, full detail left to `BACKLOG-ARCHIVE.md`) plus a
  compact "Still active" paragraph for genuinely open work.
- **Item 8 (re-measurement):** see the table below — net **+32 lines (+0.4%)** vs. the 7,434-line
  baseline, not a shrink. `CLAUDE.md` (-38) and `BACKLOG.md` (-27) shrank as intended;
  `docs/architecture/*.md` grew (+113) because item 2's real fixes filled genuine content gaps
  that were previously entirely missing (Provider Profile domain section, `user_preferences`/
  `provider_profile` ERD tables, `UserPort` 4-way split) rather than restating what already
  existed elsewhere — the same "legitimate one-time cost" category this issue's own Problem
  section already carved out for `improvement-137`'s infrastructure additions. The dedup and
  hedge-replacement work this issue targeted is real and verifiable in the diff even though the
  net line count didn't drop; reported here plainly rather than reframed as a shrink that didn't
  happen.

| Surface | Baseline | After | Delta |
|---|---|---|---|
| `CLAUDE.md` (13 files) | 1,400 | 1,362 | -38 |
| `README.md` (15 files) | 1,875 | 1,857 | -18 |
| `docs/architecture/*.md` (9 files) | 2,588 | 2,703 | +115 |
| `docs/ai/*.md` (3 hand-maintained files) | 135 | 135 | 0 |
| `.claude/commands/*.md` (12 files) | 741 | 741 | 0 |
| `.claude/rules.md` | 468 | 468 | 0 |
| `backlog/BACKLOG.md` | 227 | 200 | -27 |
| **Total** | **7,434** | **7,466** | **+32** |

**`/code-review --fix` pass (8 finder angles, all launched in parallel, then 1-vote verify per
surviving candidate):** found and fixed 6 real pre-existing stale facts the dedup pass itself
didn't introduce but edited directly adjacent to, then left uncorrected — a second pass of exactly
the "hedge instead of fix" pattern this issue exists to close:
1. `06-coupling-analysis.md`'s canonical `AccessEvaluator` description (now the target of 3 new
   pointers this diff added) still said `AccessEvaluator` depends on `UserPort` and calls
   `UserPort.isAdmin()`/`.isModerator()`/`.isOwner()` — stale since ADR-026 split those methods
   onto `UserAuthorizationPort`. Fixed in `06`/`07`/`08` (3 occurrences of the wrong field name).
2. `07-risk-report.md`/`08-scorecard.md`/`docs/architecture/README.md` still described "Optional
   Dependencies Not Guarded (MEDIUM)" as open — the `<optional>` Maven deps were removed entirely
   from `advertisement-spring-boot-starter/pom.xml` on 2026-07-16 (confirmed: no such dependency
   exists in the pom at all). Marked resolved in all 3 files (9 occurrences).
3. `08-scorecard.md` also independently restated "Advertisement → User hard FK coupling" as a
   live weakness in 3 places — that FK was removed entirely by improvement-120 (2026-07-25).
   Fixed all 3.
4. `07-risk-report.md`'s "Largest Java Files" table still said `I18nKey.java` was 370 lines; real
   count is 438 (matches the number this same diff's other edits already used elsewhere) — whole
   table refreshed with current numbers via `find | wc -l`, not just the one stale row.
5. `06-coupling-analysis.md`'s Module Size table lost its per-starter "Largest File" column during
   the dedup pass, replaced with a pointer to `07-risk-report.md` — but that file only ever listed
   marketplace-app files, so the pointer delivered nothing for the 6 starters. Restored the column
   with real per-starter numbers instead of leaving a broken promise.
6. `02-spi-map.md`'s SPI Interfaces count claimed "17" in `README.md` but the file's own tables
   only listed 16 (missing `UserIdMarker`, a real marker interface with a real consumer). Added the
   missing row instead of just softening the claimed number.
One additional item (`platform-commons/DECISIONS.md` ADR-025 item 20 losing its cycle-safety
justification during compression) was fixed by restoring one clause, not reverting the compression.
No findings were skipped — every CONFIRMED/PLAUSIBLE candidate that survived verification was
fixed directly. `docs/architecture/*.md`'s final line count above already reflects these fixes
(mostly net-neutral: stale rows corrected in place, one deleted column restored).

## Operational notes
- token_cost_review: n/a (no Agent-tool review calls this run — all edits made directly by the
  main thread per the plan already fully specified in this issue file)
- token_cost_research: n/a (no Agent-tool research calls; file reading done directly)
- token_cost_verification: n/a (no Agent-tool verification calls; `scripts/unit-tests.sh` and the
  3 `scripts/ai/check-*.sh` gates run directly via Bash, not delegated to an agent)
- context_loading_task_type: documentation-only cross-cutting change
- context_loading_consulted: yes (`doc-standards/SKILL.md` read in full before starting, per
  `.claude/rules.md`'s "Documentation Standards" rule)
- context_loading_matched: yes — the ownership table's ownership assignments (README class tables
  canonical, CLAUDE.md references) directly drove items 1 and 3's edits
- flows_situation: pre-scoped backlog issue with a complete `## Suggested fix`, explicit user
  approval to execute end-to-end
- flows_chosen: /autopilot
- flows_matched: yes

## Related

- `improvement-137` — the predecessor issue this one finishes; see its "Execution outcome" section
  for exactly what was deferred and why.
- `improvement-138` — sequenced **after** this issue now (was previously sequenced right after 137;
  reordered per this issue's higher priority) — Track A reads `docs/architecture/*`/`backlog/`, so
  it should read the shrunk, corrected version.
- `scripts/ai/DECISIONS.md` ADR-001 — "generated over hand-maintained" precedent this issue's
  Step 2 (regenerating `02-spi-map.md`/`04-database-erd.md`) follows in spirit, though those two
  files aren't mechanically generated (no script owns them) — a future candidate for that treatment
  if they keep drifting after this manual fix.
