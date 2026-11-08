# Plan for executors: db-normalization-module-decoupling

Purpose: the result for the AI that executes it, with the detail each step's executor model needs
(exact content for a weaker model, exact files and behavior for a stronger one).

Chosen approach (pending the person's decision 1 in `plan-human.md`): owner references stay
**references by identity** (`entity_type` + `entity_id`, actor columns), with no foreign key across
starters. Integrity is held by the owner's delete use case in `marketplace-orchestrator` (already
present), and two things are added: a mechanical check of the documented link, and an integrity
check in `integration-tests`. Phase A changes no runtime behavior. Phases B and C run only after
the person says yes to decisions 5 and 6.

Project rules that bind every step: the Approval Rule in `.claude/rules.md` (present each step and
wait for a yes), `git add` after each file change, no commit, English only, one-line code
comments, no ticket or ADR numbers in comments or markdown.

## Steps

### Step 1. Correct the no-FK markers with roll-forward changesets (Phase A)

- **Depends on:** none (if the person assigns the promotion marker fix to improvement-240, skip
  sub-step 1c and its include).
- **Executor:** haiku.
- **Files:**
  - new `attachment-spring-boot-starter/src/main/resources/db/attachment-changelog/changes/02-owner-reference-remarks.xml`
  - edit `attachment-spring-boot-starter/src/main/resources/db/attachment-changelog/db.attachment-changelog-master.xml`
  - new `contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/04-contact-info-entity-remarks.xml`
  - edit `contact-spring-boot-starter/src/main/resources/db/contact-changelog/contact-changelog-master.xml`
  - new `promotion-spring-boot-starter/src/main/resources/db/promotion-changelog/changes/02-promotion-entity-remarks.xml`
  - edit `promotion-spring-boot-starter/src/main/resources/db/promotion-changelog/promotion-changelog-master.xml`
  - new `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/002-taxon-assignment-actor-remarks.xml`
  - edit `taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/master.xml`
- **Input:** the precedent `contact-spring-boot-starter/src/main/resources/db/contact-changelog/changes/03-contact-view-entity-remarks.xml`
  (a `setColumnRemarks` changeset with no header comment, `author="ostap"`). Current remarks, verified:
  `attachment.entity_id` and `attachment_snapshot.entity_id` = "ID of owner entity. References advertisement(id), no FK";
  `contact_info.entity_id` = "ID of the owning entity, provider_profile(id) for PROVIDER_PROFILE, no FK";
  `promotion_placement.entity_id` = "ID of the promoted entity, provider_profile(id) for PROVIDER_PROFILE, no FK";
  `taxon_assignment.assigned_by` = "User ID who made the assignment".
- **Exact change:**
  - 1a. The attachment file, whole content:
    ```xml
    <?xml version="1.0" encoding="UTF-8"?>
    <databaseChangeLog
            xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
                                http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

        <changeSet id="02-owner-reference-remarks" author="ostap">
            <setColumnRemarks tableName="attachment" columnName="entity_id"
                    remarks="ID of owner entity. References advertisement(id) depending on entity_type, no FK"/>
            <setColumnRemarks tableName="attachment_snapshot" columnName="entity_id"
                    remarks="ID of owner entity. References advertisement(id) depending on entity_type, no FK"/>
        </changeSet>

    </databaseChangeLog>
    ```
    In `db.attachment-changelog-master.xml`, directly after the existing include
    `<include file="db/attachment-changelog/changes/01-attachment-schema.xml"` +
    `relativeToChangelogFile="false"/>`, add:
    ```xml
        <include file="db/attachment-changelog/changes/02-owner-reference-remarks.xml"
                 relativeToChangelogFile="false"/>
    ```
  - 1b. The contact file: the same skeleton, `changeSet id="04-contact-info-entity-remarks" author="ostap"`,
    one element:
    `<setColumnRemarks tableName="contact_info" columnName="entity_id" remarks="ID of the owning entity. References provider_profile(id) depending on entity_type, no FK"/>`.
    In `contact-changelog-master.xml`, after the `03-contact-view-entity-remarks.xml` include line, add
    `    <include file="db/contact-changelog/changes/04-contact-info-entity-remarks.xml"/>`.
  - 1c. The promotion file: the same skeleton, `changeSet id="02-promotion-entity-remarks" author="ostap"`,
    one element:
    `<setColumnRemarks tableName="promotion_placement" columnName="entity_id" remarks="ID of the promoted entity. References provider_profile(id) depending on entity_type, no FK"/>`.
    In `promotion-changelog-master.xml`, after the `01-promotion-schema.xml` include line, add
    `    <include file="db/promotion-changelog/changes/02-promotion-entity-remarks.xml"/>`.
  - 1d. The taxon file: the same skeleton, `changeSet id="taxon-002" author="ostap"`, one element:
    `<setColumnRemarks tableName="taxon_assignment" columnName="assigned_by" remarks="User ID who made the assignment. References user_information(id), no FK"/>`.
    In `master.xml`, after the `001-taxon.xml` include (two lines), add
    `    <include file="db/taxon-changelog/changes/002-taxon-assignment-actor-remarks.xml"` +
    newline + `             relativeToChangelogFile="false"/>`.
