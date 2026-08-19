# ── Header ──────────────────────────────────────────────────────────────────
# Description: Build environment (JDK 25) shared by every Maven-touching caller that needs Java
#   without relying on the host having one -- Windows/WSL cannot use a host-installed Java through
#   Maven cleanly (path translation issues), so callers run their builds inside this container
#   instead.
# Usage: docker build -f scripts/build-and-test/Dockerfile -t advertisement-build-env .
# Uses: eclipse-temurin:25-jdk base image.
# Env: None (build-time only, no ARG/ENV declared).
# Input: none (self-contained).
# Outputs: advertisement-build-env image (JDK 25).
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk

WORKDIR /app
