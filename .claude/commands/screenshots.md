Read screenshots from the last Playwright test run. Use only when you need to visually inspect UI output.

Steps:
1. Copy report data from the container:
   ```
   docker cp pw-runner:/tmp/pw-report/data/ /tmp/pw-screenshots/
   ```
2. List PNGs sorted by modification time (chronological test order):
   ```
   ls -lt /tmp/pw-screenshots/*.png
   ```
3. Read the relevant PNG files using the Read tool for visual analysis.
