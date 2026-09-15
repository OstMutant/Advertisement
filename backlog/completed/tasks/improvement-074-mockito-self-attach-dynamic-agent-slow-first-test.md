# improvement-074: Investigation — 40-90s delay on whichever test runs first in each Maven test JVM fork

**Type:** investigation — build/test performance, found via a direct performance investigation
requested mid-session (2026-07-16) into why `scripts/unit-tests.sh`'s "Marketplace" reactor module
was taking ~4:43 min versus ~85s combined for all 7 other modules.
**Module:** N/A — no repo-level fix identified after direct testing; see Correction below.
**Priority:** n/a — closed as investigated, not fixed. Root cause not conclusively pinned down to
anything actionable inside this repository; reopen if a new lead surfaces.
**When:** closed (2026-07-17).

## Original diagnosis (2026-07-16) — DISPROVEN, kept for record

Original hypothesis: Mockito's dynamic self-attach (falling back from a proper `-javaagent` to a
runtime `Instrumentation` self-attach on the first `@Mock` per JVM fork) was the cause, based on
this warning appearing in `run.log` the first time a mock is created in a fork:
```
Mockito is currently self-attaching to enable the inline-mock-maker...
WARNING: A Java agent has been loaded dynamically (byte-buddy-agent-1.17.8.jar)
WARNING: Dynamic loading of agents will be disallowed by default in a future release
```
Confirmed the delay is not tied to any one test class's logic — isolating `AuthServiceTest` and
`AccessEvaluatorTest` each alone showed the same ~40-90s tax landing on whichever ran first.

## Correction (2026-07-17): the self-attach diagnosis was wrong — three fixes tested, none worked

**Attempt 1 — configure Mockito as a real `-javaagent`** (the originally suggested fix: root
`pom.xml` `maven-dependency-plugin:properties` + `maven-surefire-plugin` `argLine
-javaagent:${org.mockito:mockito-core:jar}`). Result: the self-attach warning disappeared from
`run.log` entirely, confirming the agent loaded correctly — but `AuthServiceTest`'s first test
still took 44.08s (vs. 41-59s baseline), no measurable improvement. **The warning and the delay
are not causally linked** — they simply started appearing in the same log window by coincidence.

**Root cause, found via JFR profiling** (`-XX:StartFlightRecording`, `jdk.ExecutionSample`/
`jdk.NativeMethodSample` events analyzed with `jfr print`): ~98% of native-method samples during
the delay are `java.io.File.exists()` / `java.io.FileInputStream.readBytes()` calls inside
`java.util.ServiceLoader$LazyClassPathLookupIterator`, invoked from
`org.junit.platform.launcher.core.LauncherFactory.openSession()` — **JUnit Platform's own
`ServiceLoader`-based classpath scan for TestEngines/extensions at launcher startup**, not
anything Mockito-related. This happens once per JVM fork regardless of test content, which is
exactly why it always lands on whichever test runs first.

**Attempt 2 — disable JUnit's classpath-alignment launcher interceptor**
(`-Djunit.platform.launcher.interceptors.enabled=false`, targeting
`ClasspathAlignmentCheckingLauncherInterceptor`, the top frame above the ServiceLoader scan in the
JFR stack). Result: 55.63s — no improvement (within the same noise band as baseline, if anything
slightly worse).

**Attempt 3 — relocate `~/.m2/repository` off the sandbox's 9p-mounted Windows drive.** `df -T`
confirmed `/root/.m2` and `/app` are mounted via `9p` (WSL2/Docker Desktop's cross-OS file-sharing
protocol for Windows-drive-backed paths — `C:\`/`D:\`), while `/` itself is a fast native overlay
filesystem. Copied the 322MB local repo to `/var/m2-fast/repository` (native overlay path) and
pointed Maven at it via a temporary `~/.m2/settings.xml` `<localRepository>` override. Result:
**mixed** — every *other* reactor module's build time dropped 2-4x (Platform Commons 9-11s → 5.0s,
Query Lib 8-18s → 2.5s, each starter 4-9s → 1.6-2s) — a real, measurable, and separately valuable
finding about this sandbox's `.m2` placement — but `AuthServiceTest`'s first-test delay was
**still 42.90s**, essentially unchanged. 9p latency explains general Maven dependency-resolution
overhead but not this specific forked-JVM classpath-scan cost.

**Conclusion:** three independent, plausible fixes tested and ruled out. The true root cause of
the ~40-90s JUnit-Platform-launcher-session classpath scan in this sandbox remains unidentified.
All experimental changes were reverted (`pom.xml`, `marketplace-app/pom.xml` via `git checkout`;
`~/.m2/settings.xml` override removed; `/var/m2-fast` cleanup) — nothing from this investigation
is applied to the repo or the environment. Closed without a fix; reopen if a new lead surfaces
(candidates not yet tried: isolating whether this is WSL2/Docker-Desktop CPU-virtualization
overhead specifically during JVM class-loading/JIT rather than filesystem I/O at all, or comparing
against a bare non-containerized JVM run outside this sandbox entirely to establish whether it's
sandbox-specific at all).

## Related

- The `~/.m2` 9p-mount finding (Attempt 3) is real and separately actionable — relocating the
  local Maven repository off the Windows-drive 9p mount to a native path measurably speeds up
  general Maven build/dependency-resolution time in this sandbox, independent of this issue's
  specific (unresolved) test-fork delay. Not filed as its own backlog issue since it's a sandbox
  environment configuration, not a repository fix — worth a manual one-time `~/.m2` relocation
  outside version control if someone wants the general build speedup, not tracked here.
- Not related to Vaadin's `prepare-frontend` Maven goal (~27.6s, runs on every `mvn test`
  regardless of frontend changes) — a separate, smaller, and largely unavoidable contributor to
  the same module's total time.
- Not related to `ArchitectureRulesTest` (ArchUnit, ~82-93s) — inherent to scanning ~16,500
  classes across every `org.ost` module for the architecture rules, unrelated to this issue.
