#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: ci-runner's real ENTRYPOINT. Ensures buildx/compose/dagu are present in the
#   ci-tools-cache named volume (downloading only whatever's missing, so a rebuilt image never
#   re-downloads these ~190MB of binaries -- unlike a Dockerfile RUN step, this runs every
#   container start against volume-backed storage that survives image rebuilds) before handing off
#   to Dagu's own server (dagu start-all). buildx/compose are cached in the ci-tools-cache volume;
#   dagu is installed into a folder named after DAGU_VERSION, so a version change downloads the new
#   binary at the next container start.
# Usage: None -- set as the image's ENTRYPOINT (scripts/ci/Dockerfile), never invoked directly.
# Uses: bash, curl, dagu, scripts/utils/ensure-docker-plugins.sh (source'd for ensure_buildx/
#   ensure_docker_compose).
# Env:
#   Set by the Dockerfile's own ENV instructions, not overridable at container-run time:
#     DAGU_VERSION, DAGU_PORT, DAGU_DAGS_DIR.
# Input: the ci-tools-cache named volume (mounted at /root/.ci-tools), the Dagu binary's GitHub
#   release archive, scripts/ci/dagu/ci.yaml.
# Outputs: buildx/docker-compose CLI plugins symlinked into ~/.docker/cli-plugins; the dagu binary
#   installed under $CI_TOOLS/dagu/$DAGU_VERSION and linked into $CI_TOOLS/bin; ci.yaml copied into
#   $DAGU_DAGS_DIR (outside /app); Dagu's own web server started on 0.0.0.0:$DAGU_PORT, watching
#   that directory.
# Returns: never returns under normal operation (exec'd into dagu start-all, which runs until the
#   container is stopped); non-zero if a tool download fails (set -e).
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail

CI_TOOLS=/root/.ci-tools
mkdir -p "$CI_TOOLS/bin" "$CI_TOOLS/cli-plugins" ~/.docker

ln -sfn "$CI_TOOLS/cli-plugins" ~/.docker/cli-plugins

source /app/scripts/utils/ensure-docker-plugins.sh
ensure_buildx
ensure_docker_compose

DAGU_DIR="$CI_TOOLS/dagu/$DAGU_VERSION"
if [ ! -x "$DAGU_DIR/dagu" ]; then
  echo "dagu $DAGU_VERSION not cached — downloading..."
  mkdir -p "$DAGU_DIR"
  curl -Lo /tmp/dagu.tar.gz \
    "https://github.com/dagucloud/dagu/releases/download/v${DAGU_VERSION}/dagu_${DAGU_VERSION}_linux_amd64.tar.gz"
  tar -xzf /tmp/dagu.tar.gz -C /tmp
  install /tmp/dagu "$DAGU_DIR/dagu"
  rm -rf /tmp/dagu.tar.gz /tmp/dagu
  echo "dagu installed."
fi
ln -sfn "$DAGU_DIR/dagu" "$CI_TOOLS/bin/dagu"
find "$CI_TOOLS/dagu" -mindepth 1 -maxdepth 1 ! -name "$DAGU_VERSION" -exec rm -rf {} +

# The DAG directory lives outside /app: the working-tree sync wipes /app and Dagu's scheduler stops when a watched directory disappears.
mkdir -p "$DAGU_DAGS_DIR"
cp scripts/ci/dagu/ci.yaml "$DAGU_DAGS_DIR/ci.yaml"

exec dagu start-all --host 0.0.0.0 --port "$DAGU_PORT" --dags "$DAGU_DAGS_DIR"
