#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Regenerates the architecture documentation model -- runs the whole generation
#   pipeline (generate-architecture-model.sh, then unless --no-check
#   check-architecture-model-freshness.sh, a reproducibility check re-running the generator a
#   second time to diff against the first) inside a fresh, disposable container built from
#   scripts/Dockerfile (Node.js + Python 3, both baked into the image at build time -- no runtime
#   `apt-get`). Every run: removes any leftover container from a prior run, starts a new one,
#   tar-pipes repo source in, runs the generation, docker-cp's results back out, then removes the
#   container again -- same tar-pipe/docker-cp core mechanism scripts/build-and-test/run.sh already
#   uses for the same reason (avoids depending on host node/python being installed, avoids the
#   "-v mount resolves empty when the caller is itself a container" nested-Docker problem, and
#   avoids a real, confirmed Windows/WSL Permission Denied class of issue this same tar-pipe
#   pattern already works around elsewhere in this repo -- see build-and-test/run.sh's own header
#   comment on the identical symptom). Unless --no-screenshot, finishes by running
#   screenshot-architecture-map.sh (this file's own sibling), which already uses its own separate,
#   long-lived Playwright container -- unchanged.
# Usage: bash docs/architecture/architecture-doc.sh [--no-check] [--no-screenshot] [--rebuild-image]
#   (no flags)        regenerate the architecture model, verify it's reproducible (regenerate a
#                      second time, diff against the first), then screenshot every screen of
#                      architecture-map.html
#   --no-check        skip the reproducibility re-verification pass (only meaningful right after
#                      this same script already regenerated the file -- re-checking your own
#                      fresh output is pointless)
#   --no-screenshot   skip screenshotting architecture-map.html
#   --rebuild-image   force-rebuild the arch-doc-tools image even if one already exists and the
#                      Dockerfile hasn't changed -- picks up new base-image/package versions
#                      (e.g. a newer node:22-bookworm-slim or python3 patch release)
# Uses: bash, docker, tar, scripts/Dockerfile's own arch-doc-tools image (built once, rebuilt only
#   when the Dockerfile itself changes or --rebuild-image is passed -- same staleness check
#   scripts/build-and-test/run.sh's own image build already uses), scripts/screenshot-architecture-map.sh
#   (own separate Playwright container).
# Env: None.
# Input: repo source (every Java module, Liquibase changelog, ADR, backlog file the generator
#   scans).
# Outputs: docs/architecture/data/architecture-model.json, docs/architecture/architecture-map.html
#   (this file's own siblings), and any regenerated module DECISIONS.md pointer file; with the
#   reproducibility check enabled, an ERROR naming which file differs between two consecutive
#   regenerations, if any; with screenshots enabled, PNG files under the screenshot script's own
#   output directory. The generation container itself never persists past this script's own exit.
# Returns: 0 on success; non-zero if generation fails, the reproducibility check finds drift, or
#   the screenshot step fails.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
NO_CHECK=false
NO_SCREENSHOT=false
REBUILD_IMAGE=false
for arg in "$@"; do
  case "$arg" in
    --no-check) NO_CHECK=true ;;
    --no-screenshot) NO_SCREENSHOT=true ;;
    --rebuild-image) REBUILD_IMAGE=true ;;
  esac
done

GEN_CONTAINER="arch-doc-gen"
GEN_IMAGE="arch-doc-tools"
DOCKERFILE="$DIR/scripts/Dockerfile"

# Modules with no hand-authored DECISIONS.md of their own -- generate_pointer_decisions_md() may
# regenerate these; copied back out individually below (docker cp has no recursive-but-selective
# mode across unrelated top-level directories).
POINTER_MODULES=(user-spring-boot-starter advertisement-spring-boot-starter provider-profile-spring-boot-starter)

# ── Ensure the generation image exists and is current (rebuilds if Dockerfile changed since last
# build, or unconditionally with --rebuild-image) -- same staleness check
# scripts/build-and-test/run.sh's own image build uses. --rebuild-image passes --no-cache too --
# without it, Docker's own layer cache would just reproduce the identical image (the Dockerfile
# itself hasn't changed), defeating the point of picking up newer base-image/package versions. ──
if $REBUILD_IMAGE; then
  echo "Rebuilding $GEN_IMAGE image (--rebuild-image, ignoring Docker layer cache)..."
  docker build --no-cache -f "$DOCKERFILE" -t "$GEN_IMAGE" "$ROOT" >/dev/null
elif ! docker image inspect "$GEN_IMAGE" >/dev/null 2>&1; then
  echo "Building $GEN_IMAGE image..."
  docker build -f "$DOCKERFILE" -t "$GEN_IMAGE" "$ROOT" >/dev/null
else
  IMAGE_CREATED_EPOCH=$(date -d "$(docker image inspect -f '{{.Created}}' "$GEN_IMAGE")" +%s)
  DOCKERFILE_EPOCH=$(date -r "$DOCKERFILE" +%s)
  if [ "$DOCKERFILE_EPOCH" -gt "$IMAGE_CREATED_EPOCH" ]; then
    echo "Dockerfile changed since last build -- rebuilding $GEN_IMAGE image..."
    docker build -f "$DOCKERFILE" -t "$GEN_IMAGE" "$ROOT" >/dev/null
  fi
fi

GEN_CMD="bash docs/architecture/scripts/generate-architecture-model.sh"
if [ "$NO_CHECK" = false ]; then
  GEN_CMD="$GEN_CMD && bash docs/architecture/scripts/check-architecture-model-freshness.sh"
fi

# ── Kill any leftover container from a prior run, start fresh, work, close ──────────────────────
docker rm -f "$GEN_CONTAINER" >/dev/null 2>&1 || true
docker run -d --name "$GEN_CONTAINER" "$GEN_IMAGE" sleep infinity >/dev/null
docker exec "$GEN_CONTAINER" mkdir -p /repo

set +e
tar -czf - --exclude='*/target' --exclude='.git' \
  --exclude='marketplace-app/src/main/frontend/generated' \
  -C "$ROOT" . \
  | docker exec -i "$GEN_CONTAINER" bash -c "cd /repo && tar -xzf -"
docker exec "$GEN_CONTAINER" bash -c "cd /repo && $GEN_CMD"
GEN_EXIT=$?
set -e

mkdir -p "$ROOT/docs/architecture/data"
docker cp "$GEN_CONTAINER":/repo/docs/architecture/data/. "$ROOT/docs/architecture/data/" 2>/dev/null || true
docker cp "$GEN_CONTAINER":/repo/docs/architecture/architecture-map.html "$ROOT/docs/architecture/architecture-map.html" 2>/dev/null || true
for m in "${POINTER_MODULES[@]}"; do
  docker cp "$GEN_CONTAINER":/repo/"$m"/DECISIONS.md "$ROOT/$m/DECISIONS.md" 2>/dev/null || true
done

docker rm -f "$GEN_CONTAINER" >/dev/null 2>&1 || true

[ "$GEN_EXIT" -eq 0 ] || exit "$GEN_EXIT"

if [ "$NO_SCREENSHOT" = false ]; then
  bash "$DIR/scripts/screenshot-architecture-map.sh"
fi