- **Verification:** `bash docs/architecture/scripts/generate-architecture-model.sh`. Then, in
  `docs/architecture/data/architecture-model.json`, the database conceptual relationships contain
  `PROVIDER_PROFILE -> CONTACT_INFO`, `PROVIDER_PROFILE -> PROMOTION_PLACEMENT`,
  `USER_INFORMATION -> TAXON_ASSIGNMENT (assigned_by)`, and still `ADVERTISEMENT -> ATTACHMENT` and
  `ADVERTISEMENT -> ATTACHMENT_SNAPSHOT`. Liquibase applies the changesets: covered by the full
  test run at the end.
- **Sources:** https://www.liquibase.com/blog/dealing-with-changing-changesets ;
  https://dev.to/tbson87/foreign-keys-between-modules-in-a-modular-monolith-keep-restrict-or-drop-19d6
  (keep the virtual relation recorded).

### Step 2. Teach the ERD generator `addColumn`, and make it fail on an unmarked or cross-module reference (Phase A)

- **Depends on:** step 1 (otherwise the new check fails on the columns step 1 fixes). Skip the check
  part if the person assigns it to improvement-240.
- **Executor:** sonnet.
- **Files:** `docs/architecture/scripts/liquibase-schema-to-json.js`,
  `docs/architecture/scripts/generate-architecture-model.sh` (the `DB_ERD_CHANGELOG_FILES` loop near
  line 278 and `db_erd_json()` near line 1186), and the header comments of both files.
- **Input:** the parser handles `createTable`, `addForeignKeyConstraint`, `createIndex`,
  `addPrimaryKey`, raw `<sql>` and `setColumnRemarks` (applied after all tables are parsed). Each
  table carries `module`. The file loop includes a changelog only if it contains `<createTable` or
  `<setColumnRemarks`. `db_erd_json()` derives relationships with
  `refRe = /References\s+((?:\w+\(\w+\)\s*(?:or\s+)?)+)(?:depending on \w+\s*)?,\s*no FK/i`.
- **Exact behavior:**
  1. The parser adds each `<addColumn tableName="T">`'s `<column>` children (name, type, remarks,
     nullability, inline `references`) to table `T`. Apply them after every `createTable` is parsed
     and before `setColumnRemarks`. The file loop also includes files containing `<addColumn`.
     Afterwards `contact_view.revealed_value` appears in `architecture-model.json`.
  2. `db_erd_json()` exits non-zero, naming the table and column, in two cases:
     - (a) a real FK (inline `references=` or `addForeignKeyConstraint`) whose referenced table's
       `module` differs from the base table's `module`;
     - (b) a column with no real FK whose name matches
       `^(entity_id|actor_id|author_id|viewer_id|assigned_by|created_by|updated_by|deleted_by|.*_actor_id|.*_taxon_id)$`
       and whose final remarks (after `setColumnRemarks`) do not match `refRe`.
     Print every violation before exiting, not only the first.
  3. Do not change the JSON shape the second consumer (`MODULE_TABLES` extraction) reads. The
     hand-written `ATTACHMENT -> ATTACHMENT_SNAPSHOT` entry in
     `db_erd_conceptual_relationships_json()` stays (a sibling link, not a column reference, as the
     skill says).
  4. Update both files' header comments (the "Input"/"Returns" lines) per `infra-doc-standards`.
