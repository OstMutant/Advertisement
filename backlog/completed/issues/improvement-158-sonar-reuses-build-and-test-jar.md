# improvement-158: `sonar.sh` reuses `build-and-test.sh`'s shared `maven-cache` volume instead of a local `mvnw compile`

**Type:** improvement — implemented, verified.
**Module:** `scripts/build-and-test/build.sh`, `scripts/sonar/run.sh`.
**Priority:** Top — picked up immediately after `improvement-154` (deploy-and-run reuse) as the same
pattern applied to a second consumer.
**When:** done, same session as `improvement-154`.

## Problem

`scripts/sonar/run.sh` runs `"$ROOT/mvnw" -f "$ROOT/pom.xml" compile -q -DskipTests` directly on
the host, requiring a local JDK/Maven — unlike `build-and-test.sh`/`deploy-and-run.sh`, whose whole
point is working without one. It then `docker cp`s each module's `src/main/java` and `target/classes`
(host-compiled) into the sonar-scanner container.

## Design (agreed in chat)

- `sonar.java.binaries` does not accept jar files directly (confirmed via SonarQube docs) — only a
  directory of `.class` files. `sonar.java.libraries` is for third-party jar dependencies, not this.
- `scripts/build-and-test/build.sh` (already running inside a container with `maven-cache` mounted
  at `/root/.m2`) gains one more step: copy each module's own `target/classes` into that same
  volume (e.g. `/root/.m2/target-classes/<module>/`).
- `scripts/sonar/run.sh` drops its own `mvnw compile` call, instead runs `bash
  scripts/build-and-test.sh [flags]` first (same reuse pattern `deploy-and-run.sh` already uses).
- The sonar-scanner container itself mounts `maven-cache` directly (`-v maven-cache:/root/.m2`) at
  its own `docker run` — no bridge/helper container needed for this step, unlike
  `deploy-and-run.sh`'s jar extraction: that one needs a helper container because `docker build`'s
  `COPY` instruction requires files already on the host disk (a Dockerfile/build-context cannot
  reference a named volume directly) — the scanner container is started via plain `docker run`,
  which *can* mount a volume directly.
- `src/main/java` sources are unaffected — still `docker cp`'d from the host repo checkout directly
  (compilation moving into a container doesn't change where the source files themselves live).

## Implementation (as actually built, differs slightly from the design sketch above)

`sonar-project.properties`'s `sonar.java.binaries` paths were **not** rewritten to point directly
at `/root/.m2/target-classes/<module>` — that would have required touching the Python module-list
validator that auto-rewrites this file. Instead, since the mounted volume and the expected
`/tmp/sonar-src/<module>/target/classes` destination are both inside the *same* scanner container,
`run.sh` does an internal `docker exec ... cp -r /root/.m2/target-classes/$module/. /tmp/sonar-src/$module/target/classes/`
— a container-internal copy, no host round-trip, no `sonar-project.properties` changes needed.

## Real bug found and fixed during testing (unrelated to the reuse change itself)

First test run: sonar analysis itself succeeded (203 files analyzed, uploaded), but the HTML-report
generation step failed — `docker cp` couldn't find `/tmp/sonar-report.html` in the scanner
container. Root cause, confirmed by reading the actual code: the analysis was run with `--no-gate`,
which previously cleared `GATE_FLAG` (`-Dsonar.qualitygate.wait=true`) entirely — but that flag is
what makes the scanner **wait** for SonarQube to finish server-side processing the just-uploaded
report, not just a gate-blocking toggle. Without it, the scanner returned immediately after upload;
the HTML-report Python script then queried the issues API before the server had indexed anything,
hit a 10s timeout, and its own `except: print(warning); exit(0)` silently skipped writing the report
file at all — so the later `docker cp` had nothing to copy.

**Fix:** `GATE_FLAG` now always stays `-Dsonar.qualitygate.wait=true`, regardless of `--no-gate` —
the scanner always waits for processing. `--no-gate`'s own documented contract (always exit 0, gate
result informational only) is preserved separately, via an explicit `[ -n "$NO_GATE" ] && exit 0`
at the very end, after the `EXIT_CODE`-based warning message.

## Verified end to end

`bash scripts/sonar.sh --no-gate` — real run: build-and-test.sh built all modules (no local Java
invoked), scanner container mounted `maven-cache` and read `target/classes` from
`/root/.m2/target-classes/<module>`, analysis uploaded, scanner waited ("Waiting for the analysis
report to be processed (max 300s)"), quality gate genuinely evaluated (found 3 real issues, status
FAILED), HTML report generated successfully ("3 issues shown, 3 total",
`scripts/sonar/report/report.html` — 2810 bytes, real file). Script's own exit code: 0 (confirmed
via a second run piped to a log file and `echo $?`), matching `--no-gate`'s contract despite the
gate itself failing.

## Status

Done.
