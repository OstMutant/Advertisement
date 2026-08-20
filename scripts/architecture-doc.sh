#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Regenerates the architecture documentation model -- always runs
#   docs/architecture/scripts/generate-architecture-model.sh, then (unless --no-check) re-runs it a
#   second time into temp files via check-architecture-model-freshness.sh to diff-verify the
#   committed output is current, then (unless --no-screenshot) screenshots every screen of the
#   generated architecture-map.html.
# Usage: bash scripts/architecture-doc.sh [--no-check] [--no-screenshot]
#   --no-check        skip the freshness re-verification pass (only meaningful right after this
#                      same script already regenerated the file -- re-checking your own fresh
#                      output is pointless)
#   --no-screenshot   skip screenshotting architecture-map.html
# Uses: bash, docs/architecture/scripts/generate-architecture-model.sh,
#   docs/architecture/scripts/check-architecture-model-freshness.sh,
#   docs/architecture/scripts/screenshot-architecture-map.sh.
# Env: None.
# Input: repo source (every Java module, Liquibase changelog, ADR, backlog file the generator
#   scans).
# Outputs: docs/architecture/architecture-model.json, docs/architecture/architecture-map.html;
#   with the freshness check enabled, an ERROR naming which file is stale if the committed version
#   doesn't match a fresh regeneration (no file written by that step itself); with screenshots
#   enabled, PNG files under the screenshot script's own output directory.
# Returns: 0 on success; non-zero if generation fails, the freshness check finds drift, or the
#   screenshot step fails.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NO_CHECK=false
NO_SCREENSHOT=false
for arg in "$@"; do
  case "$arg" in
    --no-check) NO_CHECK=true ;;
    --no-screenshot) NO_SCREENSHOT=true ;;
  esac
done

bash "$ROOT/docs/architecture/scripts/generate-architecture-model.sh" || exit 1
if [ "$NO_CHECK" = false ]; then
  bash "$ROOT/docs/architecture/scripts/check-architecture-model-freshness.sh" || exit 1
fi
if [ "$NO_SCREENSHOT" = false ]; then
  bash "$ROOT/docs/architecture/scripts/screenshot-architecture-map.sh"
fi
