#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Removes a finished research topic folder: from git when its files are tracked, and
#   from the disk. Without --yes it only lists what would be removed.
# Usage: bash backlog/research/tools/scripts/remove-topic.sh <topic> [--yes] [--help]
#   <topic>   an existing topic
#   --yes     really remove; without it the script lists the files and exits with 2
#   --help    print the usage text and exit
# Uses: bash, git, find, topic-common.sh.
# Env: None.
# Input: the topic name.
# Outputs: the list of files to remove; with --yes the folder is removed (a staged deletion for
#   tracked files); nothing is committed.
# Returns: 0 when removed or when --help was asked; 1 when the topic does not exist; 2 on a usage
#   error or when --yes was not given.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -ge 1 ] && [ "$#" -le 2 ] || die 2 "usage: remove-topic.sh <topic> [--yes]"
TOPIC="$1"
CONFIRM="${2:-}"
[ -z "$CONFIRM" ] || [ "$CONFIRM" = "--yes" ] || die 2 "unknown option '$CONFIRM'"
require_existing_topic "$TOPIC"
DIR="$RESEARCH_DIR/$TOPIC"

echo "files of topic '$TOPIC':"
find "$DIR" -type f | sort | sed "s|^$RESEARCH_DIR/|  |"
if [ "$CONFIRM" != "--yes" ]; then
  die 2 "nothing removed; run again with --yes to remove the topic"
fi

git -C "$RESEARCH_DIR" rm -r -q -f --ignore-unmatch -- "$TOPIC"
rm -rf "$DIR"
echo "removed $DIR"
