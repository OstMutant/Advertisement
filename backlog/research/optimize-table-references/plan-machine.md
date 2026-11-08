# Plan for executors: optimize-table-references

Purpose: the result for the AI that executes it, with the detail each step's executor model needs.

Preconditions for every step: re-read `/app/.claude/rules.md`, `/app/CLAUDE.md` and the
`.claude/rules/<module>.md` of the module touched; never commit; run `git add` on each changed file;
no code comment longer than one line, no task number in a comment.
Step 1 needs no decision. Step 2 runs only if the person answered **A = yes**. Step 3 runs only if
**B = yes**, and its changelog part follows the answer to **C**. Decisions D, E, F are not planned.

## Step 1 -- FeedbackService.save: drop the throw-away entity and the contentId local

- **Depends on:** nothing
- **Files:** `/app/feedback-spring-boot-starter/src/main/java/org/ost/feedback/services/FeedbackService.java`
- **Executor:** haiku
- **Input:** method `save(@NonNull @Valid FeedbackSaveDto dto)`, currently lines 81-132.
- **Exact change:** replace this old text

```java
        String sanitizedText = HtmlSanitizer.sanitize(dto.feedbackText(), FeedbackSaveDto.TEXT_MAX_LENGTH);
        Feedback saved;
        Long contentId;
        EntityType effectiveEntityType;
        Long effectiveEntityId;
        if (dto.id() != null) {
            FeedbackView existing = repository.findViewById(dto.id())
                    .orElseThrow(() -> new NoSuchElementException(FEEDBACK_PREFIX + dto.id() + NOT_FOUND_SUFFIX));
            checkWithinEditWindow(existing.createdAt());
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .id(existing.contentId())
                    .contentText(sanitizedText)
                    .moderationStatus(existing.moderationStatus())
                    .moderationReason(existing.moderationReason())
                    .createdAt(existing.createdAt())
                    .version(dto.version())
                    .build());
            contentId = content.getId();
            effectiveEntityType = existing.entityType();
            effectiveEntityId = existing.entityId();
            saved = Feedback.builder()
                    .id(existing.id())
                    .entityType(existing.entityType())
                    .entityId(existing.entityId())
                    .authorId(existing.authorId())
                    .contentId(contentId)
                    .build();
        } else {
            feedbackRateLimiter.checkAllowed(dto.authorId().toString(), "Too many feedback entries submitted today, try again tomorrow");
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .contentText(sanitizedText)
                    .moderationStatus(FeedbackModerationStatus.NEW)
                    .build());
            contentId = content.getId();
            effectiveEntityType = dto.entityType();
            effectiveEntityId = dto.entityId();
            saved = repository.saveLink(Feedback.builder()
                    .entityType(dto.entityType())
                    .entityId(dto.entityId())
                    .authorId(dto.authorId())
                    .contentId(contentId)
                    .build());
            feedbackRateLimiter.recordAttempt(dto.authorId().toString());
        }
        repository.upsertRating(saved.getId(), dto.rating());
        repository.upsertAggregate(effectiveEntityType, effectiveEntityId);
        return toDto(repository.findViewById(saved.getId())
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + saved.getId() + NOT_FOUND_SUFFIX + " after save")));
```

  with this new text

```java
        String sanitizedText = HtmlSanitizer.sanitize(dto.feedbackText(), FeedbackSaveDto.TEXT_MAX_LENGTH);
        Long feedbackId;
        EntityType effectiveEntityType;
        Long effectiveEntityId;
        if (dto.id() != null) {
            FeedbackView existing = repository.findViewById(dto.id())
                    .orElseThrow(() -> new NoSuchElementException(FEEDBACK_PREFIX + dto.id() + NOT_FOUND_SUFFIX));
            checkWithinEditWindow(existing.createdAt());
            repository.saveContent(FeedbackContent.builder()
                    .id(existing.contentId())
                    .contentText(sanitizedText)
                    .moderationStatus(existing.moderationStatus())
                    .moderationReason(existing.moderationReason())
                    .createdAt(existing.createdAt())
                    .version(dto.version())
                    .build());
            feedbackId = existing.id();
            effectiveEntityType = existing.entityType();
            effectiveEntityId = existing.entityId();
        } else {
            feedbackRateLimiter.checkAllowed(dto.authorId().toString(), "Too many feedback entries submitted today, try again tomorrow");
            FeedbackContent content = repository.saveContent(FeedbackContent.builder()
                    .contentText(sanitizedText)
                    .moderationStatus(FeedbackModerationStatus.NEW)
                    .build());
            feedbackId = repository.saveLink(Feedback.builder()
                    .entityType(dto.entityType())
                    .entityId(dto.entityId())
                    .authorId(dto.authorId())
                    .contentId(content.getId())
                    .build()).getId();
            effectiveEntityType = dto.entityType();
            effectiveEntityId = dto.entityId();
            feedbackRateLimiter.recordAttempt(dto.authorId().toString());
        }
        repository.upsertRating(feedbackId, dto.rating());
        repository.upsertAggregate(effectiveEntityType, effectiveEntityId);
        return toDto(repository.findViewById(feedbackId)
                .orElseThrow(() -> new IllegalStateException(FEEDBACK_PREFIX + feedbackId + NOT_FOUND_SUFFIX + " after save")));
```

  Keep every import (`Feedback` is still used by the `else` branch). Do not touch anything else.
