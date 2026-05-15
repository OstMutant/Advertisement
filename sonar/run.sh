#!/bin/bash
# Usage:
#   bash /app/sonar/run.sh          — run SonarQube analysis
#
# SonarQube server starts automatically if not running (localhost:9099).
# Results: http://localhost:9099/dashboard?id=advertisement

SONAR_URL="http://localhost:9099"
COMPOSE_FILE="/app/sonar/docker-compose.sonar.yml"
SCANNER_CONTAINER="sonar-scanner"
PROPS_FILE="/app/sonar/sonar-project.properties"

# ── Ensure SonarQube server is running ───────────────────────────────────────
if ! curl -s -o /dev/null "$SONAR_URL/api/system/status"; then
  echo "SonarQube not running — starting..."
  docker compose -f "$COMPOSE_FILE" up -d
  echo "Waiting for SonarQube to be ready..."
  until curl -s "$SONAR_URL/api/system/status" | grep -q '"status":"UP"'; do
    sleep 5
  done
  echo "SonarQube ready."
fi

# ── Reuse scanner container if already running, otherwise start it ────────────
if ! docker inspect "$SCANNER_CONTAINER" &>/dev/null; then
  docker run -d --name "$SCANNER_CONTAINER" --network host \
    sonarsource/sonar-scanner-cli:latest sleep 86400
else
  STATUS=$(docker inspect -f '{{.State.Status}}' "$SCANNER_CONTAINER" 2>/dev/null)
  if [ "$STATUS" != "running" ]; then
    docker rm -f "$SCANNER_CONTAINER"
    docker run -d --name "$SCANNER_CONTAINER" --network host \
      sonarsource/sonar-scanner-cli:latest sleep 86400
  fi
fi

# ── Copy source and compiled files to container ───────────────────────────────
echo "Copying source files..."
docker exec --user root "$SCANNER_CONTAINER" rm -rf /tmp/sonar-src
docker exec "$SCANNER_CONTAINER" mkdir -p /tmp/sonar-src

for module in sql-engine advertisement-contracts audit-spring-boot-starter attachment-spring-boot-starter advertisement-app; do
  if [ -d "/app/$module/src/main/java" ]; then
    docker exec "$SCANNER_CONTAINER" mkdir -p "/tmp/sonar-src/$module/src/main/java"
    docker cp "/app/$module/src/main/java/." "$SCANNER_CONTAINER:/tmp/sonar-src/$module/src/main/java/"
  fi
  if [ -d "/app/$module/target/classes" ]; then
    docker exec "$SCANNER_CONTAINER" mkdir -p "/tmp/sonar-src/$module/target/classes"
    docker cp "/app/$module/target/classes/." "$SCANNER_CONTAINER:/tmp/sonar-src/$module/target/classes/"
  fi
done

docker cp "$PROPS_FILE" "$SCANNER_CONTAINER:/tmp/sonar-src/sonar-project.properties"

# ── Run analysis ──────────────────────────────────────────────────────────────
echo "Running SonarQube analysis..."
docker exec "$SCANNER_CONTAINER" sonar-scanner \
  -Dproject.settings=/tmp/sonar-src/sonar-project.properties \
  -Dsonar.projectBaseDir=/tmp/sonar-src

EXIT_CODE=$?

if [ $EXIT_CODE -eq 0 ]; then
  echo ""
  echo "Analysis complete: $SONAR_URL/dashboard?id=advertisement"
fi

exit $EXIT_CODE
