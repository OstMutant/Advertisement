# Plan for people: db-normalization-module-decoupling

Purpose: the result for the person who decides: clear, short and easy to follow.

## Goal

A normalized database in which every module still talks to the others only through its `*Port`,
with no change to what the application does: the fewest changes, the most decoupling, the most
flexibility.

## The problem this solves

- Every link that crosses a module is a plain number with no foreign key: the owner pair
  `entity_type` + `entity_id` in 9 tables (attachment, attachment snapshot, audit log, contact info,
  contact view, feedback, feedback aggregate, taxon assignment, promotion placement) and every actor
  column (`created_by`, `author_id`, ...). Only code keeps them right.
- Rows were already left without an owner once (the reviews of a deleted provider profile), and
  nothing would notice the next time.
- The written description of these links is partly wrong and nobody checks it. Verified in the
  changelogs: `attachment.entity_id` and `attachment_snapshot.entity_id` say they point at
  `advertisement` only, although the table is meant for any owner. `contact_info.entity_id` and
  `promotion_placement.entity_id` miss the word `References`, so the Database ERD draws no line for
  them. `taxon_assignment.assigned_by` has no marker at all. The diagram also never shows a column
  added later by `addColumn`: `contact_view.revealed_value` is missing from the generated model.
- The rule "no foreign key into another module's table" is written only in decision records and
  per-module rule files. The root `CLAUDE.md` does not state it, and nothing checks it.

One point to clear up first: normalization in the strict sense (no repeated groups, no fact stored
twice) is not broken here. I found no such case in the changelogs. The only deliberate copy is
`feedback_aggregate`, which holds derived data and was decided as such. So the real gap is
**integrity across modules**: a link that the database does not guarantee. It is not the shape of
the tables.

## The means needed

Five ways to deal with the owner links were compared. They come from relational design guides
(GitLab, Bill Karwin's *SQL Antipatterns*), from frameworks (Rails Active Storage, Django,
Spring Data JDBC) and from modular-monolith and DDD practice (Microsoft, microservices.io,
Vaughn Vernon, Milan Jovanović):

| Approach | Integrity | Changes | Decoupling | Flexibility (new owner kind) |
|---|---|---|---|---|
| 1. A link table per owner kind, with real foreign keys (GitLab, Karwin) | guaranteed by the database | very large: about 17 link tables today (10 without the audit history), each with a foreign key into another module, plus a fixed start order between modules | lost: a module can no longer run without its neighbours | a new table and new SQL for every new owner kind |
| 2. One registry table of owners that the other tables point to (Karwin's "common super-table") | guaranteed from child to registry; registry to owner still held by code | large: a new shared table, every owner registers and unregisters, a migration that reads every owner table | one hub that every module depends on | good: no child table changes |
| 3. One nullable foreign key column per owner kind, plus a check that exactly one is set ("exclusive arc") | guaranteed | large, and it grows with every owner kind | lost, as in 1 | poor: every child table changes |
| 4. **Keep the owner pair (a reference by identity) and harden it**: a checked description, a test that no row is left without its owner, and later a purpose column | detected by a test and a check, not prevented by the database | small: metadata, the generator script, tests, rules | kept as it is today (the most) | kept as it is today (a new enum value) |
| 5. Bring back a real foreign key from actor columns to `user_information`, with RESTRICT | guaranteed for actor columns | medium | reverses an accepted decision: the user module could no longer run its changelog alone | n/a |

**Proposal (the brief asks which approach fits best): approach 4.** Reasons:

- It is the only approach that keeps every hard rule of the project: starters stay independent, the
  orchestrator never touches SQL, and every link still goes through the ports.
- The sources that face exactly this setup (one module owns its data, others reach it through its
  API) all choose a reference by identity and hold integrity in the application: Microsoft,
  microservices.io, Vernon, Spring Data JDBC (which lists "no foreign key" as a valid option) and
  Jovanović. Rails Active Storage and Django ship the same owner pair as a standard feature.
- The one article that argues for keeping foreign keys across modules (dev.to) says to drop them
  when a module must rebuild its tables alone, and that is this project's rule. The same article
  lists what has to replace the guarantee: a delete rule on the owner's side (the orchestrator's
  cascades already exist), a scheduled check for orphans, and a written record of the link (the
  `remarks=` marker). Approach 4 adds the two pieces still missing: the check, and enforcement of
  the record.
