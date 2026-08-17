# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin runtime-only image for the marketplace-app -- no Maven build happens here, it
#   just packages a jar scripts/deploy-and-run/run.sh already extracted from the shared
#   maven-cache Docker volume (built by scripts/build-and-test.sh). See DECISIONS.md for the full
#   reuse-jar rationale; the isolated, from-scratch multi-stage build still exists at the repo-root
#   Dockerfile, used via scripts/deploy-and-run.sh --from-scratch.
# Usage: docker build -f scripts/deploy-and-run/Dockerfile -t marketplace-app scripts/deploy-and-run
# Uses: eclipse-temurin:25-jre base image.
# Env: None (build-time only, no ARG/ENV declared).
# Input: scripts/deploy-and-run/marketplace-app.jar (gitignored -- extracted by run.sh, not
#   present in a clean checkout).
# Outputs: marketplace-app image -- exposes port 8080, entrypoint runs the jar directly.
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY marketplace-app.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
