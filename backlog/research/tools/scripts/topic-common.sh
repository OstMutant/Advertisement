#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Shared helpers of the research helper scripts: where the research folder is, the
#   rules a topic name must follow and the checks every script needs before touching a topic.
#   Source this file, call the function you need.
# Usage: source backlog/research/tools/scripts/topic-common.sh (the other scripts in this folder do
#   it with the path of their own directory).
# Uses: bash.
# Env: None.
# Input: None.
# Outputs: sets RESEARCH_DIR (absolute path of backlog/research) and defines the functions below.
# Returns: 0 always when sourced; the functions return as stated on each one.
# ────────────────────────────────────────────────────────────────────────────

RESEARCH_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

#######################################
# Prints an error line on stderr and exits.
# Arguments: $1 -- exit code, $2 -- message.
# Outputs: the message on stderr.
# Returns: does not return, exits with $1.
#######################################
die() {
  echo "error: $2" >&2
  exit "$1"
}

#######################################
# Checks a topic name: lower-case letters, digits and hyphens, starting with a letter or a digit,
# and not the reserved name of the tools folder.
# Arguments: $1 -- topic name.
# Outputs: an error line on stderr when the name is not valid.
# Returns: 0 when valid; exits with 2 when not.
#######################################
require_topic_name() {
  if [[ ! "$1" =~ ^[a-z0-9][a-z0-9-]*$ ]]; then
    die 2 "topic name '$1' must be lower-case letters, digits and hyphens"
  fi
  if [ "$1" = "tools" ]; then
    die 2 "'tools' is reserved for the helper folder, not a topic"
  fi
}

#######################################
# Checks that a topic folder exists.
# Arguments: $1 -- topic name.
# Outputs: an error line on stderr when the folder is missing.
# Returns: 0 when it exists; exits with 1 when not.
#######################################
require_existing_topic() {
  require_topic_name "$1"
  [ -d "$RESEARCH_DIR/$1" ] || die 1 "topic '$1' does not exist (see list-topics.sh)"
}

#######################################
# Prints the Usage lines of a script's own header (from the 'Usage:' line to the line before 'Uses:').
# Arguments: $1 -- path of the script.
# Outputs: the usage text on stdout.
# Returns: 0 always.
#######################################
print_usage() {
  awk '/^# Usage:/{f=1} /^# Uses:/{f=0} f' "$1" | sed 's/^# \{0,1\}//'
}
