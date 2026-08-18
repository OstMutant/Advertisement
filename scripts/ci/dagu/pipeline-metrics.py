#!/usr/bin/env python3
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Summarizes the just-finished Dagu "ci" DAG run into a small, generator-friendly
#   JSON file (name/status/duration per step). Called by the pipeline_metrics step in
#   scripts/ci/dagu/ci.yaml -- kept as a real .py file rather than an inline `python3 -c`
#   one-liner in the YAML, since embedding multi-line Python inside a YAML block scalar hits two
#   incompatible indentation rules at once (YAML block scalars need every line indented at least
#   as much as the block's own base; Python top-level statements must not be indented at all).
# Usage: python3 scripts/ci/dagu/pipeline-metrics.py (always via the pipeline_metrics DAG step,
#   never invoked directly).
# Uses: python3 (stdlib only -- json, os, urllib.request, datetime).
# Env:
#   Set automatically by Dagu itself for every step, never exported directly by a user:
#     DAGU_PORT, DAG_NAME, DAG_RUN_ID -- identify which run's own API endpoint to query.
# Input: Dagu's local REST API (GET /api/v1/dag-runs/{name}/{run-id}).
# Outputs: a JSON summary (generatedAt/dagRunId/steps[]) printed to stdout -- the calling DAG step
#   redirects it to scripts/ci/reports/pipeline-metrics.json.
# Returns: 0 on success; a Python traceback + non-zero if the API call fails (e.g. Dagu itself
#   unreachable).
# ────────────────────────────────────────────────────────────────────────────
import json
import os
import urllib.request
from datetime import datetime

DAGU_PORT = os.environ["DAGU_PORT"]
DAG_NAME = os.environ["DAG_NAME"]
DAG_RUN_ID = os.environ["DAG_RUN_ID"]
TIME_FORMAT = "%Y-%m-%dT%H:%M:%SZ"


def duration_seconds(started, finished):
    if not started or not finished:
        return None
    try:
        return (datetime.strptime(finished, TIME_FORMAT) - datetime.strptime(started, TIME_FORMAT)).total_seconds()
    except ValueError:
        return None


url = f"http://localhost:{DAGU_PORT}/api/v1/dag-runs/{DAG_NAME}/{DAG_RUN_ID}"
with urllib.request.urlopen(url) as response:
    run = json.load(response).get("dagRunDetails", {})

steps = [
    {
        "name": node.get("step", {}).get("name"),
        "status": node.get("statusLabel"),
        "durationSeconds": duration_seconds(node.get("startedAt"), node.get("finishedAt")),
    }
    for node in run.get("nodes", [])
]

print(json.dumps({
    "generatedAt": run.get("finishedAt"),
    "dagRunId": run.get("dagRunId"),
    "steps": steps,
}, indent=2))
