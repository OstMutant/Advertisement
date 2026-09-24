#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to scripts/activity-monitor/run.sh, where the real
#   logic lives.
# Usage: same as scripts/activity-monitor/run.sh.
# Uses: bash.
# Env: same as scripts/activity-monitor/run.sh.
# Input: same as scripts/activity-monitor/run.sh.
# Outputs: same as scripts/activity-monitor/run.sh.
# Returns: same as scripts/activity-monitor/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/activity-monitor/run.sh" "$@"
