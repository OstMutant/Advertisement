# syntax=docker/dockerfile:1
# CI-runner image (improvement-059) -- one container the user interacts with
# (scripts/ci.sh -> scripts/ci/run.sh), Docker-outside-of-Docker: the host's docker.sock is
# mounted into it at `docker run` time so it can create/tear down its own isolated ci-* sibling
# containers (Postgres, MinIO, the built app, Playwright) without ever touching the persistent dev
# stack. See scripts/ci/entrypoint.sh for the in-container orchestration logic and
# scripts/ci/DECISIONS.md ADR-001 for the full design rationale (why DooD, not DinD).
FROM eclipse-temurin:25-jdk

RUN apt-get update && apt-get install -y --no-install-recommends \
      docker.io curl ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install the buildx/compose CLI plugins into this image's own ~/.docker/cli-plugins (own layer,
# only reinstalled if this one script changes -- not on every source edit). deploy.sh needs buildx
# for the app image build it runs later, at container-run time, against the host's docker daemon
# (via the mounted socket) -- see scripts/CLAUDE.md "Streaming output requirement".
COPY scripts/ensure-docker-plugins.sh scripts/ensure-docker-plugins.sh
RUN bash scripts/ensure-docker-plugins.sh

# Full repo. Unlike the app's own Dockerfile, this image runs mvn/docker commands live inside
# scripts/ci/entrypoint.sh at container *run* time, not at image *build* time -- there is no
# dependency-download layer to cache here. Maven dependency caching instead comes from the
# ci-m2-cache named volume scripts/ci/run.sh mounts at /root/.m2 on every run.
COPY . .
RUN chmod +x mvnw && sed -i 's/\r//' mvnw && chmod +x scripts/ci/entrypoint.sh

ENTRYPOINT ["bash", "scripts/ci/entrypoint.sh"]
