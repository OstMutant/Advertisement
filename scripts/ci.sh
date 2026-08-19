#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/ci/run.sh, where the real logic lives.
# Usage: same as scripts/ci/run.sh.
# Uses: bash.
# Env: same as scripts/ci/run.sh.
# Input: same as scripts/ci/run.sh.
# Outputs: same as scripts/ci/run.sh.
# Returns: same as scripts/ci/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/ci/run.sh" "$@"
