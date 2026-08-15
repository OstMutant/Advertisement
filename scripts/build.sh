#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/build/run.sh" "$@"
