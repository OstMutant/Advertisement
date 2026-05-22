## SonarQube Analysis

All config lives in `/app/scripts/sonar/`. SonarQube server runs in Docker on `localhost:9099`.

### Start server manually (if needed)
```bash
docker compose -f scripts/sonar/docker-compose.sonar.yml up -d
```

### Run analysis
```bash
bash scripts/sonar.sh        # Linux / WSL
scripts\sonar.bat            # Windows
```

The script starts SonarQube automatically if not running, copies source files into a scanner container via `docker cp`, and runs `sonar-scanner-cli`. Results: `http://localhost:9099/dashboard?id=advertisement`.

**IMPORTANT:** Same Docker socket constraint as Playwright — never use `docker run -v`. The script uses `docker cp` internally.
