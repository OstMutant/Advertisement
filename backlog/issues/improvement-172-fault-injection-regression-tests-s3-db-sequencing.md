# improvement-172: Fault-injection regression tests for S3-vs-DB-transaction sequencing

**Type:** improvement — test coverage (preventive, no observed impact)
**Module:** `integration-tests` (new test class), `advertisement-spring-boot-starter`
  (`AdvertisementSaveService`), `user-spring-boot-starter`/`marketplace-orchestrator`
  (`UserDeleteService`)
**Priority:** low
**When:** independent, no blockers — opportunistic pickup, no trigger

## Current state

`AdvertisementSaveService` and `UserDeleteService` both have a deliberate, documented ordering
between a DB transaction and an external side effect (S3 attachment moves) — e.g.
`AdvertisementSaveService.save()` moves the attachment gallery commit to just before the
transaction's own commit, logging loudly if it rolls back after the S3 move anyway (see
`marketplace-app/DECISIONS.md`, and the historical bug this exact ordering was designed to
prevent, `backlog/completed/issues/improvement-069-attachment-s3-move-inside-db-transaction-orphans-on-rollback.md`).
Grep-confirmed: neither that completed issue nor the current `integration-tests` module has any
test that actually injects a failure at the risky sequencing point (e.g. S3 succeeds, DB commit
then fails, or vice versa) and asserts the system's real behavior — the fix shipped without a
regression test for the specific failure mode it was fixing.

## Why change

This gap was surfaced discussing whether a `data-integrity-reviewer` LLM review lens (drafted
while scoping `improvement-171`, later deleted from `.claude/agents/` to keep that issue's scope to
just `solid-dry-reviewer`) was worth keeping. Conclusion: for *already-known* risky flows like
these two services, a deterministic fault-injection integration test is strictly better than an
LLM guessing at diff-review time — repeatable, runs every CI pass, doesn't depend on a reviewer
noticing the pattern. An LLM review lens only earns its keep for *new*, not-yet-tested code
introducing this same shape; it can't substitute for a test on a flow already identified as risky.

## Expected benefit

A real regression test that would have caught `improvement-069`'s original bug if it reintroduces
itself (e.g. during a future refactor of either service) — currently nothing would catch a
regression here except manual review or production data corruption.

## Approach

Using `integration-tests`' existing Testcontainers Postgres setup, with a mocked/faulty
S3 client (or a MinIO instance perturbed via the container, matching whatever this project's
existing attachment integration tests already do for storage isolation):

1. `AdvertisementSaveService`: test that a DB failure *after* the S3 move still leaves the system
   in the logged, known-inconsistent-but-detected state the current design accepts (not silently
   lost) — assert the warning log fires, not that the inconsistency is prevented (it isn't, by
   design; the point is confirming detection still works).
2. `UserDeleteService`: same shape, for its own S3-touching cascade step if one exists — check the
   real current implementation first, this issue's own description may be imprecise about which
   step is S3-adjacent.
3. Consider whether a shared test helper (inject failure at point N of a `TransactionTemplate`
   block) is reusable enough to extract, given both services use the same pattern — decide once
   the first test is written, not upfront.

## Related

- `backlog/completed/issues/improvement-069-attachment-s3-move-inside-db-transaction-orphans-on-rollback.md`
  — the original bug and fix this issue adds regression coverage for.
- `backlog/issues/improvement-171-formalize-deep-review-agents.md` — the discussion that surfaced
  this gap while evaluating whether `data-integrity-reviewer` was worth keeping as an LLM lens.
- `marketplace-app/DECISIONS.md` — ADR-047 (`AdvertisementSaveService`'s S3-before-commit
  ordering), ADR-051 (`UserDeleteService`).