- **Constraints:** Node with no external dependencies (as the parser's header states). No new
  setting or flag. Exit code and message style match the script's existing failure paths.
- **Verification:**
  1. Regenerate: it succeeds, and `revealed_value` is present in the JSON.
  2. Temporarily remove `, no FK` from one marker: generation fails naming that column. Revert.
  3. Temporarily add a `references="advertisement(id)"` to a column in another starter's
     changelog: generation fails. Revert.
  4. `git diff` shows only the two script files plus the regenerated model.
- **Sources:** https://dev.to/tbson87/foreign-keys-between-modules-in-a-modular-monolith-keep-restrict-or-drop-19d6 ;
  https://milanjovanovic.tech/blog/modular-monolith-data-isolation (enforce boundaries by a check) ;
  https://docs.spring.io/spring-modulith/reference/fundamentals.html

### Step 3. Shared owner-link integrity check in `integration-tests` (Phase A)

- **Depends on:** none. Needs the person's decision 3 (`contact_view`) and decision 4 (test only,
  or also a runtime job; this step is the test-only part).
- **Executor:** sonnet.
- **Files:** one new support class in `integration-tests/src/main/java/org/ost/integrationtests/support/`
  (the name is the executor's choice, following that package's naming). Calls are added at the end of
  the existing scenarios that delete or purge an owner: find them with `git grep -l -E
  "delete|purge|cleanup" integration-tests/src/test`. Expected:
  `ProviderProfileScenarioTest`, `UserAdminScenarioTest`, and the advertisement scenario that
  deletes an ad. Extend these. Do not add a new scenario class where an existing one already
  performs the delete.
- **Input:** `.claude/rules/integration-tests.md` (levels, `TestDataCleaner`, fixtures);
  `EntityType` = ADVERTISEMENT, USER, USER_SETTINGS, TAXON, PROVIDER_PROFILE, FEEDBACK,
  FEEDBACK_COMMENT. Owner tables: ADVERTISEMENT -> `advertisement`, PROVIDER_PROFILE ->
  `provider_profile`, USER -> `user_information`, TAXON -> `taxon`.
- **Exact behavior:**
  1. One method takes the test's `JdbcClient` and asserts zero orphans. An orphan is a row of an
     owned table, with `entity_type = X`, for which no row with `id = entity_id` exists in X's owner
     table (`NOT EXISTS`).
  2. Owned tables: `contact_info`, `feedback`, `feedback_aggregate`, `taxon_assignment`,
     `promotion_placement`, `attachment` (rows with `deleted_at IS NULL` only, since soft-deleted
     attachments are purged later by the attachment cleanup), and `contact_view` only if the person
     decides it is owned data.
  3. History tables are excluded: `audit_log`, `attachment_snapshot` (and `contact_view` if the
     person decides it is history).
  4. An `entity_type` value found in an owned table with no owner-table mapping fails the
     assertion with that value named, so a new owner kind cannot be skipped silently.
  5. The failure message lists table, entity type and the first ids found.
  6. SQL as inline text blocks, no table-name constants (root `CLAUDE.md` repository rules apply in
     spirit).
