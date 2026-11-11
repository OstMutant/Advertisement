#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Brings the Docker host to a clean state for a full run: removes the project's
#   containers and, only for the scopes named, its volumes and the Docker build cache -- never the
#   container of the working session. Prints each object it considers with a verdict before
#   removing anything; objects outside the scope lists are never touched.
# Usage: bash scripts/docker-clean.sh [--caches] [--reports] [--data] [--sonar] [--ci-history]
#   [--orphans] [--builder-cache] [--all] [--dry-run] [--yes]
#   (no scope flag)  containers only
#   --caches         build caches and the jar volumes: the next run rebuilds them, nothing is lost
#   --reports        the test-report volumes: past reports are lost
#   --data           the dev and CI databases and object stores: the data is lost
#   --sonar          the SonarQube volumes: analysis history and issue states are lost
#   --ci-history     the CI runner's home volume: the run history is lost
#   --orphans        unnamed volumes that no container uses (they may belong to another project)
#   --builder-cache  the Docker build cache: shared by the whole host, not only this project
#   --all            every scope above
#   --dry-run        print the plan, remove nothing
#   --yes            skip the typed confirmation that every scope except containers and --caches needs
# Uses: bash, docker, awk, grep, tail.
# Env: set directly in the calling shell, all optional, space-separated names:
#   CLEAN_PROTECTED_CONTAINERS (default claude-dev); CLEAN_CONTAINERS, CLEAN_CACHE_VOLUMES,
#   CLEAN_REPORT_VOLUMES, CLEAN_DATA_VOLUMES, CLEAN_SONAR_VOLUMES, CLEAN_CI_HISTORY_VOLUMES (each
#   defaults to the list written in the variable block at the top of this script);
#   CLEAN_ORPHAN_PATTERN (default a 64-character hex name, the form Docker gives an unnamed
#   volume) -- they exist so the script can be tested on throw-away objects.
# Input: the Docker daemon (containers, volumes, sizes from `docker system df -v`).
# Outputs: a table per group (containers, each volume scope, the build cache, unlisted volumes) and
#   the space freed. A container that mounts the Docker socket and binds /app from the host (the
#   working session) or is named in CLEAN_PROTECTED_CONTAINERS is never removed, nor is any volume
#   still used by a container that stays; the session cannot see its own id from inside, so it is
#   recognised by those two bind mounts. A scope that is not a pure cache asks for the typed word
#   DELETE on a terminal, or --yes when there is none; without either nothing is removed.
# Returns: 0 on success or --dry-run, 1 if a removal failed, 2 on a usage error or a refused
#   confirmation.
# ────────────────────────────────────────────────────────────────────────────
set -uo pipefail

read -ra CONTAINERS <<< "${CLEAN_CONTAINERS:-marketplace-app ci-marketplace-app advertisement-db advertisement-seaweedfs ci-advertisement-db ci-advertisement-seaweedfs ci-runner ci-runner-dagu-proxy sonarqube sonar-scanner sonar-scanner-ci pw-runner ci-pw-runner arch-map-shot dev-shell advertisement-build-only advertisement-build-only-deploy advertisement-build-only-sonar run-all-tests-reports}"
read -ra PROTECTED_NAMES <<< "${CLEAN_PROTECTED_CONTAINERS:-claude-dev}"
DEFAULT_ORPHAN_PATTERN='^[0-9a-f]{64}$'
ORPHAN_PATTERN="${CLEAN_ORPHAN_PATTERN:-$DEFAULT_ORPHAN_PATTERN}"

declare -A SCOPE_VOLUMES=(
  [caches]="${CLEAN_CACHE_VOLUMES:-maven-cache vaadin-cache dev-shell-m2-cache ci-maven-cache ci-vaadin-cache ci-m2-cache ci-tools-cache marketplace-app-jar ci-marketplace-app-jar}"
  [reports]="${CLEAN_REPORT_VOLUMES:-test-reports ci-test-reports}"
  [data]="${CLEAN_DATA_VOLUMES:-advertisement_postgres_data advertisement_seaweedfs_data app_postgres_data app_seaweedfs_data ci_advertisement_postgres_data ci_advertisement_seaweedfs_data ci_advertisement_minio_data}"
  [sonar]="${CLEAN_SONAR_VOLUMES:-sonar_sonarqube_data sonar_sonarqube_extensions sonar_sonarqube_logs sonar_sonarqube_temp}"
  [ci-history]="${CLEAN_CI_HISTORY_VOLUMES:-ci-dagu-home}"
)
ALL_SCOPES=(caches reports data sonar ci-history orphans builder-cache)
CONFIRMED_SCOPES=(reports data sonar ci-history orphans builder-cache)

