# Plan for people: optimize-table-references

## Goal

Less code to write, read, test and keep up around the database tables and the links between
modules (the entity type + entity id pair, the actor columns, the "owner" tables shared by several
kinds of owner, and clean-up code that exists only because the database does not enforce a rule),
without losing readability, type safety, testability or the module boundaries.

## The problem this solves

Three places carry code that does nothing for the running application:

1. **Feedback save** builds a throw-away object and keeps three helper variables just to reach the
   saved entry's id and its owner, in one 50-line method.
2. **A media-statistics query for one owner** in the attachment module has no caller in the
   application any more; only its own tests call it. It is a second copy of the "which photo is the
   main one" rule, the copy that once disagreed with the list version (a past bug).
3. **The "who assigned this category" column** (`taxon_assignment.assigned_by`) is never filled:
   the only production path always writes empty. Yet the column, the field, a method parameter, a
   test assertion and a pending documentation marker (in the database-integrity task) all exist
   for it.

Everything else in scope was checked and either is already as small as it can be within the
brief's limits or belongs to the database-integrity task (improvement-241).

## The means needed

- Two code-only changes inside one module each (feedback, attachment), one schema change inside the
  taxon module. No new dependency, no new class, no change to any interface other modules use.
- The existing build-and-test script with unit and integration tests.
- For the attachment change: a short note added to the existing decision that kept the method.
- For the taxon change: regenerating the architecture map (the column appears in it).

## What the result will look like

| # | Change | Lines (estimate) | Needs your decision first |
|---|---|---|---|
| 1 | Feedback save: one id variable instead of a throw-away object and an extra variable | about -10 | no |
| 2 | Attachment: delete the unused single-owner statistics query and its row reader; its tests move to the list query that the app really uses | about -45 code and tests | yes (A) |
| 3 | Taxon: drop the never-filled "assigned by" column, field, parameter and test assertion | about -15 | yes (B, C) |

Behavior seen by users, the REST API, the MCP tools and the other modules stays the same.

## Cost

| Step | Executor | Effort | Tokens (estimate) |
|---|---|---|---|
| 1 Feedback save | haiku | a few minutes | 15k-25k |
| 2 Attachment query | haiku | about 10 minutes | 25k-40k |
| 3 Taxon column | sonnet | about 20 minutes | 40k-70k |
| Final check (unit + integration, architecture map) | script, main thread | 15-25 minutes of build time | 5k-10k for reading results |

Total roughly 85k-145k tokens if all three are approved, 20k-35k if only step 1 is.

## Risks

- Step 1: the feedback edit path relies on the optimistic-lock version check inside the content
  save; the change keeps that call, only drops the unused result. Covered by the feedback service
  and repository integration tests.
- Step 2: the deleted query returned "empty statistics" for an owner with no photos, while the list
  query simply leaves that owner out. No production code relies on the single-owner form today
  (checked by searching the whole repository), so nothing changes for users; one test changes its
  expectation accordingly.
- Step 3: if the column is removed by editing the first taxon changelog in place, an already
  created local database keeps the old (empty) column until it is reset; a new changelog file
  removes it everywhere but has no automatic undo. If someone later wants to record who assigned a
  category, the column must come back together with a change to the taxon interface.

## Where the project's practice differs from best practice

1. **Entity type + id passed as two values.** Common refactoring advice (Fowler's "data clump",
   "Preserve Whole Object") says a pair that always travels together should be one object, and the
   project already has it (`EntityRef`). Most module interfaces (attachment, feedback, taxon,
   contact) still take the two values. Changing them is a change of the interfaces other modules
   call, which the brief does not allow; changing only the inside of the attachment module would
   add wrapping code, not remove it. Kept as is; a separate decision if you want it.
2. **User preferences have no foreign key to their user even inside the same module.** The project
   chose this on purpose (the actor-column convention, recorded decision on user preferences), so
   the user purge must delete preferences by hand. PostgreSQL's own documentation recommends
   "delete together" (ON DELETE CASCADE) for a table that is a part of another and cannot exist
   alone; that would remove the manual delete and close the risk of orphan rows (which happened
   once). It is allowed by the brief (same module), but it reverses a recorded decision.
3. **Feedback tables have foreign keys but no "delete together"**, so the hard-delete code removes
   each table explicitly. "Delete together" on the rating and the reply would remove two of the
   four deletes, but the text rows would still need explicit deletes and the project prefers
   explicit code. Low gain; listed only so you see it.
4. **The single-owner statistics query (step 2)** is kept today on purpose by a recorded decision
   and the attachment module's rules, because it has its own tests. Best practice is to delete code
   that only tests use.

## Decisions waiting for you

- **A.** Delete the unused single-owner statistics query in the attachment module (step 2), and
  add a note to the decision that kept it? Yes / no.
- **B.** Drop the never-filled "assigned by" column in the taxon module (step 3)? Yes / no / keep it
  and let improvement-241 only add its marker.
- **C.** If B is yes: remove it by editing the first taxon changelog in place (the project's
  pre-launch pattern) or by a new changelog file? Same question is already open in improvement-241.
- **D.** (Not planned, only if you want it.) Add an in-module "delete together" foreign key from
  user preferences to users and drop the manual delete, reversing the recorded decision?
- **E.** (Not planned.) Move the module interfaces to the single owner object (`EntityRef`)? This
  is a change of interfaces across modules and needs its own task.
- **F.** Five other findings from the earlier pass (test helpers, label stubs, chip rows) are real
  but not about tables or references; send them to the findings inventory?
