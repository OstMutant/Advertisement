# improvement-228: Services section on the architecture map's Tooling & Pipelines screen, built from INFRASTRUCTURE.md

**Type:** improvement — new section on an existing generated page
**Module:** `docs/architecture/scripts/generate-architecture-model.sh` (`renderSystem()`, `renderCodeQuality()`, model building), `INFRASTRUCTURE.md` (the "Local Services" table), `docs/architecture/data/architecture-model.json` and `docs/architecture/architecture-map.html` (generated)
**Priority:** 🟡 Top — first in the backlog, at the user's request (2026-10-26).
**When:** independent. The static-reports task [improvement-229](improvement-229-architecture-map-static-reports.md) changes the same generator file; do not run the two at the same time.

## Current state

- The System screen of the architecture map is `renderSystem()` in `docs/architecture/scripts/generate-architecture-model.sh`: five entry cards (Tooling & Pipelines, Backlog, Diagrams, ADRs, Code Quality) and the root README below them.
- `INFRASTRUCTURE.md` is already shown on the map, as markdown at the bottom of the Tooling & Pipelines screen (the model carries its text as `rootInfrastructure`).
- Its "Local Services" table has five rows (PostgreSQL, SeaweedFS, Application, SonarQube, Dagu) with columns Service, Container, Local link, Solves. Only Application, SonarQube and Dagu carry a clickable link; PostgreSQL and SeaweedFS show plain addresses, and the SeaweedFS cell holds two (master UI `localhost:9333`, S3 API `localhost:9000`).
- Swagger UI is in a separate small table higher in the same file; the API flow runner page (`/dev-tools/api-flows/`, only with `deploy-and-run.sh --dev-tools`) appears only in a flag example.
- The Code Quality screen has a card named "SonarQube" that opens per-module metrics (complexity, cognitive complexity, code smells) taken from SonarQube; it is a selection of data, not the service.

## Why change

A reader of the map has to know or look up where each running tool listens, and the addresses on the map are plain text that does not click through.

## Expected benefit

One section on the System screen with a card per local web page, each opening in a new tab, built from the table that already documents these services, so there is no second copy of the addresses.

## Approach

Decided with the user (2026-10-26):

1. `INFRASTRUCTURE.md`'s "Local Services" table stays the single home of the addresses. The generator reads its rows at generation time, writes them as a `services` list into `architecture-model.json`, and the map renders one card per row in a new "Services" section on the System screen. A card opens its page in a new tab.
2. The table gets the rows it lacks: Swagger UI and the API flow runner page (with its "only with `--dev-tools`" condition). The SeaweedFS cell is split so the master UI is its own link. Rows that are not web pages (PostgreSQL, the S3 API) are marked so the generator skips them; only web pages get a card.
3. No live status; the cards are plain links (a browser page cannot probe other ports because of CORS, and a generation-time probe goes stale at once).
4. The Code Quality card "SonarQube" is renamed "Sonar Metrics"; its description stays.
5. Out of scope: the CI stack ports (18081, 19333), the Playwright report and the other static reports (that is improvement-229), and moving ports into `.env`.

Open points to settle inside the task:

- How a table row marks "not a web page" (a column, a marker in the cell, or a separate table).
- Card wording, and whether the OpenAPI JSON gets its own card or is mentioned on the Swagger card.
- How a card states a condition such as "only with `--dev-tools`".
- The addresses are written by hand in the table, so a port changed in a compose file but not in the table makes a card point to the wrong place. A check that compares the table with the compose files is not part of this task.
- The System-page decision in `.claude/nav/adr-index.md` counts the System screen's cards in its title; the plan from the deep run found that `/record-decision` does not want an amendment section and does not require a record for a card-layout change. Decide when closing.

## Related

- [improvement-229](improvement-229-architecture-map-static-reports.md) — static reports on Code Quality, same generator, runs after this one.
- [improvement-138](improvement-138-architecture-control-plane.md) — the architecture control plane this map belongs to.
- The deep-plan run for the earlier variant (host ports moved into the repo-root `.env`, the generator reading them from there) is kept in `backlog/research/architecture-map-services-card/`; that variant was set aside in favour of the table-based one above and can be taken up again later.

## Execution plan (autopilot, 2026-10-26)

Plain-language layer: the System screen of the architecture map gets a "Services" section under the entry cards. Each running web page (app, Swagger UI, API flow runner page, SonarQube, Dagu, SeaweedFS master UI) is a card that opens it in a new tab. The cards are built from the "Local Services" table in `INFRASTRUCTURE.md`, so the addresses have one home. The Code Quality card "SonarQube" is renamed "Sonar Metrics". The task stays open in the backlog for the user's review.

