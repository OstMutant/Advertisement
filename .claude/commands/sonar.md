Run SonarQube static analysis for the marketplace app.

Steps:
1. Run: `bash /app/scripts/sonar.sh`
2. Wait for completion (script starts SonarQube automatically if not running)
3. Report the results URL: http://localhost:9099/dashboard?id=advertisement

Note: script uses docker cp internally — never use docker run -v.
