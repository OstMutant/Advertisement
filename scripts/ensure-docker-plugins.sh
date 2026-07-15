#!/bin/bash
# Idempotent check-and-install for Docker CLI plugins this sandbox doesn't ship by default
# (buildx, compose v2). Source this file, then call ensure_buildx / ensure_docker_compose from
# whichever script needs one. Each function no-ops if the plugin is already present, so it's safe
# to call on every run, including on a normal developer machine where these already exist.
#
# Not a code fix for a real bug -- this sandbox's Docker install is just missing plugins a normal
# Docker Desktop / docker-ce install ships with. See scripts/CLAUDE.md for the full rationale
# (BuildKit requirement for --mount=type=cache in the Dockerfile, docker-compose-v2 for infra).

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
