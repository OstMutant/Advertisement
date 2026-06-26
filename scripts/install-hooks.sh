#!/usr/bin/env bash
# Install git hooks for this repository.
# Run once after cloning: bash scripts/install-hooks.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

git -C "$ROOT" config core.hooksPath scripts/hooks
chmod +x "$ROOT/scripts/hooks/pre-commit"

echo "Git hooks installed (core.hooksPath = scripts/hooks)"
echo ""
echo "Pre-commit hook: docs/architecture/, DECISIONS.md, CLAUDE.md, features/issues/"
echo "are synced automatically before each commit."
echo ""
echo "Bypass for a single commit: SKIP_AUDIT=1 git commit"
