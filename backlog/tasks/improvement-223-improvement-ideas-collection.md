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
