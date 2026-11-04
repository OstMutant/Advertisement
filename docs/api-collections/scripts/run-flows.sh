#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs run-flows.mjs against the running API inside a disposable Node container
#   (via run-node.sh). Creates data in the app, so run it against a dev database.
# Usage: bash docs/api-collections/scripts/run-flows.sh [baseUrl] [--config <file>] [--flow <name>]...
#   [--role <name>] [--each-role] [--variable key=value]... [--json] [--help]
#   (no flags)         run every flow against API_BASE_URL or the config's baseUrl
#   [baseUrl]          API base URL; takes precedence over API_BASE_URL and the config's baseUrl
#   --config <file>    config file (default: check-collection.config.json next to run-flows.mjs)
#   --flow <name>      run only this flow; repeat for several
#   --role <name>      run only the flows that role can run: user, moderator or administrator
#   --each-role        run the flows of every role in turn (user, moderator, administrator)
#   --variable k=v     override a collection variable; repeat for several
#   --json             print only the step results as JSON
#   --help             print the usage text and exit
#   Arguments are forwarded unchanged to run-flows.mjs.
# Uses: run-node.sh (this directory's sibling), docker.
# Env: API_BASE_URL -- base URL when no positional argument is given; NODE_IMAGE -- see run-node.sh.
# Input: see run-flows.mjs.
# Outputs: see run-flows.mjs.
# Returns: see run-flows.mjs.
# ────────────────────────────────────────────────────────────────────────────
DIR="$(cd "$(dirname "$0")" && pwd)"
exec bash "$DIR/run-node.sh" run-flows.mjs "$@"
