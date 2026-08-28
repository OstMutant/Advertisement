#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/deploy-and-run/run.sh, where the real
#   logic lives.
# Usage: same as scripts/deploy-and-run/run.sh.
# Uses: bash.
# Env: same as scripts/deploy-and-run/run.sh.
# Input: same as scripts/deploy-and-run/run.sh.
# Outputs: same as scripts/deploy-and-run/run.sh.
# Returns: same as scripts/deploy-and-run/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/deploy-and-run/run.sh" "$@"
