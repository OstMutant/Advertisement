#!/usr/bin/env python3
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Polls Dagu's own REST API (through the ci-runner-dagu-proxy sidecar, not the
#   in-container port pipeline-metrics.py uses) for the most recently triggered "ci" DAG run and
#   prints one line each time a step's status changes, until the whole run reaches a terminal
#   state. Meant to be run inside the Monitor tool (see scripts/CLAUDE.md's "Local CI Runner"
#   section) -- ci.sh itself returns almost immediately after triggering a run (or right after the
#   image build when backgrounded), so watching its own process exit tells you nothing about the
#   run's real pass/fail outcome; this script watches the run itself instead.
# Usage: python3 -u scripts/ci/watch-run.py (run after `bash scripts/ci.sh [flags]` has already
#   triggered a run -- picks up whichever "ci" run is newest at the moment this script starts).
#   `-u` is required: Python buffers stdout when it isn't a TTY (piped into Monitor/tee), so
#   without it every print() below sits in a buffer instead of appearing as it happens -- confirmed
#   directly, a first version without `-u` produced zero visible output for several minutes of a
#   real run despite the run itself progressing normally.
# Uses: python3 (stdlib only -- json, os, sys, time, urllib.request).
# Env: DAGU_UI_PORT (default 8082, the proxy sidecar's published port) -- may be exported directly
#   in the calling shell if the sidecar was started on a non-default port.
# Input: Dagu's local REST API (GET /api/v1/dag-runs, GET /api/v1/dag-runs/ci/<id>).
# Outputs: one line per step-status transition (e.g. "unit: succeeded"), plus a "... still
#   running: <steps>" heartbeat line roughly every HEARTBEAT_INTERVAL_SECONDS whenever nothing
#   transitioned in that window (so a long, silent stall is still visible in the output -- this
#   script has no notion of each step's own normal duration, so it cannot tell a genuine hang
#   apart from a slow-but-fine step; the heartbeat only proves the watcher itself is still polling),
#   plus a final "RUN <status>" line.
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


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def latest_run_id():
    runs = fetch(f"{BASE_URL}/dag-runs?name=ci&limit=1").get("dagRuns", [])
    return runs[0]["dagRunId"] if runs else None


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

    changed = False
    running_steps = []
    for node in run.get("nodes", []):
        name = node.get("step", {}).get("name")
        status = node.get("statusLabel")
        if last_status.get(name) != status:
            print(f"{name}: {status}")
            last_status[name] = status
            changed = True
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
