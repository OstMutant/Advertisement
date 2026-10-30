# improvement-231: The local CI takes about 66 minutes in a full run -- remove duplicated work and contention

**Type:** improvement (developer tooling speed and reliability)
**Module:** `scripts/ci` (`dagu/ci.yaml`, `run.sh`), `scripts/sonar` (`run.sh`), `scripts/build-and-test` (`run.sh`), `scripts/deploy-and-run.sh`, `docs/architecture/scripts/generate-architecture-model.sh`, `playwright/`
**Priority:** high (user-requested top-of-backlog placement, 2026-10-29)
**When:** independent. Research is the first step (a `deep-research` topic folder), the changes follow from it.

## Current state

Measured on 2026-10-29 on the development machine (verified, not estimated):

- **Machine:** 4 CPUs, 9.7 GB of memory, about 2 GB available during a run. About 5 GB is held by containers that are not part of the CI run: `sonarqube` 1.8 GB, the dev `marketplace-app` 1.0 GB, `ci-marketplace-app` 1.1 GB, dev SeaweedFS and others.
- **A full parallel run (`bash scripts/ci.sh --foreground`, all stages at once) took about 66 minutes** and had integration and Sonar failing. Per stage against the same stage run alone:

| Stage | Alone | In the full run |
|---|---|---|
| Integration tests | 10 min 53 s | 15 min 35 s (+43%), failed |
| ArchUnit metrics | 3 min 22 s | 7 min 17 s (+116%) |
| Playwright (65 tests) | about 13 min (local, dev stack) | 20.2 min (+55%) |
| Docs freshness + regeneration | 1 min 43 s | 22 min 37 s |
| E2E stage as a whole | -- | 39 min 26 s, of which Playwright started only after about 19.5 min |

- **Sonar runs the unit and integration tests a second time.** `scripts/sonar/run.sh` (line 170) calls `build-and-test.sh --unit --integration` to get JaCoCo coverage, while the CI has its own `unit` and `integration` stages. That is about 19 minutes of CPU work (8 + 11 minutes alone) done twice on 4 cores, in parallel with the stages it copies.
- **Integration and Sonar share one fixed Postgres port.** `build-and-test.sh --sandbox` sets port 25432; both stages ran the integration suite on it at once, and each passes alone. This is entry 33 of the deferred-findings task (`improvement-133`), with the options listed there. As of 2026-10-29 the `integration` stage has its own port 25433 and Sonar keeps 25432 (change made in `improvement-186`, step 15 area); the full run that proves it was still running when this task was filed. The same entry names memory as another possible cause of the earlier Postgres exit (9.9 GB shared by five heavy stages), so the port change may not be enough.
- **What fills the 19.5 minutes before Playwright starts is not broken down yet.** The `e2e` step calls `deploy-and-run.sh --dev-tools`, which runs its own Maven build and the Vaadin production bundle, although the `build` stage has compiled the reactor already.
- **The `docs` stage took 22 minutes in the full run** against 1 min 43 s alone; the generator has no wait loop (its `curl` calls have timeouts of 3 to 15 seconds), so the cause is not established.
- **Playwright runs with 2 workers** (`PW_WORKERS` in `.env`); the Playwright rules record that more than 2 did not shorten a run on this 4-core machine.
- **Measured breakdown (read from the Dagu step logs of run `ci-20261029T093116Z`, 2026-10-29; all values as printed):**
  - The stages after `build` each ran a full reactor install and finished one after another, in this order: `build` 02:30 min (finished 09:33:54), `unit` 01:26 (09:35:40), `archunit_metrics` 02:12 (09:37:57), `integration` 03:50 (09:41:56), `sonar` 04:02 (09:46:09), and the `e2e` stage's own install 05:25 (09:51:44). The five installs after `build` add up to 17 min 55 s.
  - The `e2e` stage: `infra` 26 s, `reset` 2 s, `build-and-test` 1044 s (of which its own install 05:25 min, so about 12 minutes waiting for the lock), `start-container` 1076 s, `start-application` 1136 s, `playwright-run` 1230 s (cumulative stage seconds as printed). Starting the database took 26 s and the application about a minute.
  - The `docs` stage: the log line `SonarQube data stale (.../contact/entity/ContactInfo.java changed since last scan) -- rescanning via bash scripts/sonar.sh --no-gate...`, then `sonar-analysis` `durationSeconds` 1199 and an inner `build-and-test` of 992 s -- a third full test run, started because the failed Sonar stage left the stored analysis older than the code.
- **A settled decision applies:** the `scripts/ci` decision on default stage selection (find it in `.claude/nav/adr-index.md`) says the default run is the most extensive one, not the narrowest. Any speed-up keeps every stage in the default run.

## Why change

A local CI that takes over an hour and fails in the middle for reasons that are not about the code is rarely run; its result is then not trusted. Most of the time is not useful work: tests are run twice, stages fight for 4 cores and about 4 GB of free memory, and one stage waits about 20 minutes before its real work starts.

## Expected benefit

- The default full run finishes in a time set from the research (the target is written into this task when the research is in), with every stage still in it.
- Integration and Sonar both pass in the parallel run, with no two stages sharing a database.
- Fewer false failures from contention (timeouts, a Postgres container that exits).

## Approach

**Superseded by the approved plan below (research of 2026-10-29); the list is kept as the starting hypotheses.** Candidates, ordered by expected saving; each is to be measured before and after, and the research (see Related) confirms or replaces them:

