#!/usr/bin/env bash
# Description: Copies known report/log output paths from the dev-shell container's own /app back
#   onto the host after a scripts/activity-monitor.sh --in-container run finishes -- dev-shell has
#   no bind mount to the host (see sync-source.sh's own header: sync is host->container only), so
#   anything a wrapped script (playwright.sh, build-and-test.sh, sonar.sh, ...) writes stays inside
#   dev-shell's own disposable filesystem unless pulled out explicitly here. Mirrors
#   scripts/ci/run.sh's own sync_artifacts() (the same problem, solved there for ci-runner) --
#   best-effort per path, since a given run only ever produces a subset of these.
# Usage: source this file, then call: sync_artifacts_from_dev_shell
# Uses: docker
# Input: dev-shell's own /app/scripts/logs/*, /app/playwright/pw-report/,
#   /app/scripts/build-and-test/reports/, /app/scripts/sonar/report/, /app/tmp/activity-monitor/.
# Outputs: the same paths, host-side, created if missing; errors suppressed per path (a script that
#   didn't run never produced its own output, that's expected, not a failure).
# Returns: 0 always -- best-effort by design, matching scripts/ci/run.sh's own sync_artifacts().

sync_artifacts_from_dev_shell() {
  local container="dev-shell"
  local root
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

  # No `mkdir -p` here -- a raw host-side mkdir against a WSL/Windows-drive path can fail with
  # "Permission denied" (the same docker-desktop-bind-mounts issue already fixed this way in
  # build-and-test/run.sh/run-all-tests/run.sh/playwright/run.sh). `docker cp` creates its
  # destination's own missing leaf directory itself and is daemon-mediated, not subject to that bug.

  # activity-monitor's own tree.txt/raw.log/history.tsv for whichever script(s) ran inside
  # dev-shell -- the same paths Monitor/--render/--watch read on a local (non-container) run, so a
  # human or agent checking back later sees the real last result without needing `docker exec`.
  docker cp "$container:/tmp/activity-monitor/." "/tmp/activity-monitor/" 2>/dev/null

  docker cp "$container:/app/scripts/logs/playwright/." "$root/scripts/logs/playwright/" 2>/dev/null
  docker cp "$container:/app/playwright/pw-report/." "$root/playwright/pw-report/" 2>/dev/null
  docker cp "$container:/app/scripts/build-and-test/reports/." "$root/scripts/build-and-test/reports/" 2>/dev/null
  docker cp "$container:/app/scripts/logs/build-and-test/." "$root/scripts/logs/build-and-test/" 2>/dev/null
  docker cp "$container:/app/scripts/sonar/report/." "$root/scripts/sonar/report/" 2>/dev/null
  docker cp "$container:/app/scripts/logs/sonar/." "$root/scripts/logs/sonar/" 2>/dev/null

  return 0
}