- **Why it is safe:** `feedbackId` is assigned exactly once on each branch, so it is effectively
  final and can be captured by the lambda (the old `saved` relied on the same rule). The edit branch
  still calls `saveContent`, so the optimistic-lock version check is unchanged; the call order
  (rate-limit check, content save, link save, record attempt, rating, aggregate) is unchanged.
- **Verification:** the module compiles; `FeedbackServiceTest`, `FeedbackRepositoryTest` and the
  Level 2/3 feedback tests pass in the final run below.
- **Sources:** https://refactoring.guru/smells/dead-code

## Step 2 -- Attachment: delete the test-only single-entity loadMediaStats (only if A = yes)

- **Depends on:** decision A = yes
- **Files:**
  - `/app/attachment-spring-boot-starter/src/main/java/org/ost/attachment/repository/AttachmentRepository.java`
  - `/app/integration-tests/src/test/java/org/ost/integrationtests/level1/attachment/AttachmentRepositoryTest.java`
  - `/app/.claude/rules/attachment-spring-boot-starter.md`
- **Executor:** haiku
- **Input:** `git grep -n "loadMediaStats(EntityType.ADVERTISEMENT, [0-9]*L)"` shows only the test file;
  the only production caller (`AttachmentService.getMediaSummaries`) uses the bulk overload.
- **Exact change, `AttachmentRepository.java`:**
  1. Delete these two lines and the blank line after them:
     ```java
         private static final RowMapper<MediaStats> MEDIA_STATS_ROW_MAPPER =
                 (rs, _) -> new MediaStats(rs.getString("url"), rs.getString("content_type"), rs.getInt("total_count"));
     ```
  2. Delete the whole method `public MediaStats loadMediaStats(@NonNull EntityType entityType, @NonNull Long entityId) { ... }`
     (from its signature to its closing `}` before `public Map<Long, MediaStats> loadMediaStats(`)
     and the blank line after it. Keep the `MediaStats` record and the bulk overload unchanged.
- **Exact change, `AttachmentRepositoryTest.java`:**
  1. In the class Javadoc replace
     `{@code loadMediaStats} overloads (the bulk one uses a {@code ROW_NUMBER() OVER (PARTITION BY`
     with
     `{@code loadMediaStats} bulk query (it uses a {@code ROW_NUMBER() OVER (PARTITION BY`
     and in the line before it replace `restoreMarkDeleted}), retention-based cleanup selection, and both {@code`
     with `restoreMarkDeleted}), retention-based cleanup selection, and the {@code`.
     (Read the two lines first; if the wording differs, keep the meaning "the bulk loadMediaStats query" and change nothing else.)
  2. Delete the test methods `loadMediaStats_singleEntity_returnsEarliestActiveAttachmentAndCount`,
     `loadMediaStats_singleEntity_noAttachments_returnsEmptyStats` and
     `loadMediaStats_bulkAndSingle_tiedCreatedAt_agreeOnMainAttachment` (each with its `@Test` line and
     the blank line before it). Their cases stay covered by `loadMediaStats_bulk_returnsPerEntityEarliestAttachmentAndCount`
     (earliest pick, count, owner without rows omitted).
  3. Replace the method `loadMediaStats_singleEntity_tiedCreatedAt_pickIsDeterministicByLowestId` with:
     ```java
         @Test
         void loadMediaStats_bulk_tiedCreatedAt_pickIsDeterministicByLowestId() {
             Instant tiedInstant = Instant.parse("2026-01-01T00:00:00Z");
             Long firstId = insertTiedRow(10L, "first.jpg", tiedInstant);
             insertTiedRow(10L, "second.jpg", tiedInstant);

             Map<Long, AttachmentRepository.MediaStats> stats =
                     attachmentRepository.loadMediaStats(EntityType.ADVERTISEMENT, Set.of(10L));

             assertThat(stats.get(10L).mainUrl()).isEqualTo("first.jpg");
             assertThat(firstId).isNotNull();
         }
     ```
  4. Remove any import that became unused (check `Instant`, `Map`, `Set` are still used; they are).
- **Exact change, `.claude/rules/attachment-spring-boot-starter.md`:** in the last bullet of "Key
  constraints", replace the text from `` `AttachmentRepository.loadMediaStats(EntityType, Long)`, the single-entity repository ``
  to the end of that bullet (`...direct repository-level test coverage.`) with
  `` and the repository has no single-entity counterpart either. ``
- **Main thread after this step (not the executor):** annotate ADR-035 (marketplace-app) through
  `/record-decision` (its "`loadMediaStats(EntityType, Long)` itself stays" sentence is superseded),
  which also regenerates `.claude/nav/adr-index.md`.
