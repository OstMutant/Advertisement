# improvement-036: Zero observability infrastructure — no actuator, no metrics, no structured logs

**Type:** improvement — observability. Migrated from `backlog/process-improvements.md` Part 3,
item 17.
**Module:** all Spring Boot modules (root `pom.xml` dependency, `application.yml` config)
**Priority:** medium — baseline expectation for any production service; **do not confuse with
improvement-023** (already done) — that added a request-correlation id (MDC `requestId`) and
closed several silent-logging gaps in individual services, but added no `actuator`, no metrics,
and no structured (JSON) log output. This issue is the still-open remainder.
**When:** independent, no blockers — genuinely still open despite improvement-023 landing

## Problem

No `spring-boot-starter-actuator` dependency exists in any module's pom. `/health` is a hand-rolled
custom endpoint, not actuator's. No metrics (request rates, JDBC pool stats, JVM stats) are
exposed anywhere. Logs are plain unstructured console text (now with a `requestId` MDC field
since improvement-023, but still plain text, not machine-parseable JSON). The first real
production incident would currently be debugged by grepping plain-text console output with no
metrics to correlate against.

## Suggested fix

- Add `spring-boot-starter-actuator` — gives `/actuator/health`, `/actuator/metrics`,
  `/actuator/env` for free; decide whether to keep or replace the existing custom `/health`
  endpoint (likely keep actuator's, since it's more standard and integrates with the metrics it
  also exposes).
- Enable structured JSON logging via the one-property switch already available in current Spring
  Boot: `logging.structured.format.console=ecs` (Elastic Common Schema) — no custom logback XML
  needed.
- Verify the existing `requestId` MDC field (improvement-023) still appears correctly once the log
  format switches from the current Logback pattern string to structured JSON.

## Related

- `backlog/process-improvements.md` Part 3, item 17 — source item, now superseded by this issue.
- `backlog/completed/issues/improvement-023-request-correlation-id-via-mdc.md` — the adjacent,
  already-completed work this issue is explicitly not a duplicate of.
