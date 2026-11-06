# syntax=docker/dockerfile:1
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Production build for marketplace-app -- multi-stage build (Maven reactor build,
#   then a slim JRE runtime image); image name/tag is assigned by whichever command invokes the
#   build (see scripts/deploy-and-run.sh).
# Usage: docker build -f Dockerfile -t <tag> .
# Uses: eclipse-temurin:25-jdk (builder stage), eclipse-temurin:25-jre (runtime stage), Maven (via
#   mvnw), Vaadin production bundle build (via vaadin-maven-plugin during `mvnw package`).
# Env: None -- build-time only; runtime env vars are supplied by whatever starts the resulting
#   image (see scripts/deploy-and-run/docker-compose.app.yml).
# Input: the whole repo source minus what .dockerignore lists.
# Outputs: a runnable marketplace-app image -- app.jar, exposes port 8080, entrypoint `java -jar app.jar`.
# ────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy every module (.dockerignore keeps build output and reports out of the context) -- the
# module list lives only in the root pom.xml, `-am` below builds whichever modules the app needs
COPY . .
RUN chmod +x mvnw && sed -i 's/\r//' mvnw

# Build the application (Vaadin production bundle is built automatically by
# vaadin-maven-plugin on `package`; SPRING_PROFILES_ACTIVE=prod at runtime sets
# vaadin.productionMode=true, see application-prod.yml). Cache the Node.js
# runtime download (/root/.vaadin) only — a node_modules cache mount was tried
# and reverted: vaadin-maven-plugin rm-rf's and recreates that directory on
# every build, which a cache mount's fixed mountpoint can't survive, so it
# silently reinstalled from scratch anyway while logging a spurious error.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.vaadin \
    ./mvnw package -DskipTests -pl marketplace-app -am -q

FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the generated executable JAR from the application module
COPY --from=builder /app/marketplace-app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