- Most of it already exists and is reused: `EntityExistenceService` already finds which owner ids
  exist, for each kind of owner. The integration tests already run the orchestrator against a real
  Postgres. The generator already reads the marker, including the form with several targets.

**What approach 4 gives up:** the database itself will not refuse a row without an owner. A bug in
a delete path is found by the test, or by the check if you choose the scheduled one, not prevented
at write time. This is the price of starters that run without their neighbours.

## What the result will look like

Nothing changes in what the application does. Phase A changes only column comments, the diagram
generator, tests and rule text:

1. Every cross-module reference column carries the correct marker. The Database ERD then draws
   every link, including the two it misses today and the attachment's generic owner.
2. Generating the architecture map **fails, naming the column**, in two cases: a reference-shaped
   column without the marker, and a foreign key into another module's table. The diagram also
   learns columns added by `addColumn`.
3. The integration tests that delete an owner (provider profile, advertisement, user) end with one
   shared check: no row that must die with its owner points at an owner that is gone. History (the
   audit log and the gallery history) may outlive its owner.
4. The rule "foreign keys only inside a module; a reference across modules is a marked plain column
   and the owner's delete use case removes what must not outlive it" is written once in the root
   `CLAUDE.md`. One new decision record states it, and the stale line in the integration-tests rules
   is corrected.

Phase B (optional, needs your yes): remove a start-order leftover. The advertisement and
provider-profile modules still wait for the user module's database setup (`@DependsOn("userLiquibase")`),
although no foreign key between them has existed since the change that removed them.

Phase C (only with the avatar task, needs your yes): a `purpose` column on `attachment` and
`attachment_snapshot`, `GALLERY` by default, so one owner can have a gallery and an avatar. This is
the Rails Active Storage pattern, where each attachment row is named (`avatar`, `images`). Rows that
already exist read `GALLERY` with no copying, because PostgreSQL stores the default without
rewriting the table.

### Rules and decision records: before and after

