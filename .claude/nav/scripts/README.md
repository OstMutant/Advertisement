# .claude/nav/scripts/

Generation/verification logic behind `.claude/nav/adr-index.md` and `.claude/nav/flows.md` — see
`.claude/nav/README.md` for what those files are and how they're used; this directory is only the
code that builds/checks them.

## Flow

Real entry points, independently invocable:

```bash
bash .claude/nav/scripts/generate-adr-index.sh
bash .claude/nav/scripts/check-flows-completeness.sh
bash .claude/nav/scripts/check-hardcoded-counts.sh
node .claude/nav/scripts/md-to-decisions-json.js <module> [<module> ...]
node .claude/nav/scripts/md-to-decisions-json.js --stdout <module>
node .claude/nav/scripts/md-to-decisions-json.js --extract <module> <ADR-NNN>[,<ADR-NNN>...]
```

`generate-adr-index.sh` is run by the `docs` stage in `scripts/ci/dagu/ci.yaml` (via
`docs/architecture/scripts/generate-architecture-model.sh`, which regenerates the index in place
before building the model that reads it), by `/record-decision`, and by a standing
`.claude/rules.md` rule requiring it after any `DECISIONS.md` edit. The `docs` stage no longer
diffs the committed index against a fresh regeneration — it regenerates it and `scripts/ci/run.sh`
copies the result back to the host, so a run just leaves the fresh file to commit.
`check-flows-completeness.sh` and `check-hardcoded-counts.sh` are the `docs` stage's read-only
verifiers.

`md-to-decisions-json.js` has two distinct real callers, not one flow: its `--stdout` mode is
called by `docs/architecture/scripts/generate-architecture-model.sh` (a script in a different
script-group directory) only when `--with-adr-details` is passed; its `--extract` mode is invoked
directly, on demand, per `.claude/nav/README.md`'s own guidance, not from any other script.

```mermaid
flowchart TD
    CI[scripts/ci/dagu/ci.yaml docs stage] --> FL[check-flows-completeness.sh]
    CI --> H[check-hardcoded-counts.sh]
    CI --> GAM
    GAM["docs/architecture/scripts/generate-architecture-model.sh"] --> G[generate-adr-index.sh]
    RD["/record-decision command"] --> G
    GAM -->|--with-adr-details| M["md-to-decisions-json.js --stdout"]
```
