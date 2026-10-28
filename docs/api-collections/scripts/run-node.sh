#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs a Node.js script from this directory inside a disposable Node container, so
#   the host needs only Docker. The whole docs/api-collections folder is streamed into the
#   container with a tar pipe rather than a bind mount, because volume mounts do not work from
#   inside the dev container.
# Usage: bash docs/api-collections/scripts/run-node.sh <script.mjs> [args...]
#   <script.mjs>  file name relative to docs/api-collections/scripts
#   [args...]     passed unchanged to the script
# Uses: bash, tar, docker, the Node image named by NODE_IMAGE.
# Env: NODE_IMAGE -- Node image to run (default node:22-alpine).
# Input: every file in docs/api-collections (copied into the container).
# Outputs: the script's stdout and stderr; nothing is written to the host.
# Returns: the exit code of node; 2 on usage error or when the script file is missing; a tar
#   failure is also reported as non-zero (pipefail).
# ────────────────────────────────────────────────────────────────────────────
set -o pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/.." && pwd)"
IMAGE="${NODE_IMAGE:-node:22-alpine}"

if [ "$#" -lt 1 ]; then
  echo "usage: bash docs/api-collections/scripts/run-node.sh <script.mjs> [args...]" >&2
  exit 2
fi
SCRIPT="$1"
shift

if [ ! -f "$DIR/$SCRIPT" ]; then
  echo "run-node: $SCRIPT not found in $DIR" >&2
  exit 2
fi

tar -cf - -C "$ROOT" . | docker run --rm -i --network host "$IMAGE" \
  sh -c 'mkdir /work && tar -xf - -C /work && cd /work && s="scripts/$1" && shift && node "$s" "$@"' \
  sh "$SCRIPT" "$@"
