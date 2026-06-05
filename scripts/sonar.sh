#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/sonar/run.sh" "$@"
