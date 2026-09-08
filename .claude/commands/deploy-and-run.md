Rebuild the marketplace-app Docker image and start a fresh container using the project deploy script. Already reuses scripts/build-and-test.sh's shared maven-cache jar internally by default (see /build-and-test — no duplicate compile step), unless --from-scratch is passed.

Steps:
1. Launch Monitor tool (persistent: true) watching
   /tmp/activity-monitor/deploy-and-run.sh/tree.txt every 10s (wait-then-tail wrapper): report a
   step transitioning to ❌, or the tree reaching a stable final state (an `exit_code` file appears
   in the same directory).
2. Run synchronously (timeout: 600000):
   ```
   bash scripts/activity-monitor.sh -- bash scripts/deploy-and-run.sh
   ```
   Optional flags: `--reset` to wipe DB/MinIO volumes, `--restart-infra` to restart containers only, `--reset-only-db` to truncate app tables, `--from-scratch` to build fully isolated instead of reusing build-and-test.sh.
3. After deploy completes — call TaskStop on the monitor task if not already stopped.
4. Report success and confirm the app is ready (on failure, read
   /tmp/activity-monitor/deploy-and-run.sh/raw.log, or `docker logs marketplace-app` for a
   container-associated step, for the real detail behind the ❌).
