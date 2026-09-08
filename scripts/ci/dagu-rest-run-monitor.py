#!/usr/bin/env python3
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Polls Dagu's own REST API (through the ci-runner-dagu-proxy sidecar, not the
#   in-container port pipeline-metrics.py uses) for the most recently triggered "ci" DAG run and,
#   on every real step-status transition, prints an AGENTIC_SUCCESS_BLOCK/AGENTIC_ERROR_BLOCK JSON
#   marker line -- the same contract scripts/utils/agentic-output.sh's own
#   emit_agentic_success_block/emit_agentic_error_block produce -- so scripts/activity-monitor.sh's
#   existing marker parser renders this run's own tree.txt exactly the way it already does for the
#   7 other wrapped scripts, no separate rendering logic needed here. Meant to be invoked by
#   scripts/ci/run.sh's own --foreground branch (see .claude/rules/scripts.md's "Local CI Runner"
#   section) -- ci.sh's own process returns almost immediately after triggering a run, so watching
#   its own process exit tells you nothing about the run's real pass/fail outcome; this script
#   watches the run itself instead, and run.sh exits with its real exit code.
# Usage: python3 -u scripts/ci/dagu-rest-run-monitor.py (run after `bash scripts/ci.sh [flags]` has
#   already triggered a run -- picks up whichever "ci" run is newest at the moment this script
#   starts). `-u` is required: Python buffers stdout when it isn't a TTY (piped into
#   Monitor/activity-monitor.sh/tee), so without it every print() below sits in a buffer instead of
#   appearing as it happens -- confirmed directly, a first version without `-u` produced zero
#   visible output for several minutes of a real run despite the run itself progressing normally.
# Uses: python3 (stdlib only -- json, os, sys, time, urllib.request).
# Env: DAGU_UI_PORT (default 8082, the proxy sidecar's published port) -- may be exported directly
#   in the calling shell if the sidecar was started on a non-default port.
# Input: Dagu's local REST API (GET /api/v1/dag-runs, GET /api/v1/dag-runs/ci/<id>).
# Outputs: one AGENTIC_SUCCESS_BLOCK/AGENTIC_ERROR_BLOCK/AGENTIC_SKIP_BLOCK JSON line per step
#   reaching "succeeded", a failure-shaped terminal status (failed/cancelled/partially_succeeded),
#   or "skipped" (its own real status -- a precondition wasn't met, e.g. --unit-only leaves
#   integration/e2e/sonar genuinely un-run, distinct from either done or still-pending). Also a
#   "... still running: <steps>" heartbeat line roughly every HEARTBEAT_INTERVAL_SECONDS
#   whenever nothing transitioned in that window (keeps raw.log growing so
#   scripts/activity-monitor/run.sh's own generic silence-stall check doesn't fire during a long,
#   healthy step with no container-state check of its own, e.g. sonar), plus a final "RUN <status>"
#   line for a human reading stdout directly.
# Returns: 0 if the run's own final status is succeeded; 1 for any other terminal status
#   (failed, partially_succeeded, cancelled) or if no run is found.
# ────────────────────────────────────────────────────────────────────────────
import json
import os
import sys
import time
import urllib.request

DAGU_UI_PORT = os.environ.get("DAGU_UI_PORT", "8082")
BASE_URL = f"http://localhost:{DAGU_UI_PORT}/api/v1"
TERMINAL_STATUSES = {"succeeded", "failed", "partially_succeeded", "cancelled"}
NON_RUNNING_STEP_STATUSES = TERMINAL_STATUSES | {"not_started", "skipped"}
POLL_INTERVAL_SECONDS = 10
HEARTBEAT_INTERVAL_SECONDS = 60

START_TIME = time.monotonic()


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def latest_run_id():
    runs = fetch(f"{BASE_URL}/dag-runs?name=ci&limit=1").get("dagRuns", [])
    return runs[0]["dagRunId"] if runs else None


# Same JSON shape scripts/utils/agentic-output.sh's own emit_agentic_success_block produces --
# scripts/activity-monitor/profiles/agentic.sh's parser reads only "currentStep" out of this
# (durationSeconds is carried for shape-completeness, not read by that parser -- real per-step
# duration is computed by activity-monitor/run.sh itself from its own wall-clock timestamps).
def emit_agentic_success_block(step):
    elapsed = int(time.monotonic() - START_TIME)
    print(f'AGENTIC_SUCCESS_BLOCK: {{"status":"success","currentStep":"{step}","durationSeconds":{elapsed}}}')


# Same JSON shape scripts/utils/agentic-output.sh's own emit_agentic_error_block produces.
def emit_agentic_error_block(step, description):
    elapsed = int(time.monotonic() - START_TIME)
    print(f'AGENTIC_ERROR_BLOCK: {{"status":"error","errorCategory":"business","isRetryable":false,'
          f'"currentStep":"{step}","description":"{description}","durationSeconds":{elapsed}}}')


# A third marker, recognised only by scripts/activity-monitor/profiles/agentic.sh's own
# AGENTIC_SKIP_BLOCK case (not part of scripts/utils/agentic-output.sh's own two-block bash
# contract) -- a Dagu step whose own precondition wasn't met (e.g. a --unit-only run leaving
# integration/e2e/sonar genuinely un-run, not failed) needs its own real status, not a fake
# "succeeded" that would misrepresent what actually happened.
def emit_agentic_skip_block(step):
    elapsed = int(time.monotonic() - START_TIME)
    print(f'AGENTIC_SKIP_BLOCK: {{"status":"skipped","currentStep":"{step}","durationSeconds":{elapsed}}}')


run_id = latest_run_id()
if not run_id:
    print("No 'ci' DAG run found.")
    sys.exit(1)

print(f"Watching run {run_id}")
last_status = {}
last_output_at = time.monotonic()

while True:
    run = fetch(f"{BASE_URL}/dag-runs/ci/{run_id}").get("dagRunDetails", {})
    run_status = run.get("statusLabel")
    nodes = run.get("nodes", [])

    changed = False
    running_steps = []
    for node in nodes:
        name = node.get("step", {}).get("name")
        status = node.get("statusLabel")
        if last_status.get(name) != status:
            last_status[name] = status
            changed = True
            if status == "succeeded":
                emit_agentic_success_block(name)
            elif status in TERMINAL_STATUSES:
                emit_agentic_error_block(name, f"Dagu step \\\"{name}\\\" ended with status \\\"{status}\\\".")
            elif status == "skipped":
                emit_agentic_skip_block(name)
        if status not in NON_RUNNING_STEP_STATUSES:
            running_steps.append(f"{name} ({status})")

    if changed:
        last_output_at = time.monotonic()
    elif running_steps and time.monotonic() - last_output_at >= HEARTBEAT_INTERVAL_SECONDS:
        print(f"... still running: {', '.join(running_steps)}")
        last_output_at = time.monotonic()

    if run_status in TERMINAL_STATUSES:
        print(f"RUN {run_status}")
        sys.exit(0 if run_status == "succeeded" else 1)

    time.sleep(POLL_INTERVAL_SECONDS)
