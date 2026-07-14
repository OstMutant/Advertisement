#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/scripts/unit-tests/run.sh" "$@"
