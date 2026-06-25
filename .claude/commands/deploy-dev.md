Fast JAR hot-swap deploy — builds the JAR locally and copies it into the running container. No Docker image rebuild (~3-4 min vs ~7-10 min for full build).

Requires: infra (DB, MinIO) and the `marketplace-app` container already running. Run `/build` first if the container is not running.

Steps:
1. Launch Monitor tool (persistent: true) watching /tmp/deploy-dev.log every 10s:
   - If 1 minute with no new output → report "process may be stuck"
   - If ERROR appears in new output → report immediately
   - If BUILD SUCCESS or Started Application → report and call TaskStop on the monitor task
2. Run synchronously (timeout: 600000):
   ```
   bash scripts/deploy-dev.sh 2>&1 | tee /tmp/deploy-dev.log
   ```
3. After deploy completes — call TaskStop on the monitor task if not already stopped.
4. Report success and confirm the app is ready.
