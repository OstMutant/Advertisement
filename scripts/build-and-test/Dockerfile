# ── Header ──────────────────────────────────────────────────────────────────
# Description: Build environment (JDK 25) shared by every Maven-touching caller that needs Java
#   without relying on the host having one.
# Usage: docker build -f scripts/build-and-test/Dockerfile -t advertisement-build-env .
# Uses: eclipse-temurin:25-jdk base image.
# Env: None (build-time only, no ARG/ENV declared).
# Input: none (self-contained).
# Outputs: advertisement-build-env image (JDK 25).
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk

WORKDIR /app
