# Sources: architecture-map-services-card

Purpose: every source the planner used, written with add-source.sh.
Level: 0 is a direct source, 1 and 2 are reached by following references when sources conflict.

| link | taken | confirms or contradicts | date | type | level | confidence |
|---|---|---|---|---|---|---|
| https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/ | Compose reads .env from the project directory (first -f file's folder unless --project-directory); --env-file overrides the path; shell env wins over .env; ${VAR:?err} and ${VAR:-default} syntax | confirms | 2026-10-26 | official-doc | 0 | high |
| https://docs.docker.com/compose/how-tos/project-name/ | Default project name is the base name of the folder holding the compose file | confirms | 2026-10-26 | official-doc | 0 | high |
| https://docs.docker.com/reference/cli/docker/compose/ | Project name precedence: -p, COMPOSE_PROJECT_NAME, top-level name:, basename of the project directory; so --project-directory renames the project, --env-file does not | confirms | 2026-10-26 | official-doc | 0 | high |
| https://springdoc.org/ | Default paths: OpenAPI JSON /v3/api-docs (springdoc.api-docs.path), Swagger UI /swagger-ui.html (springdoc.swagger-ui.path, which serves /swagger-ui/index.html); the project's application.yml overrides neither | confirms | 2026-10-26 | official-doc | 0 | high |
| https://developer.mozilla.org/en-US/docs/Web/HTML/Attributes/rel/noopener | target=_blank implicitly gives rel=noopener behaviour; the map already uses plain target=_blank for its own links | confirms | 2026-10-26 | official-doc | 0 | high |
| https://gethomepage.dev/configs/docker/ | Alternative variant: service cards discovered at runtime from homepage.* labels on containers via the Docker socket; needs a live dashboard server, and here the app and Dagu proxy are started with docker run, not compose | contradicts | 2026-10-26 | official-doc | 0 | medium |
| https://12factor.net/config | Config (resource handles to backing services) lives in the environment, varies per deploy; supports one env file as the home of host ports | confirms | 2026-10-26 | official-doc | 0 | medium |
