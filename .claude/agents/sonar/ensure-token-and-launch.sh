#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Ensures the local SonarQube server is up and its auth token is valid, then execs
#   into the real sonarqube-mcp Docker container -- this is the sonar-analyst agent's own
#   mcpServers `command`, re-run fresh on every agent dispatch (per Claude Code's own inline-MCP-
#   server lifecycle: connects when the subagent starts, disconnects when it finishes), so the
#   token is guaranteed current every single time, never a stale copy from whenever the Claude
#   Code process itself first started. The official distribution is a Docker image
#   (sonarsource/sonarqube-mcp) -- the npm package of the same name is deprecated/no longer
#   supported (confirmed directly), not a viable alternative.
# Usage: invoked by Claude Code itself as sonar-analyst.md's mcpServers command -- never run
#   directly.
# Uses: bash, docker, curl, scripts/utils/ensure-sonar-token.sh's ensure_sonar_token,
#   scripts/utils/ensure-docker-plugins.sh's ensure_docker_compose.
# Env: SONARQUBE_URL, SONARQUBE_TOOLSETS -- set by the agent's own mcpServers env block, forwarded
#   through unchanged (SONARQUBE_TOOLSETS scopes the MCP server to only the tools sonar-analyst
#   actually needs -- quality-gates/issues/measures, keeping it within the ~4-5-tools-per-agent
#   range instead of the server's full ~20-tool default). SONARQUBE_TOKEN -- set by this script
#   itself (computed fresh below), not by the agent's env block -- never export this yourself.
# Input: scripts/sonar/docker-compose.sonar.yml, scripts/sonar/sonar-project.properties.
# Outputs: starts the SonarQube server container if not already running; regenerates
#   sonar-project.properties's token if invalid; pulls the sonarsource/sonarqube-mcp image if not
#   already present locally.
# Returns: whatever the real sonarqube-mcp container process returns -- exec replaces this
#   script's own process, so this script's own exit code is never observed.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
source "$ROOT/scripts/utils/ensure-sonar-token.sh"
source "$ROOT/scripts/utils/ensure-docker-plugins.sh"
ensure_docker_compose

COMPOSE_FILE="$ROOT/scripts/sonar/docker-compose.sonar.yml"
PROPS_FILE="$ROOT/scripts/sonar/sonar-project.properties"
SONAR_URL="${SONARQUBE_URL:-http://localhost:9099}"

ensure_sonar_token "$COMPOSE_FILE" "$PROPS_FILE" "$SONAR_URL"

SONARQUBE_TOKEN=$(grep "^sonar.token=" "$PROPS_FILE" | cut -d= -f2 | tr -d '\r')

# Same pull-skip-by-default reasoning as scripts/sonar/run.sh's own image-freshness checks -- this
# wrapper re-runs on every single agent dispatch (far more often than a scan), so checking Docker
# Hub every time would add real, repeated latency for no benefit on the common path.
docker image inspect sonarsource/sonarqube-mcp >/dev/null 2>&1 || docker pull -q sonarsource/sonarqube-mcp

# --network host: the MCP server runs in its own container and must reach the sonarqube server
# container's published port at localhost:9099 -- same reasoning as the scanner container in
# scripts/sonar/run.sh, which uses --network host for the identical reason.
exec docker run --init -i --rm --network host \
  -e SONARQUBE_TOKEN="$SONARQUBE_TOKEN" \
  -e SONARQUBE_URL="$SONAR_URL" \
  -e SONARQUBE_TOOLSETS="${SONARQUBE_TOOLSETS:-}" \
  sonarsource/sonarqube-mcp
