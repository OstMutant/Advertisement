#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs check-collection.mjs against the running API inside a disposable Node
#   container (via run-node.sh).
# Usage: bash docs/api-collections/scripts/check-collection.sh [baseUrl] [--config <file>] [--help]
#   Arguments are forwarded unchanged to check-collection.mjs.
# Uses: run-node.sh (this directory's sibling), docker.
# Env: API_BASE_URL -- base URL when no positional argument is given; NODE_IMAGE -- see run-node.sh.
# Input: see check-collection.mjs.
# Outputs: see check-collection.mjs.
# Returns: see check-collection.mjs.
# ────────────────────────────────────────────────────────────────────────────
DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$DIR/run-node.sh" check-collection.mjs "$@"
