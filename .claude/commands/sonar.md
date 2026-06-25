Run SonarQube static analysis for the marketplace app.

Steps:
1. Launch Monitor tool (persistent: true) watching /tmp/sonar.log every 10s:
   - If 2 minutes with no new output → report "process may be stuck"
   - If ERROR appears → report immediately
   - If EXECUTION SUCCESS or Analysis total time appears → report and call TaskStop on the monitor task
2. Run synchronously (timeout: 600000):
   ```
   bash scripts/sonar.sh 2>&1 | tee /tmp/sonar.log
   ```
3. After analysis completes — call TaskStop on the monitor task if not already stopped.
4. Report results URL: http://localhost:9099/dashboard?id=advertisement

Note: script uses docker cp internally — never use docker run -v.
