Run SonarQube static analysis for the marketplace app.

Usage: /sonar [--no-gate] [--pull-latest] [--metrics]
  --metrics  in addition to the always-on bug/issue check (step 6), also persist the
             sonar-analyst agent's full structured report (quality gate + summary metrics) to
             scripts/sonar/report/metrics.md -- only meaningful right after a fresh scan, since
             it reads whatever was just uploaded.

Steps:
1. `rm -rf scripts/sonar/report scripts/logs/sonar && mkdir -p scripts/logs` -- clears the stale
   report/log from a previous invocation before starting, so a check against these paths mid-run
   can never show leftover data from an earlier call; `mkdir -p scripts/logs` guarantees the
   shared parent directory exists (see `scripts/clean.bat`'s own header for why this matters).
2. Launch Monitor tool (persistent: true) watching /tmp/sonar.log every 10s:
   - If 2 minutes with no new output → report "process may be stuck"
   - If ERROR appears → report immediately
   - If EXECUTION SUCCESS or Total time: appears → report and call TaskStop on the monitor task
3. Run synchronously (timeout: 600000):
   ```
   bash scripts/sonar.sh 2>&1 | tee /tmp/sonar.log
   ```
4. After analysis completes — call TaskStop on the monitor task if not already stopped.
5. Report results URL: http://localhost:9099/dashboard?id=advertisement
6. Always (regardless of `--metrics`): dispatch `Agent({description: "SonarQube bug check",
   subagent_type: "sonar-analyst", prompt: "are there any new BUG or CRITICAL/BLOCKER-severity
   issues?"})` and report its answer in the summary -- this is the real "is it actually OK" check,
   not just the gate's own pass/fail (a gate can pass while real issues exist, depending on which
   conditions it checks).
7. If `--metrics` was passed: also dispatch `Agent({description: "SonarQube metrics",
   subagent_type: "sonar-analyst", prompt: "quality gate status and summary metrics"})`, then
   write its structured summary to `scripts/sonar/report/metrics.md`. Skip this step entirely if
   `--metrics` wasn't passed -- step 6 above always runs either way.

Note: script uses docker cp internally — never use docker run -v.
