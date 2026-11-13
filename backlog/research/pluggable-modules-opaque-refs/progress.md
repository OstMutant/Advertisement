# Progress: pluggable-modules-opaque-refs

Purpose: one line per step of the planner, written with log-progress.sh and read with show-status.sh.

## 2026-11-09

- 14:41 [read] 0 sources (+0 new), choice unchanged: Read brief and task improvement-249; starting the study of the Taxon flow
- 14:44 [read] 0 sources (+0 new), choice unchanged: Read the Taxon flow, both filter repositories, all changelogs, poms, autoconfigs and ADRs 001(taxon), 034/005/023/079(app), 031/032/034(commons); no cross-starter FK exists anywhere
- 14:59 [measure] 0 sources (+0 new), choice unchanged: Disposable Postgres 15 (container research-pg-opaque-refs) loaded with 1M ads and 2.58M assignments; rare-category run done, larger scenarios running
- 15:02 [search] 6 sources (+6 new), choice unchanged: First web pass: reference-by-id and module-owned migrations agree with the project's practice; SQL runs re-started with bound arrays (psql literal substitution of a 242k array stalled the client, not the server)
- 15:06 [compare] 9 sources (+3 new), choice unchanged: Measurements done and container removed; leading proposal: keep A, add A+ fixes and the pluggability test, keep B as the measured next step for Taxon only. Conflict flagged early: B would supersede taxon ADR-001, commons ADR-031/032 and app ADR-079
- 15:08 [draft] 9 sources (+0 new), choice unchanged: Verified bean-level wiring (User ports injected directly = User is a required module; orchestrator declares no port factory); matched findings to improvement-003 K and improvement-133 #11/#23/#25; writing the plans
- 15:17 [done] 9 sources (+0 new), choice unchanged: Plans written: plan-human.md (report parts A-I, measured table, 6 decisions) and plan-machine.md (7 batches + whole verification); stop rule: the last three sources only confirmed the choice, and the measurements decided between A and B
