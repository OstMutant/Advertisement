#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs run-flows.mjs against the running API inside a disposable Node container
#   (via run-node.sh). Creates data in the app, so run it against a dev database.
# Usage: bash docs/api-collections/scripts/run-flows.sh [baseUrl] [--config <file>] [--flow <name>]...
#   [--variable key=value]... [--json] [--help]
#   Arguments are forwarded unchanged to run-flows.mjs.
# Uses: run-node.sh (this directory's sibling), docker.
# Env: API_BASE_URL -- base URL when no positional argument is given; NODE_IMAGE -- see run-node.sh.
# Input: see run-flows.mjs.
# Outputs: see run-flows.mjs.
# Returns: see run-flows.mjs.
# ────────────────────────────────────────────────────────────────────────────
DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$DIR/run-node.sh" run-flows.mjs "$@"
