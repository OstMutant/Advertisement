#!/usr/bin/env bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Creates a research topic folder under backlog/research/ with the six files a
#   planning run works with (brief, progress, sources, notes, human plan, machine plan), each
#   from a template that starts with a one-line purpose.
# Usage: bash backlog/research/tools/scripts/start-topic.sh <topic> [--help]
#   <topic>   lower-case letters, digits and hyphens; 'tools' is reserved
#   --help    print the usage text and exit
# Uses: bash, topic-common.sh.
# Env: None.
# Input: the topic name.
# Outputs: the folder backlog/research/<topic>/ with brief.md, progress.md, sources.md, notes.md,
#   plan-human.md and plan-machine.md; prints the paths it created.
# Returns: 0 on success; 1 when the topic already exists; 2 on a usage error or an invalid name.
# ────────────────────────────────────────────────────────────────────────────
set -euo pipefail
# shellcheck source=topic-common.sh
source "$(dirname "${BASH_SOURCE[0]}")/topic-common.sh"

if [ "${1:-}" = "--help" ]; then
  print_usage "${BASH_SOURCE[0]}"
  exit 0
fi
[ "$#" -eq 1 ] || die 2 "usage: start-topic.sh <topic>"
TOPIC="$1"
require_topic_name "$TOPIC"
DIR="$RESEARCH_DIR/$TOPIC"
[ ! -e "$DIR" ] || die 1 "topic '$TOPIC' already exists"

mkdir -p "$DIR"

cat > "$DIR/brief.md" <<EOF
# Brief: $TOPIC

Purpose: what the planner is asked to find out and plan; written by the main thread, read by the planner.

## Goal

## Task

(link to the backlog task file, or "none yet")

## Context

(decisions, constraints and rejected options from the conversation)

## Where to look in the project

## Completion criterion

## Available executor models

haiku, sonnet, opus
EOF

cat > "$DIR/progress.md" <<EOF
# Progress: $TOPIC

Purpose: one line per step of the planner, written with log-progress.sh and read with show-status.sh.
EOF

cat > "$DIR/sources.md" <<EOF
# Sources: $TOPIC

Purpose: every source the planner used, written with add-source.sh.
Level: 0 is a direct source, 1 and 2 are reached by following references when sources conflict.

| link | taken | confirms or contradicts | date | type | level | confidence |
|---|---|---|---|---|---|---|
EOF

cat > "$DIR/notes.md" <<EOF
# Notes: $TOPIC

Purpose: findings, the comparison of variants and the differences from the project's own practice.

## Findings

## Variants and comparison

## Differences from the project
EOF

cat > "$DIR/plan-human.md" <<EOF
# Plan for people: $TOPIC

Purpose: the result for the person who decides: clear, short and easy to follow.

## Goal

## The problem this solves

## The means needed

## What the result will look like

## Cost

## Risks

## Where the project's practice differs from best practice

## Decisions waiting for you
EOF

cat > "$DIR/plan-machine.md" <<EOF
# Plan for executors: $TOPIC

Purpose: the result for the AI that executes it, with the detail each step's executor model needs
(exact content for a weaker model, exact files and behavior for a stronger one).

## Steps

Each step: number, dependency, files, executor model, input, exact change, verification, sources.
EOF

echo "created $DIR:"
for f in brief progress sources notes plan-human plan-machine; do
  echo "  $f.md"
done
