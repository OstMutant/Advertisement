# Planning run observations

Purpose: one row per planning run, so the loop thresholds of `show-status.sh` (a link or a step text used 3 times, 5 steps without a new source) can be tuned from real runs. A row is added by the main thread when a run ends, whatever its outcome.

| date | topic | duration | sources read | signals that fired | verdict | note |
|---|---|---|---|---|---|---|
| 2026-10-24 | optimize-command | 21 min | 10 | none (status progressing throughout) | PLAN READY, approved with all six proposals | first real run: 15 progress steps, about 178k Opus tokens, 56 tool calls; found two tooling problems (extraction of `.claude` ADRs fails, no ShellCheck-only CI mode) |
| 2026-10-24 | playwright-test-isolation | 48 min | 16 | none from show-status (a stall counter on progress.md idle fired for 20+ min while the plans were being written, a false alarm) | PLAN READY, approved with all six proposals | third run on this topic: the first stalled 104 min with no new step and was restarted, the second answered a research brief with a menu of options, this one got a goal-and-recipe brief; 15 progress steps, about 218k Opus tokens, 72 tool calls |
| 2026-10-26 | api-key-management-ui | 14 min | 12 | none (status progressing throughout) | PLAN READY, approved with all recommendations and the log line (D1 yes) | brief asked for a recipe with one recommendation per open point; 7 progress steps, 237k Opus tokens, 84 tool calls; the OpenAI help page returned 403 so only search snippets were used, and the Vaadin Grid page was cut off so the Grid API was read from the local Vaadin jar |
