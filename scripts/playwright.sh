#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/playwright/run.sh" "$@"
