#!/usr/bin/env bash
# ── Header ────────────────────────────────────────────────
# Description: Retries a Docker pull command up to 3 times, 5 s apart, so a single transient
#   registry or layer-extraction failure does not abort the calling script. Also pulls, with
#   retry, only the images that are not present locally, for a given image list or a
#   Dockerfile's FROM images.
# Usage: source scripts/utils/docker-pull-retry.sh; docker_pull_retry docker pull <image>
#   or docker_pull_retry docker compose -f <file> pull -q
#   or docker_pull_missing_retry <image>...
#   or docker_pull_missing_bases <Dockerfile>
# Uses: bash, docker, awk, sort.
# Env: None.
# Input: For docker_pull_missing_bases, the Dockerfile named by its argument.
# Outputs: the pull command's own output, plus a stderr line per failed attempt.
# Returns: 0 as soon as one attempt succeeds, 1 after the third failure (when sourced under
#   `set -e`, the calling script stops there). docker_pull_missing_retry and
#   docker_pull_missing_bases return 1 at the first image that cannot be pulled.
# ────────────────────────────────────────────────────────

#######################################
# Runs the given Docker pull command, retrying up to 3 times with a 5 s pause between attempts.
# Arguments: the pull command and its arguments, e.g. docker pull <image>.
# Outputs: the command's own output, plus a stderr line per failed attempt.
# Returns: 0 on the first successful attempt, 1 after the third failure.
#######################################
docker_pull_retry() {
  local attempt
  for attempt in 1 2 3; do
    "$@" && return 0
    echo "Pull failed (attempt $attempt/3)." >&2
    [ "$attempt" -lt 3 ] && sleep 5
  done
  return 1
}

#######################################
# Pulls, with retry, each given image that is not already present locally.
# Arguments: one or more image references.
# Outputs: the pull output of each image that was missing, plus a stderr line per failed attempt.
# Returns: 0 when every image is present or pulled, 1 at the first image that cannot be pulled.
#######################################
docker_pull_missing_retry() {
  local image
  for image in "$@"; do
    docker image inspect "$image" >/dev/null 2>&1 || docker_pull_retry docker pull -q "$image" >/dev/null || return 1
  done
}

#######################################
# Runs docker_pull_missing_retry for every FROM image of a Dockerfile, skipping images with variables.
# Arguments: the Dockerfile path.
# Outputs: the pull output of each missing image, plus a stderr line per failed attempt.
# Returns: 0 when every base image is present or pulled, 1 at the first image that cannot be pulled.
#######################################
docker_pull_missing_bases() {
  local base
  while read -r base; do
    case "$base" in *'$'*) continue ;; esac
    docker_pull_missing_retry "$base" || return 1
  done < <(awk 'tolower($1)=="from" { i = 2; while ($i ~ /^--/) i++; img = $i; if (tolower(img) != "scratch" && !(tolower(img) in alias)) print img; if (tolower($(i + 1)) == "as") alias[tolower($(i + 2))] = 1 }' "$1" | sort -u)
}