declare -A WANT=()
DRY_RUN=false
ASSUME_YES=false
for arg in "$@"; do
  case "$arg" in
    --caches|--reports|--data|--sonar|--ci-history|--orphans|--builder-cache) WANT[${arg#--}]=1 ;;
    --all) for s in "${ALL_SCOPES[@]}"; do WANT[$s]=1; done ;;
    --dry-run) DRY_RUN=true ;;
    --yes) ASSUME_YES=true ;;
    *) echo "Unknown argument: $arg (see the header of $0)" >&2; exit 2 ;;
  esac
done

row() { printf '  %-46s %10s  %s\n' "$1" "$2" "$3"; }

# Converts a size such as 1.5GB to bytes, in the decimal units `docker system df` prints.
to_bytes() {
  awk -v s="$1" 'BEGIN {
    m["B"]=1; m["kB"]=1e3; m["MB"]=1e6; m["GB"]=1e9; m["TB"]=1e12
    if (match(s, /[A-Za-z]+$/)) { u=substr(s, RSTART); n=substr(s, 1, RSTART-1) } else { u="B"; n=s }
    printf "%.0f", n * m[u] }'
}

to_human() {
  awk -v b="$1" 'BEGIN { split("B kB MB GB TB", u, " "); i=1; while (b >= 1000 && i < 5) { b/=1000; i++ } printf "%.1f%s", b, u[i] }'
}

# True when the container binds /app and the Docker socket from the host, as the working session does.
is_session_container() {
  local mounts
  mounts=$(docker inspect -f '{{range .Mounts}}{{.Type}}:{{.Destination}}{{"\n"}}{{end}}' "$1" 2>/dev/null) || return 1
  grep -qx 'bind:/app' <<< "$mounts" && grep -qx 'bind:/var/run/docker.sock' <<< "$mounts"
}

declare -A VOL_SIZE=()
while read -r name _ size; do
  VOL_SIZE[$name]=$size
