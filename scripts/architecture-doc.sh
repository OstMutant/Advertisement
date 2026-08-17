#!/bin/bash
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NO_CHECK=false
NO_SCREENSHOT=false
for arg in "$@"; do
  case "$arg" in
    --no-check) NO_CHECK=true ;;
    --no-screenshot) NO_SCREENSHOT=true ;;
  esac
done

bash "$ROOT/docs/architecture/scripts/generate-architecture-model.sh" || exit 1
if [ "$NO_CHECK" = false ]; then
  bash "$ROOT/docs/architecture/scripts/check-architecture-model-freshness.sh" || exit 1
fi
if [ "$NO_SCREENSHOT" = false ]; then
  bash "$ROOT/docs/architecture/scripts/screenshot-architecture-map.sh"
fi