- **Constraints:** test code only. No production class changes. One-line comments.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`
  (re-read `scripts/CLAUDE.md` first): green. Then, temporarily comment out
  `feedbackAccessService.deleteByEntity(...)` in `ProviderProfileSaveService.delete`: the provider
  profile scenario fails naming `feedback`. Revert.
- **Sources:** https://dev.to/tbson87/foreign-keys-between-modules-in-a-modular-monolith-keep-restrict-or-drop-19d6
  (orphan check replaces the FK guarantee) ;
  https://docs.djangoproject.com/en/5.2/ref/contrib/contenttypes/ (a deleted owner leaves the pair dangling) ;
  https://www.postgresql.org/docs/current/ddl-constraints.html

### Step 4. Rule text edits with exact text (Phase A)

- **Depends on:** steps 2 and 3 (the text describes them). Present the plan and wait for approval:
  edits to `CLAUDE.md` and rule files follow the Approval Rule.
- **Executor:** haiku.
- **Files:** `CLAUDE.md`, `.claude/rules/marketplace-orchestrator.md`, `.claude/rules/integration-tests.md`.
- **Exact change:**
  - 4a. `CLAUDE.md`, guideline 7. Old text:
    ```
       file across starters. Every starter `CLAUDE.md`'s own "Key constraints"/"Schema" section states
       only what's specific to that module beyond this baseline.
    ```
    New text:
    ```
       file across starters. A foreign key may reference only a table created in the same starter's
       changelog; a reference to another module's row is a plain column whose `remarks=` carries the
       no-FK marker, and the owner's delete use case in `marketplace-orchestrator` removes, through
       the other modules' ports, the rows that must not outlive the owner. Every starter
       `CLAUDE.md`'s own "Key constraints"/"Schema" section states only what's specific to that
       module beyond this baseline.
    ```
  - 4b. `.claude/rules/marketplace-orchestrator.md`, section `## Key constraints`. Insert a new
    bullet directly before the bullet that starts `- **Feedback not-found errors are`:
    ```
    - **An owner's delete use case clears what must not outlive it.** It removes, in its own
      transaction and through each module's port, every row of another module that names the owner
      as `entity_type`/`entity_id`, except history (`audit_log`, `attachment_snapshot`); the shared
      owner-link integrity check in `integration-tests` fails when a delete path leaves one behind,
      so a new owner kind or owned table is added to that check in the same change.
    ```
  - 4c. `.claude/rules/integration-tests.md`. Old text:
    ```
    needs a FK-satisfying user row (e.g. `AdvertisementRepository` — `advertisement.created_by` is
    `NOT NULL` with an FK to `user_information.id`) would otherwise force each starter needing this to
    ```
    New text:
    ```
    needs a real user row (e.g. `AdvertisementRepository` — `advertisement.created_by` is
    `NOT NULL` and names a `user_information` row, without a foreign key) would otherwise force each starter needing this to
    ```
- **Verification:** `git diff` shows exactly these three hunks.
- **Sources:** https://milanjovanovic.tech/blog/modular-monolith-data-isolation ;
  https://learn.microsoft.com/en-us/dotnet/architecture/microservices/architect-microservice-container-applications/data-sovereignty-per-microservice

### Step 5. The skill paragraph on the marker (Phase A)

- **Depends on:** step 2 (it quotes the real name pattern).
- **Executor:** sonnet.
- **File:** `.claude/skills/module-doc-standards/SKILL.md`: section "No-FK reference columns"
  (first paragraph, around lines 157-161) and the pre-write checklist line around line 216.
- **Exact behavior:** replace "a missing or misworded marker silently drops that relationship from
  the diagram instead of raising an error" with a statement of what step 2 really checks. A reference
  column whose name matches the generator's pattern, and has no marker, fails the generator with the
  column named. So does a foreign key into another module's table. A column with any other name
  still drops silently. Adjust the checklist line the same way. Do not name the pattern twice: point
  to `db_erd_json()` as its home (the "one fact, one home" rule in `.claude/rules.md`).
- **Verification:** the paragraph matches the code of step 2. `git diff` touches only these lines.
- **Sources:** none beyond step 2.

### Step 6. Decision records (Phase A)

- **Depends on:** steps 1-5.
- **Executor:** sonnet (the main thread runs `/record-decision`; never hand-write a `DECISIONS.md` entry).
- **Exact behavior:**
  1. `/record-decision platform-commons — Owner references across modules stay references by identity; integrity is held by the owner's delete use case, a generator check and an integrity test`.
     - Context: the 9 owner-pair tables, the orphan incident, the unchecked marker.
     - Decision: as in steps 1-4.
     - Rejected alternatives, each with its reason: link table per owner kind; owner registry /
       common super-table; exclusive arcs; FK from actor columns to `user_information` with RESTRICT.
     - **Also affects:** attachment-spring-boot-starter, marketplace-orchestrator,
       docs/architecture/scripts, integration-tests.
  2. Annotate the `docs/architecture/scripts` decision "Database ERD's no-FK point relationships
     derived from a `remarks=` marker convention" (find it in `.claude/nav/adr-index.md`) with a
     status note: the marker is now checked by the generator for name-pattern columns.
  3. `bash .claude/nav/scripts/generate-adr-index.sh` in the same change.
