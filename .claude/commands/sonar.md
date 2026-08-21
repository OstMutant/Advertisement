Run SonarQube static analysis for the marketplace app.

Steps:
1. `rm -rf scripts/sonar/report scripts/logs/sonar && mkdir -p scripts/logs` -- clears the stale
   report/log from a previous invocation before starting, so a check against these paths mid-run
   can never show leftover data from an earlier call; `mkdir -p scripts/logs` guarantees the
   shared parent directory exists (see `scripts/clean.bat`'s own header for why this matters).
2. Launch Monitor tool (persistent: true) watching /tmp/sonar.log every 10s:
   - If 2 minutes with no new output → report "process may be stuck"
   - If ERROR appears → report immediately
   - If EXECUTION SUCCESS or Analysis total time appears → report and call TaskStop on the monitor task
3. Run synchronously (timeout: 600000):
   ```
   bash scripts/sonar.sh 2>&1 | tee /tmp/sonar.log
   ```
4. After analysis completes — call TaskStop on the monitor task if not already stopped.
5. Report results URL: http://localhost:9099/dashboard?id=advertisement

Note: script uses docker cp internally — never use docker run -v.
