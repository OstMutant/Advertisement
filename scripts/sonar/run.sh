#!/bin/bash
# ── Header ──────────────────────────────────────────────────────────────────
# Description: Runs SonarQube static analysis against the whole Maven reactor -- ensures the
#   SonarQube server and scanner images/containers are current, builds all modules via
#   scripts/build-and-test.sh (no local Java needed -- compiled classes come from the shared
#   maven-cache volume, mounted directly into the scanner container), uploads the analysis, and
#   generates a local HTML report.
# Usage: bash scripts/sonar.sh [--no-gate]. --no-gate skips quality-gate blocking (always exits 0);
#   default blocks on a gate result of ERROR (exits non-zero).
# Uses: bash, docker (SonarQube server + scanner containers), scripts/build-and-test.sh, python3
#   (HTML report).
# Env: None.
# Input: sonar-project.properties, each module's src/main/java (copied from the host), the shared
#   maven-cache Docker volume's target-classes/<module> (mounted into the scanner container), the
#   running SonarQube server's own API.
# Outputs: analysis uploaded to http://localhost:9099/dashboard?id=advertisement (anonymous
#   browsing enabled every run -- see DECISIONS.md); local report at
#   scripts/sonar/report/report.html.
# Returns: 0 on success (or always, with --no-gate); non-zero if the quality gate result is ERROR.
# ────────────────────────────────────────────────────────────────────────────
set -e

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

NO_GATE=""
for arg in "$@"; do
  [ "$arg" = "--no-gate" ] && NO_GATE=1
done

LOG=/tmp/sonar.log
trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; echo "Last output:"; tail -20 "$LOG" 2>/dev/null; echo "Full log: $LOG"; exit $_rc' ERR

SONAR_URL="http://localhost:9099"
COMPOSE_FILE="$ROOT/scripts/sonar/docker-compose.sonar.yml"
SCANNER_CONTAINER="sonar-scanner"
PROPS_FILE="$ROOT/scripts/sonar/sonar-project.properties"

# ── Validate sonar-project.properties module list against pom.xml, auto-fix drift ────────────
# Keeps `newline=''` on read/write -- this repo's working tree is CRLF, and Python's default text
# mode would silently rewrite the whole file to LF, producing a spurious full-file diff.
echo "Validating sonar-project.properties module list against pom.xml..."
python3 - "$PROPS_FILE" "$ROOT" << 'PYEOF'
import re, sys

props_file = sys.argv[1]
pom_file = sys.argv[2] + "/pom.xml"

with open(pom_file) as f:
    pom = f.read()
modules_block = re.search(r"<modules>(.*?)</modules>", pom, re.S).group(1)
real_modules = re.findall(r"<module>([^<]+)</module>", modules_block)
real_modules = [m for m in real_modules if m != "integration-tests"]

with open(props_file, newline='') as f:
    lines = f.readlines()

def line_ending(l):
    return "\r\n" if l.endswith("\r\n") else "\n"

def rewrite_block(lines, key, suffix):
    start = None
    for i, l in enumerate(lines):
        if l.startswith(key + "="):
            start = i
            break
    if start is None:
        return lines, False
    nl = line_ending(lines[start])
    end = start
    while lines[end].rstrip("\r\n").endswith("\\"):
        end += 1
    new_lines = [f"{key}=\\{nl}"]
    for j, mod in enumerate(real_modules):
        suffix_line = f"  {mod}/{suffix}"
        if j < len(real_modules) - 1:
            new_lines.append(f"{suffix_line},\\{nl}")
        else:
            new_lines.append(f"{suffix_line}{nl}")
    old_block = lines[start:end + 1]
    if old_block == new_lines:
        return lines, False
    return lines[:start] + new_lines + lines[end + 1:], True

lines, changed1 = rewrite_block(lines, "sonar.sources", "src/main/java")
lines, changed2 = rewrite_block(lines, "sonar.java.binaries", "target/classes")

if changed1 or changed2:
    with open(props_file, "w", newline='') as f:
        f.writelines(lines)
    print("  sonar-project.properties module list updated to match pom.xml.")
else:
    print("  sonar-project.properties module list already matches pom.xml.")
PYEOF

