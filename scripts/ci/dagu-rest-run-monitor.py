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
#   already triggered a run). It waits up to FRESH_RUN_WAIT_SECONDS for a run that is brand-new
#   (a different id from whatever was newest when this script started) or still in flight, so it
#   never latches onto a previous run's already-terminal result. `-u` is required: Python buffers
#   stdout when it isn't a TTY (piped into
#   Monitor/activity-monitor.sh/tee), so without it every print() below sits in a buffer instead of
#   appearing as it happens -- confirmed directly, a first version without `-u` produced zero
#   visible output for several minutes of a real run despite the run itself progressing normally.
# Uses: python3 (stdlib only -- json, os, sys, time, urllib.request).
# Env: DAGU_UI_PORT (default 8082, the proxy sidecar's published port) -- may be exported directly
#   in the calling shell if the sidecar was started on a non-default port. DAGU_RUN_ID (set
#   automatically by run.sh, which assigns it via `dagu start -r`) -- the exact run to watch; when
#   absent (monitor run by hand against a UI-triggered run) it falls back to guessing the newest
#   non-terminal run.
# Input: Dagu's local REST API (GET /api/v1/dag-runs, GET /api/v1/dag-runs/ci/<id>).
# Outputs: one AGENTIC_SUCCESS_BLOCK/AGENTIC_ERROR_BLOCK/AGENTIC_SKIP_BLOCK JSON line per step
#   reaching "succeeded", a failure-shaped terminal status (failed/cancelled/partially_succeeded),
#   or "skipped" (its own real status -- a precondition wasn't met, e.g. --unit-only leaves
#   integration/e2e/sonar genuinely un-run, distinct from either done or still-pending). Also a
#   "... still running: <steps>" heartbeat line roughly every HEARTBEAT_INTERVAL_SECONDS
#   whenever nothing transitioned in that window (keeps raw.log growing so
#   scripts/activity-monitor/run.sh's own generic silence-stall check doesn't fire during a long,
#   healthy step with no container-state check of its own, e.g. sonar), plus a
#   "Watching Dagu run <id>" line at the start and a "RUN <status> (Dagu run <id>)" line at the
#   end for a human reading stdout directly.
# Returns: 0 if the run's own final status is succeeded; 1 for any other terminal status
#   (failed, partially_succeeded, cancelled), if no fresh run appears within
#   FRESH_RUN_WAIT_SECONDS, or if the Dagu REST API fails MAX_CONSECUTIVE_FETCH_FAILURES polls in
#   a row (transient single failures are tolerated and retried).
# ────────────────────────────────────────────────────────────────────────────
import json
import os
import sys
import time
import urllib.error
import urllib.request

DAGU_UI_PORT = os.environ.get("DAGU_UI_PORT", "8082")
BASE_URL = f"http://localhost:{DAGU_UI_PORT}/api/v1"
TERMINAL_STATUSES = {"succeeded", "failed", "partially_succeeded", "cancelled"}
NON_RUNNING_STEP_STATUSES = TERMINAL_STATUSES | {"not_started", "skipped"}
POLL_INTERVAL_SECONDS = 10
HEARTBEAT_INTERVAL_SECONDS = 60
FETCH_TIMEOUT_SECONDS = 30
# run.sh triggers `dagu start` detached, then starts this script -- the new run can take a few
# seconds to register. Wait this long for a run that is either brand-new (different id from the
# one that was newest at startup) or still in flight, so we never latch onto a previous run's
# already-terminal result.
FRESH_RUN_WAIT_SECONDS = 120
# Tolerate transient REST blips (proxy restart, Dagu momentarily busy) without crashing the whole
# monitor -- only give up after this many consecutive failed polls.
MAX_CONSECUTIVE_FETCH_FAILURES = 12

START_TIME = time.monotonic()


def fetch(url):
    with urllib.request.urlopen(url, timeout=FETCH_TIMEOUT_SECONDS) as response:
        return json.load(response)


def latest_run_id():
    runs = fetch(f"{BASE_URL}/dag-runs?name=ci&limit=1").get("dagRuns", [])
    return runs[0]["dagRunId"] if runs else None


def run_status_label(run_id):
    return fetch(f"{BASE_URL}/dag-runs/ci/{run_id}").get("dagRunDetails", {}).get("statusLabel")


def wait_for_run(run_id):
    """Wait up to FRESH_RUN_WAIT_SECONDS for a run with this exact id to register with Dagu
    (a detached `dagu start -r <id>` takes a few seconds to appear in the API), then return it."""
    deadline = time.monotonic() + FRESH_RUN_WAIT_SECONDS
    while time.monotonic() < deadline:
        try:
            if run_status_label(run_id):
                return run_id
        except (urllib.error.URLError, OSError, ValueError):
            pass
        time.sleep(2)
    return None


def await_fresh_run_id():
    """Fallback when run.sh did not pass an explicit DAGU_RUN_ID (e.g. the monitor is run by hand
    against a run triggered from Dagu's own web UI). Guesses the run to watch -- the one that
    appeared after (or is still running since) monitoring started. Can misfire when a *previous*
    run is still in flight and this one hasn't registered yet, which is exactly why run.sh assigns
    an explicit id instead."""
    try:
        baseline = latest_run_id()
    except (urllib.error.URLError, OSError, ValueError):
        baseline = None
    deadline = time.monotonic() + FRESH_RUN_WAIT_SECONDS
    while time.monotonic() < deadline:
        try:
            current = latest_run_id()
            if current and (current != baseline or run_status_label(current) not in TERMINAL_STATUSES):
                return current
        except (urllib.error.URLError, OSError, ValueError):
            pass
        time.sleep(2)
    return None


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


explicit_run_id = os.environ.get("DAGU_RUN_ID")
if explicit_run_id:
    run_id = wait_for_run(explicit_run_id)
    if not run_id:
        print(f"Dagu run {explicit_run_id} did not register within {FRESH_RUN_WAIT_SECONDS}s.")
        sys.exit(1)
else:
    run_id = await_fresh_run_id()
    if not run_id:
        print(f"No fresh 'ci' DAG run appeared within {FRESH_RUN_WAIT_SECONDS}s of monitoring start.")
        sys.exit(1)

print(f"Watching Dagu run {run_id}")
# Persistent header line for scripts/activity-monitor.sh's tree.txt (agentic profile) -- so every
# rendered tree, at every state, names which Dagu run it is.
print(f"AGENTIC_CONTEXT: Dagu run {run_id}")
last_status = {}
last_output_at = time.monotonic()
consecutive_failures = 0

while True:
    try:
        run = fetch(f"{BASE_URL}/dag-runs/ci/{run_id}").get("dagRunDetails", {})
        consecutive_failures = 0
    except (urllib.error.URLError, OSError, ValueError) as exc:
        consecutive_failures += 1
        if consecutive_failures >= MAX_CONSECUTIVE_FETCH_FAILURES:
            print(f"Giving up: {consecutive_failures} consecutive failed polls of the Dagu REST API ({exc}).")
            sys.exit(1)
        time.sleep(POLL_INTERVAL_SECONDS)
        continue
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
        print(f"RUN {run_status} (Dagu run {run_id})")
        sys.exit(0 if run_status == "succeeded" else 1)

    time.sleep(POLL_INTERVAL_SECONDS)
