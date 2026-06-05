# Build environment for local dev deploy (deploy-dev.bat / deploy-dev.sh).
#
# WHY THIS EXISTS:
#   deploy-dev.sh builds the JAR with Maven and hot-swaps it into the running
#   marketplace-app container via `docker cp` + `docker restart`.
#   This requires Java (to run mvnw) and the Docker CLI (to run docker cp/restart).
#
#   On Windows, Java lives on the host but is not available inside WSL in a way
#   that Maven can use (path translation issues). Rather than installing Java in WSL,
#   we run the entire build inside this container, which already has both tools.
#
# CONTENTS:
#   - eclipse-temurin:25-jdk  — JDK 25 (same version as the application)
#   - docker.io               — Docker CLI to talk to the host Docker daemon via socket

FROM eclipse-temurin:25-jdk

RUN apt-get update \
    && apt-get install -y --no-install-recommends docker.io \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
