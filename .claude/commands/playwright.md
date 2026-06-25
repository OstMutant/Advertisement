Run Playwright UI tests for the marketplace app.

Usage: /playwright [scenario] [--full]
Examples: /playwright   /playwright smoke   /playwright 05-seed-filter-sort-pagination --full

Flags:
- --full  — include spec 05 seed data (skipped by default, takes ~2 min extra)
- --ux is always appended automatically

Steps:
1. Check app is running: `docker ps --filter name=marketplace-app --format '{{.Names}}'`
   - If not running, tell the user to run /build first
2. Kill stale processes: `docker exec pw-runner pkill -f "node.*playwright" 2>/dev/null; true`
3. Launch Monitor tool (persistent: true) watching /tmp/playwright.log every 10s:
   - If 2 minutes with no new output → report "process may be stuck"
   - If `failed` or `Error` appears → report immediately
   - If `passed` summary line appears → report and call TaskStop on the monitor task
4. Run synchronously (timeout: 600000):
   ```
   bash scripts/playwright.sh $ARGUMENTS --ux 2>&1 | tee /tmp/playwright.log
   ```
5. After tests complete — call TaskStop on the monitor task if not already stopped.
6. Report pass/fail counts and any failures with error details.
