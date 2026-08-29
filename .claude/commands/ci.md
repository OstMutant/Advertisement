Run the local, isolated, parameterized CI runner (scripts/ci.sh -> scripts/ci/run.sh).

Usage: /ci [flags] [--metrics]
  --metrics  after the run reaches a terminal state, also dispatch dagu-analyst for a full
             structured report and write it to scripts/ci/reports/dagu-metrics.md -- only
             meaningful once a run has actually finished.
Examples:
  /ci                                    # default: most extensive run (unit+integration+e2e+sonar,
                                          # e2e uses "e2e --full --ux"), backgrounded
  /ci --unit --integration               # scripts/ci/run.sh always applies the Testcontainers
                                          # sandbox workaround internally for --integration -- no
                                          # flag of its own, unlike build-and-test.sh/integration-tests/run.sh
  /ci --e2e
  /ci --foreground --unit                # block until this one stage finishes (rare -- see step 2)
  /ci --metrics                          # default run, plus a persisted dagu-analyst report after

Steps:
1. Strip `--metrics` from the arguments (it's this command's own flag, not `scripts/ci.sh`'s) and
   run `bash scripts/ci.sh <remaining args>` as a normal synchronous Bash call -- unless
   `--foreground` was passed, this returns within seconds (after the image build/start) and prints
   `Dagu web UI is up: http://localhost:8082` plus confirmation the run was triggered.
2. If `--foreground` was passed: the call blocks until the run finishes -- read its final output
   directly, no Monitor needed. Skip to step 5.
3. Otherwise (the default, backgrounded case): launch a `Monitor` with
   `command: "python3 -u scripts/ci/watch-run.py"` (`-u` required, see the script's own header) --
   polls Dagu's REST API through the proxy sidecar and emits one line per step-status transition,
   then a final `RUN <statusLabel>` line (Dagu's own terminal status, lowercase --
   `succeeded`/`failed`/`partially_succeeded`/`cancelled`, e.g. `RUN succeeded`) and exits on its
   own.
4. Continue with other work while the Monitor runs -- do not sleep-poll it yourself, notifications
   arrive automatically on each step-status change.
5. On that final `RUN <statusLabel>` event (or the `--foreground` call's own output, which prints
   `===== PASSED =====`/`===== FAILED (exit N) =====` instead -- a different format from the
   backgrounded case): report the per-stage summary (which stages passed/failed/skipped) and the
   Dagu UI link (`http://localhost:8082`) for the full history. If any stage failed, read its
   actual output via `dagu-analyst` (or the UI) before reporting -- never just "it failed."
6. If `--metrics` was passed: dispatch `Agent({description: "Dagu run metrics", subagent_type:
   "dagu-analyst", prompt: "full status and per-stage breakdown for the run that just finished"})`,
   then write its structured summary to `scripts/ci/reports/dagu-metrics.md` (`mkdir -p` the
   directory first -- it does not exist by default). Skip this step entirely if `--metrics` wasn't
   passed.
