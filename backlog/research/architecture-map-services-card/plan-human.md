# Plan for people: architecture-map-services-card

Purpose: the result for the person who decides: clear, short and easy to follow.

## Goal

Add a "Services" card to the System screen of the architecture map. It opens a screen with one card
per local web page (the app, Swagger UI, the API flow runner page, SonarQube, Dagu, the SeaweedFS
master UI); each card opens its page in a new tab. At the same time, the host ports that are still
written as numbers inside compose files and scripts (9099, 8082, 9333, and the app's 8081) move into
the repo-root `.env`, so the map and the scripts read every address from that one file.

## The problem this solves

To open a local tool today you have to remember its port or look it up in `INFRASTRUCTURE.md`. The
ports are numbers repeated in several files (the SonarQube port alone sits in 7 places: its compose
file, `scripts/sonar/run.sh` twice, `sonar-project.properties`, the sonar-analyst agent and its
launcher, and the map generator), and nothing keeps those copies in step. After the change, a port
is written once in `.env`; the scripts, the compose files and the map read it from there.

## The means needed

- `.env` gets four new lines: `APP_PORT`, `SEAWEEDFS_CONSOLE_PORT`, `SONAR_PORT`, `DAGU_UI_PORT`.
  The names are the ones the scripts already use, so the CI pipeline's own port overrides keep
  working with no change.
- The files that hold these numbers read them back from `.env`:
  - deploy: the SeaweedFS compose file and `scripts/deploy-and-run/run.sh`;
  - CI: `scripts/ci/run.sh` and the Dagu run monitor;
  - SonarQube: its compose file, `scripts/sonar/run.sh`, the shared token helper,
    `sonar-project.properties`, and the sonar-analyst agent with its launcher.
  The shell scripts keep their current number as a last-resort default after `.env`. That is how
  the project already handles the database and storage ports.
- The map generator reads the four ports from `.env` and writes a `services` list into
  `architecture-model.json`. The page only shows what that list says. The generator also takes its
  own SonarQube address from `.env` instead of its hardcoded `http://localhost:9099`.
- The map gets the sixth System card, the Services screen, and a "Services" step in the breadcrumb.
  The screenshot script gets one more screenshot of the new screen.
- Documentation: `docs/architecture/README.md`, `.claude/rules/scripts.md` (the manual SonarQube
  start command) and the `INFRASTRUCTURE.md` editing procedure in the app-readme-standards skill
  are updated. The decision record is updated through `/record-decision`, as described under the
  decisions below.

No new tool, dependency, data file or setting is added. The list of services (name, what it is, the
page path, the script that starts it, any condition) is one small table inside the generator,
because the generator already keeps similar fixed text.

## What the result will look like

On the System screen, a sixth blue card, "Services", sits next to Code Quality. It opens a screen
with six cards, one per page. Each card shows the name, one line about what the page is, its address
(for example `http://localhost:9099/dashboard?id=advertisement`), the script that starts it, and,
for the API flow runner, that it exists only with `--dev-tools`. A click opens the page in a new
tab. At the bottom, a "Source: .env" line links to the file the ports come from. A page that is not
running simply does not open: the cards show no live status, as already decided.

To change a port later, you edit one line in `.env` and regenerate the map. The scripts pick up the
new port on their next run.

## Cost

| Part | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| `.env`, deploy, CI, SonarQube and agent edits, screenshot script, docs (7 steps) | haiku | small, exact edits | about 200-300k in total |
| Generator: `services` list and the new screen (1 step) | sonnet | the only step that needs judgment inside a 4,400-line file | about 150-250k |
| Regenerate the map, run the checks | haiku | runs scripts | about 30k |
| Decision record through `/record-decision` | main thread (opus) | needs your approval | about 50-80k |
| Final checks: deploy, unit and integration tests, compose checks | scripts | about 30-40 minutes of machine time | about 20-40k (watching only) |

Overall: about half a day, most of it waiting for scripts and for your approvals. Running a full
SonarQube scan or rebuilding the CI runner is optional, adds 15-30 minutes, and costs almost no
tokens.

## Risks

- **SonarQube scan history.** To read `.env`, the SonarQube compose file must be started from the
  repo root. Done naively, that renames its Docker project and leaves the existing scan-history
  volumes behind. The plan pins the project name (`name: sonar`) so the same volumes stay in use,
  and the final check confirms the running container still belongs to the `sonar` project.
- **Dagu UI port.** A new `DAGU_UI_PORT` takes effect only when the CI runner container is
  recreated (`bash scripts/ci.sh --rebuild`). With the port left at 8082 nothing changes.
- **Line endings.** The working tree uses Windows line endings in places, so every new `.env`
  reader strips the trailing carriage return, as the existing readers do.
- **Map freshness gate.** The committed `architecture-model.json` and `architecture-map.html` must
  be regenerated in the same change, or the docs stage of CI reports them as stale.
- **The IDE dev-mode compose command** shown in root `CLAUDE.md` and `INFRASTRUCTURE.md` runs
  without `--project-directory`. According to `.claude/rules/scripts.md`, that already leaves
  `DB_PORT`/`S3_PORT` empty. The new SeaweedFS variable is affected the same way. This is an
  existing problem outside this task, and the plan does not fix it.

## Where the project's practice differs from best practice

1. **Last-resort numbers in scripts.** The project's way (scripts ADR-009) is: explicit variable
   first, `.env` next, the old number last. A strict "one home per fact" would drop the number and
   fail when `.env` is missing. The plan keeps the project's way. Cost of the project's way: the
   number still appears once more in each script, unused while `.env` exists.
2. **How the SonarQube compose file finds `.env`.** The project's way for compose files outside the
   root is `--project-directory`. Here it needs one extra line (`name: sonar`) to keep the scan
   history. The plan follows the project's way. Alternative: `--env-file "$ROOT/.env"`. It is
   smaller (no `name:` line) but different from how the other compose files are run.
3. **Copies of the addresses in documentation.** The project's precedent (scripts ADR-009) leaves
   port numbers in prose, and the app-readme-standards skill requires `INFRASTRUCTURE.md`'s
   services table with a link per row. The plan keeps the table and makes the skill's editing
   procedure read ports from `.env`. A strict "one home" would make the table name the `.env` key
   and point to the map's Services screen. This matters more here because `INFRASTRUCTURE.md` is
   itself shown inside the map, so the same addresses would appear twice in one tool.
4. **Recording the card in the decision log.** The brief asks to annotate or supersede the System
   page decision (docs/architecture/scripts ADR-003, whose title counts "3 entry-point cards").
   `/record-decision` forbids appending amendment sections to an old entry, and says a change to
   the map's own card layout is not worth a record. The two earlier cards (ADRs and Code Quality)
   each got a new record and left ADR-003 unchanged.

## Decisions waiting for you

1. **OpenAPI JSON:** (a) mentioned on the Swagger card only, six cards; or (b) a card of its own,
   seven cards. The plan uses (a) as a placeholder.
2. **How a card states a condition** such as "only with `--dev-tools`": (a) a separate highlighted
   "Only when …" line plus a "Started by" line on every card; (b) only the "Started by" line, which
   already reads `scripts/deploy-and-run.sh --dev-tools`; (c) two groups on the screen, "always on
   with deploy" and "started on demand". The plan uses (a) as a placeholder.
3. **Card wording.** A draft for all six cards is in `plan-machine.md`, step 6. Change it as you
   like before execution.
4. **Decision records:**
   - Ports: (a) extend scripts ADR-009, the `.env` decision this continues; or (b) a new record that
     supersedes it.
   - System card: (a) supersede ADR-003 with a version that does not count the cards; (b) reword
     ADR-003's title in place to drop the count; (c) leave ADR-003 as it is, as was done for the
     ADRs and Code Quality cards.
5. **`INFRASTRUCTURE.md` and the other prose copies of the addresses:** (a) keep them, with the
   skill procedure reading `.env` (the plan's default, the project's practice); or (b) make the
   services table and the startup table name the `.env` key and point to the map's Services screen.
   Under (b), the other copies in `.claude/rules/scripts.md`, `.claude/commands/sonar.md` and
   `ci.md`, the Dagu and Sonar agent files, `scripts/ci/README.md` and the root `README.md` are
   decided the same way.
6. **SonarQube compose:** keep the plan's `--project-directory` plus `name: sonar` (the project's
   practice), or use `--env-file`.
7. **App port in client tools** (outside the brief's list): the Playwright runner and config and the
   API-collection test config each default to `http://localhost:8081`, and so does the shipped
   Postman collection. Leave them (the plan's default; all of them can already be pointed at another
   address), or make the Playwright and API-collection runners read `APP_PORT` from `.env` too.
