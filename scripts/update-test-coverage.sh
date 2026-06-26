#!/usr/bin/env bash
# Parse Playwright run log and update docs/test-coverage.md.
# Called automatically by playwright/run.sh after each test run.
#
# Usage:
#   bash scripts/update-test-coverage.sh /tmp/pw-live.log

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOG_FILE="${1:-/tmp/pw-live.log}"
COVERAGE="$ROOT/docs/test-coverage.md"
DATE="$(date +%Y-%m-%d)"

if [ ! -f "$LOG_FILE" ]; then
  echo "[test-coverage] No log at $LOG_FILE — skipping."
  exit 0
fi

LOG_FILE="$LOG_FILE" COVERAGE="$COVERAGE" DATE="$DATE" python3 << 'PYEOF'
import os, re
from collections import defaultdict
from pathlib import Path

log_file = os.environ['LOG_FILE']
coverage_file = os.environ['COVERAGE']
date = os.environ['DATE']

# Each result line in pw-live.log:
#   ✓  1 e2e/04-spec.js › Advertisement flow › userEn creates advertisement — ... (45.2s)
#   ×  2 e2e/02-spec.js › Authentication flow › adminEn signs up — ... (5.1s)
#   -  3 e2e/05-spec.js › Seed data › ... (skipped) (0.0s)
pattern = re.compile(
    r'^([✓×\-])\s+\d+\s+(.+?\.spec\.js)\s+›\s+(.+?)\s+›\s+(.+?)\s+\(\d+[\.\d]*s\)\s*$'
)

entries = []
with open(log_file, encoding='utf-8') as f:
    for line in f:
        m = pattern.match(line.rstrip())
        if not m:
            continue
        icon, spec, group, name = m.groups()
        status = 'passed' if icon == '✓' else ('skipped' if icon == '-' else 'failed')
        entries.append((spec, group, name, status))

if not entries:
    print('[test-coverage] No test results found in log — skipping.')
    raise SystemExit(0)

# Organize: spec → group → [(name, status)]
coverage = defaultdict(lambda: defaultdict(list))
for spec, group, name, status in entries:
    coverage[spec][group].append((name, status))

# Stats
total   = len(entries)
passed  = sum(1 for *_, s in entries if s == 'passed')
failed  = sum(1 for *_, s in entries if s == 'failed')
skipped = sum(1 for *_, s in entries if s == 'skipped')

lines = [
    '# Playwright Test Coverage',
    '',
    f'Last updated: {date} · {passed} passed · {failed} failed · {skipped} skipped · {total} total',
    '',
    '`[x]` passed &nbsp; `[!]` failed &nbsp; `[-]` skipped',
    '',
]

for spec in sorted(coverage.keys()):
    lines.append(f'## {spec}')
    lines.append('')
    for group in coverage[spec]:
        lines.append(f'**{group}**')
        for name, status in coverage[spec][group]:
            marker = {'passed': '[x]', 'failed': '[!]', 'skipped': '[-]'}.get(status, '[ ]')
            lines.append(f'- {marker} {name}')
        lines.append('')

Path(coverage_file).parent.mkdir(parents=True, exist_ok=True)
Path(coverage_file).write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(f'[test-coverage] Updated {coverage_file} — {passed}/{total} passed.')
PYEOF
