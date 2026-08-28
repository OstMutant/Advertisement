#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Thin entry point -- delegates to playwright/run.sh, where the real logic lives.
# Usage: same as playwright/run.sh.
# Uses: bash.
# Env: same as playwright/run.sh.
# Input: same as playwright/run.sh.
# Outputs: same as playwright/run.sh.
# Returns: same as playwright/run.sh.
# ────────────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/playwright/run.sh" "$@"
