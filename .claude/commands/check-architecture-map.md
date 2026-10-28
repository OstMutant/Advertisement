Check that the architecture map files on disk still match what the generator produces.

Usage: /check-architecture-map

Run manually before saving changes that can touch the map (module, SPI, schema, ADR, backlog, command or skill changes). It is not part of the regular generation run.

Steps:
1. Run `bash docs/architecture/scripts/check-architecture-model-freshness.sh` -- it backs up `architecture-model.json` and `architecture-map.html`, regenerates once, diffs, and restores the originals.
2. The `testReports` and `runLogs` sections are ignored; they hold this machine's test and script runs and differ between machines.
3. On exit 0, report that the map is up to date. On exit 1, show the ERROR lines and tell the user to run `bash docs/architecture/scripts/generate-architecture-model.sh`, review the diff and stage the result.