| Where | Before | After |
|---|---|---|
| `CLAUDE.md`, guideline 7 "Starter independence" | Says each starter owns its changelog and that changelogs are never merged. Says nothing about foreign keys. | Adds: a foreign key may reference only a table created in the same starter's changelog. A reference to another module's row is a plain column whose `remarks=` carries the no-FK marker. The owner's delete use case in `marketplace-orchestrator` removes, through the other modules' ports, the rows that must not outlive the owner. |
| `.claude/skills/module-doc-standards/SKILL.md`, "No-FK reference columns" | "a missing or misworded marker silently drops that relationship from the diagram instead of raising an error" | A missing marker on a reference-shaped column (the generator's name pattern), or a foreign key into another module, fails the generator with the column named. A column with any other name still drops silently. |
| `.claude/rules/marketplace-orchestrator.md`, "Key constraints" | No general rule. Each cascade is described per service. | One new bullet: an owner's delete use case clears every owned row of other modules in its own transaction. History rows stay. The shared integrity check in `integration-tests` covers it. |
| `.claude/rules/integration-tests.md`, lines 58-59 | "`advertisement.created_by` is `NOT NULL` with an FK to `user_information.id`" | The example is corrected: no such foreign key has existed since it was removed. The reason given for the module (one shared user fixture) is kept. |
| Decision records | The "no cross-module FK" rule is spread over the attachment, marketplace-app and platform-commons records. Nothing checks the marker. | One new record (through `/record-decision`): owner references stay references by identity, with the checks above and the four approaches it rejects. The ERD marker record in `docs/architecture/scripts` gets a note that the marker is now checked. If Phase C runs, the attachment "generic owner" record gets a note about `purpose`. |

## Cost

| Phase | Steps | Executor | Estimated effort | Estimated tokens for the executor |
|---|---|---|---|---|
| A | marker changesets (4 files + 3 master includes) | haiku | 15 min | about 30 k |
| A | generator checks + `addColumn` parsing | sonnet | 1-2 h | about 150-200 k |
| A | integrity check in integration tests | sonnet | 1-2 h | about 150-250 k |
| A | rule text edits | haiku (exact text) + sonnet (the skill paragraph, which depends on the generator pattern) | 30 min | about 40 k + 40 k |
| A | decision record via `/record-decision` | sonnet | 20 min | about 60 k |
| B | remove two `@DependsOn` lines | haiku | 10 min + a full test run | about 15 k |
| C | `purpose` column: schema, port, S3 path, history (design) | opus, then sonnet/haiku | 1-2 days, as part of the avatar task | about 400-800 k |

The full test runs (`build-and-test.sh --unit --integration --sandbox`, and Playwright only if
Phase B or C runs) take wall-clock time, not tokens.

## Risks

- **The check sees only names.** The generator recognizes reference columns by a name pattern, so a
  reference with an unusual name is still missed. The integration check covers the owner pair
  tables by their content, not by name.
- **The test covers only the delete flows it runs.** A new owner kind, or a new owned table, must be
  added to the check's list. The rule text says so, and the check fails loudly on an unknown
  `entity_type`, so a new kind cannot slip past silently.
- **`contact_view` rows outlive their owner today** (only `contact_info` is deleted). If you decide
  they are owned data, the check fails at once and a small cascade is needed, which changes
  behavior. That is why it is your decision (below).
- **Overlap with improvement-240.** Its finding "the ERD no-FK marker is not enforced" proposes the
  same generator check and the same promotion marker fix. Only one task should carry them.
- **Changeset style.** New changesets (roll forward) only add comments, so existing databases need
  no checksum handling.

## Where the project's practice differs from best practice

1. **Foreign keys across modules.** GitLab's database guide says "always use separate tables
   instead of polymorphic associations", and Karwin calls the owner pair an antipattern because
   integrity lives in code. The project forbids foreign keys across starters (attachment's generic
   owner, the removal of the last cross-starter FK, the feedback starter's own decision). Cost of
   the project's way: integrity is detected, not prevented. Cost of the best practice:
   approach 1 or 3 above, and starters lose independence. The plan keeps the project's way;
   decision 1 below.
2. **Liquibase changesets.** Liquibase recommends rolling forward with new changesets once a
   changeset is shared. The project has done both: editing unreleased changesets in place with
   `validCheckSum ANY` (user deletion, advertisement FK removal) and rolling forward (contact
   02/03). The plan rolls forward. Cost of editing in place instead: fewer files, but every local
   database has to be rebuilt or checksum-cleared.
3. **The no-FK rule is not in the root rules.** Best practice (Jovanović, Spring Modulith) is to make
   a boundary rule checkable by a test. Here it lives only in decision records. The plan writes it
   into `CLAUDE.md` and adds the generator check.

## Decisions waiting for you

1. **Keep references by identity across modules (approach 4), or allow foreign keys across
   starters (approaches 1, 2, 3 or 5)?** The plan is written for approach 4. Any other choice
   reverses the starter-independence rule and needs a new plan.
2. **Who carries the generator check and the promotion marker fix: this work or
   improvement-240?**
3. **`contact_view`: history (may outlive its owner, as now) or owned data (deleted with the
   owner, which is a behavior change)?**
4. **Integrity check: in the integration tests only (proposed: no runtime change), or also a
   scheduled job in the application that logs orphans (the dev.to advice; it adds new runtime
   behavior and one port method per owned table)?**
5. **Phase B: remove the `@DependsOn("userLiquibase")` leftover now?**
6. **Phase C: add the `purpose` column now, or with the avatar task (proposed: with the avatar task,
   since nothing reads it before then)?**
7. **Changesets: roll forward (proposed), or edit the existing changesets in place?**
