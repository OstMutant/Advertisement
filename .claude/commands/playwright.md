Run Playwright UI tests for the marketplace app.

Usage: /playwright [scenario] [--ux]
Examples: /playwright smoke --ux   /playwright   /playwright filter-users-advanced

Steps:
1. Check app is running: `docker ps --filter name=marketplace-app --format '{{.Names}}'`
   - If not running, tell the user to run /build first
2. Run: `bash /app/playwright/run.sh $ARGUMENTS`
3. Report pass/fail counts and any failures with error details
4. If --ux flag was passed, read screenshots from `/app/playwright/screenshots/` with the Read tool for visual analysis
