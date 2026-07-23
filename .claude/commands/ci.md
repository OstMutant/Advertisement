Run the local, isolated, parameterized CI runner (scripts/ci.sh -> scripts/ci/run.sh).

Usage: /ci [flags]
Examples:
  /ci                                    # default: most extensive run (unit+integration+e2e+sonar,
                                          # e2e uses "e2e --full --ux"), backgrounded
  /ci --unit --integration --sandbox     # this claude-dev sandbox needs --sandbox for Testcontainers
  /ci --e2e
  /ci --foreground --unit                # block until this one stage finishes (rare -- see step 2)

**Do NOT wrap this in the Monitor + `| tee` blocking pattern used for deploy.sh/playwright.sh/
sonar.sh.** `scripts/ci.sh` already backgrounds itself by default and writes a live progress file
-- re-wrapping it in a blocking foreground call defeats the point (confirmed directly: doing this
once blocked the whole conversation for the full run length and drew direct user pushback). Always
add `--sandbox` in this environment when the run includes `--integration` or defaults to it (no
explicit stage flags) -- Testcontainers needs it here, see scripts/CLAUDE.md.

Steps:
1. Run `bash scripts/ci.sh $ARGUMENTS` as a normal synchronous Bash call (no `run_in_background`,
   no Monitor+tee) -- unless `$ARGUMENTS` contains `--foreground`, this returns within seconds
   (after the image build) and prints two paths: `Progress: <dir>/progress.txt` and
   `Full log: <dir>/run.log`.
2. If `--foreground` was passed: the call blocks until the run finishes -- read its final output
   directly, no Monitor needed. Skip straight to step 5.
3. Otherwise (the default, backgrounded case): launch a `Monitor` tool call polling the printed
   `progress.txt` path, not raw stdout:
   ```
   LAST=""
   while true; do
     CUR=$(cat <progress.txt path> 2>/dev/null)
     if [ "$CUR" != "$LAST" ]; then echo "$CUR"; echo "---"; LAST="$CUR"; fi
     echo "$CUR" | grep -q "RESULT:" && break
     sleep 15
   done
   ```
   `persistent: true`, since a full `--all --sonar` run takes ~15-20 minutes.
4. Continue with other work while the Monitor runs -- do not sleep-poll it yourself, notifications
   arrive automatically on each progress change.
5. On the final `RESULT: PASSED`/`RESULT: FAILED` event (or the `--foreground` call's own output):
   call `TaskStop` on the monitor task if one was launched, then report the per-stage summary
   (which stages passed/failed, elapsed time each) and the report directory path. If any stage
   failed, read the actual failing output from that stage's report subdirectory
   (`<report-dir>/<stage>/run.log` or surefire reports) before reporting -- never just "it failed."
