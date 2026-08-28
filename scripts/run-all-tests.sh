#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/run-all-tests/run.sh, where the real logic
#   lives.
# Usage: same as scripts/run-all-tests/run.sh.
# Uses: bash.
# Env: same as scripts/run-all-tests/run.sh.
# Input: same as scripts/run-all-tests/run.sh.
# Outputs: same as scripts/run-all-tests/run.sh.
# Returns: same as scripts/run-all-tests/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/run-all-tests/run.sh" "$@"
