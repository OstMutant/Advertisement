Wraps a target script/command and renders a live step checklist (✅/⏳/⬜/⚠️/❌) instead of raw
stdout, so watching a long background run costs tokens proportional to real signal, not log
volume. Known `AGENTIC_SUCCESS_BLOCK`/`AGENTIC_ERROR_BLOCK` markers are recognised mechanically,
for free; anything else falls back to one cheap-model narration call per batch. Pass/fail is
always the wrapped command's own real exit code, never narration. See
`scripts/activity-monitor/README.md` for the full flow.

Usage: `/activity-monitor <script and args>` or `/activity-monitor --in-container <script and args>`

`--in-container` runs the wrapped command inside a dedicated, disposable `dev-shell` container
instead of the current shell — use it when a standalone run's own live state (`tree.txt`/
`raw.log`) needs to be visible regardless of which host shell/terminal triggered it (see
`scripts/activity-monitor/README.md`'s "Running in a container" section for why this exists).
Default (no flag) behavior is unchanged.

Steps:
1. Background the wrapped run:
   ```
   bash scripts/activity-monitor.sh -- <script> [args...]
   bash scripts/activity-monitor.sh --in-container -- <script> [args...]
   ```
2. Attach `Monitor` to `/tmp/activity-monitor/<script-basename>/tree.txt` (the wait-then-tail
   wrapper) instead of the wrapped script's own raw log — this file only changes when a real step
   transition happens, not per raw line.
3. Stay silent on routine per-step transitions; when asked for status, print the current tree
   directly (`bash scripts/activity-monitor.sh --render <script-basename>`) rather than
   paraphrasing it. On a real error, print that same tree, then read the pointer it names
   (`docker logs <container>` or the wrapped script's own raw log at
   `/tmp/activity-monitor/<script-basename>/raw.log`) and add your own analysis underneath.
4. Once the run reaches a terminal state (`/tmp/activity-monitor/<script-basename>/exit_code`
   exists), call `TaskStop` on the `Monitor` task.

For a human running this directly (not via the agent): `bash scripts/activity-monitor.sh --
<script> [args...]` is already the whole thing — it redraws the checklist live in that same
terminal automatically, no second command needed. `bash scripts/activity-monitor.sh --watch
<script-basename>` is only for peeking at a run already started elsewhere (e.g. by the agent) from
a separate terminal, without launching a second one.
