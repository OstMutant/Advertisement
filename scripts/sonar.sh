#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/sonar/run.sh, where the real logic lives.
# Usage: same as scripts/sonar/run.sh.
# Uses: bash.
# Env: same as scripts/sonar/run.sh.
# Input: same as scripts/sonar/run.sh.
# Outputs: same as scripts/sonar/run.sh.
# Returns: same as scripts/sonar/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/sonar/run.sh" "$@"