done < <(docker system df -v 2>/dev/null | awk '
  /^Local Volumes space usage:/ { f=1; next }
  f && /^VOLUME NAME/ { next }
  f && NF == 0 { if (seen) exit; next }
  f { seen=1; print $1, $2, $3 }')

# ── Containers ───────────────────────────────────────────────────────────

declare -A PROTECTED=() IN_LIST=() TO_REMOVE=()
for c in "${PROTECTED_NAMES[@]}"; do PROTECTED[$c]=1; done
for c in "${CONTAINERS[@]}"; do IN_LIST[$c]=1; done
mapfile -t ALL_CONTAINERS < <(docker ps -a --format '{{.Names}}')
for c in "${ALL_CONTAINERS[@]}"; do
  is_session_container "$c" && PROTECTED[$c]=1
done

echo "== containers =="
for c in "${ALL_CONTAINERS[@]}"; do
  if [[ -n ${PROTECTED[$c]:-} ]]; then
    row "$c" "" "kept: protected"
  elif [[ -n ${IN_LIST[$c]:-} ]]; then
    row "$c" "" "remove"
    TO_REMOVE[$c]=1
  else
    row "$c" "" "kept: not a project container"
  fi
done

# ── Volumes ──────────────────────────────────────────────────────────────

TO_REMOVE_VOLUMES=()
TOTAL_BYTES=0

# Prints one volume's verdict and queues it for removal unless a container that stays uses it; a missing volume is skipped.
plan_volume() {
  local v=$1 u remaining=()
  docker volume inspect "$v" >/dev/null 2>&1 || return 0
  while read -r u; do
    [[ -z $u || -n ${TO_REMOVE[$u]:-} ]] || remaining+=("$u")
  done < <(docker ps -a --filter "volume=$v" --format '{{.Names}}')
  if ((${#remaining[@]})); then
    row "$v" "${VOL_SIZE[$v]:--}" "kept: in use by ${remaining[*]}"
  else
    row "$v" "${VOL_SIZE[$v]:--}" "remove"
    TO_REMOVE_VOLUMES+=("$v")
    TOTAL_BYTES=$((TOTAL_BYTES + $(to_bytes "${VOL_SIZE[$v]:-0B}")))
  fi
}

declare -A LISTED=()
for scope in caches reports data sonar ci-history; do
  read -ra names <<< "${SCOPE_VOLUMES[$scope]}"
  for v in "${names[@]}"; do LISTED[$v]=1; done
  [[ -n ${WANT[$scope]:-} ]] || continue
  echo "== volumes: $scope =="
  for v in "${names[@]}"; do plan_volume "$v"; done
done

mapfile -t ORPHANS < <(docker volume ls -q | grep -E "$ORPHAN_PATTERN")
if [[ -n ${WANT[orphans]:-} ]]; then
  echo "== volumes: orphans =="
  for v in "${ORPHANS[@]}"; do plan_volume "$v"; done
fi

echo "== volumes: kept, not a project volume =="
while read -r v; do
  [[ -z $v || -n ${LISTED[$v]:-} ]] && continue
  grep -qE "$ORPHAN_PATTERN" <<< "$v" && continue
  row "$v" "${VOL_SIZE[$v]:--}" "kept: not a project volume"
done < <(docker volume ls -q)
if [[ -z ${WANT[orphans]:-} ]]; then
  row "${#ORPHANS[@]} unnamed volumes" "" "kept: add --orphans to remove the unused ones"
fi

# ── Build cache ──────────────────────────────────────────────────────────

if [[ -n ${WANT[builder-cache]:-} ]]; then
  echo "== docker build cache =="
  row "build cache (whole host)" "$(docker system df --format '{{.Type}}|{{.Size}}' | awk -F'|' '$1 == "Build Cache" { print $2 }')" "remove"
fi

# ── Confirmation ─────────────────────────────────────────────────────────

echo
echo "Plan: ${#TO_REMOVE[@]} containers, ${#TO_REMOVE_VOLUMES[@]} volumes (about $(to_human "$TOTAL_BYTES"))${WANT[builder-cache]:+, the Docker build cache}."
if $DRY_RUN; then
  echo "--dry-run: nothing removed."
  exit 0
fi

NEEDS_CONFIRM=false
for s in "${CONFIRMED_SCOPES[@]}"; do [[ -n ${WANT[$s]:-} ]] && NEEDS_CONFIRM=true; done
if $NEEDS_CONFIRM && ! $ASSUME_YES; then
  if [[ -t 0 ]]; then
    read -r -p "Data, history or host-wide objects are in scope. Type DELETE to continue: " answer
    [[ $answer == DELETE ]] || { echo "Not confirmed, nothing removed." >&2; exit 2; }
  else
    echo "Refused: no terminal to confirm on; pass --yes to remove data, history or host-wide objects." >&2
    exit 2
  fi
fi

# ── Removal ──────────────────────────────────────────────────────────────

STATUS=0
for c in "${!TO_REMOVE[@]}"; do
  docker rm -f "$c" >/dev/null || { echo "Failed to remove container $c" >&2; STATUS=1; }
done

FREED_BYTES=0
for v in "${TO_REMOVE_VOLUMES[@]}"; do
  if err=$(docker volume rm "$v" 2>&1 >/dev/null); then
    FREED_BYTES=$((FREED_BYTES + $(to_bytes "${VOL_SIZE[$v]:-0B}")))
  else
    echo "Kept volume $v: $err" >&2
    STATUS=1
  fi
done

if [[ -n ${WANT[builder-cache]:-} ]]; then
  docker builder prune -af | tail -1 || STATUS=1
fi

echo "Done: volumes freed about $(to_human "$FREED_BYTES")."
exit $STATUS
