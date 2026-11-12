# syntax=docker/dockerfile:1
# ── Header ──────────────────────────────────────────────────────────────────
# Description: ci-runner image -- one persistent container the user interacts with (scripts/ci.sh
#   -> scripts/ci/run.sh), running Dagu, orchestrating this project's CI stages
#   (scripts/ci/dagu/ci.yaml). Docker-outside-of-Docker:
#   the host's docker.sock is mounted into it at `docker run` time so it can create/tear down its
#   own isolated ci-* sibling containers (Postgres, SeaweedFS, the built app, Playwright) without ever
#   touching the persistent dev stack. Full design rationale: see DECISIONS.md.
# Usage: docker build -f scripts/ci/Dockerfile -t ci-runner . (always via scripts/ci/run.sh, never
#   invoked directly).
# Uses: eclipse-temurin:25-jdk base image; apt-get installs docker.io (DooD docker CLI against the
#   mounted docker.sock), curl (entrypoint's tool downloads), python3 (pipeline-metrics.py), nodejs
#   (Vaadin frontend build), shellcheck (the shellcheck DAG step), ca-certificates.
# Env: ARG DAGU_VERSION (default 2.26.0, also re-exposed as ENV for the entrypoint script to read);
#   ENV PATH (prepends /root/.ci-tools/bin), DAGU_HOME, DAGU_PORT, DAGU_DAGS_DIR, DAGU_AUTH_MODE
#   (all fixed, not caller-overridable at build time).
# Input: repo source (mvnw, pom.xml, the full tree via COPY . .).
# Outputs: ci-runner image -- see scripts/ci/docker-entrypoint.sh for the in-container
#   orchestration logic this image's ENTRYPOINT hands off to.
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
      docker.io curl ca-certificates python3 nodejs shellcheck \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# buildx/compose plugins and Dagu are downloaded at first start into the ci-tools-cache volume by docker-entrypoint.sh, so image rebuilds do not re-fetch them.
ARG DAGU_VERSION=2.26.0
ENV DAGU_VERSION=$DAGU_VERSION
ENV PATH="/root/.ci-tools/bin:${PATH}"

# Full repo copy; Maven dependencies are cached by the ci-m2-cache volume at run time, not by an image layer.
COPY . .
RUN chmod +x mvnw && sed -i 's/\r//' mvnw && chmod +x scripts/ci/docker-entrypoint.sh

# Dagu run history/state lives here, persisted by a named volume mounted at run time.
ENV DAGU_HOME=/root/.dagu
ENV DAGU_PORT=18080
# The DAG directory Dagu watches; outside /app because the per-run working-tree sync wipes /app.
ENV DAGU_DAGS_DIR=/dagu-dags

# Auth is off because the UI is reachable only via the localhost-bound proxy sidecar (scripts/ci/run.sh).
ENV DAGU_AUTH_MODE=none

ENTRYPOINT ["scripts/ci/docker-entrypoint.sh"]
