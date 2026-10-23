#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs the API collection tests one after the other -- check-collection.sh (the
#   collection against the OpenAPI document) and run-flows.sh (the collection's flows against the
#   running app). A failing test does not stop the other one; a summary names each test's outcome.
# Usage: bash docs/api-collections/api-collection-tests.sh [--no-check] [--no-flows]
#     [--base-url <url>] [--flow <name>]... [--help]
#   --no-check          skip check-collection
#   --no-flows          skip run-flows
#   --base-url <url>    base URL passed to both tests (default: each test's own default)
#   --flow <name>       run only this flow in run-flows; repeat for several
#   --help              print this text and exit
# Uses: scripts/check-collection.sh, scripts/run-flows.sh (in this folder's scripts/ subfolder),
#   docker (via run-node.sh).
# Env: API_BASE_URL, NODE_IMAGE -- read by the tests, see run-node.sh.
# Input: the arguments above; the app must be running (default http://localhost:8081).
# Outputs: a "== <test> ==" header before each test, the tests' own output, then one summary line
#   per test: "<test>: passed", "<test>: failed (exit N)" or "<test>: skipped". Errors on stderr.
# Returns: 0 when every selected test passed; 1 when at least one selected test exited non-zero;
#   2 on an unknown option, a missing value, or when both tests are skipped.
# ────────────────────────────────────────────────────────────────────────────
DIR="$(cd "$(dirname "$0")" && pwd)"
RUN_CHECK=true
RUN_FLOWS=true
BASE_URL=""
FLOWS=()

usage() {
  cat <<'EOF'
Usage: bash docs/api-collections/api-collection-tests.sh [--no-check] [--no-flows]
                                                         [--base-url <url>] [--flow <name>]... [--help]

Runs check-collection and run-flows one after the other.

Options:
  --no-check          skip check-collection
  --no-flows          skip run-flows
  --base-url <url>    base URL passed to both tests
  --flow <name>       run only this flow in run-flows; repeat for several
  --help              print this text and exit

Exit codes:
  0  every selected test passed
  1  at least one selected test failed
  2  usage error, or both tests skipped
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --no-check) RUN_CHECK=false; shift ;;
    --no-flows) RUN_FLOWS=false; shift ;;
    --base-url)
      if [ "$#" -lt 2 ]; then echo "--base-url needs a value" >&2; usage >&2; exit 2; fi
      BASE_URL="$2"
      shift 2
      ;;
    --flow)
      if [ "$#" -lt 2 ]; then echo "--flow needs a flow name" >&2; usage >&2; exit 2; fi
      FLOWS+=("$2")
      shift 2
      ;;
    --help) usage; exit 0 ;;
    *) echo "unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
done

if [ "$RUN_CHECK" = false ] && [ "$RUN_FLOWS" = false ]; then
  echo "nothing to run: both tests are skipped" >&2
  exit 2
fi

CHECK_ARGS=()
FLOW_ARGS=()
if [ -n "$BASE_URL" ]; then
  CHECK_ARGS+=("$BASE_URL")
  FLOW_ARGS+=("$BASE_URL")
fi
for flow in "${FLOWS[@]}"; do
  FLOW_ARGS+=(--flow "$flow")
done

CHECK_RESULT="skipped"
FLOWS_RESULT="skipped"
ANY_FAILED=false

if [ "$RUN_CHECK" = true ]; then
  echo "== check-collection =="
  bash "$DIR/scripts/check-collection.sh" "${CHECK_ARGS[@]}"
  code=$?
  if [ "$code" -eq 0 ]; then CHECK_RESULT="passed"; else CHECK_RESULT="failed (exit $code)"; ANY_FAILED=true; fi
fi

if [ "$RUN_FLOWS" = true ]; then
  echo "== run-flows =="
  bash "$DIR/scripts/run-flows.sh" "${FLOW_ARGS[@]}"
  code=$?
  if [ "$code" -eq 0 ]; then FLOWS_RESULT="passed"; else FLOWS_RESULT="failed (exit $code)"; ANY_FAILED=true; fi
fi

echo
echo "check-collection: $CHECK_RESULT"
echo "run-flows: $FLOWS_RESULT"

if [ "$ANY_FAILED" = true ]; then exit 1; fi
exit 0
