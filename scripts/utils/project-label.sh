#!/usr/bin/env bash
# ── Header ────────────────────────────────────────────────
# Description: Loads the label every long-lived container of this project carries, so that
#   scripts/docker-clean.sh finds them by label, not by name. The value is written once, in the
#   repo-root .env (PROJECT_LABEL). A container created before the label existed has none until it
#   is recreated; short-lived `--rm` helpers need none.
# Usage: source scripts/utils/project-label.sh; docker run -d --label "$PROJECT_LABEL" ...
# Uses: bash, grep, head, cut.
# Env: None.
# Input: PROJECT_LABEL in the .env at the repo root (two levels above this file).
# Outputs: sets and exports PROJECT_LABEL (key=value).
# Returns: 0 when loaded; 1 with a message on stderr when .env has no PROJECT_LABEL (a sourcing
#   script running under `set -e` stops, any other caller decides).
# ────────────────────────────────────────────────────────

PROJECT_LABEL="$(grep -E '^PROJECT_LABEL=' "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.env" | head -1 | cut -d= -f2- || true)"
export PROJECT_LABEL
if [ -z "$PROJECT_LABEL" ]; then
  echo "ERROR: PROJECT_LABEL is missing in the repo-root .env" >&2
  return 1
fi