- **Verification:** the index shows the new row and the annotated status.
- **Sources:** all of `sources.md` level 0.

### Step 7. Remove the start-order leftover (Phase B, only if decision 5 is yes)

- **Depends on:** none.
- **Executor:** haiku.
- **Files:** `advertisement-spring-boot-starter/src/main/java/org/ost/advertisement/config/AdvertisementAutoConfiguration.java`,
  `provider-profile-spring-boot-starter/src/main/java/org/ost/provider/config/ProviderProfileAutoConfiguration.java`.
- **Exact change:** in each file, delete the line `    @DependsOn("userLiquibase")` above the
  `*Liquibase` bean method, and the line `import org.springframework.context.annotation.DependsOn;`.
  Verify by grep first that `DependsOn` is used nowhere else in that file.
- **Verification:** `bash scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`
  green. Then `bash scripts/deploy-and-run.sh` starts the app on a reset database (each starter's
  changelog runs alone; no seed data references `user_information`, verified by grep). Then the
  Playwright `e2e --full --ux` run per the Definition of Done.
- **Sources:** https://dev.to/tbson87/foreign-keys-between-modules-in-a-modular-monolith-keep-restrict-or-drop-19d6
  (a module must rebuild its tables alone).

### Step 8. Design of the attachment `purpose` (Phase C, only with the avatar task, decision 6)

- **Depends on:** Phase A done; the avatar task opened.
- **Executor:** opus.
- **Goal:** one owner can hold more than one kind of attachment (gallery, avatar). Existing data and
  behavior stay the same.
- **Constraints:**
  - The owner pair stays.
  - New column `purpose VARCHAR(30) NOT NULL DEFAULT 'GALLERY'` on `attachment` and
    `attachment_snapshot`, added by a new roll-forward changeset with `remarks=`. Existing rows read
    `GALLERY` without a rewrite (PostgreSQL `ADD COLUMN` with a non-volatile default).
  - Indexes `idx_attachment_entity` and `idx_attachment_snapshot_entity` gain `purpose`.
  - The enum lives in `platform-commons` `attachment.model`.
  - The ERD shows the column (step 2 parses `addColumn`).
- **Open decisions:**
  - How `AttachmentPort` takes the purpose: a parameter on every method, or a small owner-key record
    next to `EntityRef`.
  - Whether the S3 folder layout `entityType/entityId` gains the purpose.
  - Whether the per-owner snapshot version counter becomes per (owner, purpose).
  - Which existing ADR (attachment "generic over EntityType") gets the annotation.
- **Output:** a sonnet/haiku step list in the avatar task file.
- **Sources:** https://guides.rubyonrails.org/active_storage_overview.html ;
  https://www.postgresql.org/docs/current/sql-altertable.html ;
  https://docs.gitlab.com/development/database/polymorphic_associations/ (the separate-table
  alternative, to weigh).

## Verification of the whole

1. `bash docs/architecture/scripts/generate-architecture-model.sh` succeeds. The model has every
   marker link from step 1 and `contact_view.revealed_value`. The two negative probes of step 2 fail
   as expected and are reverted.
2. `bash scripts/build-and-test.sh --unit --integration --sandbox` is green, including the extended
   delete scenarios of step 3 and `ArchitectureRulesTest`. Read `scripts/CLAUDE.md` before running.
   Run it backgrounded through `scripts/activity-monitor.sh`, per `.claude/rules.md`.
3. Phase A changes no runtime code: `git diff --stat` shows only changelog files, the two generator
   files, the regenerated model, `integration-tests` test code, rule and skill markdown,
   `DECISIONS.md` and `adr-index.md`. Playwright is needed only if step 7 or Phase C runs.
4. `git grep -n "References advertisement(id), no FK"` matches only the original createTable lines.
   The live remark comes from the step-1 changesets.
