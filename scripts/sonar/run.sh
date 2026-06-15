#!/bin/bash
set -e
# Usage:
#   bash /app/scripts/sonar/run.sh          — run SonarQube analysis
#
# SonarQube server starts automatically if not running (localhost:9099).
# Results: http://localhost:9099/dashboard?id=advertisement

LOG=/tmp/sonar.log
trap '_rc=$?; echo ""; echo "=== FAILED (exit $_rc) ==="; echo "Last output:"; tail -20 "$LOG" 2>/dev/null; echo "Full log: $LOG"; exit $_rc' ERR

SONAR_URL="http://localhost:9099"
COMPOSE_FILE="/app/scripts/sonar/docker-compose.sonar.yml"
SCANNER_CONTAINER="sonar-scanner"
PROPS_FILE="/app/scripts/sonar/sonar-project.properties"

# ── Ensure SonarQube server is running ───────────────────────────────────────
if ! curl -s -o /dev/null "$SONAR_URL/api/system/status"; then
  echo "SonarQube not running — starting..."
  docker compose -f "$COMPOSE_FILE" up -d
  echo "Waiting for SonarQube to be ready..."
  until curl -s "$SONAR_URL/api/system/status" | grep -q '"status":"UP"'; do
    sleep 5
  done
  echo "SonarQube ready."
fi

# ── Reuse scanner container if already running, otherwise start it ────────────
if ! docker inspect "$SCANNER_CONTAINER" &>/dev/null; then
  docker run -d --name "$SCANNER_CONTAINER" --network host \
    sonarsource/sonar-scanner-cli:latest sleep 86400
else
  STATUS=$(docker inspect -f '{{.State.Status}}' "$SCANNER_CONTAINER" 2>/dev/null)
  if [ "$STATUS" != "running" ]; then
    docker rm -f "$SCANNER_CONTAINER"
    docker run -d --name "$SCANNER_CONTAINER" --network host \
      sonarsource/sonar-scanner-cli:latest sleep 86400
  fi
fi

# ── Copy source and compiled files to container ───────────────────────────────
echo "Copying source files..."
docker exec --user root "$SCANNER_CONTAINER" rm -rf /tmp/sonar-src
docker exec "$SCANNER_CONTAINER" mkdir -p /tmp/sonar-src

for module in query-lib platform-commons audit-spring-boot-starter attachment-spring-boot-starter marketplace-app; do
  if [ -d "/app/$module/src/main/java" ]; then
    docker exec "$SCANNER_CONTAINER" mkdir -p "/tmp/sonar-src/$module/src/main/java"
    docker cp "/app/$module/src/main/java/." "$SCANNER_CONTAINER:/tmp/sonar-src/$module/src/main/java/"
  fi
  if [ -d "/app/$module/target/classes" ]; then
    docker exec "$SCANNER_CONTAINER" mkdir -p "/tmp/sonar-src/$module/target/classes"
    docker cp "/app/$module/target/classes/." "$SCANNER_CONTAINER:/tmp/sonar-src/$module/target/classes/"
  fi
done

docker cp "$PROPS_FILE" "$SCANNER_CONTAINER:/tmp/sonar-src/sonar-project.properties"

# ── Run analysis ──────────────────────────────────────────────────────────────
echo "Running SonarQube analysis..."
docker exec "$SCANNER_CONTAINER" sonar-scanner \
  -Dproject.settings=/tmp/sonar-src/sonar-project.properties \
  -Dsonar.projectBaseDir=/tmp/sonar-src 2>&1 | tee "$LOG"

EXIT_CODE=$?

# ── Generate HTML report (runs inside sonar-scanner container) ────────────────
REPORT_DIR="/app/scripts/sonar/report"
REPORT_FILE="$REPORT_DIR/report.html"
mkdir -p "$REPORT_DIR"

echo "Generating HTML report..."

SONAR_TOKEN=$(grep "^sonar.token=" "$PROPS_FILE" | cut -d= -f2)

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

exit $EXIT_CODE
