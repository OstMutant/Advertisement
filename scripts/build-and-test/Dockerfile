# ── Header ──────────────────────────────────────────────────────────────────
# Description: Build environment (JDK 25) shared by every Maven-touching caller that needs Java
#   without relying on the host having one.
# Usage: docker build -f scripts/build-and-test/Dockerfile -t advertisement-build-env .
# Uses: eclipse-temurin:25-jdk base image; apt-get installs docker.io (docker CLI against the
#   host's docker.sock, mounted in at `docker run` time -- needed by build.sh's own leaked
#   Testcontainers-container cleanup step before running integration tests).
# Env: None (build-time only, no ARG/ENV declared).
# Input: none (self-contained).
# Outputs: advertisement-build-env image (JDK 25 + docker CLI).
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
      docker.io \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
