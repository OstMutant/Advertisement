Builds the whole reactor inside the shared build-and-test container (works even without a local Java install) — refreshes marketplace-app.jar in the shared maven-cache volume. Optionally runs unit/integration tests too (see scripts/build-and-test/build-and-test.properties for defaults, or pass --unit/--no-unit/--integration/--no-integration).

Steps:
1. Launch Monitor tool (persistent: true) watching /tmp/build-and-test.log every 10s:
   - If 1 minute with no new output → report "process may be stuck"
   - If ERROR appears in new output → report immediately
   - If BUILD SUCCESS or "Build done" appears → report and call TaskStop on the monitor task
2. Run synchronously (timeout: 600000):
   ```
   bash scripts/build-and-test.sh 2>&1 | tee /tmp/build-and-test.log
   ```
3. After the build completes — call TaskStop on the monitor task if not already stopped.
4. Report success (or failure with the exact error lines).
