# improvement-223: improvement ideas (running collection)

**Type:** improvement — meta/process issue, ongoing collection bucket, not a single fix.
**Module:** cross-cutting — whichever module each entry below actually touches.
**Priority:** ⚪ lowest — ideas only, no committed work, nothing is blocked on them.
**When:** ongoing; an entry is picked up only when the user explicitly takes it, never on a schedule.

## Current state

Ideas that come up while looking at finished work (a tool, a page, a flow) currently have no
home: they either stay in the closing task's history and vanish with its archive, or get spun
into one task file each. improvement-133 collects oversized review findings only.

## Why change

An idea that is not a defect and not a review finding still has to survive the end of the task
that surfaced it, in one place that can be triaged.

## Expected benefit

One list to look at when choosing what to improve next; nothing proposed during a review of
work is lost, and no per-idea task files are created.

## Approach

Each entry below is independent: a title, where it came from, what it would be, and what is
known about its cost. When one is taken, size it fresh (the code may have moved on), turn it into
its own task or a batch, and remove the entry. An entry is added only with the user's approval.

## Entries

### 1. Flow runner page: status icon in diagram nodes and a status filter (from the runner page work)

A small icon in every diagram node (check passed, cross failed, circle skipped, spinner running)
so the status does not rest on colour alone, as in the GitHub Actions workflow graph; and a
filter above the diagram (all, passed, failed, skipped) so the failures in a row of 19 nodes
can be seen at once, as in the Postman collection runner results. Both live in
`docs/api-collections/flow-runner/page` (`diagram.js`, `step.css`). Small: a few dozen lines and
a spec 11 assertion each.

### 2. Flow runner page: the browser login dialog on an empty database (from the runner page work)

On an empty database the first flow step asks for an API key with Basic credentials of the
default administrator, the app answers 401 with `WWW-Authenticate: Basic`, and the browser shows
its own login dialog (closing it lets the run go on, the next step registers the administrator).
Options: stop sending that header for the key-issuing endpoint, or find the administrator by
another request. Both change the app or the recorded flow rules (`docs/api-collections`
DECISIONS.md), so the choice needs a plan of its own.

### 3. One-off OpenRewrite experiment on the Lombok recipes (from the `/optimize` plan, 2026-10-24)

The `/optimize` plan leaves OpenRewrite out of the command because Lombok is already applied in about 176 files and the expected yield is small. One `rewrite:dryRun` with the Lombok recipes would show what it would really change. Cost: a throw-away Maven plugin run, no repository change; the result decides whether OpenRewrite is worth adding later.

### 4. `md-to-decisions-json.js --extract .claude ADR-002` fails (found by the planner, 2026-10-24)

`node .claude/nav/scripts/md-to-decisions-json.js --extract .claude ADR-002` answers ".claude/DECISIONS.md not found" although the file exists. Checked again on 2026-10-24 and reproduced. Likely a path rule for the `.claude` module name; not investigated.

### 5. No ShellCheck-only mode in the local CI and no ShellCheck on the machine (found by the planner, 2026-10-24)

`scripts/ci.sh` has no flag that runs only the ShellCheck stage and ShellCheck is not installed on the working machine, so a new script cannot be checked in isolation. A workaround used once: the npm package `shellcheck` downloads the binary into a scratch folder. Options: a ShellCheck-only flag in `ci.sh`, or a small helper script that fetches the binary.

### 6. Author link on advertisements opens the author's provider profile (from the contacts work)

On advertisement cards and in the advertisement view and edit form, the author's name becomes an accented link to the author's provider profile (name plain text when the author has no profile). An administrator gets Edit in the profile as usual. The in-depth research found: one batched profile-id-per-author lookup per page (a new method on the provider-profile port, filling a new `AdvertisementInfoDto.ownerProviderProfileId` in the existing author enrichment, also useful to REST clients); `EntityMetaPanel` gets an optional author click action with keyboard support; the moderation view already links an author to a profile (`ModerationView.buildProfileLink`). Open design point, deliberately left for later because the first plan was too complex: how to move to the profile and back. The tab-switch-and-restore-URL route (the moderation wiring) needs the browser address restored when the profile closes over an open advertisement; the simpler idea is to open the profile as a nested overlay on top of whatever is open (like the history overlay), which returns to the exact same screen with an unsaved edit form intact, but needs a flag so the profile overlay does not touch the browser address. Size: roughly half a day to a day of supervised work. The full step-by-step plan (steps 8 to 13, with test steps by role) is kept in `completed/tasks/improvement-217-contacts-headed-card-view-and-edit.md`, section "Execution plan -- phases 2 and 3".

## Related

improvement-133 (the same lifecycle for review findings).

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
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: none
