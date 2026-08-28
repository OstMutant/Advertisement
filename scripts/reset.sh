#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/deploy-and-run/reset.sh, where the real
#   logic lives.
# Usage: same as scripts/deploy-and-run/reset.sh.
# Uses: bash.
# Env: same as scripts/deploy-and-run/reset.sh.
# Input: same as scripts/deploy-and-run/reset.sh.
# Outputs: same as scripts/deploy-and-run/reset.sh.
# Returns: same as scripts/deploy-and-run/reset.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/deploy-and-run/reset.sh" "$@"
