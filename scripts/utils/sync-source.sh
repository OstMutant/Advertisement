#!/usr/bin/env bash
# Description: Streams the current git working tree (tracked + untracked-not-gitignored files)
#   into a running container's /app, wiping it first so a file deleted from the working tree since
#   the last sync doesn't linger stale inside the container and break a build (a compile against a
#   since-removed .java, say). The file set comes from `git ls-files`, not a tar tree-walk -- git
#   already excludes .git, every */target, node_modules, and report/log dirs for free, and never
#   trips over a socket/FIFO or an IDE-locked build artifact. Extracted from scripts/ci/run.sh so
#   any container needing a live copy of the working tree (ci-runner, or a standalone dev-shell
#   container for scripts/activity-monitor.sh's --in-container mode) shares one implementation.
# Usage: source this file, then call: sync_source_into <container-name>
# Uses: git, tar, docker
# Input: the caller's own working tree (git ls-files' tracked + untracked-not-ignored set).
# Outputs: none on the host; wipes and repopulates /app inside the named container.
# Returns: (via sync_source_into) 0 on success; 1 if any of git ls-files/tar/the container-side
#   extract failed, printing which stage failed to stderr.

sync_source_into() {
  local container="$1"
  local root
  root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

  git -C "$root" ls-files -z --cached --others --exclude-standard \
    | tar -C "$root" --null --no-recursion -T - -cf - \
    | docker exec -i "$container" sh -c 'find /app -mindepth 1 -delete 2>/dev/null; exec tar -C /app -xf -'
  local rc=("${PIPESTATUS[@]}")

  if [ "${rc[0]}" -ne 0 ] || [ "${rc[1]}" -ne 0 ] || [ "${rc[2]}" -ne 0 ]; then
    echo "ERROR: working-tree sync into $container failed (ls-files=${rc[0]} tar=${rc[1]} extract=${rc[2]})" >&2
    return 1
  fi
  return 0
}