1. **Sonar reads the JaCoCo reports of the `unit` and `integration` stages instead of running the tests again.** The scanner imports XML from the paths in `sonar.coverage.jacoco.xmlReportPaths` (a comma-separated list is accepted): [SonarSource, importing JaCoCo XML](https://community.sonarsource.com/t/coverage-test-data-importing-jacoco-coverage-report-in-xml-format/12151/). Sonar then runs after those two stages, which does not lengthen the critical path as long as `e2e` is longer. Known pitfall in a multi-module build: per-module paths may miss coverage from tests in other modules ([SonarSource, multi-module Maven](https://community.sonarsource.com/t/issue-while-generating-sonarqube-8-9-report-in-multi-maven-project/45577)); check the coverage numbers against today's.
2. **Break down and cut the 19.5 minutes before Playwright.** Time the steps of `deploy-and-run.sh` inside the `e2e` stage (jar, Vaadin bundle, image or container, health check) and reuse what the `build` stage already produced where possible.
3. **Free memory during the run.** The always-on dev stack (`marketplace-app`, dev SeaweedFS, `pw-runner`) is not needed by the CI; stopping it frees about 1.5 to 2 GB. Check what the stages really need before deciding.
4. **Keep 2 Playwright workers.** Workers above the core count slow a run through context switching, and sharding helps only across machines ([Currents, sharding vs workers](https://currents.dev/posts/optimizing-test-runtime-playwright-sharding-vs-workers), [TestDino, optimizing workers](https://testdino.com/blog/optimize-playwright-workers)). The gain comes from less contention (items 1 to 3), not from more workers.
5. **Parallel test classes in the integration suite** through `junit-platform.properties`. Possible, but the tests share one database and need data isolation; the sources give no measured speed-up ([Docker guide, singleton containers](https://docs.docker.com/guides/testcontainers-java-lifecycle/singleton-containers)). Only after items 1 to 3, and only if measured.
6. **Find why the `docs` stage took 22 minutes in the full run** (it depends on the metrics stage, which does not wait for `e2e`, so it may have run alongside it) and make it run after the heavy stages, or find the slow call.
7. **Serialize the stages that cannot share resources** (an alternative to separate ports and memory relief, listed in entry 33 of `improvement-133`): costs wall time, so a last resort.

## Related

- Entry 33 of [improvement-133](../../tasks/improvement-133-deferred-oversized-review-findings.md) (the integration and Sonar port collision) was removed on 2026-10-30: the real cause (the host-wide Testcontainers cleanup) is fixed by step 1 and the own ports, and the full runs after it had no `Connection refused`. Entries 34 to 36 that the review of plan F added there were fixed by plan H and removed.
- [improvement-186](improvement-186-rest-hypermedia-media-subresource-and-patch-support.md): the `integration` stage's own port 25433 was added there; its status block records the full-run verification.
- Research: a `deep-research` topic folder under `backlog/research/` for this task (best practices for CI speed on one small host, the project's own measurements, a compared plan).

## Plan (approved 2026-10-29)

Researched with the `deep-researcher` agent (topic `ci-speed-duplicated-work`). Approved with the answers at the end. It found that three of the four big causes differ from the guesses above: the stages after `build` each repeat a full install and queue on one lock; the integration script removes every Testcontainers container on the machine, so Sonar's own integration run kills the integration stage's database (the port split alone does not fix it); the 22-minute `docs` stage was a third Sonar run.


### Goal

The full local CI run (every stage kept) gets much faster than the 66 minutes of 2026-10-29, and stops failing for reasons that have nothing to do with the code.

### The problem this solves

The run logs (Dagu run `ci-20261029T093116Z`, read step by step) show where the 66 minutes went. Three of the four big causes are not the ones the task guessed:

1. **Every stage builds the whole project again, one after another.** The `build` stage compiles and installs everything (2 min 30 s). Then `unit`, `integration`, `archunit_metrics`, `sonar` and the build inside `e2e` each do the same full install again. They share one lock, so they queue up: 1:26 + 2:12 + 3:50 + 4:02 + 5:25, about 17 minutes of repeated work. Each build container starts without the earlier compiled output, so each install compiles everything from zero.
2. **Most of the 19.5 minutes before Playwright starts is waiting in that queue.** The `e2e` stage's build took 1044 s: about 12 minutes of waiting for the lock and 5 min 25 s of its own install. Starting the database took 26 s and starting the application about 1 minute.
3. **The integration tests break each other, and the port change alone will not fix it.** Before the integration suite runs, the build script removes *every* test database container on the machine, so it can clean up leftovers from a crashed run. When Sonar's own copy of the integration tests starts, it removes the `integration` stage's live database. I watched this happen in the proof run that was going on while I worked: the `integration` stage was on its new port 25433, Sonar's tests started at 11:30:21, and from 11:31:13 the `integration` stage logged `Connection to 172.17.0.1:25433 refused`. The earlier "unexpected postmaster exit" in entry 33 of `improvement-133` fits the same cause. Low memory is not needed to explain it.
4. **The 22-minute `docs` stage was a third run of Sonar.** The `docs` stage did *not* run at the same time as `e2e`: it starts only after every stage has ended. The Sonar stage had failed (cause 3), so the stored Sonar analysis was older than the code. The architecture-map generator noticed this and ran the whole of `sonar.sh` again, unit and integration tests included (1199 s). When Sonar passes, `docs` takes about 1 min 43 s.

Unit and integration tests also really do run twice (once in their own stages, once inside Sonar), as the task said.

The run history shows that full runs took about 30 to 31 minutes until mid-October. The test suites have grown since then, so the queue in cause 1 got longer and the timing in cause 3 started to collide.

### The means needed

All changes are in scripts and CI configuration. No product code changes, and no stage is removed from the default run.

Ranked by saving, then risk:

| # | Change | Saving (measured or estimated) | Risk | Effort |
|---|---|---|---|---|
| 1 | The integration test script removes only a leftover test database on **its own** port, not every one on the machine | Removes the false integration and Sonar failures. That also stops the 20-minute Sonar rerun inside `docs` | Low. The command is narrower than today's. A test that crashes leaves a container behind, and that container is on the same port, so it is still cleaned up | One line |
| 2 | **Build once.** The stages after `build` reuse the jars `build` already installed instead of installing again. `e2e` starts the application from the jar `build` made. A failed `build` stops the run | About 12 min off the critical path (the lock wait in `e2e`) and about 17 min of CPU work (measured install times) | Medium. If `build` failed and the run went on, stages would test old jars. That is why a failed `build` must stop the run (decision 2) | Two new script flags plus CI configuration |
| 3 | **Sonar reads the coverage reports of the `unit` and `integration` stages** instead of running the tests a third time. It starts after those two stages. A plain `bash scripts/sonar.sh` outside the CI works as today | About 19 min of CPU work (8 + 11 min of tests) plus one install. The Sonar stage shrinks from about 24 min to a scan of about 3 to 4 min, and it still finishes well before `e2e` | Low to medium. The coverage numbers must match today's: 76.9 % overall, 88.3 % lines, 5261 lines to cover | One script plus CI configuration |
| 4 | The `docs` stage in CI uses the Sonar data already stored and never starts its own Sonar run | 0 min when Sonar passes. Up to 20 min when it fails | Low. When Sonar fails, the architecture map shows the last good Sonar numbers (decision 3) | One flag |
| 5 | Measure memory during a run before stopping anything | None by itself | None | A sampling command |
| -- | Keep 2 Playwright workers | -- | -- | No change |
| -- | Run integration test classes in parallel | Not proven by any source | They share one database | Not now |
| -- | Run stages one after another | Costs wall time | -- | Not needed once 1 to 3 are done |

Order: 1, then 2, then 3, then 4. Change 3 depends on 2: Sonar must not install again either. Change 1 comes first because it alone turns the run green.

What to measure, and where:
- **The first measurement (the 19.5 minutes before Playwright and the 22-minute docs stage) is already done** from the Dagu step logs. Read them again with `docker exec ci-runner sh -c 'cat /root/.dagu/logs/ci/<run-dir>/run_*/e2e.*.out' | grep -E 'AGENTIC|Total time|Finished at'`. In the e2e log, `durationSeconds` of the steps `build-and-test` / `start-application` / `playwright-run` give the split. In the docs log, the line `SonarQube data stale ... rescanning` shows the rescan.
- Before and after each change: the per-stage durations from Dagu's run status (the command is in the executor plan), and for change 3 the Sonar coverage numbers above.
- Memory: sample `docker stats --no-stream` every 30 s during one full run.

### What the result will look like

- One full run with every stage: `build` (about 2.5 min), then all stages side by side. Playwright starts about 4 minutes into the run instead of about 22. Sonar finishes after the integration stage, at about 17 to 20 minutes. Then `docs` takes about 2 minutes.
- **Proposed target: 30 minutes for the default full run, with 25 to 28 minutes expected.** The reasoning: `build` 2.5 min, plus `e2e` (database 0.5 min, application start 1 min, Playwright about 17 to 20 min on the CI stack while other stages run beside it), plus `docs` 1.7 min. The 13-minute Playwright figure in the task was measured on the developer stack. In the measured CI run, Playwright ran alone for its last 15 minutes and still took 20.2 minutes in total, so 13 minutes is not the right base for the CI stack. That last part is an estimate: it has not been measured alone yet.
- The integration and Sonar stages both pass in the parallel run.

### Cost

- Effort: about half a day of executor work, plus two full CI runs to verify (about 30 minutes each once the changes are in).
- Tokens (rough estimate, not measured): Haiku steps about 20k to 40k each (two steps). Sonnet steps about 50k to 90k each (five steps). The total is about 350k to 500k tokens with Sonnet doing the Sonnet steps. With Opus for those steps, the token count is similar but the price is several times higher, and no step needs Opus. Watching the verification runs through the activity monitor adds about 20k to 40k.

### Risks

- **Old jars tested by mistake.** If the `build` stage fails and the run goes on, the later stages would now test what an earlier build left behind. Decision 2 closes this.
- **The CI shares its Maven folder with your own builds.** The CI build containers use the same `maven-cache` volume as `bash scripts/build-and-test.sh` on your machine. If you build by hand during a CI run, you can replace the jars the CI is testing. This is true today too. Change 2 makes the stages rely on that volume for longer (decision 4).
- **Coverage may change.** Sonar will read reports made in other containers. They should give the same numbers, but this must be checked against 76.9 / 88.3 / 5261.
- **Playwright may slow down while the unit and integration tests run beside it.** Today it starts after most of them have finished. If tests start timing out, run the stages more one after another (Dagu's `max_active_steps`).
- **Not settled by the evidence:**
  - Playwright's time alone on the CI stack.
  - Whether memory (about 2 GB free) still causes trouble once the duplicate work is gone.
  - Whether `docker ps --filter publish=<port>` also finds a stopped leftover container. A stopped one holds no port, so it does no harm, but it may pile up.
  - The final pass or fail of the proof run that was still going on (`ci-20261029T111453Z`).

### Where the project's practice differs from best practice

- **"Build once, reuse in later stages"** is the common CI practice: build the artifacts once and let later stages reuse them, not rebuild them. The project's build-and-test script assumes a repeat install is cheap ("Maven's own incremental compilation makes a no-op rebuild cheap"). With a fresh container each time, it is not. The plan follows the practice and fixes that comment. Changes 2 and 3 add a new mode to `sonar.sh` that the Sonar decision (`scripts/sonar`, coverage wired reactor-wide) does not describe, so a new decision should be recorded.
- **The CI's own isolation decision** (`scripts/ci`, "concurrency with normal dev work") says the CI's Maven state lives in its own `ci-m2-cache` volume. In the code, the stages use the shared `maven-cache` volume. Best practice ties the reused artifacts to one run. The plan keeps the project's current way and leaves the choice to you (decision 4).
- **What the brief assumed:** the brief expected the port split (25433) to fix the integration and Sonar collision, and the task expected `docs` to run alongside `e2e`. Neither matches the evidence. The fix for the collision is in the build script, not in Sonar, so the brief's "do not touch Sonar for the port question" still holds.

### Decisions (answered 2026-10-29)

1. **Target wall time:** 30 minutes for the default full run (25 to 28 expected). Approved.
2. **A failed `build` stops the whole run:** yes. Approved.
3. **The `docs` stage in CI never starts its own Sonar run:** yes, it uses the stored data. Approved.
4. **A separate Maven volume for the CI:** not decided. The plan keeps the shared `maven-cache`; to be decided as its own small change.
5. **Stop the developer stack during a CI run:** measure memory first (plan step 7), decide only if memory is still short after changes 1 to 3.
6. **Names of the new switches:** `--no-install` (build-and-test), `--reuse-jar` (deploy-and-run), `SONAR_JACOCO_FROM` (sonar), `--no-sonar-rescan` (architecture-map generator). Approved.

## Execution plan


### Context for every executor

- Task file: `backlog/tasks/improvement-231-local-ci-speed-duplicated-work-and-contention.md`. Evidence: `backlog/research/ci-speed-duplicated-work/notes.md`.
- Project rules that apply to every step: one-line code comments only, no task numbers in comments, English only, run scripts through `scripts/*.sh` (never raw `mvn`), `git add` after each file change, never commit. Every changed script keeps its `# ── Header ──` block current: Usage, Env and Outputs list any new flag or env var. That header is the fact's canonical home; other docs only point to it.
- Decisions this plan assumes are answered as proposed (see `plan-human.md`, "Decisions waiting for you"): D2 = a failed `build` stops the run, D3 = `docs` does not rescan Sonar in CI, D6 = the names below. If the person answers differently, adapt steps 5 and 6 before running them.
- Baseline to compare against (measured 2026-10-29). Full run 64 min 44 s. Stage durations: build 161 s, unit 493 s, integration 935 s (failed), e2e 2366 s, sonar 1469 s (failed), archunit 437 s, docs 1357 s. Sonar: coverage 76.9, line_coverage 88.3, lines_to_cover 5261, uncovered_lines 617.

Command that prints the per-step durations of every Dagu run (used in steps 0 and 9):

```bash
docker exec ci-runner sh -c 'for d in /root/.dagu/data/dag-runs/ci/dag-runs/*/*/*/*/; do f=$(ls $d*/status.jsonl 2>/dev/null | head -1); [ -n "$f" ] && tail -1 $f; echo; done' | python3 -c "
import sys,json
from datetime import datetime
p=lambda s: datetime.fromisoformat(s.replace('Z','+00:00')) if s else None
for line in sys.stdin:
  line=line.strip()
  if not line: continue
  st=json.loads(line); out=[]
  for n in st.get('nodes',[]):
    a,b=p(n.get('startedAt')),p(n.get('finishedAt'))
    if a and b: out.append(f\"{n['step']['name']}={int((b-a).total_seconds())}s/{n['status']}\")
  print(st.get('startedAt','')[:16], ' '.join(out))
" | tail -5
```

(Status 4 = succeeded, 2 = failed, 5 = skipped, as observed in this project's runs.)

### Verified while executing (2026-10-29)

- **Dagu 2.16.3 dependency semantics (tested on a throwaway DAG in `ci-runner`, not in the repo):** a dependent step runs when its dependency was skipped by a precondition and has `continue_on: skipped: true`; it runs when the dependency failed and has `continue_on: failure: true`; it and everything behind it become `aborted` when the dependency failed without `continue_on`. So `sonar` after `[unit, integration]` runs also when they are switched off or failed, and a failed `build` (no `continue_on`) stops every later stage, `pipeline_metrics` and `docs` included.
- **Stage layout wanted by the user:** `unit` and `integration` in parallel, `sonar` after both, that chain in parallel with `e2e`, `archunit_metrics`, `lint`, `shellcheck`; every one of them after `build`.
- **Tick-box rules after step 5:** all stages ticked = Sonar imports the reports of `unit` and `integration`; `sonar` ticked without both of them = Sonar runs its own tests as before; a missing report folder = Sonar stops with an error (step 4), never a silently lower coverage; `build` has no tick-box and always runs. `ci.sh` flags and Dagu parameters stay in sync (`DAGU_PARAMS` in `scripts/ci/run.sh`).
- **Docs generator:** `--with-sonar` rescans via `sonar.sh` in three cases only: Sonar not reachable, no analysis stored, or a `.java` file newer than the last analysis (`ensure_sonar_fresh`). A green Sonar stage leaves nothing newer, so `docs` takes about 1 min 43 s; the 22-minute run was the "newer file" case after a failed Sonar. Step 6's flag skips all three rescans; the metrics then come from the stored analysis, or are `null` when Sonar is not reachable.
- **Added to step 5:** the monitor `scripts/activity-monitor/run.sh` lists `unit integration e2e sonar archunit_metrics` as one parallel group (`SCRIPT_STEP_PARALLEL_GROUPS["ci.sh"]`), which says they depend only on `build`. With `sonar` behind `unit`/`integration` the tree would show it running while it waits; the group, its comment and `scripts/activity-monitor/README.md` are corrected in step 5, and the look of the tree is checked in step 9.
- **Added to step 4's verification:** a negative check, `SONAR_JACOCO_FROM=no-such-run bash scripts/sonar.sh --no-gate` stops with `ERROR: no JaCoCo XML in /reports/no-such-run/jacoco`.
- **Added to step 9:** short runs `ci.sh --sonar` (own tests, as before) and `ci.sh --unit --integration --sonar` (imported reports), next to the full run.

### Result of the full default run after steps 1 to 8 (2026-10-29, Dagu run `ci-20261029T133710Z`)

The whole run **Succeeded in 23 min 4 s** (`ci-run` 1384 s), against 64 min 44 s before. Per stage:

| Stage | Before | After |
|---|---|---|
| build | 161 s | 2 min 0 s |
| unit | 493 s | 5 min 38 s |
| integration | 935 s, failed | 7 min 47 s, 894 passed, no `refused` line in its log |
| sonar | 1469 s, failed | 2 min 5 s, imported 5 reports, no tests run |
| archunit_metrics | 437 s | 2 min 47 s |
| e2e | 2366 s | 17 min 6 s (Playwright 64 passed, 1 flaky, 15.7 min; "Step 1.5 skipped" in the log) |
| lint / shellcheck | failed / 32 s | 40 s / 38 s, both green |
| docs | 1357 s | 1 min 5 s (no `rescanning` line) |

- Sonar coverage after the run: coverage 76.9, line_coverage 88.3, lines_to_cover 5261, uncovered_lines 617 -- the baseline.
- Memory (step 7): 40 samples of `free -m` over the run, lowest `available` 1947 MB; memory did not run short.
- The one flaky test is the setup test `adminEn signs up`: its first click on "Sign up" timed out after 10 s on the first render of the freshly started application and passed on the retry. The earlier CI run of the same suite had none. This is the risk named in the plan (the application starts while unit and integration run beside it); it is not fixed here.
- The monitor tree of this run was garbled because a second wrapper `ci.sh --foreground` was started while the wrapper of the aborted earlier run was still alive and both wrote the same `/tmp/activity-monitor/ci.sh/` files; the Dagu data and `raw.log` (`PASSED`) are right. Open: the look of the tree (the sonar line, the parallel group) is checked in the two short runs below, started one at a time.

### Result of the short runs (2026-10-29, one wrapper at a time, all other containers except `claude-dev` stopped before the first one that counts)

| Run | Total | build | unit | integration | sonar | Quality gate |
|---|---|---|---|---|---|---|
| `ci.sh --unit --integration --sonar` (imported reports), 15:57 | about 10 min | 1 min 53 s | 2 min 44 s | 5 min 8 s | 2 min 21 s (scan 1 min 16 s) | PASSED |
| `ci.sh --sonar` (own tests), 16:17 | about 10 min | 1 min 42 s | in the sonar stage: 1 min 52 s | in the sonar stage: 4 min 40 s | 7 min 52 s | PASSED |

- Imported-reports run: the sonar log has `Skipping the test run: importing JaCoCo reports from: advertisement-build-only-unit advertisement-build-only-integration` and `Importing 5 report(s)`; no ❌ in the tree; skipped stages show ⏭️.
- Own-tests run (fallback): the sonar log shows the full reactor build, 220 + 276 + 100 unit and 894 integration tests with no failures, then `Importing 5 report(s)` and `QUALITY GATE STATUS: PASSED`.
- **A first attempt of the imported-reports run took 79 min** (build 17 min 10 s, unit 30 min 4 s, integration 52 min 57 s; the same stages took 1 min 51 s, 5 min 18 s, 7 min 24 s in the full run). Every module was slower from the start (Query Lib 54.4 s against 6.7 s, the frontend build 450 s against 49 s), so the cause was not one stage and not the order of stages. During it the machine ran at 100% CPU (user 65%, system 31-36%), load average 8-10 on 4 cores, steal and iowait 0, and 1.1 GB of swap used. After all containers except `claude-dev` were stopped and the run was repeated, the stages ran at the normal speed. **The cause is not established**: the containers outside the CI run are a candidate, but it was not tested whether they are the cause. No monitor was attached during that attempt, so the slowdown was seen only 79 min later.
- The stall signal of the monitor used for the second short run fired once without a real stall (the sonar step runs its tests for more than 7 min); the threshold is a per-step guess, not a measurement.

### Steps

### Step 0 -- Re-read the breakdown (measurement, no file change) -- done 2026-10-29, numbers match the expected values and are in "Measured breakdown" above

- Depends on: nothing.
- Files: none changed.
- Executor: haiku.
- Input: the `ci-runner` container and its Dagu logs.
- Exact action: run these commands and paste their output into the task file's status section. The task file is `backlog/tasks/improvement-231-...md`; add a "Measured breakdown" bullet list under "Current state" and copy the numbers as printed.
  1. The `e2e` split: `docker exec ci-runner sh -c 'cat /root/.dagu/logs/ci/dag-run_20261029_093117Z_ci-20261029T093116Z-39711-325477805/run_*/e2e.*.out' | grep -E 'AGENTIC|Total time|Finished at'`. Expected: `build-and-test` 1044 s, own install `Total time: 05:25 min`, `start-application` 1136 s, `playwright-run` 1230 s.
  2. The lock queue: for each of `unit integration sonar archunit_metrics build`, run `docker exec ci-runner sh -c 'grep -m2 -E "Total time:|Finished at:" /root/.dagu/logs/ci/dag-run_20261029_093117Z_*/run_*/<step>.*.out'`.
  3. The docs rescan: `docker exec ci-runner sh -c 'grep -E "stale|AGENTIC" /root/.dagu/logs/ci/dag-run_20261029_093117Z_*/run_*/docs.*.err'`. Expected: the line `SonarQube data stale (... ContactInfo.java changed since last scan) -- rescanning` and `durationSeconds":1199`.
- Verification: the numbers match the expected values above.
- Sources: https://docs.dagu.sh/reference/yaml

### Step 1 -- Narrow the Testcontainers leftover cleanup to this run's own port -- done 2026-10-29: edit applied and staged, `bash -n` clean; the `publish=<port>` filter checked on two throwaway labelled containers (it picks only the container on that host port, the new command removed only the one on the given port); check 3 (no `Connection ... refused` in the integration log) waits for the verification run of step 9

- Depends on: nothing. Do this first: on its own, it removes the false integration and Sonar failures.
- Files: `scripts/build-and-test/build.sh`.
- Executor: haiku.
- Input: lines 235-238 of `scripts/build-and-test/build.sh`.
- Exact change. Old text:

```bash
  # Ryuk is disabled here, so remove any Testcontainers container leaked by a prior crashed run.
  if [ -n "${TESTCONTAINERS_RYUK_DISABLED:-}" ]; then
    docker ps -aq --filter "label=org.testcontainers=true" | xargs -r docker rm -f
  fi
```

New text:

```bash
  # Ryuk is disabled here, so remove a Testcontainers container a crashed earlier run left on this run's own fixed port.
  if [ -n "${TESTCONTAINERS_RYUK_DISABLED:-}" ] && [ -n "${INTEGRATION_TESTS_POSTGRES_FIXED_PORT:-}" ]; then
    docker ps -aq --filter "label=org.testcontainers=true" --filter "publish=$INTEGRATION_TESTS_POSTGRES_FIXED_PORT" | xargs -r docker rm -f
  fi
```

- Verification:
  1. `bash -n scripts/build-and-test/build.sh` prints nothing.
  2. While any integration run is going (for example the CI `integration` stage), run `docker ps --filter label=org.testcontainers=true --filter publish=25433 --format '{{.Names}} {{.Ports}}'`. It must list exactly that run's Postgres container (`0.0.0.0:25433->5432/tcp`). Run the same with `publish=25432` and check that it does *not* list the 25433 container. This confirms that `publish` matches the host port. The Docker docs page does not say this outright.
  3. In the verification run of step 9, the `integration` stage log has no `Connection to 172.17.0.1:25433 refused` line.
- Sources: https://docs.docker.com/reference/cli/docker/container/ls/, https://java.testcontainers.org/features/configuration/

### Step 2 -- `build-and-test.sh --no-install` -- done 2026-10-29: flag added to `run.sh` and `build.sh`, staged; checked with a full build followed by `--unit --no-integration --skip-vaadin --no-install`: "Install skipped" is line 2 of the log, no install goal ran, unit counts 43, 6, 5, 10, 220, 276, 100 as before, BUILD SUCCESS in 2 min 28 s (the "Reactor Build Order" in that log is the test command's own list of 7 modules, not an install). Open: the skipped block is not re-indented inside the `if/else`, and the comment about `SKIP_VAADIN` now sits above the `if NO_INSTALL` line

- Depends on: nothing (step 5 uses it).
- Files: `scripts/build-and-test/run.sh`, `scripts/build-and-test/build.sh`.
- Executor: sonnet.
- Input: both files as they are; `scripts/build-and-test/README.md` only if it restates the flag list.
- Exact behavior:
  - `run.sh` accepts a new flag `--no-install` (default off). It sets `NO_INSTALL=true` and forwards it into the container with `-e NO_INSTALL="$NO_INSTALL"`, next to the existing `SKIP_VAADIN` forwarding. Add the flag to the header's Usage list in the same style as `--skip-vaadin`. The one-line meaning: "skip the reactor install and test against the jars an earlier build already put into the shared maven-cache; only for callers that ran a full build in the same pipeline just before".
  - `build.sh`: when `NO_INSTALL=true`, skip the whole "Build" block. That covers the `flock ... ./mvnw install`, the `target-classes` refresh loop and the `marketplace-app.jar` copy. Print one line such as `=== Install skipped (--no-install): using the jars already in the shared maven-cache ===`. Everything from the `build-info.txt` block onward is unchanged. Add `NO_INSTALL=${NO_INSTALL:-false}` to `build-info.txt`. Document `NO_INSTALL` in the header's Env list.
  - In the same header, correct the false claim "Maven's own incremental compilation makes a no-op rebuild cheap either way". Replace it with one sentence: each container starts from sources without `target/`, so every install compiles from scratch.
  - Do not change the test commands (`./mvnw -pl ... test`). They already resolve the modules outside `-pl` from `/root/.m2`, and the integration JaCoCo CLI already reads the installed jars.
- Constraints: no new dependency. No change to the defaults of any existing flag. A run without `--no-install` behaves exactly as before.
- Verification:
  1. `bash -n` on both files.
  2. `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --no-unit --no-integration` (a full build), then `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --no-integration --skip-vaadin --no-install`. The second run passes, its log shows the "Install skipped" line, there is no `Reactor Build Order` before the tests, and the test counts match the earlier unit run (43, 6, 5, 10, 220, 276, 100 in the 2026-10-29 log).
- Sources: https://xebia.com/blog/how-to-build-true-pipelines-with-jenkins-and-maven, https://maven.apache.org/extensions/maven-build-cache-extension/ (the variant not chosen; see notes.md)

### Step 3 -- `deploy-and-run.sh --reuse-jar` -- done 2026-10-29: flag added to `scripts/deploy-and-run/run.sh`, staged; checked: `--reuse-jar --from-scratch` prints the error and exits 2; `--reset-only-db --reuse-jar` deployed in 37 s (the build step took 3 s, against 1 min 44 s before), the log has the "Step 1.5 skipped" line and no "Building (full reactor)" line, and the application answers HTTP 200 on port 8081. The `build.sh` header line was also reworded: "full reactor install and shared-artifact refresh; skipped when NO_INSTALL=true"

- Depends on: nothing (step 5 uses it).
- Files: `scripts/deploy-and-run/run.sh`.
- Executor: sonnet.
- Input: the file. The flag parsing is at lines 100-121, "Step 1.5" at lines 266-278, and the jar copy from `maven-cache:/src/artifacts/marketplace-app.jar` at about line 343.
- Exact behavior:
  - New flag `--reuse-jar` (default off): skip "Step 1.5" (the internal `build-and-test.sh` call) and start the application from the `marketplace-app.jar` already in the shared `maven-cache` volume. Print one line saying so.
  - `--reuse-jar` together with `--from-scratch` or `--with-tests` is a usage error. Exit 2 with a clear message, in the script's existing error style.
  - Add no extra existence check for the jar. The copy step's `cp` already fails under the script's ERR trap when the file is missing. Make sure that failure path's message names the jar path.
  - Add the flag to the header's Usage list. Its meaning: "start from the jar a build in the same pipeline already produced; for the CI's e2e stage".
- Constraints: default behavior unchanged; `deploy-and-run.bat` only forwards arguments (check it, and change it only if it filters flags).
- Verification: `bash -n`; after a full build, `bash scripts/activity-monitor.sh -- scripts/deploy-and-run.sh --reset-only-db --reuse-jar` brings the dev app up on 8081, with no `=== Building (full reactor) ===` line in its output.
- Sources: https://xebia.com/blog/how-to-build-true-pipelines-with-jenkins-and-maven

### Step 4 -- `sonar.sh` imports existing JaCoCo reports when asked -- done 2026-10-29: `SONAR_JACOCO_FROM` added to `scripts/sonar/run.sh` (a folder without XML stops the run with exit 1), staged; checked: after a real `build-and-test.sh --unit --integration` (unit green, integration 894 passed) the Sonar run with `SONAR_JACOCO_FROM=advertisement-build-only` skipped the tests, logged "Importing 5 report(s)", took 1 min 38 s, and the Sonar API gave coverage 76.9, line_coverage 88.3, lines_to_cover 5261, uncovered_lines 617 -- identical to the baseline; the negative run with `SONAR_JACOCO_FROM=no-such-run` stopped after 28 s with exit code 1 and `ERROR: no JaCoCo XML in /reports/no-such-run/jacoco`, no analysis uploaded

- Depends on: nothing (step 5 uses it).
- Files: `scripts/sonar/run.sh`.
- Executor: sonnet.
- Input: the file. The `build-and-test.sh --unit --integration` call is at line 170, and the JaCoCo copy (`cp /reports/$SONAR_BUILD_CONTAINER_NAME/jacoco/*.xml`) is at lines 202-205. The scanner container already mounts `-v test-reports:/reports`.
- Exact behavior:
  - New env var `SONAR_JACOCO_FROM`: a space-separated list of `BUILD_CONTAINER_NAME`s whose `/reports/<name>/jacoco/*.xml` files are imported.
  - When it is set: skip the `build-and-test.sh` call, and copy `*.xml` from each listed `/reports/<name>/jacoco/` into `/tmp/sonar-src/jacoco-reports/`. Print one line naming the folders. If a listed folder has no XML, print a `WARNING:` line naming it; Sonar then reports lower coverage, which is visible.
  - When it is unset: behavior is exactly as today. This keeps the scripts/sonar decision that a standalone scan runs real tests.
  - The file names must stay `query-lib.xml`, `marketplace-app.xml`, `marketplace-orchestrator.xml`, `marketplace-rest-api.xml` and `integration-tests-aggregate.xml`, which `sonar-project.properties` lists. The unit stage writes the first four, the integration stage the last one.
  - Document `SONAR_JACOCO_FROM` in the header's Env line, and correct the Description, which says the script always builds and tests.
- Constraints: do not touch `sonar-project.properties`, the port, or the gate logic.
- Verification: `bash -n`. Then, after `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration`, run `SONAR_JACOCO_FROM=advertisement-build-only bash scripts/sonar.sh --no-gate`. It finishes in a few minutes, and the scanner log shows `Importing 5 report(s)`. Coverage from `curl -s -u "$TOKEN:" "http://localhost:9099/api/measures/component?component=advertisement&metricKeys=coverage,line_coverage,lines_to_cover"` is within ±0.5 of 76.9 / 88.3 and lines_to_cover is 5261 (± code changed since).
- Sources: https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/test-coverage/java-test-coverage/

### Step 5 -- Wire the CI DAG to build once -- done 2026-10-29: the 7 edits applied to `scripts/ci/dagu/ci.yaml` plus the monitor comment and the `ci.sh:sonar` description in `scripts/activity-monitor/run.sh`, staged; `dagu dry` in `ci-runner` parses the file and resolves the graph; both READMEs state nothing about Sonar being parallel and stay unchanged. Real behaviour (stage order, imported reports, the look of the tree) is checked by the runs of step 9. Left for step 8: `.claude/rules/scripts.md` ("Local CI Runner") still says all stages depend only on `build`, and the comment above the `integration` port line in `ci.yaml` speaks of an integration run inside the Sonar stage, which now exists only in the fallback mode

- Depends on: steps 2, 3, 4.
- Files: `scripts/ci/dagu/ci.yaml`.
- Executor: haiku.
- Input: the file as it is now.
- Exact edits (old text → new text):

1. `build` step. Old:
```yaml
  - id: build
    run: SESSION_ID=${params.session_id} bash scripts/build-and-test.sh --no-unit --no-integration
    continue_on:
      failure: true
```
New:
```yaml
  # No continue_on: the later stages reuse this build's jars, so a failed build stops the run.
  - id: build
    run: SESSION_ID=${params.session_id} bash scripts/build-and-test.sh --no-unit --no-integration
```

2. `unit` step. Old: `    run: SESSION_ID=${params.session_id} BUILD_CONTAINER_NAME=advertisement-build-only-unit bash scripts/build-and-test.sh --unit --no-integration --skip-vaadin`. New: `    run: SESSION_ID=${params.session_id} BUILD_CONTAINER_NAME=advertisement-build-only-unit bash scripts/build-and-test.sh --unit --no-integration --skip-vaadin --no-install`

3. `integration` step. Old: `        bash scripts/build-and-test.sh --no-unit --integration --skip-vaadin --sandbox`. New: `        bash scripts/build-and-test.sh --no-unit --integration --skip-vaadin --sandbox --no-install`

4. `archunit_metrics` step. Old: `        bash scripts/build-and-test.sh --no-unit --no-integration --archunit-metrics --skip-vaadin`. New: `        bash scripts/build-and-test.sh --no-unit --no-integration --archunit-metrics --skip-vaadin --no-install`

5. `e2e` step. Old: `        bash scripts/deploy-and-run.sh "$RESET_FLAG" --dev-tools`. New: `        bash scripts/deploy-and-run.sh "$RESET_FLAG" --dev-tools --reuse-jar`

6. `sonar` step. Old:
```yaml
  - id: sonar
    depends: build
    run: SESSION_ID=${params.session_id} bash scripts/sonar.sh
```
New:
```yaml
  # Imports the unit and integration stages' JaCoCo reports when both ran, instead of running the tests again.
  # A listed stage without JaCoCo XML (it failed) makes this stage fail with an error, never a silent lower coverage.
  - id: sonar
    depends: [unit, integration]
    run: |
      if [ "${params.unit}" = "true" ] && [ "${params.integration}" = "true" ]; then
        export SONAR_JACOCO_FROM="advertisement-build-only-unit advertisement-build-only-integration"
      fi
      SESSION_ID=${params.session_id} bash scripts/sonar.sh
```

7. Header comment, first Description line. Old: `# Description: The "ci" DAG -- build (reactor compile+install) -> unit/integration/e2e/sonar/`. New: `# Description: The "ci" DAG -- build (reactor compile+install, reused by every later stage) -> unit/integration/e2e/`. On the next line, old: `#   archunit_metrics/lint/shellcheck in parallel -> pipeline_metrics -> docs. Loaded by Dagu's`. New: `#   archunit_metrics/lint/shellcheck in parallel, sonar after unit+integration -> pipeline_metrics -> docs. Loaded by Dagu's`

- Verification: `python3 -c "import yaml,sys; yaml.safe_load(open('scripts/ci/dagu/ci.yaml'))"` succeeds (if PyYAML is missing, use `docker exec ci-runner dagu dry scripts/ci/dagu/ci.yaml`; if `dry` does not exist in the installed Dagu, report that instead of guessing); `grep -c -- '--no-install' scripts/ci/dagu/ci.yaml` prints 3.
- Sources: https://docs.dagu.sh/writing-workflows/continue-on, https://docs.dagu.sh/reference/yaml

### Step 6 -- `docs` stage: no Sonar rescan in CI -- done 2026-10-29: `--no-sonar-rescan` added to the generator (a string flag like `WITH_SONAR`; the guard is `[ -n "$WITH_SONAR" ] && [ -z "$NO_SONAR_RESCAN" ] && ensure_sonar_fresh`) and to the `docs` step of `ci.yaml`, staged; checked: after touching a `.java` file newer than the last analysis, the generator with `--with-sonar --no-sonar-rescan` exits 0 with no `rescanning` line (the generator itself took 3 min 38 s). The two generated architecture files changed again in the working tree and are not staged

- Depends on: step 5 (same DAG file, edit after it).
- Files: `docs/architecture/scripts/generate-architecture-model.sh`, `scripts/ci/dagu/ci.yaml`.
- Executor: sonnet.
- Input: the generator's arg parsing (around lines 72-90), `ensure_sonar_fresh()` (lines 1193-1218), and its call at line 2053 (`[ -n "$WITH_SONAR" ] && ensure_sonar_fresh`).
- Exact behavior:
  - New flag `--no-sonar-rescan`. It is only meaningful with `--with-sonar`. The generator then reads whatever analysis the server holds (`sonar_metrics_json`), and never calls `scripts/sonar.sh`. Implement it as a guard at the single call site, not inside the function. Add the flag to the header's Usage line, with the one-line meaning "use the stored Sonar analysis as is (the CI's own sonar stage already scanned)".
  - In `ci.yaml`'s `docs` step, change `[ "${params.sonar}" = "true" ] && SONAR_FLAG="--with-sonar"` to `[ "${params.sonar}" = "true" ] && SONAR_FLAG="--with-sonar --no-sonar-rescan"`.
- Constraints: a manual run without the new flag behaves as today. Do not touch the generated outputs by hand. Regenerate only through the generator, and only if the change requires it (it should not).
- Verification: `bash -n`; `bash docs/architecture/scripts/generate-architecture-model.sh --with-sonar --no-sonar-rescan` finishes in about 1-2 min, and its stderr has no `rescanning` line, even when a `.java` file is newer than the last analysis (touch one file to test, then restore its mtime or leave it; touching does not change content).
- Sources: none external (the cause is in the project's own code; see notes.md).

### Step 7 -- Memory measurement (no file change)

- Depends on: steps 1-6 done (it measures the new shape).
- Files: none in the repo. The samples go to the session scratchpad.
- Executor: haiku.
- Exact action: during the step 9 run, in a background Bash call, run `while true; do date -u +%T; docker stats --no-stream --format '{{.Name}} {{.MemUsage}} {{.CPUPerc}}'; free -m | sed -n 2p; sleep 30; done > <scratchpad>/stats.txt`, and stop it when the run ends. Report the lowest `available` value from `free -m`, and the five largest containers at that moment.
- Verification: the file has one sample block per 30 s across the whole run.
- Sources: https://currents.dev/posts/optimizing-test-runtime-playwright-sharding-vs-workers (contention as the reason to measure before adding parallelism)

### Step 8 -- Documentation that restates the changed facts -- done 2026-10-29: four statements corrected (`.claude/rules/scripts.md` SonarQube and Local CI Runner sections, `scripts/build-and-test/README.md`, the port comment in `ci.yaml`), staged; both check scripts pass. Left for a decision record (not edited here): in `scripts/sonar/DECISIONS.md` the entry that says a sonar run always runs real tests (true only for a standalone run); in `scripts/ci/DECISIONS.md` the statements that every stage installs the reactor / that sonar runs in parallel with unit and integration / the `--skip-vaadin` explanation that does not mention `--no-install` and `--reuse-jar`

- Depends on: steps 1-6.
- Files: only where a fact became false. Check `scripts/ci/README.md`, `scripts/build-and-test/README.md`, `scripts/deploy-and-run/README.md`, the "Local CI Runner" and "SonarQube Analysis" sections of `.claude/rules/scripts.md`, and `scripts/CLAUDE.md` if it exists. Find candidates with `git grep -n -E 'skip-vaadin|build-and-test.sh --unit --integration|full reactor' -- '*.md'`.
- Executor: sonnet.
- Exact behavior: wherever one of these files states that Sonar runs its own tests in CI, that every stage installs the reactor, or that `sonar` starts right after `build`, change the text to the new state, or replace it with a pointer to the script header. Add nothing that only repeats a script header. Follow `.claude/rules.md`: state only the target, no before/after, no task numbers, no ADR numbers.
- Verification: `git diff --stat` touches only `.md` files that contained a now-false statement. Run `bash .claude/nav/scripts/check-hardcoded-counts.sh` and `bash .claude/nav/scripts/check-flows-completeness.sh`; both pass.
- Sources: none external.

### Step 9 -- Verification of the whole (one full default run) -- partly done 2026-10-29: the first full run (23 min 4 s) and both short runs passed (see the two result sections above); left: a repeat full run with the containers outside the CI run stopped, to take the final timing, and the three "After passing" items

- Depends on: steps 1-8.
- Executor: sonnet (it reads logs and compares numbers, no design work).
- Exact action:
  1. Background `bash scripts/activity-monitor.sh -- bash scripts/ci.sh --foreground` (the default: every stage) and watch `/tmp/activity-monitor/ci.sh/tree.txt` with Monitor, as `.claude/rules.md` "Scripts" requires. Run step 7's sampler at the same time.
  2. When the run ends, run the duration command from "Context for every executor".
  3. Check the Sonar coverage with the `curl` from step 4.
- Pass criteria:
  - Every stage is `4` (succeeded). Integration and Sonar pass.
  - The `unit`, `integration` and `archunit_metrics` logs contain the "Install skipped" line. The `e2e` log has no `=== Building (full reactor) ===`, and its `start-application` comes within about 2 min of the step's start.
  - The `sonar` step starts when the later of `unit` and `integration` ends. Its log shows `Importing 5 report(s)`, and there is no `Reactor Build Order` in it.
  - The `docs` stage takes under 3 min, and its stderr has no `rescanning`.
  - Coverage within ±0.5 of 76.9 / 88.3.
  - Total wall time is written into the task file. The proposed target is 30 min, with 25-28 expected. If the total is above 30 min, report which step is on the critical path, and Playwright's own duration (the `N passed (X.Xm)` line), and stop. Do not tune further without the person.
- After passing (main thread, with the person's approval, not an executor):
  - Record the decisions with `/record-decision`. One is for `scripts/ci`: build once, sonar after unit+integration, a failed build stops the run, and an annotation on the CI isolation decision that the stages share the host's `maven-cache`. One is for `scripts/sonar`: the CI-only report import beside the existing coverage decision.
  - Entry 33 of `backlog/tasks/improvement-133-deferred-oversized-review-findings.md` -- removed 2026-10-30 (resolved, see the Related section).
  - Write the measured target into `improvement-231`.
- Sources: https://docs.sonarsource.com/sonarqube-server/latest/analyzing-source-code/test-coverage/java-test-coverage/, https://playwright.dev/docs/test-parallel

### Follow-up plan A -- The CI gets its own Maven volume (decision 4, planned 2026-10-29, nothing executed)

Facts checked: `maven-cache` (823 MB) is written by the CI stages and by every manual `build-and-test.sh` / `deploy-and-run.sh` / `sonar.sh` run; the name is hard-coded in `scripts/build-and-test/run.sh` (line 262 `-v maven-cache:/root/.m2`, lines 173-175 `--reset-cache`), `scripts/deploy-and-run/run.sh` (line 354 `-v maven-cache:/src:ro`) and `scripts/sonar/run.sh` (line 156 `-v maven-cache:/root/.m2` of the long-lived container `sonar-scanner`, name set on line 59). `build.sh` serializes concurrent `mvn` runs with a lock file inside the volume, so the shared volume does not break builds; the risk is that a manual build replaces jars the CI reuses (`--no-install`, `--reuse-jar`). Disk is not a limit (920 GB free). `vaadin-cache` stays shared (no evidence of a problem). Proposal, not decided: variable `MAVEN_CACHE_VOLUME` (default `maven-cache`) and `SONAR_SCANNER_CONTAINER` (default `sonar-scanner`); the CI DAG sets `ci-maven-cache` and `sonar-scanner-ci`. The second variable is needed because the scanner container is long-lived and its mount is fixed when it is created, and its check (`sonar/run.sh` lines 151-158) compares only the image and the status, so a changed volume would not be noticed.

- A1 -- `scripts/build-and-test/run.sh` (executor: haiku). Exact edits: (1) line 262 `-v maven-cache:/root/.m2 \` becomes `-v "${MAVEN_CACHE_VOLUME:-maven-cache}:/root/.m2" \`; (2) in the `--reset-cache` block (lines 172-176) the echo becomes `echo "=== Clearing build caches (${MAVEN_CACHE_VOLUME:-maven-cache}, vaadin-cache) ==="` and `docker volume rm maven-cache` becomes `docker volume rm "${MAVEN_CACHE_VOLUME:-maven-cache}"`; (3) in the header, the `Input:`/`Outputs:` lines that say "shared maven-cache" (lines 20, 50, 77) get the words "the Maven cache volume (`maven-cache` unless MAVEN_CACHE_VOLUME names another)"; add `MAVEN_CACHE_VOLUME` to the header's environment variables with one line. Check: `bash -n scripts/build-and-test/run.sh`; `grep -n 'maven-cache' scripts/build-and-test/run.sh` shows only defaults and comments.
- A2 -- `scripts/deploy-and-run/run.sh` (haiku). Line 354 `-v maven-cache:/src:ro` becomes `-v "${MAVEN_CACHE_VOLUME:-maven-cache}":/src:ro`; the comments on lines 3-4, 30, 49, 280, 289, 349-350 keep their meaning with "the Maven cache volume" in place of "the shared maven-cache"; `MAVEN_CACHE_VOLUME` added to the header's environment list. Check: `bash -n`, and the grep as in A1.
- A3 -- `scripts/sonar/run.sh` (haiku). Line 59 becomes `SCANNER_CONTAINER="${SONAR_SCANNER_CONTAINER:-sonar-scanner}"`; line 156 becomes `-v "${MAVEN_CACHE_VOLUME:-maven-cache}:/root/.m2" -v test-reports:/reports \`; header lines 6, 20, 24 and 181 reworded the same way; both variables added to the header's environment list. Check: `bash -n`, grep.
- A4 -- `scripts/ci/dagu/ci.yaml` (haiku). Directly after `working_dir: /app` (line 14) add `env:` with two list items `- MAVEN_CACHE_VOLUME=ci-maven-cache` and `- SONAR_SCANNER_CONTAINER=sonar-scanner-ci`; header gets one sentence saying the CI uses its own Maven volume and scanner container so manual runs cannot change what a CI run reuses. Check: Dagu accepts a top-level `env:` list (checked 2026-10-29 on Dagu 2.16.3 in `ci-runner` with a throwaway DAG: a top-level `env:` list reaches a step and a child `bash -c`; the real run of A6 still has to show `ci-maven-cache` in a build container's `docker inspect`).
- A1-A4 -- done 2026-10-29 by one Haiku agent, diff checked in the main thread (`bash -n` clean on the three scripts, `dagu dry` parses `ci.yaml`); the agent's two deviations (the existing `# Env:` paragraphs extended instead of new lines; `working_dir` at line 27) are harmless, and three wording fixes (the `ci.yaml` `Env:` paragraph, a duplicate header sentence, a doubled parenthesis in `deploy-and-run/run.sh`) were made afterwards.
- A5 -- wording done 2026-10-29 in `scripts/ci/README.md` (the "Can I develop ... while this runs?" bullet), `.claude/rules/scripts.md` (three places) and `scripts/build-and-test/README.md`; the dev-facing mentions of `maven-cache` (commands, `flows.md`) stay, they are true for the default. Left: the `/record-decision` entries below. Original plan text: docs (haiku for the wording, main thread for the decision). `scripts/ci/README.md` lines 108-116 ("the same `maven-cache` named Docker volume") state the new volume; `.claude/rules/scripts.md` ("Local CI Runner") the same; `scripts/ci/DECISIONS.md` and `scripts/sonar/DECISIONS.md` are changed only through `/record-decision` (one decision: the CI has its own Maven volume and scanner container; annotate the CI isolation decision that said the stages share `maven-cache`), then `bash .claude/nav/scripts/generate-adr-index.sh`.
- A6 -- Verification (main thread, sonnet): (1) before: `docker run --rm -v maven-cache:/m alpine stat -c '%Y %s' /m/artifacts/marketplace-app.jar`, write the number down; (2) `bash scripts/activity-monitor.sh -- scripts/ci.sh --unit --integration --sonar --no-archunit-metrics --no-lint --no-shellcheck --no-docs --foreground` with a Monitor on the tree and the stall check, all containers outside the run stopped except `claude-dev`; (3) pass: all stages green, `docker volume ls` shows `ci-maven-cache` with content, the number from (1) is unchanged, the `sonar-scanner-ci` container exists and `sonar-scanner` was not touched; (4) record the cold-volume build time (the first run downloads all dependencies) and then the time of a second run; (5) run `bash scripts/build-and-test.sh --unit --no-integration` by hand while nothing else runs and check that it still uses `maven-cache` (`docker inspect` of its container shows the mount before it is removed, or the volume's `stat` changes).
- Risks: the first CI run with an empty `ci-maven-cache` is slower (dependencies are downloaded); other scripts that mount `maven-cache` by name outside the three above would not follow the variable (the grep in A1-A3 and `grep -rn maven-cache scripts` must show none left that the CI reaches); `docs` stage calls `sonar/run.sh` through the generator and must inherit the two variables from the DAG env.

### Follow-up plan H -- Fix entries 34 to 36 of `improvement-133` now instead of deferring them (asked for 2026-10-30, done 2026-10-30)

Decisions of the person on 2026-10-30: (34) the build must be a mandatory step before the parallel stages -- it already is (the `build` step; every other stage depends on it); what is missing is that the Sonar stage's own-tests mode installs the reactor a second time, so the fix is that it does not; (35) the reports of a failed test stage are not deleted, the stage writes a success marker and Sonar imports only stages that have it; (36) the names are read from the dynamic source, not copied into a second script.

- H1 -- `scripts/build-and-test/build.sh` (haiku). Directly before the last line `[ "$FINAL_EXIT" -eq 0 ] || exit $FINAL_EXIT`, insert: `if [ "$FINAL_EXIT" -eq 0 ] && { [ "$RUN_UNIT" = "true" ] || [ "$RUN_INTEGRATION" = "true" ]; }; then` / `  mkdir -p "$REPORTS_DIR/jacoco"` / `  touch "$REPORTS_DIR/jacoco/tests.passed"` / `fi`, with one comment line above: `# Marks a run whose every requested test phase passed, so a coverage import can tell it from a run that failed after writing some XML.` Header (the paragraph that lists the files written under `$REPORTS_DIR/jacoco/`): one added sentence naming `tests.passed`. Check: `bash -n`.
- H2 -- `scripts/sonar/run.sh` (haiku). (1) In the import loop (`for jacoco_src in $SONAR_JACOCO_FROM`), directly after the `do`, add the check: `if ! docker exec --user root "$SCANNER_CONTAINER" test -f "/reports/$jacoco_src/jacoco/tests.passed"; then` / `  echo "ERROR: $jacoco_src did not pass its tests (no tests.passed marker) -- its reports are kept but not imported" >&2` / `  exit 1` / `fi`; the existing XML check stays after it. (2) The line `BUILD_CONTAINER_NAME="$SONAR_BUILD_CONTAINER_NAME" bash "$ROOT/scripts/build-and-test.sh" --unit --integration` becomes `BUILD_CONTAINER_NAME="$SONAR_BUILD_CONTAINER_NAME" bash "$ROOT/scripts/build-and-test.sh" --unit --integration ${SONAR_BUILD_ARGS:-}` (unquoted on purpose, it holds flags). (3) Header `Env:` gets `SONAR_BUILD_ARGS (default empty) -- extra flags for the build-and-test.sh call that runs this stage's own tests; the CI passes --no-install so the reactor built once by the build step is not installed again.` and the `SONAR_JACOCO_FROM` text says a listed folder needs the `tests.passed` marker. Check: `bash -n`.
- H3 -- `scripts/ci/dagu/ci.yaml` (haiku). In the `sonar` step, above `SESSION_ID=$DAG_RUN_ID bash scripts/sonar.sh`, add `export SONAR_BUILD_ARGS=--no-install` with the comment line `# The build step already installed the reactor; an own-tests run here must not install it again while the e2e stage reads the jar.` The comment above the step ("A listed stage without JaCoCo XML (it failed) makes this stage fail ...") becomes: `# A listed stage without the tests.passed marker or without JaCoCo XML makes this stage fail with an error, never a silent lower coverage.` Check: `dagu dry` in `ci-runner`.
- H4 -- `scripts/ci/run.sh` (haiku). (1) Replace `CI_TEST_REPORTS_VOLUME="ci-test-reports"` and its comment by: `# The volume name is read from the DAG file, the single place that sets it.` / `CI_TEST_REPORTS_VOLUME="$(sed -n 's/^  - TEST_REPORTS_VOLUME=//p' "$ROOT/scripts/ci/dagu/ci.yaml")"` / `[ -n "$CI_TEST_REPORTS_VOLUME" ] || { echo "ERROR: TEST_REPORTS_VOLUME not found in scripts/ci/dagu/ci.yaml" >&2; exit 1; }`. (2) In `sync_artifacts`, after the `VOL_READER` container is started, add `local STAGE_DIRS` and `STAGE_DIRS="$(docker exec "$VOL_READER" sh -c 'ls /reports' 2>/dev/null | grep '^advertisement-build-only-')"`; replace the line `for name in advertisement-build-only-unit advertisement-build-only-integration advertisement-build-only-ci-sonar; do` by `for name in $STAGE_DIRS; do` (the two `docker cp` lines inside stay); replace the single `it-mirror` copy by a loop `for name in $STAGE_DIRS; do docker cp "$VOL_READER:/reports/$name/it-mirror/." "$ROOT/integration-tests/reports/" 2>/dev/null; done` (folders without `it-mirror` fail silently as before; the folders are in alphabetical order, so `-integration` comes after `-ci-sonar` and its copy is the one that stays). (3) The session-info loop `for sub in playwright sonar build-and-test build-and-test/advertisement-build-only-{unit,integration,ci-sonar,archunit,deploy}; do` becomes `for sub in playwright sonar build-and-test $(for name in $STAGE_DIRS; do echo "build-and-test/$name"; done); do` -- `STAGE_DIRS` must be set before that loop (it is, same function, and the volume holds the report folder of every stage that ran, `archunit` and `deploy` included). (4) The header lines that name the folders `advertisement-build-only-{unit,integration,ci-sonar}` say `advertisement-build-only-*` instead. Check: `bash -n`; `grep -n 'advertisement-build-only' scripts/ci/run.sh` shows only the prefix filter and comments.
- H5 -- verification (main thread, the machine otherwise idle): (1) `bash scripts/ci.sh --unit --integration --sonar --foreground`: green; Sonar imports (the log has "Importing"); `tests.passed` exists in both stage folders; the host folders `scripts/build-and-test/reports/advertisement-build-only-{unit,integration,ci-sonar?}` were refreshed (modified time). (2) Negative marker test: remove `tests.passed` from the unit folder of `ci-test-reports`, then run by hand `MAVEN_CACHE_VOLUME=ci-maven-cache TEST_REPORTS_VOLUME=ci-test-reports SONAR_SCANNER_CONTAINER=sonar-scanner-ci SONAR_JACOCO_FROM="advertisement-build-only-unit advertisement-build-only-integration" bash scripts/sonar.sh`: it stops with the `did not pass its tests` error and the XML files are still in the folder; with the marker put back it imports. (3) `bash scripts/ci.sh --sonar --foreground` (the Sonar stage runs its own tests): green, and the Sonar stage's own-tests log has the line "Install skipped" (so no second install). (4) `git diff --stat` shows only the four files and the docs changed by this plan.
- H6 -- bookkeeping (main thread): remove entries 34, 35 and 36 from `backlog/tasks/improvement-133-deferred-oversized-review-findings.md`, record the results here.

Result of plan H (2026-10-30): H1 to H4 were applied by one haiku agent and checked in the main thread (diff read, `bash -n` clean on the three scripts, `dagu dry` of `ci.yaml` succeeds). The agent raised two doubts that did not hold: a stale `tests.passed` marker cannot survive, because `scripts/build-and-test/run.sh` removes `/reports/<container name>` before every run; and the `Env:` list in the `ci.yaml` header documents the DAG's own settings, it is not a second copy. H5 (1): `ci.sh --unit --integration --sonar --foreground` green (unit 3 min 36 s, integration 5 min 59 s, Sonar 1 min 12 s importing 5 reports, docs 1 min 1 s); both stage folders hold `jacoco/tests.passed`, and the host copies of the stage folders and `session-info.txt` were refreshed through the volume-listing loop in `sync_artifacts`. H5 (2): with the marker removed from the unit folder, `sonar.sh` with the CI volumes stopped with `advertisement-build-only-unit did not pass its tests (no tests.passed marker)` and the XML files stayed in the folder; with the marker put back it imported 5 reports and the quality gate was PASSED. H5 (3): `ci.sh --sonar --foreground`: the Sonar stage ran its own tests in 7 min 19 s, its log has `Install skipped (--no-install)` and the quality gate PASSED. Not done: a real failing test stage to see the marker missing in a CI run (the negative case was made by removing the marker), and the race between Sonar and e2e itself was not reproduced, only the second install that caused it is gone. H6: entries 34, 35 and 36 were removed from `improvement-133`.

### Follow-up plan I -- The architecture map keeps its test and coverage tables when CI generates it (found 2026-10-30, planned, nothing executed)

Bug, checked in the code and on disk: `docs/architecture/scripts/generate-architecture-model.sh` (the Python block that builds `testReports`, about lines 1334 to 1397) reads coverage from `scripts/build-and-test/reports/jacoco/*.xml` and tests from `scripts/build-and-test/reports/surefire/<module>/`, the output of a plain `build-and-test.sh` run, and skips every folder named `advertisement-build-only*`. The CI stages write to `advertisement-build-only-unit` and `advertisement-build-only-integration`, so the map the CI `docs` stage generates always has an empty `testReports`. The two stage folders have the plain folder's layout and do not overlap: `-unit` holds `jacoco/<module>.xml` and `surefire/<module>/` for 7 modules, `-integration` holds `jacoco/integration-tests-aggregate.xml` and `surefire/integration-tests/`; the plain folder holds their union.

- I1 -- the generator (haiku with the exact code, the design is decided here: ids can repeat, the newest `runAt` wins). The `testReports` block collects the roots `scripts/build-and-test/reports` (plain) and the two stage folders `scripts/build-and-test/reports/advertisement-build-only-unit` and `...-integration`; each root is read with the same code (its own `jacoco` and `surefire` subfolders, its own relative link prefix); when an id appears in several roots the entry with the newest `runAt` wins; `buildInfo` is read from the plain `scripts/logs/build-and-test/build-info.txt` and, when that file is missing, from the same file inside the `-unit` log folder. The generator header (`Input:`) names the stage folders. Nothing else in the generator changes.
- I2 -- check (main thread): run the generator on the host over the report folders that exist now; the map shows coverage for 8 ids (7 modules and `integration-tests-aggregate`) and tests for 8 ids (7 modules and `integration-tests`) from the V4 run; then the same in `ci-runner` through one CI run (docs stage) and the diff of the two generated files shows no emptied `testReports`.
- I2b -- the sync of the map (checked in the code): after every CI run `scripts/ci/run.sh` (lines 210 to 213) copies `architecture-model.json` and `architecture-map.html` from `ci-runner` over the same files in the project. That copy is why the project map loses its tests table after each CI run, and no change to it is needed: once I1 is in, the map CI generates holds the tables and the same copy carries it into the project. Check: after a CI run (docs stage on) the two files in the project hold the tests and coverage ids of I2.
- I3 -- the map in the project (main thread): the person uses `docs/architecture/architecture-map.html` and `data/architecture-model.json`, so after the fix the generator is run on the host (`--with-sonar --no-sonar-rescan --with-archunit`) over the report folders of the V4 run, the diff is read and both files are staged, with the ADR rows and the tests tables in them.
- I4 -- bookkeeping: the result goes here and into the final verification (V5) of improvement-186.

### Follow-up plan J -- Fixes from the second review (review of the staged diff, 2026-10-30, planned, nothing executed)

The second `/code-review` (report only, default effort) raised ten findings; each was checked against the code. Four are real and small and are fixed here; the rest are not defects or too small to act on.
- J1 (finding 1) -- the `/tmp/ci-sonar-ok` marker is removed only inside the `sonar` step; a marker left by an earlier run survives when the sonar step is skipped while `sonar` stays true. Fix, `scripts/ci/dagu/ci.yaml`: the `build` step starts with `rm -f /tmp/ci-sonar-ok` (haiku).
- J2 (finding 2) -- a run filtered with `--unit-test` or `--integration-test` that passed also writes `tests.passed`, so Sonar would import a partial coverage as the whole suite. Fix, `scripts/build-and-test/build.sh`: the marker is written only when `UNIT_TEST_ARG` and `INTEGRATION_TEST_ARG` are both empty, and its comment says "of the whole suite" (haiku).
- J3 (finding 7) -- `--reset-cache` prints only "volume ... is still in use". Fix, `scripts/build-and-test/run.sh`: the error branch also prints the containers that mount the volume (`docker ps -a --filter "volume=$volume" --format "  {{.Names}} ({{.Status}})"`) (haiku).
- J4 (finding 9) -- the failure of `OperationCoverageTest` does not say why it fails. Fix, `OperationCoverageTest.java`: the message becomes `"Level 3 scenario classes that did not run (a whole-module run through build.sh requires all of them): " + missing` (haiku).
- Checked and not defects: finding 3 (`FINAL_EXIT` is built from the unit, ArchUnit and integration exit codes, lines 372 to 425 of `build.sh`); finding 4 (the build step has no switch, and a single step's rerun keeps the volume); finding 5 (the stale stage folders were copied by the old fixed list too; only the order of the `it-mirror` copy changed, integration comes last); finding 6 (the report files keep their modification time through `docker cp`; the timestamps share one format); finding 8 (`PLAYWRIGHT_VERSION` is set at the top of `playwright/run.sh`; a half-finished install leaves no `node_modules/playwright`); finding 10 (the scanner container is brought up before the import loop, so `docker exec` on it works).

Result of plans I, J and the third review (2026-10-30): plan I -- the generator now reads the test and coverage tables from the plain folder and the CI stage folders (`-unit`, `-integration`, `-ci-sonar`), newest entry per id, and the build info from the newest of the plain log and the stage logs; the run-logs scan skips the `shots` folder. Checked: on the host the map has 8 coverage ids and 8 test ids (1554 tests); the same generator run inside `ci-runner` gives identical tables; after the final full CI run (Dagu run `ci-20261030T135618Z-796954-2442026424`, green in 23 min 46 s) the map copied into the project by the run holds the same tables. Plan J (four small fixes of the second review: stale marker removed in the build step, `tests.passed` only for unfiltered runs, the holders of a volume listed on a failed `--reset-cache`, a clearer message of `OperationCoverageTest`) was applied by a haiku agent and checked: `bash -n`, `dagu dry`, shellcheck at severity error, and the final full run. The third review raised six findings; five were fixed (`--shot` without a URL now exits 2 instead of starting the test suite, a missing `shot.js` copy or a failed npm install emits the error marker, `--shot-name` is limited to letters, digits, dot, dash and underscore, the `it-mirror` copy goes oldest folder first, the Sonar own-tests folder is a source of the map tables) and checked on the real script (exit 2 twice, a normal screenshot still saved); one was not a defect (`ci/run.sh` has no `set -e`). Not done: a fourth review after these last fixes, and a CI run after them (they touch `playwright/run.sh`, `ci/run.sh` and the generator, which the shellcheck, `bash -n`, the shot checks and the generator run on the host cover).

Result of the fourth review and the last full run (2026-10-30): the fourth `/code-review` (report only, default effort) raised nine findings; each was checked and none is a defect (the stage folders are created again at the start of every run, so their time is current; the two stages that Sonar imports write differently named files; `/tmp/sonar-src` is removed at the start of every Sonar run; `ci.sh` refuses concurrent runs, so one marker path is enough; the narrower cleanup of Testcontainers containers is the decision of step 1 and `--sandbox` always sets the port). Two small hardenings were made: the shot mode removes an old PNG of the same name in the container before the run, and `build.sh` removes `tests.passed` itself at the start of a run. Checked: `bash -n`, a normal screenshot, and two runs with their own container name (`advertisement-build-only-k3`, folders removed afterwards): `--unit-test AccessEvaluatorTest` leaves no `tests.passed`, a full `--unit` run writes it. The full default `ci.sh --foreground` run before these two one-line hardenings (Dagu run `ci-20261030T172449Z-863700-2019313720`) was green in 23 min 59 s: compile 2 min 5 s, unit 6 min 32 s, integration 8 min 25 s, e2e 17 min 46 s, Sonar 2 min 12 s, ArchUnit 3 min 18 s, docs 1 min 3 s, with `OperationCoverageTest` `tests=1`, `skipped=0` and both `tests.passed` markers; no CI run follows the two last one-line edits. The architecture map in the project was regenerated on the host after them: 8 coverage ids, 8 test ids, 1554 tests, build info of unit and integration, no screenshot folder in the run logs.

### Follow-up plan B -- `OperationCoverageTest` cannot pass by being skipped in a full run (planned 2026-10-29, done 2026-10-30)

Facts checked: the test starts with `assumeTrue(everyScenarioClassRan(), ...)` (`integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/OperationCoverageTest.java` line 45), so a Level 3 scenario class that did not register makes it skipped, which a green build does not show. The class order is already fixed (`integration-tests/src/test/resources/junit-platform.properties` sets `ClassOrderer$OrderAnnotation`; the test has `@Order(Integer.MAX_VALUE)`). The last integration report (2026-10-29 16:04) has the test with `tests=1 skipped=0 failures=0`. A subset is run only through `-Dtest=<names>` (`scripts/build-and-test/build.sh` lines 251-253); a whole-module run has no `test` property. Proposal, not decided: in a run without a `test` filter the missing classes fail the test and are named; with a filter the test is skipped as before.

Design decided 2026-10-30 after reading the code (replaces the proposal above): the test must not read the `test` filter property, because a test run alone from an IDE (no Surefire, no `test` property) would then fail on its own assumption. Instead the build script opts in explicitly: a whole-module integration run through `scripts/build-and-test/build.sh` passes `-Dlevel3.requireCoverage=true` (Maven user properties reach the forked test JVM as system properties, the same way `-Dtest` does; B3 proves it with a negative run), and the test fails, naming the missing classes, only when that property is true. Without the property (a filtered run, an IDE run, a direct `mvn` run) the test is skipped as before.

- B1 -- `scripts/build-and-test/build.sh` (haiku). In `run_integration_tests()`, the block that sets `INTEGRATION_TEST_FLAG` gets a final `else` branch: `INTEGRATION_TEST_FLAG="-Dlevel3.requireCoverage=true"` (so an empty `INTEGRATION_TEST_ARG` means a whole-module run); the `smoke` and named-filter branches stay as they are. One comment line above the `else`: `# A whole-module run must fail, not skip, OperationCoverageTest when a Level 3 scenario class did not run.` Check: `bash -n scripts/build-and-test/build.sh`.
- B2 -- `integration-tests/src/test/java/org/ost/integrationtests/level3/restapi/OperationCoverageTest.java` (haiku). (1) Add `import static org.junit.jupiter.api.Assertions.fail;` next to the `assumeTrue` import. (2) Add the constant `private static final String REQUIRE_COVERAGE = "level3.requireCoverage";` after `ALLOW_LIST`. (3) Replace the first line of the test body, `assumeTrue(everyScenarioClassRan(), "coverage is only meaningful when every Level 3 scenario class ran");`, by `List<String> missing = scenarioClassesNotRun();`, `if (!missing.isEmpty() && Boolean.getBoolean(REQUIRE_COVERAGE)) {`, `    fail("Level 3 scenario classes that did not run: " + missing);`, `}`, `assumeTrue(missing.isEmpty(), "coverage is only meaningful when every Level 3 scenario class ran: " + missing);`. (4) Replace the method `everyScenarioClassRan()` and its one-line Javadoc by `/** Sorted names of the Level 3 scenario classes found on the classpath that did not run in this JVM. */` and `private List<String> scenarioClassesNotRun()` with the same scan, returning `expected.stream().filter(name -> !ran.contains(name)).sorted().toList()`. (5) The class Javadoc line gets the sentence `A whole-module run through build.sh (level3.requireCoverage=true) fails when a scenario class did not run; any other run skips.` Check: the module compiles in the B3 run.
- B3 -- verification (main thread), the machine otherwise idle: (1) `bash scripts/build-and-test.sh --no-unit --integration --skip-vaadin --sandbox` (whole module): green, `OperationCoverageTest` `tests=1 skipped=0` in the report; (2) `bash scripts/build-and-test.sh --no-unit --integration "OperationCoverageTest" --skip-vaadin --sandbox --no-install`: build green, the test skipped; (3) negative: one scenario class temporarily annotated `@Disabled`, whole-module run: the run fails and the failure message names that class; then the annotation is reverted and `git diff --stat` shows only `OperationCoverageTest.java` and `build.sh`; (4) these are two whole integration runs, about 10 minutes each.

Result of plan B (2026-10-30): B1 and B2 were applied by one haiku agent and checked in the main thread (the diff matches the plan, `bash -n` clean). B3 (1): whole-module run `build-and-test.sh --no-unit --integration --skip-vaadin --sandbox` green in 6 min 49 s, 894 tests, 0 failures, `OperationCoverageTest` ran (`Skipped: 0`), and the surefire report of the test lists the system property `level3.requireCoverage=true`, so the Maven property does reach the forked test JVM. B3 (2): the plan wrote the filter as an argument of `--integration`; the real flag is `--integration-test <name>` (the first run took the argument for nothing and ran all 894 tests, green); with `--integration --integration-test OperationCoverageTest ... --no-install` the test is skipped (`Skipped: 1`) and the build is green (1 min 29 s). B3 (3): with `@Disabled` temporarily put on `AdvertisementAuthorizationScenarioTest`, the whole-module run failed (exit 1, 894 tests, 1 failure) with `Level 3 scenario classes that did not run: [org.ost.integrationtests.level3.restapi.AdvertisementAuthorizationScenarioTest]`; the annotation was removed with `git checkout`, `git diff` shows only `OperationCoverageTest.java` and `build.sh` as changed by this plan. The integration report folders on the host now hold the failed run 3 until the next integration run overwrites them.

### Follow-up plan C -- A parameter that wipes the CI's Maven volume (asked for 2026-10-29, planned, nothing executed; starts only after the A6 run has finished)

Facts checked: `build-and-test.sh --reset-cache` already removes the Maven volume (by `MAVEN_CACHE_VOLUME`) **and `vaadin-cache`**, which is shared with manual runs, so it is not used for the CI. `ci.sh --reset-e2e-stack` is the pattern to copy: flag in `scripts/ci/run.sh` (variable line 120, case line 274, `DAGU_PARAMS` line 507), boolean param with `default: false` in `ci.yaml` (shown as a field in Dagu's "Start" dialog). `docker volume rm` refuses a volume that a container still mounts, and the existing reset hides that error (`|| true`); the long-lived `sonar-scanner-ci` container mounts `ci-maven-cache`, so it has to be removed first. Checked 2026-10-29 with a throwaway volume and container: `docker volume rm` answers `volume is in use` (exit 1) while a container that only exists, not even running, mounts it, and works (exit 0) once that container is removed; the DAG logic of the `build` step (`RESET_FLAG` set by `if [ "${params.reset_maven_cache}" = "true" ]`, `$SONAR_SCANNER_CONTAINER` and `$MAVEN_CACHE_VOLUME` read from the top-level `env:`) was run in a probe DAG in `ci-runner`: with `-- reset_maven_cache=true` it printed `FLAG=[--reset-maven-cache] SC=[sonar-scanner-ci] VOL=[ci-maven-cache]`, with the default it printed `FLAG=[]`. Not seen by me: how Dagu's web dialog draws a boolean param (no browser view); the new param is declared exactly like the existing boolean ones (`unit`, `docs`, ...), so it is drawn the same way. **Default is no wipe**: the flag is off and the Dagu param is `false` unless asked.

- C1-C4 -- done 2026-10-29 by one Haiku agent, diff checked in the main thread (`bash -n` clean, `dagu dry` parses `ci.yaml`); `scripts/build-and-test/README.md` needed no change (`--reset-cache` is only a node in its flow diagram). Left: C5, the run that proves the wipe. Original text of C1: `scripts/build-and-test/run.sh` (haiku). New flag `--reset-maven-cache`: (1) after line 125 `RESET_CACHE=false` add `RESET_MAVEN_CACHE=false`; (2) after the case line 148 `--reset-cache)      RESET_CACHE=true ;;` add `    --reset-maven-cache) RESET_MAVEN_CACHE=true ;;`; (3) in the `Usage:` line (14-17) add `[--reset-maven-cache]` and under the `--reset-cache` description (line 20-21) add a flag description: `--reset-maven-cache  wipe only the Maven cache volume (MAVEN_CACHE_VOLUME) before building; vaadin-cache stays`; (4) after the existing `if $RESET_CACHE; then ... fi` block (lines 174-178) add: `if $RESET_MAVEN_CACHE; then` / `  echo "=== Clearing the Maven cache volume (${MAVEN_CACHE_VOLUME:-maven-cache}) ==="` / `  docker volume rm "${MAVEN_CACHE_VOLUME:-maven-cache}" 2>/dev/null || true` / `  if docker volume inspect "${MAVEN_CACHE_VOLUME:-maven-cache}" >/dev/null 2>&1; then echo "ERROR: volume ${MAVEN_CACHE_VOLUME:-maven-cache} is still in use -- remove the containers that mount it and retry" >&2; exit 1; fi` / `fi`. Check: `bash -n`; `grep -n 'RESET_MAVEN_CACHE' scripts/build-and-test/run.sh` shows 4 places.
- C2 -- `scripts/ci/run.sh` (haiku). (1) after line 120 `RESET_E2E_STACK="false"` add `RESET_MAVEN_CACHE="false"`; (2) after the case line 274 `--reset-e2e-stack)     RESET_E2E_STACK="true" ;;` add `    --reset-maven-cache)   RESET_MAVEN_CACHE="true" ;;`; (3) in `DAGU_PARAMS` after `"reset_e2e_stack=$RESET_E2E_STACK"` add `  "reset_maven_cache=$RESET_MAVEN_CACHE"`; (4) in the header flag list after the `--reset-e2e-stack` paragraph (lines 30-33) add `#   --reset-maven-cache      -- wipe the CI's own Maven volume (ci-maven-cache) and remove the Sonar scanner container before the build, so the run starts with no cached dependencies or jars (off by default; the next run downloads every dependency)`. Check: `bash -n`; `grep -n 'RESET_MAVEN_CACHE\|reset_maven_cache' scripts/ci/run.sh` shows 4 places.
- C3 -- `scripts/ci/dagu/ci.yaml` (haiku). (1) after the param `reset_e2e_stack` (its three lines, line 39-41) add `  - name: reset_maven_cache` / `    type: boolean` / `    default: false`; (2) header `Input:` paragraph: after the `reset_e2e_stack ... (only needed when the DB schema itself changed);` text add `reset_maven_cache -- boolean, default false, wipe the CI's Maven volume before the build;`; (3) the `build` step becomes a block: `run: |` / `  RESET_FLAG=""` / `  if [ "${params.reset_maven_cache}" = "true" ]; then` / `    docker rm -f "$SONAR_SCANNER_CONTAINER" >/dev/null 2>&1 || true` / `    RESET_FLAG="--reset-maven-cache"` / `  fi` / `  SESSION_ID=${params.session_id} bash scripts/build-and-test.sh $RESET_FLAG --no-unit --no-integration` (indentation as in the `integration` step). Check: `docker cp` the file into `ci-runner` and `dagu dry` parses it (as in A4).
- C4 -- docs (haiku). `scripts/ci/README.md`: the flag list gets `--reset-maven-cache` with the same wording as in C2 (find the place with `grep -n 'reset-e2e-stack' scripts/ci/README.md`); `.claude/rules/scripts.md` ("Local CI Runner" command list): a line `bash scripts/ci.sh --reset-maven-cache` in the style of its neighbours; `scripts/build-and-test/README.md`: one line next to where `--reset-cache` is described, if it is described there.
- C5 -- verification (main thread, sonnet). Before: put a marker in the CI volume (`docker exec --user root sonar-scanner-ci touch /root/.m2/.reset-marker`) and note the `maven-cache` jar's `stat` and the `CreatedAt` of `vaadin-cache` (`docker volume inspect`). Run `bash scripts/activity-monitor.sh -- scripts/ci.sh --reset-maven-cache --unit --integration --no-archunit-metrics --no-lint --no-shellcheck --no-docs --foreground` with a Monitor on the tree. Pass: the marker is gone; `sonar-scanner-ci` was removed (and not recreated, because sonar did not run); a fresh jar exists in `ci-maven-cache`; `maven-cache` jar `stat` and `vaadin-cache` `CreatedAt` are unchanged; the run is green; the build time of a cold volume is written down. Then a run **without** the flag does not wipe (a new marker survives). The Dagu checkbox: the DAG definition read from Dagu's REST API lists `reset_maven_cache` with `type: boolean`, default `false`.
- Risks: a wipe while another CI run is active would break that run (the plan does not guard it; never start two CI runs at once, as already required); a failed removal now stops the build with a clear error instead of passing silently.

### Follow-up plan D -- Remove the `session_id` parameter from the Dagu form (asked for 2026-10-29; D1 to D4 done 2026-10-30)

Facts checked (read in the code, and one probe DAG run in `ci-runner`): `session_id` was added with the architecture-map "Code Quality" screens (`e20a76d1`). Its only reader is the "Run logs" screen: each log folder gets a `session-info.txt` (written by `scripts/utils/session-id.sh`), the generator (`docs/architecture/scripts/generate-architecture-model.sh` line 1443) reads its `SESSION_ID` and the map prints it as text in the first column (`architecture-map.html` line 2559); where there is no file the column shows `-`. Dagu itself puts the run id into every step as the environment variable `DAG_RUN_ID` (probe on Dagu 2.16.3: `DAG_RUN_ID`, `DAG_NAME`, `DAG_RUN_STEP_NAME`, `DAG_PARAMS_JSON` and others are present in a step). `ci.sh` already gives the run its id (`dagu start -r <id>`, `scripts/ci/run.sh` line 493), so the parameter repeats what the step can read itself. A run started from Dagu's "Start" dialog leaves the field empty, so the scripts then make an id from the clock instead of the Dagu run id. Checked on 2026-10-29 with a probe DAG in `ci-runner` (Dagu 2.16.3) that copies the forms used in `ci.yaml`: started the way `ci.sh` does (`dagu start -r my-fixed-id-123 <file> -- <params>`), `DAG_RUN_ID` in the step is exactly `my-fixed-id-123` in all three forms (inline `SESSION_ID=$DAG_RUN_ID bash -c ...`, a `run: |` block with `export SESSION_ID=$DAG_RUN_ID`, and a line continued with `\` together with `BUILD_CONTAINER_NAME=x`); started without `-r` (as the dialog does), it is Dagu's own id (`034oQrJKC9sNCgQtOItJ5u` in the probe). Dagu does not blank `$DAG_RUN_ID` in a `run:` line. The Dagu REST record of the last real run (`GET /api/v1/dag-runs?name=ci`) lists the param as `session_id=ci-20261029T164718Z-514955-651318672`, equal to the run id, so the param does carry the same value the step can read itself.

- D1 -- `scripts/ci/dagu/ci.yaml` (haiku). (1) Delete the param block at lines 45-47 (`  - name: session_id` / `    type: string` / `    default: ""`). (2) Replace `${params.session_id}` by `$DAG_RUN_ID` at lines 73, 77, 94, 140, 153 (the text `SESSION_ID=${params.session_id}` becomes `SESSION_ID=$DAG_RUN_ID`) and at line 107 (`export SESSION_ID=${params.session_id}` becomes `export SESSION_ID=$DAG_RUN_ID`). (3) Check the header for any sentence that names `session_id` (`grep -n session ...` shows none today); if one appears, reword it to say each step takes the Dagu run id from `DAG_RUN_ID`. Check: `grep -n 'session_id' scripts/ci/dagu/ci.yaml` prints nothing; `grep -c 'DAG_RUN_ID' ...` prints 6; `dagu dry` parses the file in `ci-runner` (as in A4).
- D2 -- `scripts/ci/run.sh` (haiku). Delete the line `  "session_id=$SESSION_ID"` in `DAGU_PARAMS` (line 509). Keep `export SESSION_ID="$DAGU_RUN_ID"` (line 495) and the comment above it, because scripts that run outside Dagu still read `SESSION_ID`. Check: `bash -n scripts/ci/run.sh`; `grep -n 'session_id' scripts/ci/run.sh` prints nothing.
- D3 -- verification (main thread, sonnet). (1) `bash scripts/activity-monitor.sh -- scripts/ci.sh --unit --no-archunit-metrics --no-lint --no-shellcheck --no-docs --foreground`, Monitor on the tree. Pass: green; `scripts/logs/build-and-test/session-info.txt` has `SESSION_ID=` equal to the Dagu run id printed on the second line of `tree.txt`. (2) Optional, a run started the way the Dagu dialog does (no `-r`): in `ci-runner`, `dagu start <ci.yaml path> -- unit=false integration=false e2e=false sonar=false archunit_metrics=false lint=false shellcheck=false docs=false` (the `-- key=value` form is the one `ci.sh` uses); only `build` runs; its `session-info.txt` must hold that run's own Dagu id (printed as `run-id=` by the command), not a clock value. (3) The last run's record in Dagu's REST API (`curl -s localhost:18080/api/v1/dag-runs/ci/<id>` inside `ci-runner`) has no `session_id=` in its `params`.
- D4 -- decision text. `docs/architecture/scripts/DECISIONS.md` item 5 says the session ID is "passed to every Dagu step as a parameter"; after D1-D3 an annotation through `/record-decision` (never a hand edit) says the CI steps read the Dagu run id from `DAG_RUN_ID`; then `bash .claude/nav/scripts/generate-adr-index.sh`.
- Risks: none left open from the earlier assumptions (both checked above); the architecture map's committed copy shows `-` for CI logs until the next generation, as today.

Result of D1 to D3 (2026-10-30): D1 and D2 were applied by one haiku agent and checked in the main thread (diff read, `bash -n` clean, `grep` for `session_id` in `ci.yaml` and `scripts/ci/run.sh` prints nothing, six `DAG_RUN_ID` uses, `dagu dry` succeeds); the agent wrongly named the variable in `scripts/ci/run.sh` `DAG_RUN_ID` in its report, the file itself keeps `export SESSION_ID="$DAGU_RUN_ID"`. D3 (1): `ci.sh --unit --foreground` ran green (8 min 46 s) as Dagu run `ci-20261030T101725Z-628299-642812568`; the `session-info.txt` of the `ci-build`, `unit` and `archunit` folders (inside `ci-runner` and the host copy of the `unit` folder) hold exactly `SESSION_ID=ci-20261030T101725Z-628299-642812568`. D3 (3): the run record in Dagu's REST API has the params `unit=true integration=false reset_e2e_stack=false reset_ci_cache=false e2e_args=... e2e=false sonar=false archunit_metrics=true lint=true shellcheck=true docs=true teardown_e2e_after=false`, no `session_id`. D4 done 2026-10-30: item 5 of the session-ID entry in `docs/architecture/scripts/DECISIONS.md` now says every Dagu step reads the Dagu run id from `DAG_RUN_ID` (one phrase changed in place, no new entry), the ADR index was regenerated. Not done: D3 (2) (a run started without `-r`, the way the dialog does). The architecture map was not inspected for the "Run logs" column.

### Follow-up plan F -- The CI uses only its own volumes, container names and ports, so it can run beside manual runs (decided 2026-10-29; F1 to F7 done 2026-10-29/30; it replaces the `--reset-maven-cache` part of plan C)

Decision of the person: everything the CI uses is its own, so a CI run and a manual run can go in parallel. A single "clear the CI cache" parameter then wipes the whole CI build cache. The SonarQube server is not split now (see the last paragraph).

Facts checked in the code on 2026-10-29. What the CI shared with manual runs and what the plan gives it:
- Volume `vaadin-cache` (Node.js for the Vaadin build, 512 MB; mounted at `scripts/build-and-test/run.sh` line 278) -> the CI gets `ci-vaadin-cache`.
- Volume `test-reports` (test reports of every run; mounted by `scripts/build-and-test/run.sh` line 279, `scripts/sonar/run.sh` line 159, `playwright/run.sh` lines 126 and 131, read by `scripts/ci/run.sh` line 225) -> the CI gets `ci-test-reports`. `scripts/run-all-tests/run.sh` is a manual tool and keeps `test-reports`.
- Container name `advertisement-build-only` is the default of `build-and-test.sh`; the CI `build` step runs with that default, so a manual build and the CI build remove each other's container (`docker rm -f` of that name before each run) -> the CI build step gets `advertisement-build-only-ci-build`. The unit, integration and archunit stages already have their own names (`-unit`, `-integration`, `-archunit`).
- Container name `advertisement-build-only-sonar` is fixed in `scripts/sonar/run.sh` line 175, so a manual `sonar.sh` and the CI Sonar stage (own tests) collide -> `SONAR_BUILD_CONTAINER_NAME` variable, the CI uses `advertisement-build-only-ci-sonar`.
- Postgres port 25432 is used by manual integration runs and by the Sonar stage when it runs its own tests (the CI `integration` stage already has 25433) -> the CI Sonar stage gets 25434.
- Already separate: `ci-maven-cache`, `sonar-scanner-ci`, the `ci-*` e2e stack and `ci-pw-runner`, the Dagu volumes.

Parameter: one `reset_ci_cache` (`ci.sh --reset-ci-cache`, a checkbox in Dagu's "Start" dialog, off by default) wipes `ci-maven-cache` and `ci-vaadin-cache`. It does not wipe `ci-test-reports` (results, not cache) or `ci-dagu-home` (run history). It reuses `build-and-test.sh --reset-cache`, which now wipes the two volumes named by `MAVEN_CACHE_VOLUME` and `VAADIN_CACHE_VOLUME`; the flag `--reset-maven-cache` added by plan C is removed again, and a volume that a container still mounts now stops the build with an error (it was ignored before). For manual runs `--reset-cache` keeps wiping `maven-cache` and `vaadin-cache`.

- F1 -- `scripts/build-and-test/run.sh` (haiku). (1) Remove what plan C added: the variable `RESET_MAVEN_CACHE=false`, the case line `--reset-maven-cache)`, `[--reset-maven-cache]` in the `Usage:` line, the two header lines describing it, and the whole block `if $RESET_MAVEN_CACHE; then ... fi`. (2) Replace the block `if $RESET_CACHE; then ... fi` by: `if $RESET_CACHE; then` / `  echo "=== Clearing build caches (${MAVEN_CACHE_VOLUME:-maven-cache}, ${VAADIN_CACHE_VOLUME:-vaadin-cache}) ==="` / `  docker volume rm "${MAVEN_CACHE_VOLUME:-maven-cache}" "${VAADIN_CACHE_VOLUME:-vaadin-cache}" 2>/dev/null || true` / `  for volume in "${MAVEN_CACHE_VOLUME:-maven-cache}" "${VAADIN_CACHE_VOLUME:-vaadin-cache}"; do` / `    if docker volume inspect "$volume" >/dev/null 2>&1; then` / `      echo "ERROR: volume $volume is still in use -- remove the containers that mount it and retry" >&2` / `      exit 1` / `    fi` / `  done` / `fi`. (3) Line 278 `-v vaadin-cache:/root/.vaadin \` becomes `-v "${VAADIN_CACHE_VOLUME:-vaadin-cache}:/root/.vaadin" \`; line 279 `-v test-reports:/reports \` becomes `-v "${TEST_REPORTS_VOLUME:-test-reports}:/reports" \`. (4) Header: the `--reset-cache` description says it wipes the Maven cache volume and the Vaadin cache volume and stops with an error when a container still mounts one; the `Env:` paragraph names `VAADIN_CACHE_VOLUME` (default vaadin-cache) and `TEST_REPORTS_VOLUME` (default test-reports) next to `MAVEN_CACHE_VOLUME`. Check: `bash -n`; `grep -n 'RESET_MAVEN_CACHE\|reset-maven-cache' scripts/build-and-test/run.sh` prints nothing.
- F2 -- `scripts/sonar/run.sh` and `playwright/run.sh` (haiku). `scripts/sonar/run.sh`: line 159 `-v test-reports:/reports` becomes `-v "${TEST_REPORTS_VOLUME:-test-reports}:/reports"`; line 175 becomes `SONAR_BUILD_CONTAINER_NAME="${SONAR_BUILD_CONTAINER_NAME:-advertisement-build-only-sonar}"`; the `Env:` paragraph names both variables with their defaults. `playwright/run.sh`: both `-v test-reports:/reports` (lines 126 and 131) become `-v "${TEST_REPORTS_VOLUME:-test-reports}:/reports"`; its `Env:` paragraph names `TEST_REPORTS_VOLUME` (default test-reports). Check: `bash -n` on both; `grep -n 'test-reports' ` shows only defaults and comments.
- F3 -- `scripts/ci/run.sh` (haiku). (1) Near the top (after the `source` lines) add `CI_TEST_REPORTS_VOLUME="ci-test-reports"` with one comment line `# Same volume as TEST_REPORTS_VOLUME in scripts/ci/dagu/ci.yaml; this script runs on the host and cannot read that file.` (2) Line 225 `-v test-reports:/reports` becomes `-v "$CI_TEST_REPORTS_VOLUME:/reports"`. (3) In the sync loops (lines 226 and 238) the name `advertisement-build-only-sonar` becomes `advertisement-build-only-ci-sonar`, and the comment on line 87 the same. (4) Rename plan C's flag: variable `RESET_MAVEN_CACHE` -> `RESET_CI_CACHE`, case `--reset-maven-cache)` -> `--reset-ci-cache)`, param `"reset_maven_cache=$RESET_MAVEN_CACHE"` -> `"reset_ci_cache=$RESET_CI_CACHE"`, and the header text says: `--reset-ci-cache -- wipe the CI's own build caches (ci-maven-cache and ci-vaadin-cache) and remove the Sonar scanner container before the build, so the run starts with no cached dependencies, jars or Node.js (off by default; the next run downloads them again)`. Check: `bash -n`; `grep -n 'RESET_MAVEN_CACHE\|maven_cache' scripts/ci/run.sh` prints nothing.
- F4 -- `scripts/ci/dagu/ci.yaml` (haiku). (1) Top-level `env:` gets three more items: `VAADIN_CACHE_VOLUME=ci-vaadin-cache`, `TEST_REPORTS_VOLUME=ci-test-reports`, `SONAR_BUILD_CONTAINER_NAME=advertisement-build-only-ci-sonar`; the header `Env:` paragraph lists all five variables. (2) Param `reset_maven_cache` -> `reset_ci_cache` (name and the header text, wording as in F3). (3) `build` step: `BUILD_CONTAINER_NAME=advertisement-build-only-ci-build` is added in front of `bash scripts/build-and-test.sh`, `RESET_FLAG="--reset-maven-cache"` becomes `RESET_FLAG="--reset-cache"`, and the `if` tests `${params.reset_ci_cache}`; the `docker rm -f "$SONAR_SCANNER_CONTAINER"` line stays. (4) `sonar` step: before `bash scripts/sonar.sh` add `export INTEGRATION_TESTS_POSTGRES_FIXED_PORT=25434` (own Postgres port for the case where this stage runs its own tests), and the step comment above says so. (5) The header paragraph about the `sonar` stage (added earlier) gets the sentence that the CI Sonar stage uses port 25434. Check: `dagu dry` in `ci-runner` parses it (as in A4); `grep -n 'maven_cache' scripts/ci/dagu/ci.yaml` prints nothing.
- F5 -- docs (haiku for wording). `scripts/ci/README.md`: the paragraph added by plan C is rewritten for `--reset-ci-cache` (what it wipes and does not wipe), and the "Can I develop and run the app/tests myself while this runs?" bullet lists the CI's own volumes (`ci-maven-cache`, `ci-vaadin-cache`, `ci-test-reports`), container names and ports, with the one exception below; `.claude/rules/scripts.md`: the `--reset-maven-cache` command entry becomes `--reset-ci-cache`, and the sentence about `MAVEN_CACHE_VOLUME`/`SONAR_SCANNER_CONTAINER` names all the variables. The architecture-map generator reads the host copies of the report folders by the prefix `advertisement-build-only` (`docs/architecture/scripts/generate-architecture-model.sh` line 1370), so the renamed Sonar folder keeps being found; F6 checks it.
- F6 -- verification (main thread, sonnet), all after removing the two containers that were created with the old mounts (`docker rm -f sonar-scanner-ci ci-pw-runner`, they are recreated by the run). (1) `bash scripts/activity-monitor.sh -- scripts/ci.sh --unit --integration --sonar --no-archunit-metrics --no-lint --no-shellcheck --no-docs --foreground`: green, `ci-vaadin-cache` and `ci-test-reports` exist with content, a build container inspected during the run mounts `ci-*` volumes only. (2) Parallel proof: while that run is in its unit/integration stages, start `bash scripts/build-and-test.sh --unit --no-integration` by hand (default names, `maven-cache`); both finish green, and the CI `ci-maven-cache` jar and the manual `maven-cache` jar each change only in their own run. (3) Wipe proof: a marker file in `ci-maven-cache` and one in `ci-vaadin-cache`, then `ci.sh --reset-ci-cache --unit --integration ...`: both markers gone, the run green, `maven-cache`, `vaadin-cache` and `test-reports` untouched (jar `stat`, volume `CreatedAt`); a run without the flag keeps a new marker. (4) One full default run (all stages, e2e and docs included): green, and the architecture map's Code Quality data (coverage, tests, run logs) is still generated. (5) The Dagu "Start" dialog shows `reset_ci_cache` as a checkbox, off (`bash scripts/playwright.sh --shot ... --shot-click Start`).
- F7 -- decision text (main thread, with the person's yes): `/record-decision` entries for `scripts/ci` (the CI uses only its own volumes, container names and ports; one `reset_ci_cache` parameter) and for `scripts/sonar` (the SonarQube server and the project `advertisement` are shared between CI and manual runs by decision), then `bash .claude/nav/scripts/generate-adr-index.sh`.

Result of F1 to F6 (2026-10-29 and 2026-10-30), what was really checked:
- F1 to F5 were applied by one haiku agent and checked in the main thread: the diff of every file was read, `bash -n` is clean on the four scripts, `grep` for `reset-maven-cache`, `RESET_MAVEN_CACHE` and `reset_maven_cache` in `scripts`, `playwright` and `.claude` prints nothing, all files are staged. Two corrections were made by hand after that check: the pre-run cleanup line of `scripts/build-and-test/run.sh` (`rm -rf /reports/$BUILD_CONTAINER_NAME`) still mounted the manual `test-reports` volume and now mounts `${TEST_REPORTS_VOLUME:-test-reports}`; and three header lines ended with a stray `;`.
- F6 (1) and (2): run 1, `ci.sh --unit --integration --sonar --foreground`, green; compile 4 min 11 s, unit 5 min 28 s, integration 10 min 19 s, Sonar 3 min 0 s (imported the JaCoCo reports, ran no tests). `ci.sh` has no `--no-*` flags; a stage set is chosen by listing the stages. While its integration stage ran, `build-and-test.sh --unit --no-integration` ran by hand: green, 8 min 31 s. After the run the mounts of the leftover containers were read: `sonar-scanner-ci` mounts `ci-maven-cache` and `ci-test-reports`, `ci-pw-runner` mounts `ci-test-reports`, the manual `sonar-scanner` mounts `maven-cache` and `test-reports`. `ci-test-reports` and `ci-vaadin-cache` were created at the start of that run. Not done: a build container was not inspected during the run, the jar `stat` comparison was not made.
- F6 (3): a marker file was put into `ci-maven-cache`, `ci-vaadin-cache`, `maven-cache`, `vaadin-cache`, `test-reports` and `ci-test-reports`; `ci.sh --reset-ci-cache --unit --foreground` ran green (compile 2 min 54 s, unit 2 min 36 s, ArchUnit 1 min 21 s, docs 1 min 0 s); afterwards the markers were gone in `ci-maven-cache` and `ci-vaadin-cache` and still present in the other four. The markers were removed again. Not done: the run without the flag keeping a new marker, and the run with `--integration`.
- F6 (4): one full default run, `ci.sh --foreground`, green in 22 min 53 s: compile 1 min 55 s, unit 5 min 46 s, integration 8 min 26 s, e2e 18 min 7 s, Sonar 2 min 27 s, ArchUnit 2 min 40 s, docs 1 min 4 s. The docs stage passed; the content of the generated architecture map (coverage, tests, run logs) was not inspected.
- F6 (5): the "Start" dialog (`scripts/logs/playwright/shots/dagu-start-f6.png`) shows 13 fields, `reset_ci_cache` is a checkbox and is off, `reset_maven_cache` is gone; `session_id` is still there (plan D).
- F7 was done afterwards, see the end of plan G.

### Follow-up plan G -- Fixes from the review of plan F (review and documentation sync run 2026-10-30, planned, nothing executed)

The review (`/code-review` on the staged diff, report only, default effort) raised nine findings; each was checked against the code. Three are confirmed defects and are fixed here; three are valid but unreproduced or low and go to `improvement-133` (the deferred-findings bucket); one was disproven; two are not defects.
- Confirmed, fixed in G1 to G3: (1) a manual `build-and-test.sh --reset-cache` now stops with "volume maven-cache is still in use" whenever the long-lived `sonar-scanner` container (which mounts `maven-cache`) exists, which is after every Sonar run; (3) the `docs` step passes `--no-sonar-rescan` whenever the `sonar` param is true, even when the Sonar stage failed, so the architecture map would embed the analysis of an earlier run; (4) in `playwright/run.sh` `--shot` mode the `npm install ... | grep -v` pipeline returns grep's exit code, so a failed install is not reported.
- To `improvement-133` (G4): (2) a Sonar stage that runs its own tests (not both unit and integration selected) reinstalls the reactor into `ci-maven-cache` and can race with the e2e stage's jar copy -- only when sonar and e2e are selected without unit and integration, existed before plan F; (7) the JaCoCo import accepts a partial report when a test stage failed after writing some XML (the stage itself is red, so the run fails anyway); (8) `ci-test-reports` and `advertisement-build-only-ci-sonar` are written both in `ci.yaml` and in `scripts/ci/run.sh` (`sync_artifacts`), a rename in one place silently copies nothing.
- Disproven or not defects: (5) `networkidle` in `playwright/shot.js` -- two real screenshots of the Dagu UI succeeded in 9 s; (6) the Testcontainers cleanup by the run's own port is the decision of step 1; (9) multi-line script headers follow `infra-doc-standards`.

- G1 -- `scripts/build-and-test/run.sh` (haiku). In the `if $RESET_CACHE; then` block, before the `docker volume rm` line, add `docker rm -f "${SONAR_SCANNER_CONTAINER:-sonar-scanner}" >/dev/null 2>&1 || true` (the scanner container is stateless, `sonar.sh` recreates it). Header: the `--reset-cache` description says it also removes the Sonar scanner container (`SONAR_SCANNER_CONTAINER`, default `sonar-scanner`) because that container mounts the Maven volume; the `Env:` paragraph names `SONAR_SCANNER_CONTAINER`. `scripts/ci/dagu/ci.yaml`: in the `build` step delete the line `docker rm -f "$SONAR_SCANNER_CONTAINER" >/dev/null 2>&1 || true` (the script now does it). Check: `bash -n`; `dagu dry` of `ci.yaml`; a manual `bash scripts/build-and-test.sh --reset-cache --no-unit --no-integration` while the `sonar-scanner` container exists finishes without the "still in use" error and `docker ps -a` no longer lists `sonar-scanner` (this wipes the manual `maven-cache` and `vaadin-cache`: the next manual build downloads everything again; the run needs the person's yes).
- G2 -- `scripts/ci/dagu/ci.yaml` (haiku). `sonar` step: first line `rm -f /tmp/ci-sonar-ok`, last line (after `bash scripts/sonar.sh`) `touch /tmp/ci-sonar-ok`. `docs` step: replace `[ "${params.sonar}" = "true" ] && SONAR_FLAG="--with-sonar --no-sonar-rescan"` by `[ "${params.sonar}" = "true" ] && SONAR_FLAG="--with-sonar"` followed by `[ -f /tmp/ci-sonar-ok ] && SONAR_FLAG="$SONAR_FLAG --no-sonar-rescan"`; one comment line above says the rescan is skipped only when this run's Sonar stage passed. Check: `dagu dry`; the `docs` block run by hand in `ci-runner` with and without the marker prints the expected `SONAR_FLAG` (`echo` instead of the generator).
- G3 -- `playwright/run.sh` (haiku). In `--shot` mode the install line becomes `docker exec "$PW_CONTAINER" bash -c "cd /shot && { [ -d node_modules/playwright ] || npm install --no-save -q playwright@$PLAYWRIGHT_VERSION; }"` (no `grep` pipe), and the next line stops with the install's exit code when it is not 0 (`|| exit $?` placed after that `docker exec`). Check: `bash -n`; `docker exec pw-runner rm -rf /shot/node_modules`, then `bash playwright/run.sh --shot http://localhost:8082/ --shot-name g3-check` installs and writes the PNG.
- G4 -- `backlog/tasks/improvement-133-*.md` (main thread, with the person's yes): three new entries (2), (7), (8) above, in that file's own entry format.

Result of G1 to G4 (2026-10-30):
- G1 to G3 were applied by one haiku agent and checked in the main thread: the diff was read and matches the plan, `bash -n` is clean, `dagu dry` of `ci.yaml` succeeds. The agent reported one flaw in G2 that was real and fixed by hand: the `--no-sonar-rescan` line did not test the `sonar` param, so a stale marker with `sonar` off would pass a lone `--no-sonar-rescan`; it now tests `${params.sonar}` first.
- G1 checked: `bash scripts/build-and-test.sh --reset-cache --no-unit --no-integration` with the `sonar-scanner` container present finished with exit code 0 (3 min 4 s), the container is gone, `maven-cache` and `vaadin-cache` were created again at the start of that run and `test-reports` was not touched. This wiped the manual caches.
- G2 checked: the `docs` flag block run in the host shell for the four combinations of `sonar` (true/false) and marker (present/absent) gives `--with-sonar --no-sonar-rescan`, `--with-sonar`, and an empty flag twice. Not checked: the `docs` stage in a real CI run, and a run in which the Sonar stage fails.
- G3 checked: after `docker exec pw-runner rm -rf /shot/node_modules`, `bash playwright/run.sh --shot http://localhost:8082/ --shot-name g3-check` installed the 2 packages and wrote the PNG. Not checked: a failing install.
- G4 done: entries 34, 35 and 36 added to `improvement-133`.
- F7 done 2026-10-30 (with the person's yes to the drafted text): `scripts/ci/DECISIONS.md` got ADR-016 (the DAG builds the reactor once, Sonar imports the reports) and ADR-015 (the CI's own volumes, names and ports, `reset_ci_cache`); `scripts/sonar/DECISIONS.md` got ADR-011 (the SonarQube server and project are shared by decision). Four older entries got a one-line `Status` pointer: `scripts/ci` ADR-005 and ADR-008 are `Superseded`, ADR-009 and ADR-010 are `Accepted` with the revised statement named; `scripts/sonar` ADR-010 names the report-import mode. `.claude/nav/adr-index.md` was regenerated. The generated architecture map was not regenerated for these entries.
- Open: the open items of task 186 (plans D and B are done, entry 33 of `improvement-133` was removed).

**Deliberately left shared (decision of 2026-10-29): the SonarQube server and its project `advertisement`.** A CI Sonar run and a manual `sonar.sh` upload their analysis into the same project, so the later one overwrites the earlier one's result, and two Sonar runs at the same time are not supported. A separate project key for the CI would create a second project and a second quality gate; this is not done now, and the idea is kept as entry 7 of `improvement-223` (the ideas collection) for later. Everything else in the CI can run beside a manual run once plan F is done.

### Not in this plan (left for the person or later)

- Giving the CI its own Maven volume (decision 4) -- now planned above as follow-up plan A.
- Stopping the developer stack during a run (decision 5; decide after step 7's numbers).
- More Playwright workers: the project rule and https://playwright.dev/docs/test-parallel both keep 2.
- Parallel JUnit classes in `integration-tests`: no source gives a measured gain, and the classes share one database (https://docs.docker.com/guides/testcontainers-java-lifecycle/singleton-containers).
- Serializing stages with Dagu `max_active_steps`: only if step 9 shows timeouts from contention.
- `html-sanitizer-lib` and `platform-commons` coverage reports are not listed in `sonar.coverage.jacoco.xmlReportPaths`. This is a separate finding, outside this goal.

## To decide at the end of this task: where to record the explanation style

The user confirmed this style for plans and explanations (2026-10-29): short, factual, plain words, exact names for things.
- Order: what exists now (only verified facts), what the problem is, what changes (one or two sentences), which goals it reaches (a numbered list), a real and checked edge case if there is one, one short question.
- Exact terms: a container with Postgres is not a "database"; say what a command really does ("removes all test containers on the machine"), not a coined word like "cleanup".
- No guesses presented as facts, no invented side effects, no filler.
- Open: whether to record it in the project rules (`.claude/rules.md`, as a standing rule on how plans are written), in the assistant's memory, or in both. Nothing is recorded anywhere yet; the user decides when the task ends.

## Operational notes
- token_cost_review: 307616
- token_cost_research: 227311
- token_cost_verification: n/a
- review_signal_ratio: 14 / 34
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: find why the local CI is slow and plan the fix in depth
- flows_chosen: /deep-research
- flows_matched: yes
- haiku_decomposition_used: n/a
- haiku_decomposition_dispatch_count: n/a
- haiku_decomposition_rework_needed: n/a
- adrs_referenced: ADR-015 (scripts/ci), ADR-016 (scripts/ci), ADR-011 (scripts/sonar)

### Agent calls
- CI own volumes, names and ports (F1 to F5) | subagent_type=general-purpose (model haiku) | tokens=181044 | tool_uses=50 | duration_s=5031 | mode=background | batch=solo
- session_id parameter removal (D1, D2) | subagent_type=general-purpose (model haiku) | tokens=101784 | tool_uses=7 | duration_s=36 | mode=background | batch=solo
- OperationCoverageTest requireCoverage (B1, B2) | subagent_type=general-purpose (model haiku) | tokens=90808 | tool_uses=11 | duration_s=42 | mode=background | batch=solo
- fixes of the first review (G1 to G3) | subagent_type=general-purpose (model haiku) | tokens=99552 | tool_uses=15 | duration_s=63 | mode=background | batch=solo
- entries 34 to 36 fixed (H1 to H4) | subagent_type=general-purpose (model haiku) | tokens=109231 | tool_uses=24 | duration_s=97 | mode=background | batch=solo
- map generator reads the CI stage folders (I1) | subagent_type=general-purpose (model haiku) | tokens=87213 | tool_uses=12 | duration_s=81 | mode=background | batch=solo
- map generator build info and shots folder | subagent_type=general-purpose (model haiku) | tokens=83288 | tool_uses=7 | duration_s=42 | mode=background | batch=solo
- fixes of the second review (J1 to J4) | subagent_type=general-purpose (model haiku) | tokens=90744 | tool_uses=13 | duration_s=48 | mode=background | batch=solo
- fixes of the third review (K1 to K5) | subagent_type=general-purpose (model haiku) | tokens=122977 | tool_uses=19 | duration_s=79 | mode=background | batch=solo
- two small hardening edits | subagent_type=general-purpose (model haiku) | tokens=85070 | tool_uses=6 | duration_s=44 | mode=background | batch=solo
- second code review | subagent_type=code-review | tokens=92191 | tool_uses=8 | duration_s=91 | mode=background | batch=solo
- third code review | subagent_type=code-review | tokens=117580 | tool_uses=8 | duration_s=108 | mode=background | batch=solo
- fourth code review | subagent_type=code-review | tokens=97845 | tool_uses=9 | duration_s=166 | mode=background | batch=solo
(the first review and the agent calls made before the context of this work was reset are not listed: their usage was not captured; the review token total above is the three listed reviews)

### Script/command runs
- bash scripts/ci.sh --foreground (full default run) | duration_s=1373 | mode=background | result=pass
- bash scripts/ci.sh --foreground (full default run, after the map generator fix) | duration_s=1426 | mode=background | result=pass
- bash scripts/ci.sh --foreground (full default run, last run) | duration_s=1439 | mode=background | result=pass
- bash scripts/ci.sh --sonar --foreground | duration_s=728 | mode=background | result=pass
- bash scripts/ci.sh --unit --foreground | duration_s=526 | mode=background | result=pass
- bash scripts/deploy-and-run.sh --reset-only-db | duration_s=154 | mode=background | result=pass
- bash scripts/build-and-test.sh --no-unit --integration --skip-vaadin --sandbox | duration_s=409 | mode=background | result=pass
