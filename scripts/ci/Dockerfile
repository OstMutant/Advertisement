# syntax=docker/dockerfile:1
# ── Header ──────────────────────────────────────────────────────────────────
# Description: ci-runner image -- one persistent container the user interacts with (scripts/ci.sh
#   -> scripts/ci/run.sh), running Dagu, orchestrating this project's CI stages
#   (scripts/ci/dagu/ci.yaml). Docker-outside-of-Docker:
#   the host's docker.sock is mounted into it at `docker run` time so it can create/tear down its
#   own isolated ci-* sibling containers (Postgres, MinIO, the built app, Playwright) without ever
#   touching the persistent dev stack. Full design rationale: see DECISIONS.md.
# Usage: docker build -f scripts/ci/Dockerfile -t ci-runner . (always via scripts/ci/run.sh, never
#   invoked directly).
# Uses: eclipse-temurin:25-jdk base image; apt-get installs docker.io (DooD docker CLI against the
#   mounted docker.sock), curl (entrypoint's tool downloads), python3 (pipeline-metrics.py), nodejs
#   (Vaadin frontend build), ca-certificates.
# Env: ARG DAGU_VERSION (default 2.15.3, also re-exposed as ENV for the entrypoint script to read);
#   ENV PATH (prepends /root/.ci-tools/bin), DAGU_HOME, DAGU_PORT, DAGU_AUTH_MODE (all fixed, not
#   caller-overridable at build time).
# Input: repo source (mvnw, pom.xml, the full tree via COPY . .).
# Outputs: ci-runner image -- see scripts/ci/docker-entrypoint.sh for the in-container
#   orchestration logic this image's ENTRYPOINT hands off to.
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
      docker.io curl ca-certificates python3 nodejs \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# buildx/compose CLI plugins and the Dagu binary are NOT installed at build time -- they're
# downloaded once into the ci-tools-cache named volume by scripts/ci/docker-entrypoint.sh, the
# first time the container actually starts, and reused from there on every later start regardless
# of how often this image gets rebuilt (a build-time RUN step here would re-download all ~190MB
# every time any earlier layer's cache is invalidated, e.g. by any source-file change reaching the
# COPY below). See docker-entrypoint.sh for the download-if-missing logic.
ARG DAGU_VERSION=2.15.3
ENV DAGU_VERSION=$DAGU_VERSION
ENV PATH="/root/.ci-tools/bin:${PATH}"

# Full repo. Unlike the app's own Dockerfile, this image runs mvn/docker commands live via Dagu's
# DAG steps (scripts/ci/dagu/ci.yaml) at container *run* time, not at image *build* time -- there is
# no dependency-download layer to cache here. Maven dependency caching instead comes from the
# ci-m2-cache named volume mounted at /root/.m2 when the container is run.
COPY . .
RUN chmod +x mvnw && sed -i 's/\r//' mvnw && chmod +x scripts/ci/docker-entrypoint.sh

# Dagu's own run history/state lives under this path -- a named volume mounted here at
# container-run time (not baked into the image) persists it across container recreations, the same
# pattern already used for the ci-m2-cache volume.
ENV DAGU_HOME=/root/.dagu
ENV DAGU_PORT=18080

# This UI is reachable only via the localhost-bound proxy sidecar (scripts/ci/run.sh) -- no login
# wizard/account needed for a single-user, local-only dev tool.
ENV DAGU_AUTH_MODE=none

ENTRYPOINT ["scripts/ci/docker-entrypoint.sh"]
