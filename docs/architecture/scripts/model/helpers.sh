# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Node.js runner (host node, else a disposable node:22-alpine container), the option flags the generator's argument loop fills, and shared JSON/escaping helpers every other fragment uses.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: node, docker (only when no host node).
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: None.
# Outputs: defines run_node, json_escape and the WITH_* option variables (WITH_SONAR, NO_SONAR_RESCAN, WITH_ARCHUNIT, WITH_CI_METRICS, WITH_ADR_DETAILS).
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── Node.js runner: host `node` if available, else a disposable node:22-alpine container --------
# same "use it if the host has it, else containerize it" self-healing pattern this repo already
# uses for Java/Playwright/SonarScanner (build-and-test/playwright/sonar all run their runtime
# inside a container rather than requiring it on the host) -- a host with Docker Desktop + WSL but
# no host-level node install is a real, confirmed case, not hypothetical.
if command -v node >/dev/null 2>&1; then
  NODE_MODE=host
else
  NODE_MODE=docker
  NODE_IMAGE="node:22-alpine"
  docker image inspect "$NODE_IMAGE" >/dev/null 2>&1 || docker pull -q "$NODE_IMAGE" >/dev/null
fi
run_node() {
  if [ "$NODE_MODE" = host ]; then
    node "$@"
  else
    docker run --rm -i -v "$REPO_ROOT:$REPO_ROOT" -w "$REPO_ROOT" "$NODE_IMAGE" node "$@"
  fi
}

# Opt-in Sonar/ArchUnit/CI-metrics/ADR-details fetch -- all off by default so a plain run never
# triggers a SonarQube rescan, depends on build-and-test.sh --archunit-metrics having run
# recently, depends on a Dagu CI run having run recently, or bakes every module's full ADR text
# into the output. See docs/architecture/scripts/DECISIONS.md.
WITH_SONAR=""
NO_SONAR_RESCAN=""
WITH_ARCHUNIT=""
WITH_CI_METRICS=""
WITH_ADR_DETAILS=""
for arg in "$@"; do
  case "$arg" in
    --with-sonar) WITH_SONAR=1 ;;
    --no-sonar-rescan) NO_SONAR_RESCAN=1 ;;
    --with-archunit) WITH_ARCHUNIT=1 ;;
    --with-ci-metrics) WITH_CI_METRICS=1 ;;
    --with-adr-details) WITH_ADR_DETAILS=1 ;;
  esac
done

# Every real Liquibase changelog holding an actual createTable, discovered dynamically per module
# (db/*/changes/*.xml under each module in pom.xml's own <modules> list) rather than a hand-typed
# path per module -- single source of truth for the live Database ERD (see root CLAUDE.md's
# "Database Changes" guideline) that never goes stale when a new module adds its own schema.
# Declared early so the module-table-ownership computation below can use it too, not just
# db_erd_json() further down. MODULES itself is populated a few lines below via mapfile from
# pom.xml -- this loop runs after that assignment.

json_escape() {
  # Escapes backslash and double-quote, strips newlines/carriage-returns and other C0 control
  # bytes -- sufficient for the plain-text fields this script emits (file paths, short prose
  # lines); not a general-purpose JSON encoder. LC_ALL=C keeps tr from mangling multi-byte UTF-8
  # (em-dashes etc. in command descriptions) while still stripping 0x00-0x1F control bytes.
  printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | LC_ALL=C tr -d '\000-\037'
}

