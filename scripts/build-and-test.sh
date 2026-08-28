#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/build-and-test/run.sh, where the real
#   logic lives.
# Usage: same as scripts/build-and-test/run.sh.
# Uses: bash.
# Env: same as scripts/build-and-test/run.sh.
# Input: same as scripts/build-and-test/run.sh.
# Outputs: same as scripts/build-and-test/run.sh.
# Returns: same as scripts/build-and-test/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/build-and-test/run.sh" "$@"
