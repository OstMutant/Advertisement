Rebuild the marketplace-app Docker image and start a fresh container using the project deploy script. Already reuses scripts/build-and-test.sh's shared maven-cache jar internally by default (see /build-and-test — no duplicate compile step), unless --from-scratch is passed.

Steps:
1. Launch Monitor tool (persistent: true) watching /tmp/deploy.log every 10s:
   - If 1 minute with no new output → report "process may be stuck"
   - If ERROR appears in new output → report immediately
   - If BUILD SUCCESS or Started Application → report and call TaskStop on the monitor task
2. Run synchronously (timeout: 600000):
   ```
   bash scripts/deploy-and-run.sh 2>&1 | tee /tmp/deploy.log
   ```
   Optional flags: `--reset` to wipe DB/MinIO volumes, `--restart-infra` to restart containers only, `--reset-only-db` to truncate app tables, `--from-scratch` to build fully isolated instead of reusing build-and-test.sh.
3. After deploy completes — call TaskStop on the monitor task if not already stopped.
4. Report success and confirm the app is ready.
