Scaffold a new tracked issue in `backlog/issues/` from the standard template, then rank it in
`backlog/BACKLOG.md`'s priority table — implements improvement-034.

Usage: /feature <short description or title>
Example: /feature UserPickerField pagination bug with CallbackDataProvider offset math

Steps:
1. Determine the prefix (default `improvement` — the dominant convention; use `goal` only if the
   user explicitly frames this as a new capability/epic rather than a fix/improvement, or `feature`
   only if the user explicitly says "feature"). Determine the next available number by scanning
   both `backlog/issues/*.md` and `backlog/completed/issues/*.md` for the highest `<prefix>-NNN`
   across all prefixes seen (numbers are a single shared sequence, not per-prefix) and incrementing.
2. Slugify the title into a filename: `backlog/issues/<prefix>-NNN-<kebab-case-slug>.md`.
3. Fill the template using what you already know from the current conversation — do not leave
   placeholder text for fields you can reasonably infer:
   ```
   # <prefix>-NNN: <title>

   **Type:** <improvement|bug|feature — one line, inferred from context>
   **Module:** <module(s) affected, as file paths — verify these exist before writing them, per
     the general "verify before recommending" rule>
   **Priority:** <low|medium|medium-high|high — see step 5, must not be left blank>
   **When:** <independent, no blockers — OR — blocked on [other-issue](path.md) — explain why>

   ## Problem

   <what's wrong, grounded in specifics already discussed — file/line references, verified
     against actual source if this session has already read the relevant files>

   ## Suggested fix

   <concrete approach>

   ## Related

   <cross-references to related issues/ADRs/CLAUDE.md sections already surfaced in conversation>
   ```
   If the conversation so far doesn't have enough concrete detail for `Problem`/`Suggested fix`
   (e.g. the user just said a short title with no discussion), read the relevant source first
   (per this project's established pattern in every issue filed so far this session) rather than
   writing a vague placeholder — verify claims against actual code before committing them to the
   issue file.
4. Present the drafted file content and wait for confirmation before writing (per the Approval
   Rule in `.claude/rules.md`) — unless the user's `/feature` invocation already came after an
   explicit "so, roby"/"yes, do it" for this exact content in the current conversation, in which
   case write directly.
5. Assign a Priority (mandatory — see `.claude/rules.md` "Issue Lifecycle": every new issue needs
   both a `**Priority:**` line and a ranked row in `BACKLOG.md` in the same change, never left for
   later triage). Judge tier from the same rubric already used throughout this backlog: 🟢 cheap +
   low-impact, 🟡 high/medium ROI (real bug or high-value fix, proportionate effort), 🔵 larger
   tech-debt (no live bug, bigger effort or needs a design decision), ⚪ lowest (preventive/no
   observed impact, or blocked on other deprioritized work).
6. Insert a new row into `backlog/BACKLOG.md`'s Priority order table at the position matching that
   tier (near similarly-tiered existing rows), renumbering subsequent rows sequentially.
7. Report the created file path and its assigned position in the priority table.
