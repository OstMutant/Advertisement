Find and read named screenshots from the last Playwright test run.

Usage:
  /screenshots              — list all named screenshots from the report
  /screenshots <pattern>    — find and read screenshots whose name matches the pattern

---

## Step 1 — Extract name→file mapping from the HTML report

The Playwright HTML report embeds a ZIP archive in a base64 `<script>` block.
Run this Python snippet to extract the mapping:

```bash
python3 - <<'EOF'
import re, base64, zipfile, io, sys

pattern = sys.argv[1] if len(sys.argv) > 1 else ""

with open('/app/playwright/pw-report/index.html', 'rb') as f:
    content = f.read()

scripts = re.findall(rb'<script[^>]*>(.*?)</script>', content, re.DOTALL)
b64_script = next(s for s in scripts if s.strip().startswith(b'window.playwrightReportBase64'))
m = re.search(rb'base64,([A-Za-z0-9+/=]+)"', b64_script)
raw = base64.b64decode(m.group(1))
z = zipfile.ZipFile(io.BytesIO(raw))

results = []
for name in z.namelist():
    if not name.endswith('.json') or name == 'report.json':
        continue
    text = z.read(name).decode('utf-8', errors='replace')
    for attach_name, path in re.findall(r'"name":"([^"]+)","[^}]*"path":"(data/[a-f0-9]+\.png)"', text):
        if not pattern or pattern.lower() in attach_name.lower():
            results.append((attach_name, '/app/playwright/pw-report/' + path))

seen = set()
for n, p in results:
    if p not in seen:
        seen.add(p)
        print(f"{n}\t{p}")
EOF
```

Pass the pattern as `sys.argv[1]` — e.g. `python3 - taxon` to filter by "taxon".

---

## Step 2 — Read the matching PNGs

For each match, use the Read tool on the file path returned above.

If listing all (no pattern): print the full name→path table and stop — do not read everything.
If pattern given: read all matching PNGs (deduplicated by path) for visual inspection.

---

## Notes

- The report is at `/app/playwright/pw-report/index.html` — updated after every test run.
- Multiple tests may attach the same logical screenshot name; deduplication by path avoids duplicates.
- If the report is missing, tell the user to run `/playwright` first.
