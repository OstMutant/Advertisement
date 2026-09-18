Find and read named screenshots from the last Playwright test run.

Usage:
  /screenshots              — list all named screenshots from the report
  /screenshots <pattern>    — find and read screenshots whose name matches the pattern

---

## Step 1 — Extract name→file mapping from the HTML report

The Playwright HTML report (playwright@1.61.1, the version this project pins) embeds a ZIP
archive as base64 inside a `<template id="playwrightReportBase64">` element — not inside a
`<script>` tag, and not as a `window.playwrightReportBase64 = "..."` assignment (an older report
shape that no longer matches; searching for it silently finds zero matches instead of erroring,
so this is easy to miss). Run this Python snippet to extract the mapping:

```bash
python3 - <<'EOF'
import re, base64, zipfile, io, sys

pattern = sys.argv[1] if len(sys.argv) > 1 else ""

with open('/app/playwright/pw-report/index.html', 'rb') as f:
    content = f.read()

m = re.search(rb'<template id="playwrightReportBase64">data:application/zip;base64,([A-Za-z0-9+/=]+)</template>', content)
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
