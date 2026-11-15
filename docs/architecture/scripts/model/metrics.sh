# shellcheck shell=bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Quality and run metrics: SonarQube measures (with an optional rescan), ArchUnit metrics, CI pipeline step metrics, JaCoCo/Surefire test reports and script run logs.
# Usage: sourced at top level by generate-architecture-model.sh in the order that script names, never run on its own.
# Uses: bash, curl, grep, find, python3, scripts/sonar.sh (only to start and scan SonarQube when it is not reachable).
# Env: None (reads REPO_ROOT and the variables set by the fragments sourced before it).
# Input: SonarQube at SONAR_URL (only with --with-sonar) and its token from scripts/sonar/sonar-project.properties, the ArchUnit and CI metrics files, build-and-test reports, scripts/logs.
# Outputs: defines ensure_sonar_fresh, sonar_metrics_json, archunit_metrics_json, ci_pipeline_metrics_json, test_reports_json, run_logs_json.
# Returns: 0 on source; each function returns the status of its last command.
# ────────────────────────────────────────────────────────────────────────────

# ── SonarQube: real code-quality metrics (ncloc/complexity/cognitive_complexity/code_smells,
# project-level duplication) from a running SonarQube server, via its stable
# /api/measures/component_tree endpoint (one call returns every real package-level DIR component's
# metrics; grouped and summed here into a per-module rollup, since SonarQube has no aggregate
# component for e.g. "advertisement-spring-boot-starter/src/main/java" itself -- only real leaf
# package directories are indexed). Gracefully degrades to null if the server isn't reachable even
# after ensure_sonar_fresh()'s attempt -- optional data, never blocks generation.
SONAR_URL="http://localhost:9099"
SONAR_PROPS="$REPO_ROOT/scripts/sonar/sonar-project.properties"

