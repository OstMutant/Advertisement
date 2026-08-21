Builds the whole reactor inside the shared build-and-test container (works even without a local Java install) — refreshes marketplace-app.jar in the shared maven-cache volume. Optionally runs unit/integration tests too (see scripts/build-and-test/build-and-test.properties for defaults, or pass --unit/--no-unit/--integration/--no-integration). Pass --archunit-metrics to also export marketplace-app's ArchUnit module-coupling metrics (architecture-map.html --with-archunit data) — off by default, several minutes even on a warm build, run this occasionally, not on every call.

Steps:
1. `rm -rf scripts/build-and-test/reports scripts/logs/build-and-test && mkdir -p scripts/logs` --
   clears stale reports/logs from a previous invocation before starting, so a check against these
   paths mid-run can never show leftover data from an earlier call; `mkdir -p scripts/logs`
   guarantees the shared parent directory exists (a real user's own `.bat` entry point gets this
   from `clean.bat` instead -- see that file's own header -- but a direct `bash` invocation like
   this one doesn't go through it, so this command does it itself).
2. Launch Monitor tool (persistent: true) watching /tmp/build-and-test.log every 10s:
   - If 1 minute with no new output → report "process may be stuck"
   - If ERROR appears in new output → report immediately
   - If BUILD SUCCESS or "Build done" appears → report and call TaskStop on the monitor task
3. Run synchronously (timeout: 600000):
   ```
   bash scripts/build-and-test.sh 2>&1 | tee /tmp/build-and-test.log
   ```
4. After the build completes — call TaskStop on the monitor task if not already stopped.
5. Report success (or failure with the exact error lines).
