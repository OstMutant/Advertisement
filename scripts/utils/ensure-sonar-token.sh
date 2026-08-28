#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Ensures the local SonarQube server container is up (waiting through a
#   DB_MIGRATION_NEEDED cycle if needed) and that the stored auth token authenticates against it,
#   regenerating via admin/admin if not. Shared by scripts/sonar/run.sh and the sonar-analyst
#   agent's MCP-launch wrapper, so this logic exists in exactly one place. Source this file, call
#   ensure_sonar_token.
# Usage: source scripts/utils/ensure-sonar-token.sh; then call
#   ensure_sonar_token <compose_file> <props_file> <sonar_url>.
# Uses: bash, docker, curl.
# Env: None.
# Input: None -- paths passed as arguments.
# Outputs: starts/migrates the SonarQube server container if needed; writes a regenerated token
#   into <props_file> if the existing one was invalid; progress messages to stdout.
# Returns: 0 on success; 1 if admin/admin token generation itself failed.
# ────────────────────────────────────────────────────────────────────────────

#######################################
# Ensures the SonarQube server at $3 is reachable and $2's own sonar.token= value authenticates
# against it, regenerating via admin/admin if not.
# Arguments:
#   $1 - compose_file: path to docker-compose.sonar.yml.
#   $2 - props_file: path to sonar-project.properties (read/written for the token).
#   $3 - sonar_url: e.g. http://localhost:9099.
# Outputs: progress messages to stdout; "ERROR: failed to generate SonarQube token..." if
#   admin/admin token generation itself fails.
# Returns: 0 on success, 1 if token generation failed.
#######################################
ensure_sonar_token() {
  local compose_file="$1" props_file="$2" sonar_url="$3"

  docker compose -f "$compose_file" up -d

  echo "Waiting for SonarQube to be ready..."
  local migration_triggered=""
  while true; do
    local status
    status=$(curl -s "$sonar_url/api/system/status" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    [ "$status" = "UP" ] && break
    if [ "$status" = "DB_MIGRATION_NEEDED" ] && [ -z "$migration_triggered" ]; then
      migration_triggered=1
      echo "SonarQube schema needs migrating after the image update — triggering..."
      local migrate_result
      migrate_result=$(curl -s -X POST "$sonar_url/api/system/migrate_db")
      if echo "$migrate_result" | grep -q '"state":"NOT_SUPPORTED"'; then
        echo "Migration not supported on the embedded database — wiping local scan history and starting fresh on the new image..."
        docker compose -f "$compose_file" down -v
        docker compose -f "$compose_file" up -d
      fi
    fi
    sleep 5
  done
  echo "SonarQube ready."

  curl -s -u admin:admin -X POST "$sonar_url/api/settings/set" \
    -d "key=sonar.forceAuthentication&value=false" >/dev/null

  local current_token
  current_token=$(grep "^sonar.token=" "$props_file" | cut -d= -f2 | tr -d '\r')
  if ! curl -s -u "$current_token:" "$sonar_url/api/authentication/validate" | grep -q '"valid":true'; then
    echo "Sonar token invalid or missing — generating new token..."
    local new_token
    new_token=$(curl -s -u admin:admin -X POST "$sonar_url/api/user_tokens/generate" \
      -d "name=claude-$(date +%s)" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    if [ -z "$new_token" ]; then
      echo "ERROR: failed to generate SonarQube token. Check admin credentials."
      return 1
    fi
    sed -i "s|^sonar.token=.*|sonar.token=$new_token|" "$props_file"
    echo "New token saved to sonar-project.properties."
  fi
}