# ── Ensure docker compose plugin is available ────────────────────────────────
source "$ROOT/scripts/ensure-docker-plugins.sh"
ensure_docker_compose

# ── Ensure SonarQube server image is current, container exists and is running (see DECISIONS.md) ──
echo "Checking SonarQube server image is up to date..."
docker compose -f "$COMPOSE_FILE" pull -q
docker compose -f "$COMPOSE_FILE" up -d

echo "Waiting for SonarQube to be ready..."
# A stale-image version jump can dead-end in DB_MIGRATION_NEEDED (embedded-DB, NOT_SUPPORTED) -- see DECISIONS.md.
MIGRATION_TRIGGERED=""
while true; do
  STATUS=$(curl -s "$SONAR_URL/api/system/status" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
  [ "$STATUS" = "UP" ] && break
  if [ "$STATUS" = "DB_MIGRATION_NEEDED" ] && [ -z "$MIGRATION_TRIGGERED" ]; then
    MIGRATION_TRIGGERED=1
    echo "SonarQube schema needs migrating after the image update — triggering..."
    MIGRATE_RESULT=$(curl -s -X POST "$SONAR_URL/api/system/migrate_db")
    if echo "$MIGRATE_RESULT" | grep -q '"state":"NOT_SUPPORTED"'; then
      echo "Migration not supported on the embedded database — wiping local scan history and starting fresh on the new image..."
      docker compose -f "$COMPOSE_FILE" down -v
      docker compose -f "$COMPOSE_FILE" up -d
    fi
  fi
  sleep 5
done
echo "SonarQube ready."

# ── Allow anonymous dashboard browsing -- resets to default (auth required) on every wipe/create ──
curl -s -u admin:admin -X POST "$SONAR_URL/api/settings/set" \
  -d "key=sonar.forceAuthentication&value=false" >/dev/null

# ── Ensure sonar token is valid; regenerate via admin/admin if not ───────────
# Strip a trailing \r (CRLF checkout) or it silently corrupts the Basic Auth header -- see DECISIONS.md.
CURRENT_TOKEN=$(grep "^sonar.token=" "$PROPS_FILE" | cut -d= -f2 | tr -d '\r')
if ! curl -s -u "$CURRENT_TOKEN:" "$SONAR_URL/api/authentication/validate" | grep -q '"valid":true'; then
  echo "Sonar token invalid or missing — generating new token..."
  NEW_TOKEN=$(curl -s -u admin:admin -X POST "$SONAR_URL/api/user_tokens/generate" \
    -d "name=claude-$(date +%s)" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  if [ -z "$NEW_TOKEN" ]; then
    echo "ERROR: failed to generate SonarQube token. Check admin credentials."
    exit 1
  fi
  sed -i "s|^sonar.token=.*|sonar.token=$NEW_TOKEN|" "$PROPS_FILE"
  echo "New token saved to sonar-project.properties."
fi

# ── Ensure scanner image is current, container exists and is running (see DECISIONS.md) ──
echo "Checking scanner image is up to date..."
docker pull -q sonarsource/sonar-scanner-cli:latest >/dev/null
LATEST_IMAGE_ID=$(docker image inspect -f '{{.Id}}' sonarsource/sonar-scanner-cli:latest)
CONTAINER_IMAGE_ID=$(docker inspect -f '{{.Image}}' "$SCANNER_CONTAINER" 2>/dev/null || true)
CONTAINER_STATUS=$(docker inspect -f '{{.State.Status}}' "$SCANNER_CONTAINER" 2>/dev/null || true)
if [ "$CONTAINER_IMAGE_ID" != "$LATEST_IMAGE_ID" ] || [ "$CONTAINER_STATUS" != "running" ]; then
  [ -n "$CONTAINER_STATUS" ] && docker rm -f "$SCANNER_CONTAINER" >/dev/null
  docker run -d --name "$SCANNER_CONTAINER" --network host \
    -v maven-cache:/root/.m2 \
    sonarsource/sonar-scanner-cli:latest sleep 86400 >/dev/null
fi

# Prune any now-dangling image left by either freshness check above (same as deploy-and-run.sh, see DECISIONS.md).
docker image prune -f >/dev/null

# ── Build all modules via build-and-test.sh (shared maven-cache volume, no local Java needed) ──
echo "Building modules via build-and-test.sh..."
# Distinct container name -- lets this run concurrently with a parallel Dagu DAG branch.
BUILD_CONTAINER_NAME="advertisement-build-only-sonar" bash "$ROOT/scripts/build-and-test.sh" --no-unit --no-integration

# ── Copy source files from host; compiled classes come from the mounted maven-cache volume ────
echo "Copying source files..."
docker exec --user root "$SCANNER_CONTAINER" rm -rf /tmp/sonar-src
docker exec "$SCANNER_CONTAINER" mkdir -p /tmp/sonar-src

for module in query-lib platform-commons audit-spring-boot-starter attachment-spring-boot-starter user-spring-boot-starter advertisement-spring-boot-starter taxon-spring-boot-starter provider-profile-spring-boot-starter marketplace-orchestrator marketplace-app; do
  if [ -d "$ROOT/$module/src/main/java" ]; then
    docker exec "$SCANNER_CONTAINER" mkdir -p "/tmp/sonar-src/$module/src/main/java"
    docker cp "$ROOT/$module/src/main/java/." "$SCANNER_CONTAINER:/tmp/sonar-src/$module/src/main/java/"
  fi
  if docker exec "$SCANNER_CONTAINER" test -d "/root/.m2/target-classes/$module"; then
    docker exec "$SCANNER_CONTAINER" mkdir -p "/tmp/sonar-src/$module/target/classes"
    docker exec "$SCANNER_CONTAINER" bash -c "cp -r /root/.m2/target-classes/$module/. /tmp/sonar-src/$module/target/classes/"
  fi
done

docker cp "$PROPS_FILE" "$SCANNER_CONTAINER:/tmp/sonar-src/sonar-project.properties"

# ── Run analysis ──────────────────────────────────────────────────────────────
# Real exit code read via PIPESTATUS, not `$?` (tee's) -- see DECISIONS.md.
# sonar.qualitygate.wait=true always stays on, even with --no-gate -- it's what makes the scanner
# block until the server has actually finished processing the just-uploaded report, not just a
# gate-blocking toggle. Without it, the HTML-report step below queries the issues API before the
# server has indexed anything, hits a timeout, and silently skips writing the report file
# (confirmed directly: exactly this happened with --no-gate before this fix). --no-gate's own
# contract (always exit 0, gate result informational only) is enforced separately at the bottom.
GATE_FLAG="-Dsonar.qualitygate.wait=true"

echo "Running SonarQube analysis..."
set +e
docker exec "$SCANNER_CONTAINER" sonar-scanner \
  -Dproject.settings=/tmp/sonar-src/sonar-project.properties \
  -Dsonar.projectBaseDir=/tmp/sonar-src \
  $GATE_FLAG 2>&1 | tee "$LOG"
EXIT_CODE=${PIPESTATUS[0]}
set -e

# ── Generate HTML report (runs inside sonar-scanner container) ────────────────
REPORT_DIR="$ROOT/scripts/sonar/report"
REPORT_FILE="$REPORT_DIR/report.html"
mkdir -p "$REPORT_DIR"

echo "Generating HTML report..."

SONAR_TOKEN=$(grep "^sonar.token=" "$PROPS_FILE" | cut -d= -f2 | tr -d '\r')

cat > /tmp/sonar-gen-report.py << 'PYEOF'
import urllib.request, urllib.error, json, datetime, os, base64

token = os.environ.get("SONAR_TOKEN", "")
url = "http://localhost:9099/api/issues/search?projectKeys=advertisement&ps=500&resolved=false&s=SEVERITY&asc=false"
req = urllib.request.Request(url)
if token:
    creds = base64.b64encode(f"{token}:".encode()).decode()
    req.add_header("Authorization", f"Basic {creds}")
try:
    with urllib.request.urlopen(req, timeout=10) as r:
        data = json.loads(r.read())
except Exception as e:
    print(f"  WARNING: could not fetch issues from SonarQube API: {e}")
    exit(0)

issues = data.get("issues", [])
total  = data.get("total", len(issues))
now    = datetime.datetime.now().strftime("%Y-%m-%d %H:%M")

SEV_COLOR = {
    "BLOCKER":  ("#c0392b", "#fff"),
    "CRITICAL": ("#e67e22", "#fff"),
    "MAJOR":    ("#f39c12", "#000"),
    "MINOR":    ("#2980b9", "#fff"),
    "INFO":     ("#7f8c8d", "#fff"),
}
TYPE_ICON = {"BUG": "🐛", "VULNERABILITY": "🔒", "CODE_SMELL": "👃"}

counts = {}
for i in issues:
    counts[i.get("severity","?")] = counts.get(i.get("severity","?"), 0) + 1

summary_cells = ""
for sev in ["BLOCKER","CRITICAL","MAJOR","MINOR","INFO"]:
    if sev in counts:
        bg, fg = SEV_COLOR[sev]
        summary_cells += f'<span style="background:{bg};color:{fg};padding:4px 10px;border-radius:4px;font-size:13px;font-weight:bold">{sev}: {counts[sev]}</span> '

rows = ""
for i in issues:
    sev  = i.get("severity", "")
    typ  = i.get("type", "")
    comp = i.get("component", "").split(":")[-1]
    line = i.get("line", "")
    msg  = i.get("message", "").replace("<","&lt;").replace(">","&gt;")
    rule = i.get("rule", "")
    bg, fg = SEV_COLOR.get(sev, ("#eee","#000"))
    icon = TYPE_ICON.get(typ, "")
    rows += f"""<tr>
      <td><span style="background:{bg};color:{fg};padding:2px 7px;border-radius:3px;font-size:12px;white-space:nowrap">{sev}</span></td>
      <td style="text-align:center">{icon}</td>
      <td style="font-family:monospace;font-size:12px;word-break:break-all">{comp}</td>
      <td style="text-align:center">{line}</td>
      <td>{msg}</td>
      <td style="font-size:11px;color:#777">{rule}</td>
    </tr>"""

html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>SonarQube Report — advertisement</title>
<style>
  body  {{ font-family: -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; margin: 32px; color: #222; }}
  h1    {{ font-size: 22px; margin-bottom: 4px; }}
  .meta {{ color: #777; font-size: 13px; margin-bottom: 20px; }}
  .summary {{ margin-bottom: 20px; display: flex; gap: 8px; flex-wrap: wrap; }}
  table {{ border-collapse: collapse; width: 100%; font-size: 13px; }}
  th    {{ background: #f4f4f4; border-bottom: 2px solid #ddd; padding: 8px 10px; text-align: left; white-space: nowrap; }}
  td    {{ border-bottom: 1px solid #eee; padding: 7px 10px; vertical-align: top; }}
  tr:hover td {{ background: #fafafa; }}
</style>
</head>
<body>
<h1>SonarQube — advertisement</h1>
<div class="meta">Generated: {now} &nbsp;|&nbsp; Total issues: {total}</div>
<div class="summary">{summary_cells}</div>
<table>
  <thead><tr><th>Severity</th><th>Type</th><th>File</th><th>Line</th><th>Message</th><th>Rule</th></tr></thead>
  <tbody>{rows}</tbody>
</table>
</body>
</html>"""

with open("/tmp/sonar-report.html", "w") as f:
    f.write(html)
print(f"  {len(issues)} issues shown, {total} total")
PYEOF

docker cp /tmp/sonar-gen-report.py "$SCANNER_CONTAINER":/tmp/sonar-gen-report.py
docker exec -e SONAR_TOKEN="$SONAR_TOKEN" "$SCANNER_CONTAINER" python3 /tmp/sonar-gen-report.py
docker cp "$SCANNER_CONTAINER":/tmp/sonar-report.html "$REPORT_FILE"

echo ""
echo "Analysis complete: $SONAR_URL/dashboard?id=advertisement"
echo "Local report:      $REPORT_FILE"

if [ "$EXIT_CODE" -ne 0 ] && [ -z "$NO_GATE" ]; then
  echo ""
  echo "=== QUALITY GATE FAILED (exit $EXIT_CODE) === (--no-gate to make this informational only)"
fi

# --no-gate always exits 0 -- gate result is informational only, per its own documented contract.
# GATE_FLAG (sonar.qualitygate.wait=true) still ran above regardless, so EXIT_CODE may be non-zero
# here even under --no-gate; that's expected and deliberately not surfaced as a failure.
[ -n "$NO_GATE" ] && exit 0
exit $EXIT_CODE
