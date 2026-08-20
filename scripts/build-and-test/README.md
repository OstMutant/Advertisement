# scripts/build-and-test

A Docker container (JDK 25) that gives every Maven-touching script a working Java
environment regardless of whether the host machine has one — this project's WSL/Windows users hit
a real, confirmed gap where WSL cannot use a host-installed Java through Maven cleanly (path
translation issues between Linux-style and Windows-style paths). Everything Maven-related routes
through this one container instead of assuming Java is available wherever the caller happens to
run.

## Flow

Entry point: `run.sh`.

```mermaid
flowchart TD
    R[run.sh]
    R --> B{"reset-cache?"}
    B -->|yes| B1["rm maven-cache<br/>volume"] --> C
    B -->|no| C{"image missing or<br/>--rebuild-image or<br/>Dockerfile newer?"}
    C -->|yes| C1[docker build] --> E
    C -->|no| E[tar-pipe source in]
    E --> F["build.sh<br/>(inside container)"]
    F --> G["mvn install<br/>whole reactor (via flock)"]
    G --> H["refresh marketplace-app.jar<br/>in shared volume"]
    H --> I{"RUN_UNIT?"}
    H --> J{"RUN_INTEGRATION?"}
    I -->|yes| I1["mvn test unit"] --> I2{"ARCHUNIT_METRICS?"}
    I -->|no| I2
    I2 -->|yes| I3["mvn test<br/>ArchitectureMetricsExport"] --> M
    I2 -->|no| M
    J -->|yes| J1["mvn test integration<br/>(background)"] --> M
    J -->|no| M
    M["wait for both,<br/>docker cp reports out,<br/>docker rm"] --> K["docker image<br/>prune -f"]
    K --> Z[done]
```

`run.sh` reads `build-and-test.properties`' own values as the baseline for whether unit/integration
tests run, then a CLI flag overrides that baseline for one invocation only — see `run.sh`'s and
`build-and-test.properties`' own headers for the actual defaults and flag names. The build step
always builds the whole reactor and always refreshes `marketplace-app.jar` in the shared volume
regardless of which tests run — Maven's own incremental compilation makes a no-op rebuild cheap, so
there's no separate "build-only" vs "full" mode to keep in sync.

## Environment notes

The shared `.m2` lives entirely inside a Docker named volume (`maven-cache`), never bind-mounted
to the host's real `~/.m2` — a container build can never corrupt a developer's own local
repository, and a developer's own local Maven runs are never affected by what happens inside this
container. Concurrent `mvn` invocations across separate container instances running at the same
time are serialized via `flock` against a lock file living inside that same shared volume, so the
lock is visible to every container instance that mounts it, not just one. `marketplace-app.jar`
itself lives at a fixed path inside the same volume (`/root/.m2/artifacts/marketplace-app.jar`),
outside Maven's own `.m2/repository` tree — the volume doubles as the shared artifact store, no
second volume needed.