- **Verification:** `git grep -n "MEDIA_STATS_ROW_MAPPER\|loadMediaStats(EntityType.ADVERTISEMENT, [0-9]*L)"` returns nothing;
  `AttachmentRepositoryTest` passes in the final run.
- **Sources:** https://refactoring.guru/smells/dead-code

## Step 3 -- Taxon: drop the never-written assigned_by (only if B = yes)

- **Depends on:** decision B = yes; the changelog part follows decision C
- **Files:**
  - `/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/entities/TaxonAssignment.java`
  - `/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/repository/TaxonAssignmentRepository.java`
  - `/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/TaxonAssignmentService.java`
  - `/app/taxon-spring-boot-starter/src/main/java/org/ost/taxon/services/DefaultTaxonPort.java`
  - `/app/integration-tests/src/test/java/org/ost/integrationtests/level1/taxon/TaxonAssignmentRepositoryTest.java`
  - C = in place: `/app/taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/001-taxon.xml`
  - C = new file: new `/app/taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/changes/002-taxon-drop-assigned-by.xml` and `/app/taxon-spring-boot-starter/src/main/resources/db/taxon-changelog/master.xml`
- **Executor:** sonnet
- **Input:** the only production write is `DefaultTaxonPort.replaceAssignments`, which passes `null`
  as the actor; the only reader of `getAssignedBy()` is one assertion in `TaxonAssignmentRepositoryTest`
  (verified with `git grep -n "assignedBy\|assigned_by\|getAssignedBy"`).
- **Exact behavior:**
  - `TaxonAssignment`: remove the `assignedBy` field; nothing else changes.
  - `TaxonAssignmentRepository`: remove the `.assignedBy(...)` line from `ROW_MAPPER`; `assign(...)`
    loses its last parameter and the `assigned_by` column, placeholder and `addValue`; both SELECTs
    (`findAllByEntity`, `findAllByEntities`) stop selecting `assigned_by`. SQL otherwise identical
    (`ON CONFLICT ... DO NOTHING` stays).
  - `TaxonAssignmentService.replaceAssignments`: drop the `Long actorId` parameter and pass no actor
    to `assign`. Log line, diff logic and `@Transactional` unchanged.
  - `DefaultTaxonPort.replaceAssignments`: call the service without the `null` argument. `TaxonPort`
    itself is not touched.
  - `TaxonAssignmentRepositoryTest`: every `assign(..., 42L)` call loses the `, 42L` argument
    (13 calls); delete the line `assertThat(assignments.getFirst().getAssignedBy()).isEqualTo(42L);`.
  - Changelog, C = in place: remove the `<column name="assigned_by" .../>` line from the
    `taxon_assignment` `createTable` in `001-taxon.xml` (the changeset already carries
    `<validCheckSum>ANY</validCheckSum>`). Existing local databases keep the empty column until reset;
    say so in the report.
  - Changelog, C = new file: a changelog in the same XML header style as `001-taxon.xml`, one
    changeSet `id="taxon-002"` `author="taxon-starter"` with `<dropColumn tableName="taxon_assignment" columnName="assigned_by"/>`
    and a `<rollback>` that re-adds it as `BIGINT` with the old `remarks="User ID who made the assignment"`
    (Liquibase has no automatic rollback for dropColumn); add its `<include>` after the `001` include
    in `master.xml`, same attributes; one-line header comment like the master's own.
- **Constraints:** no change to `TaxonPort`, to `marketplace-orchestrator`, or to any other module;
  no new comments beyond the changelog header line.
- **Main thread after this step (not the executor):** regenerate the architecture map
  (`/check-architecture-map`; `docs/architecture/data/architecture-model.json` and
  `architecture-map.html` mention `assigned_by`), and update improvement-241's "Current state" line
  that says `taxon_assignment.assigned_by` has no marker.
- **Verification:** `git grep -n "assignedBy\|assigned_by" -- '*.java'` returns nothing; with C = new
  file, Liquibase applies `taxon-002` in the integration-test container (the taxon tests start);
  `TaxonAssignmentRepositoryTest`, `TaxonPortResolveCategoryAndCityFilterTest` and the provider-profile
  and advertisement tests that assign taxons pass in the final run.
- **Sources:** https://docs.liquibase.com/change-types/drop-column.html, https://refactoring.guru/smells/dead-code

## Verification of the whole

Run by the main thread, after re-reading `scripts/CLAUDE.md` and `.claude/nav/flows.md`:

1. `bash scripts/activity-monitor.sh -- scripts/build-and-test.sh --unit --integration --sandbox --skip-vaadin`
   in the background, watched through `/tmp/activity-monitor/build-and-test/tree.txt`. Expect every
   unit and integration test green, including `ArchitectureRulesTest`.
2. `git diff --stat`: only the files named in the executed steps (plus the regenerated architecture
   files and the ADR index when steps 2 or 3 ran).
3. No UI-visible behavior changes, so the Playwright e2e run is not required by the Definition of
   Done; run it only if the person asks.
