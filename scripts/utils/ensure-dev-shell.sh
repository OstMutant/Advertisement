#!/usr/bin/env bash
# Description: Ensures a persistent "dev-shell" container is running -- a plain
#   Docker-outside-of-Docker shell (docker.sock mounted, same tool image scripts/ci/Dockerfile
#   already builds for ci-runner: Java/Maven/Docker CLI) for scripts/activity-monitor.sh's
#   --in-container mode to exec standalone commands (deploy-and-run.sh, playwright.sh, ...) into.
#   Deliberately a separate container from ci-runner, not a second use of ci-runner itself: an
#   ad-hoc exec sharing ci-runner's own container would race a real `ci.sh` DAG run's own
#   container lifecycle and its own working-tree sync, colliding on the same /app and the same
#   fixed-name e2e stack (the exact "concurrent runs collide" failure mode already fixed for
#   ci.sh itself, see .claude/nav/adr-index.md) -- dev-shell only ever runs one exec at a time, for
#   one interactive user, and is never touched by ci.sh. Reuses the ci-runner image tag (builds it
#   if missing) rather than maintaining a second Dockerfile, since the toolset needed is identical.
#   Self-terminates after DEV_SHELL_IDLE_SECONDS of no real use (an internal watchdog loop checks a
#   last-used timestamp file every 60s) so it never accumulates as a permanent, always-on resource
#   -- `--rm` means Docker removes the container itself the moment the watchdog exits.
# Usage: source this file, then call:
#   ensure_dev_shell           -- idempotent; starts the container if not already running, and
#                                 always refreshes its idle timer (the "real use" this file exists
#                                 to track) regardless of whether a start actually happened.
# Env: DEV_SHELL_CONTAINER (default "dev-shell"), DEV_SHELL_IMAGE (default "ci-runner"),
#   DEV_SHELL_IDLE_SECONDS (default 3600 -- how long since the last ensure_dev_shell call before
#   the container self-terminates).
# Uses: docker
# Input: scripts/ci/Dockerfile (only read if the ci-runner image doesn't exist yet).
# Outputs: a running container named $DEV_SHELL_CONTAINER (auto-removed by Docker once idle), a
#   docker volume "dev-shell-m2-cache" (persists across container restarts, not removed by idle
#   cleanup -- only the container itself is disposable).
# Returns: (via ensure_dev_shell) 0 once the container is confirmed running; 1 on an image-build
#   or container-start failure, printing the real docker error to stderr.

ensure_dev_shell() {
  local container="${DEV_SHELL_CONTAINER:-dev-shell}"
  local image="${DEV_SHELL_IMAGE:-ci-runner}"
  local idle_seconds="${DEV_SHELL_IDLE_SECONDS:-3600}"
  local root
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

  if [ "$(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null)" = "true" ]; then
    docker exec "$container" touch /tmp/.dev-shell-last-used
    return 0
  fi

  if ! docker image inspect "$image" >/dev/null 2>&1; then
    echo "=== Building $image image (shared by ci-runner and dev-shell) ===" >&2
    if ! docker build -f "$root/scripts/ci/Dockerfile" -t "$image" "$root"; then
      echo "ERROR: failed to build $image image for dev-shell" >&2
      return 1
    fi
  fi

  docker volume create dev-shell-m2-cache >/dev/null

  echo "=== Starting $container (idle timeout ${idle_seconds}s) ===" >&2
  docker rm -f "$container" >/dev/null 2>&1
  # --entrypoint skips ci-runner's own entrypoint (would start a second, colliding Dagu server); the watchdog below is this container's own main process, self-exiting past the idle threshold so --rm removes it.
  if ! docker run -d --rm --name "$container" \
    --network host \
    --entrypoint sh \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -v dev-shell-m2-cache:/root/.m2 \
    "$image" -c '
      touch /tmp/.dev-shell-last-used
      while true; do
        sleep 60
        last=$(stat -c %Y /tmp/.dev-shell-last-used 2>/dev/null || echo 0)
        now=$(date +%s)
        if [ $((now - last)) -gt '"$idle_seconds"' ]; then
          exit 0
        fi
      done
    ' >/dev/null; then
    echo "ERROR: failed to start $container" >&2
    return 1
  fi

  # No Dagu/web-UI readiness check needed here (unlike ci-runner) -- no long-lived server of its own.
  for _ in $(seq 1 30); do
    docker exec "$container" true >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "ERROR: $container did not become responsive within 30s" >&2
  return 1
}
