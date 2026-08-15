#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Installs platform-commons and all domain starters into ~/.m2 via a single mvn
#   install, so integration-tests.sh's own staleness check finds them fresh instead of
#   re-triggering its own install right after unit-tests.sh already compiled the same source.
# Usage: bash scripts/build.sh. No arguments.
# Uses: bash, mvn (install).
# Env: None.
# Input: repo source; the module list is read live from pom.xml's own <module> entries
#   (platform-commons plus anything named *-spring-boot-starter) -- never hand-maintained, so a
#   newly added starter is picked up automatically without editing this file.
# Outputs: fresh jars for platform-commons + every domain starter in ~/.m2/repository.
# Returns: 0 on success, non-zero on install failure.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

MODULES="$(sed -n 's/.*<module>\(.*\)<\/module>.*/\1/p' "$ROOT/pom.xml" \
  | grep -E '^(platform-commons|.*-spring-boot-starter)$' \
  | tr '\n' ',' | sed 's/,$//')"

cd "$ROOT"
echo "Installing: $MODULES"
./mvnw install -pl "$MODULES" -am -DskipTests