ensure_sonar_fresh() {
  [ -f "$SONAR_PROPS" ] || return 0
  local token
  token="$(grep '^sonar.token=' "$SONAR_PROPS" 2>/dev/null | cut -d= -f2 | tr -d '\r')"
  [ -z "$token" ] && return 0
  if ! curl -s --max-time 3 -o /dev/null "$SONAR_URL/api/system/status"; then
    echo "SonarQube not reachable -- running bash scripts/sonar.sh --no-gate to start/scan it..." >&2
    bash "$REPO_ROOT/scripts/sonar.sh" --no-gate >&2 || true
    return 0
  fi
  local analysis_date newest_java stamp_file
  analysis_date="$(curl -s -u "$token:" --max-time 5 "$SONAR_URL/api/project_analyses/search?project=advertisement&ps=1" 2>/dev/null | grep -oP '"date":"\K[^"]+' | head -1)"
  if [ -z "$analysis_date" ]; then
    echo "No SonarQube analysis found -- running bash scripts/sonar.sh --no-gate..." >&2
    bash "$REPO_ROOT/scripts/sonar.sh" --no-gate >&2 || true
    return 0
  fi
  stamp_file="$(mktemp)"
  touch -d "$analysis_date" "$stamp_file" 2>/dev/null
  newest_java="$(find "$REPO_ROOT" -name '*.java' -not -path '*/target/*' -not -path '*/.claude/worktrees/*' -newer "$stamp_file" 2>/dev/null | head -1)"
  rm -f "$stamp_file"
  if [ -n "$newest_java" ]; then
    echo "SonarQube data stale ($newest_java changed since last scan) -- rescanning via bash scripts/sonar.sh --no-gate..." >&2
    bash "$REPO_ROOT/scripts/sonar.sh" --no-gate >&2 || true
  fi
}

sonar_metrics_json() {
  local token
  [ -f "$SONAR_PROPS" ] && token="$(grep '^sonar.token=' "$SONAR_PROPS" 2>/dev/null | cut -d= -f2 | tr -d '\r')"
  if [ -z "${token:-}" ] || ! curl -s --max-time 3 -o /dev/null "$SONAR_URL/api/system/status"; then
    echo "null"
    return 0
  fi
  local analysis_date project_json tree_json file_counts mod count
  analysis_date="$(curl -s -u "$token:" --max-time 5 "$SONAR_URL/api/project_analyses/search?project=advertisement&ps=1" 2>/dev/null | grep -oP '"date":"\K[^"]+' | head -1)"
  project_json="$(curl -s -u "$token:" --max-time 10 "$SONAR_URL/api/measures/component?metricKeys=ncloc,complexity,cognitive_complexity,code_smells,duplicated_lines_density&component=advertisement" 2>/dev/null)"
  tree_json="$(curl -s -u "$token:" --max-time 15 "$SONAR_URL/api/measures/component_tree?component=advertisement&metricKeys=ncloc,complexity,cognitive_complexity,code_smells&qualifiers=DIR&ps=500" 2>/dev/null)"
  file_counts=""
  for mod in "${MODULES[@]}"; do
    [ -d "$REPO_ROOT/$mod/src/main/java" ] || continue
    count="$(find "$REPO_ROOT/$mod/src/main/java" -name '*.java' 2>/dev/null | wc -l | tr -d ' ')"
    file_counts="$file_counts$mod=$count,"
  done
  python3 -c "
import json, sys

analysis_date = sys.argv[1]
project = json.loads(sys.argv[2]) if sys.argv[2] else {}
tree = json.loads(sys.argv[3]) if sys.argv[3] else {}
modules = [m for m in sys.argv[4].split(',') if m]
file_counts = dict(p.split('=') for p in sys.argv[5].split(',') if p)

def get_measure(measures, key):
    for m in measures:
        if m['metric'] == key:
            return m['value']
    return None

proj_measures = project.get('component', {}).get('measures', [])
proj = {k: get_measure(proj_measures, k) for k in ['ncloc','complexity','cognitive_complexity','code_smells','duplicated_lines_density']}

per_module = {m: {'ncloc': 0, 'complexity': 0, 'cognitiveComplexity': 0, 'codeSmells': 0, 'javaFileCount': int(file_counts.get(m, 0))} for m in modules}
for c in tree.get('components', []):
    path = c.get('path', '')
    for m in modules:
        if path.startswith(m + '/'):
            for meas in c.get('measures', []):
                key = meas['metric']
                val = int(meas['value']) if meas['value'].isdigit() else 0
                if key == 'ncloc': per_module[m]['ncloc'] += val
                elif key == 'complexity': per_module[m]['complexity'] += val
                elif key == 'cognitive_complexity': per_module[m]['cognitiveComplexity'] += val
                elif key == 'code_smells': per_module[m]['codeSmells'] += val
            break

out = {
  'analysisDate': analysis_date,
  'project': {
    'ncloc': int(proj['ncloc']) if proj['ncloc'] else None,
    'complexity': int(proj['complexity']) if proj['complexity'] else None,
    'cognitiveComplexity': int(proj['cognitive_complexity']) if proj['cognitive_complexity'] else None,
    'codeSmells': int(proj['code_smells']) if proj['code_smells'] else None,
    'duplicatedLinesDensity': float(proj['duplicated_lines_density']) if proj['duplicated_lines_density'] else None,
  },
  'modules': per_module,
}
print(json.dumps(out))
" "$analysis_date" "$project_json" "$tree_json" "$(IFS=,; echo "${MODULES[*]}")" "$file_counts"
}

archunit_metrics_json() {
  if [ -f "$ARCHUNIT_METRICS_FILE" ]; then
    cat "$ARCHUNIT_METRICS_FILE"
  elif [ -f "$ARCHUNIT_METRICS_FILE_FALLBACK" ]; then
    cat "$ARCHUNIT_METRICS_FILE_FALLBACK"
  else
    echo "null"
  fi
}

# ── CI pipeline run metrics: each step's status/duration from the last `bash scripts/ci.sh` run,
# written by the `pipeline_metrics` step in scripts/ci/dagu/ci.yaml (queries Dagu's own API for
# its own just-finished run, `docker cp`s the result onto the host). Read here if present; null if
# no Dagu-backed CI run has ever finished -- same passive, no-auto-trigger shape as
# archunit_metrics_json() above, and this generator never talks to Dagu directly either way.
CI_PIPELINE_METRICS_FILE="$REPO_ROOT/scripts/ci/reports/pipeline-metrics.json"

ci_pipeline_metrics_json() {
  if [ -f "$CI_PIPELINE_METRICS_FILE" ]; then
    cat "$CI_PIPELINE_METRICS_FILE"
  else
    echo "null"
  fi
}

# Python helper shared by the test-report and run-log snippets: parses a key=value file into a dict.
PY_READ_INFO='
def read_info(p):
    kv = {}
    try:
        with open(p, encoding="utf-8", errors="replace") as f:
            for line in f:
                if "=" in line:
                    k, v = line.rstrip("\r\n").split("=", 1)
                    kv[k] = v
    except OSError:
        pass
    return kv
'

# JaCoCo coverage and Surefire results of the last plain build-and-test.sh run and of the CI unit, integration and Sonar stages (newest entry per id) as one JSON object.
test_reports_json() {
  python3 -c "$PY_READ_INFO"'
import json, sys, glob, os, datetime
import xml.etree.ElementTree as ET

root = sys.argv[1]
reports = os.path.join(root, "scripts/build-and-test/reports")
rel = "scripts/build-and-test/reports"

def utc(sec):
    return datetime.datetime.fromtimestamp(sec, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def info_of(path):
    return read_info(path) if os.path.isfile(path) else None

logs_dir = os.path.join(root, "scripts/logs/build-and-test")
plain_info = info_of(os.path.join(logs_dir, "build-info.txt"))
stage_infos = [i for i in (info_of(os.path.join(logs_dir, "advertisement-build-only-unit", "build-info.txt")),
                           info_of(os.path.join(logs_dir, "advertisement-build-only-integration", "build-info.txt")),
                           info_of(os.path.join(logs_dir, "advertisement-build-only-ci-sonar", "build-info.txt"))) if i]
stage_time = max((i.get("timestamp") or "" for i in stage_infos), default="")
build_info = None
if plain_info and (plain_info.get("timestamp") or "") >= stage_time:
    build_info = {"timestamp": plain_info.get("timestamp"), "runUnit": plain_info.get("RUN_UNIT"),
                  "runIntegration": plain_info.get("RUN_INTEGRATION"),
                  "unitTestArg": plain_info.get("UNIT_TEST_ARG"),
                  "integrationTestArg": plain_info.get("INTEGRATION_TEST_ARG")}
elif stage_infos:
    build_info = {"timestamp": stage_time,
                  "runUnit": "true" if any(i.get("RUN_UNIT") == "true" for i in stage_infos) else "false",
                  "runIntegration": "true" if any(i.get("RUN_INTEGRATION") == "true" for i in stage_infos) else "false",
                  "unitTestArg": next((i.get("UNIT_TEST_ARG") for i in stage_infos if i.get("RUN_UNIT") == "true"), ""),
                  "integrationTestArg": next((i.get("INTEGRATION_TEST_ARG") for i in stage_infos if i.get("RUN_INTEGRATION") == "true"), "")}

roots = [(reports, rel)] + [(os.path.join(reports, d), rel + "/" + d)
                            for d in ("advertisement-build-only-unit", "advertisement-build-only-integration", "advertisement-build-only-ci-sonar")]

coverage_by_id = {}
tests_by_id = {}
for base, base_rel in roots:
    for path in sorted(glob.glob(os.path.join(base, "jacoco", "*.xml"))):
        mid = os.path.splitext(os.path.basename(path))[0]
        try:
            rt = ET.parse(path).getroot()
        except Exception as e:
            print("skipped unparsable " + path + ": " + str(e), file=sys.stderr)
            continue
        cnt = {"LINE": (0, 0), "BRANCH": (0, 0)}
        for c in rt.findall("counter"):
            if c.get("type") in cnt:
                cnt[c.get("type")] = (int(c.get("covered")), int(c.get("missed")))
        dumps = [int(si.get("dump")) for si in rt.findall("sessioninfo") if si.get("dump")]
        entry = {"id": mid, "lineCovered": cnt["LINE"][0], "lineMissed": cnt["LINE"][1],
                 "branchCovered": cnt["BRANCH"][0], "branchMissed": cnt["BRANCH"][1],
                 "runAt": utc(max(dumps) / 1000) if dumps else None,
                 "html": base_rel + "/jacoco/" + mid + "/index.html"}
        old = coverage_by_id.get(mid)
        if old is None or (entry["runAt"] or "") > (old["runAt"] or ""):
            coverage_by_id[mid] = entry

    sf_root = os.path.join(base, "surefire")
    for mid in sorted(d for d in os.listdir(sf_root) if os.path.isdir(os.path.join(sf_root, d))) if os.path.isdir(sf_root) else []:
        if mid.startswith("advertisement-build-only") or mid == "it-mirror":
            continue
        files = sorted(glob.glob(os.path.join(sf_root, mid, "TEST-*.xml")))
        tot = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
        secs = 0.0
        newest = None
        failing = []
        for path in files:
            try:
                ts = ET.parse(path).getroot()
            except Exception as e:
                print("skipped unparsable " + path + ": " + str(e), file=sys.stderr)
                continue
            for k in tot:
                tot[k] += int(ts.get(k, 0))
            secs += float(ts.get("time", 0))
            mt = os.path.getmtime(path)
            newest = mt if newest is None else max(newest, mt)
            if int(ts.get("failures", 0)) + int(ts.get("errors", 0)) > 0:
                failing.append({"name": ts.get("name"), "txt": base_rel + "/surefire/" + mid + "/" + ts.get("name") + ".txt"})
        if newest is None:
            continue
        failing.sort(key=lambda x: x["name"])
        entry = {"id": mid, **tot, "time": round(secs, 1), "runAt": utc(newest), "failing": failing}
        old = tests_by_id.get(mid)
        if old is None or entry["runAt"] > old["runAt"]:
            tests_by_id[mid] = entry

coverage = [coverage_by_id[k] for k in sorted(coverage_by_id)]
tests = [tests_by_id[k] for k in sorted(tests_by_id)]

print(json.dumps({"buildInfo": build_info, "coverage": coverage, "tests": tests}))
' "$REPO_ROOT"
}

# Run logs grouped by folder under scripts/logs and scripts/ci/reports; a run's ID is the session ID its script wrote.
run_logs_json() {
  python3 -c "$PY_READ_INFO"'
import json, sys, os, datetime

root = sys.argv[1]
ROOTS = ["scripts/logs", "scripts/ci/reports"]
NOTES = {"unit-tests.log": "Unit tests output", "integration-tests.log": "Integration tests output",
         "archunit-metrics.log": "ArchUnit metrics export output", "build-info.txt": "Run parameters",
         "run.log": "Run output", "orchestrator.log": "Parallel orchestrator output",
         "build-and-test-session.log": "Whole build-and-test session",
         "pipeline-metrics.json": "CI pipeline metrics", "dagu-metrics.md": "CI run report from /ci --metrics"}

def utc(sec):
    return datetime.datetime.fromtimestamp(sec, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

def mtime(p):
    try:
        with open(p, "rb"):
            pass
        return os.path.getmtime(p)
    except OSError as e:
        print("skipped unreadable " + p + ": " + str(e), file=sys.stderr)
        return None

groups = []
for base in ROOTS:
    base_dir = os.path.join(root, base)
    if not os.path.isdir(base_dir):
        continue
    for dirpath, dirnames, filenames in os.walk(base_dir):
        # Screenshots of the page-shot mode are images, not run logs.
        dirnames[:] = sorted(d for d in dirnames if d != "shots")
        rel = os.path.relpath(dirpath, root).replace(os.sep, "/")
        files = []
        for n in sorted(filenames):
            if n.startswith("."):
                continue
            m = mtime(os.path.join(dirpath, n))
            if m is not None:
                files.append({"name": n, "path": rel + "/" + n, "note": NOTES.get(n, ""), "mtime": m})
        if not files:
            continue
        runs = []
        session = read_info(os.path.join(dirpath, "session-info.txt")).get("SESSION_ID")
        stamp = read_info(os.path.join(dirpath, "build-info.txt")).get("timestamp")
        if stamp:
            runs.append({"id": session or "-", "startedAt": stamp, "files": [{"path": f["path"], "note": f["note"]} for f in files]})
        else:
            for f in files:
                runs.append({"id": session or "-", "startedAt": utc(f["mtime"]), "files": [{"path": f["path"], "note": f["note"]}]})
        runs.sort(key=lambda r: r["startedAt"], reverse=True)
        groups.append({"folder": rel, "runs": runs})

groups.sort(key=lambda g: g["folder"])
groups.sort(key=lambda g: g["runs"][0]["startedAt"], reverse=True)
print(json.dumps(groups))
' "$REPO_ROOT"
}

