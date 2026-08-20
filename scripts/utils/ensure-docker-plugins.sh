#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Idempotent check-and-install for Docker CLI plugins this sandbox doesn't ship by
#   default (buildx, compose v2) -- a normal Docker Desktop / docker-ce install already has both,
#   not a code fix for a real bug. Source this file, call the function you need.
# Usage: source scripts/utils/ensure-docker-plugins.sh; then call ensure_buildx / ensure_docker_compose.
#   Can also run directly (bash scripts/utils/ensure-docker-plugins.sh) to install both, e.g. as a
#   one-off sandbox bootstrap step.
# Uses: bash, curl.
# Env: None.
# Input: None.
# Outputs: installs a plugin into ~/.docker/cli-plugins/ if missing.
# Returns: 0 always when sourced (each function no-ops if its plugin is already present); when run
#   directly, the exit code of whichever install step ran last.
# ────────────────────────────────────────────────────────────────────────────

#######################################
# Ensures `docker buildx` is available, installing it into ~/.docker/cli-plugins/ if missing.
# Arguments: None.
# Outputs: progress messages to stdout.
# Returns: 0 always.
#######################################
ensure_buildx() {
  if docker buildx version >/dev/null 2>&1; then
    return 0
  fi
  echo "docker buildx plugin not found — installing..."
  mkdir -p ~/.docker/cli-plugins
  curl -Lo ~/.docker/cli-plugins/docker-buildx \
    https://github.com/docker/buildx/releases/download/v0.21.0/buildx-v0.21.0.linux-amd64
  chmod +x ~/.docker/cli-plugins/docker-buildx
  echo "docker buildx installed."
}

#######################################
# Ensures `docker compose` is available, installing it into ~/.docker/cli-plugins/ if missing.
# Arguments: None.
# Outputs: progress messages to stdout.
# Returns: 0 always.
#######################################
ensure_docker_compose() {
  if docker compose version >/dev/null 2>&1; then
    return 0
  fi
  echo "docker compose plugin not found — installing..."
  mkdir -p ~/.docker/cli-plugins
  curl -Lo ~/.docker/cli-plugins/docker-compose \
    https://github.com/docker/compose/releases/download/v2.32.4/docker-compose-linux-x86_64
  chmod +x ~/.docker/cli-plugins/docker-compose
  echo "docker compose installed."
}

# Allow running this file directly to install both, e.g. as a one-off sandbox bootstrap step.
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  ensure_buildx
  ensure_docker_compose
fi
