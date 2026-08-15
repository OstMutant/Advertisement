# scripts/build-and-test

A Docker container (JDK 25) that gives every Maven-touching script a working Java
environment regardless of whether the host machine has one — this project's WSL/Windows users hit
a real, confirmed gap where WSL cannot use a host-installed Java through Maven cleanly (path
translation issues between Linux-style and Windows-style paths). Everything Maven-related routes
through this one container instead of assuming Java is available wherever the caller happens to
run.

## Flow

Two entry points, same underlying flow — an OS-specific pair converging into the same shared
logic:

```bash
bash scripts/build-and-test.sh          # Linux/WSL
scripts\build-and-test.bat              # Windows
```

```mermaid
flowchart TD
    A1[build-and-test.sh] --> R[run.sh]
    A2[build-and-test.bat] --> R
    R --> B{"reset-cache?"}
    B -->|yes| B1["rm maven-cache<br/>volume"] --> C
    B -->|no| C{"image missing or<br/>--rebuild-image?"}
    C -->|yes| C1[docker build] --> E
    C -->|no| D{"Dockerfile newer<br/>than image?"}
    D -->|yes| C1
    D -->|no| E[tar-pipe source in]
    E --> F["build.sh<br/>(inside container)"]
    F --> G["mvn install<br/>whole reactor (via flock)"]
    G --> H["refresh marketplace-app.jar<br/>in shared volume"]
    H --> I{"RUN_UNIT?"}
    I -->|yes| I1["mvn test<br/>unit (via flock)"] --> J
    I -->|no| J{"RUN_INTEGRATION?"}
    J -->|yes| J1["mvn test<br/>integration (via flock)"] --> K
    J -->|no| K["docker image<br/>prune -f"]
    K --> Z[done]
```

`RUN_UNIT`/`RUN_INTEGRATION` are both **on** by default (`build-and-test.properties`); `run.sh`
sets them from flags/`.properties` defaults before invoking the container. The build step always
builds the whole reactor and always refreshes `marketplace-app.jar` in the shared volume
regardless — Maven's own incremental compilation makes a no-op rebuild cheap, so there's no
separate "build-only" vs "full" mode to keep in sync.

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

`run.sh` runs under a fixed container name so a run in progress can be attached to
directly, without first looking it up: `advertisement-build-only` (`docker exec -it
advertisement-build-only bash`).
