#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: ci-runner's real ENTRYPOINT. Ensures buildx/compose/dagu are present in the
#   ci-tools-cache named volume (downloading only whatever's missing, so a rebuilt image never
#   re-downloads these ~190MB of binaries -- unlike a Dockerfile RUN step, this runs every
#   container start against volume-backed storage that survives image rebuilds) before handing off
#   to Dagu's own server (dagu start-all).
# Usage: None -- set as the image's ENTRYPOINT (scripts/ci/Dockerfile), never invoked directly.
# Uses: bash, curl, dagu, scripts/ensure-docker-plugins.sh (source'd for ensure_buildx/
#   ensure_docker_compose).
# Env:
#   Set automatically by scripts/ci/run.sh (via `docker run -e`), never exported directly by a user:
#     FORCE_TOOLS_REFRESH (true/false) -- re-downloads all three tools even if already cached.
#   Set by the Dockerfile's own ENV instructions, not overridable at container-run time:
#     DAGU_VERSION, DAGU_PORT.
# Input: the ci-tools-cache named volume (mounted at /root/.ci-tools), the Dagu binary's GitHub
#   release archive.
# Outputs: buildx/docker-compose CLI plugins symlinked into ~/.docker/cli-plugins; the dagu binary
#   installed into ci-tools-cache; Dagu's own web server started on 0.0.0.0:$DAGU_PORT.
# Returns: never returns under normal operation (exec'd into dagu start-all, which runs until the
#   container is stopped); non-zero if a tool download fails (set -e).
# ────────────────────────────────────────────────────────────────────────────
set -e

CI_TOOLS=/root/.ci-tools
mkdir -p "$CI_TOOLS/bin" "$CI_TOOLS/cli-plugins" ~/.docker

# FORCE_TOOLS_REFRESH=true (run.sh --refresh-tools) re-downloads all three even if already
# cached -- e.g. after bumping DAGU_VERSION or wanting the latest buildx/compose release.
if [ "$FORCE_TOOLS_REFRESH" = "true" ]; then
  echo "FORCE_TOOLS_REFRESH set -- clearing cached tools before checking."
  rm -f "$CI_TOOLS/bin/dagu" "$CI_TOOLS/cli-plugins/docker-buildx" "$CI_TOOLS/cli-plugins/docker-compose"
fi

ln -sfn "$CI_TOOLS/cli-plugins" ~/.docker/cli-plugins

source /app/scripts/ensure-docker-plugins.sh
ensure_buildx
ensure_docker_compose

if [ ! -x "$CI_TOOLS/bin/dagu" ]; then
  echo "dagu binary not cached — downloading..."
  curl -Lo /tmp/dagu.tar.gz \
    "https://github.com/dagucloud/dagu/releases/download/v${DAGU_VERSION}/dagu_${DAGU_VERSION}_linux_amd64.tar.gz"
  tar -xzf /tmp/dagu.tar.gz -C /tmp
  install /tmp/dagu "$CI_TOOLS/bin/dagu"
  rm -rf /tmp/dagu.tar.gz /tmp/dagu
  echo "dagu installed."
fi

exec dagu start-all --host 0.0.0.0 --port "$DAGU_PORT" --dags scripts/ci/dagu
