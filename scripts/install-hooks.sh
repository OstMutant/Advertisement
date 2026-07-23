#!/usr/bin/env bash
# Install git hooks for this repository.
# Run once after cloning: bash scripts/install-hooks.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

git -C "$ROOT" config core.hooksPath scripts/hooks
chmod +x "$ROOT/scripts/hooks/pre-commit"
chmod +x "$ROOT/scripts/hooks/commit-msg"

echo "Git hooks installed (core.hooksPath = scripts/hooks)"
echo ""
echo "pre-commit : syncs docs/architecture/, DECISIONS.md, CLAUDE.md, backlog/issues/"
echo "commit-msg : prepends entry to CHANGELOG.md from conventional commit message"
echo ""
echo "Bypass for a single commit: SKIP_AUDIT=1 git commit"