Decisions taken for the open points (all follow the user's rule "only web pages, no live status"):

- A table row becomes a card only when its "Local link" cell holds a markdown link to an `http://` address; a plain address (PostgreSQL, the S3 API) means "not a web page" and is skipped. No new column.
- The condition "only with `--dev-tools`" and the OpenAPI JSON note live in the row's "Solves" text, which the card shows as is.
- The Services section is inline on the System screen (cards only, no separate screen, no router change).
- The System-page ADR is left unchanged and no new ADR is written: a card-layout change does not meet the record bar, and the earlier ADRs and Code Quality cards did the same.

Steps (technical layer):

1. **Table** (`INFRASTRUCTURE.md`, Local Services). Agent: yes. Model: haiku. Input: the five-row table. Result: the SeaweedFS row keeps only the S3 API (plain address); new rows "SeaweedFS master UI" (`advertisement-seaweedfs`, link `http://localhost:9333`), "Swagger UI" (`marketplace-app`, link `http://localhost:8081/swagger-ui/index.html`, Solves mentions the spec at `/v3/api-docs`), "API flow runner page" (`marketplace-app`, link `http://localhost:8081/dev-tools/api-flows/`, Solves says it is served only with `deploy-and-run.sh --dev-tools`); the Application, SonarQube and Dagu rows unchanged.
2. **Model** (`docs/architecture/scripts/generate-architecture-model.sh`). Agent: no (judgment inside a 4,400-line file). A function `services_json` next to `root_md_json_for` reads the table through `run_node` and prints a JSON array of `{name, container, label, url, solves}` for rows with an `http://` link; the model gets `"services": ...` next to `rootInfrastructure`. Fails loudly if the table is missing or yields no rows.
3. **Page** (same file). Agent: no. `renderSystem()` gets a "Services" section under the entry cards: a card-grid of `<a class="card service-card" href target="_blank" rel="noopener">` with name, Solves text, link text and container; one CSS rule for `.service-card` (block, inherited color, no underline). The Code Quality card, its crumb label, screen title and block heading "SonarQube" become "Sonar Metrics" (description unchanged).
4. **Docs.** Agent: yes. Model: haiku. `docs/architecture/README.md` System bullet names the Services section; `.claude/skills/app-readme-standards/SKILL.md` item 3 states that a row with an `http://` link becomes a map card and a plain address does not.
5. **Regenerate and check.** `bash docs/architecture/scripts/generate-architecture-model.sh` (backgrounded), the freshness check script, a node check that the model lists six services with the expected URLs, `docs/architecture/scripts/screenshot-architecture-map.sh` and a look at the System screenshot, ShellCheck of the generator.
6. **Review and report.** `deep-review-orchestrator` on the diff, findings applied; `/sync-docs`; final report. No version-control commit, no move to `completed/`.

Not covered by this plan: moving ports into `.env`, the CI stack, static reports, a check that the table matches the compose files.

## Operational notes
- token_cost_review: 57615
- token_cost_research: 221984 (deep-planner, topic architecture-map-services-card, for the set-aside `.env` variant)
- token_cost_verification: n/a
- review_signal_ratio: 2 / 2 (both low-confidence findings applied; no lens fan-out, the review read the diff directly)
- context_loading_task_type: n/a
- context_loading_consulted: n/a
- context_loading_matched: n/a
- flows_situation: plan a task in depth
- flows_chosen: /deep-plan
- flows_matched: yes
- haiku_decomposition_used: yes
- haiku_decomposition_dispatch_count: 2
- haiku_decomposition_rework_needed: 0 / 2
- adrs_referenced: none

### Agent calls
- deep plan | subagent_type=deep-planner | tokens=221984 | tool_uses=75 | duration_s=1201 | mode=background | batch=solo
- table rows | subagent_type=general-purpose model=haiku | tokens=72033 | tool_uses=3 | duration_s=27 | mode=background | batch=H1
- docs sentences | subagent_type=general-purpose model=haiku | tokens=71354 | tool_uses=5 | duration_s=27 | mode=background | batch=H1
- code review | subagent_type=deep-review-orchestrator | tokens=57615 | tool_uses=3 | duration_s=26 | mode=background | batch=solo

### Script/command runs
- docs/architecture/scripts/generate-architecture-model.sh (2 runs) | duration_s=120+ each | mode=background | result=pass
- docs/architecture/scripts/check-architecture-model-freshness.sh (2 runs) | duration_s=60+ | mode=background | result=pass
- docs/architecture/scripts/screenshot-architecture-map.sh (2 runs) | duration_s=60+ | mode=background | result=pass
- scripts/deploy-and-run.sh --dev-tools | duration_s=150 | mode=background | result=pass

## Autonomous decisions (autopilot run)

- The review (two low-confidence findings: a two-line comment above `services_json()`, a dead `|| []` fallback on `MODEL.services`) was applied in full and the map regenerated.
- ShellCheck is not installed in this container, so the generator was not ShellCheck-checked here; the CI `shellcheck` stage covers it.
- `/sync-docs` was not run as a separate step: the only docs touched were the two sentences above and the regenerated map, all checked by the freshness check.
- The task stays open in the backlog for the user's review, as asked.
- After the first version the user moved the section from the System screen to Tooling & Pipelines (above the infrastructure block) and asked for importance order; the order comes from the rows of the Local Services table, so the generator has no sorting of its own.
- The "Could not navigate to 'dev-tools/api-flows'" error came from a running container started without `MARKETPLACE_DEV_TOOLS_ENABLED`, not from the card; the app was redeployed with `--dev-tools` and the page answers 200.
- Closed 2026-10-26 at the user's word after checking the result in the browser.
