# improvement-075: Timeline actor filter — support multiple actors (add, not replace)

**Type:** improvement — new feature, requested directly by the user while reviewing the Timeline
filter UI (2026-07-17).
**Module:** `platform-commons` (`AuditTimelineFilterDto`), `query-lib` (`SqlCondition.inSet`),
`audit-spring-boot-starter` (`AuditLogRepository`), `marketplace-app` (`UserPickerField`,
`TimelineFilterMeta`, `TimelineQueryBlock`).
**Priority:** medium — real UX improvement on an existing, already-shipped filter; not a bug, no
data-loss/correctness risk, moderate cross-layer scope.
**When:** after the current high-ROI/bug-fix wave; no blockers.

## Problem

The Timeline view's actor filter (`UserPickerField`) only supports selecting a single user —
clicking a row in the picker dialog replaces the current selection and closes the dialog. There is
no way to filter the timeline by "any of these N actors" in one query; a user has to run the
filter once per actor and compare results manually.

## Confirmed current plumbing (verified directly, not assumed)

- UI: `UserPickerField` (`marketplace-app/src/main/java/org/ost/marketplace/ui/query/elements/fields/UserPickerField.java`)
  extends `CustomField<UserDto>` — single value only; `grid.asSingleSelect()` (line ~130) closes
  the dialog on the first row click.
- Wiring: `TimelineQueryBlock.java` registers the field via
  `filterProcessor.register(TimelineFilterMeta.ACTOR, actorField, queryActionBlock)`.
- Mapping: `TimelineFilterMeta.ACTOR` maps `UserDto → Long`:
  `dto.setActorId(v != null ? v.id() : null)`.
- DTO: `AuditTimelineFilterDto` (`platform-commons/src/main/java/org/ost/platform/audit/dto/AuditTimelineFilterDto.java:23`)
  — `private Long actorId;` (scalar). `entityTypes`/`actionTypes` on the same DTO are already
  `Set<EntityType>`/`Set<ActionType>` — `actorId` is the odd one out.
- SQL: `AuditLogRepository.java:66` —
  `SqlBoundFilter.of(actorId, "al.actor_id", (m, v) -> equalsTo(m, v.getActorId()))` →
  `al.actor_id = :actorId`.
- SQL condition factory: `SqlCondition.inSet()` (`query-lib/src/main/java/org/ost/query/filter/SqlCondition.java:62-68`)
  is currently typed `<E extends Enum<E>>` and hardcodes `Enum::name` as the value mapper — it
  cannot be reused for `Set<Long>` as-is, but `SqlOperator.IN` itself (`"%s IN (:%s)"`) is generic
  and reusable.
- No existing lazy, DB-backed, searchable multi-select component exists in the codebase today.
  `QueryMultiSelectComboField<T>` (used for `entityTypes`/`actionTypes`) wraps Vaadin's
  `MultiSelectComboBox` with eager `setItems(T[])` — fine for small enum sets, not usable for a
  large paginated user table.

## Confirmed UX decision (2026-07-17, user confirmed via direct question)

- Clicking a user in the picker dialog **adds** them to the filter's selection instead of
  **replacing** it (currently: replace).
- The dialog **closes after every pick** (not kept open for rapid multi-pick) — simplest change,
  matches today's single-click-then-close interaction exactly; user reopens the picker to add
  another actor.
- Because the dialog always closes after one pick, `grid.asSingleSelect()` does **not** need to
  become `asMultiSelect()` — only the click handler's effect changes (append instead of replace).
- Each selected actor must be individually removable via an "X" — the field's current single
  `Span` + one clear button must become a small removable-chip list (one chip per selected actor,
  each with its own remove control).
- The field's own visible width/layout must expand to reasonable bounds to hold multiple chips
  without overflow or clipping — today's `.user-picker-field` is sized for a single `Span` (no
  dedicated CSS file exists yet; it inherits whatever width the surrounding query-filter form grid
  gives a normal field). A multi-chip display needs either a wider fixed width or wrapping
  (`flex-wrap`) onto multiple lines when the chip count grows, with some reasonable practical cap
  in mind (e.g. don't design for an unbounded number of chips — the field should stay usable and
  not blow out the filter panel's layout if a user picks many actors).

## Suggested fix

- `platform-commons/.../AuditTimelineFilterDto.java`: `Long actorId` → `Set<Long> actorIds`.
- `query-lib/.../SqlCondition.java`: add an `inSet` overload (or generalize the existing one) that
  accepts a `Set<Long>` directly (no `Enum::name` mapping) — reuses the existing `SqlOperator.IN`
  operator, no new SQL operator needed.
- `audit-spring-boot-starter/.../AuditLogRepository.java:66`: change the `actorId` binding from
  `equalsTo` to the new `inSet` overload → `al.actor_id IN (:actorIds)`.
- `marketplace-app/.../UserPickerField.java`: change the field's value type from `UserDto` to
  `Set<UserDto>`; on row click, add to the set (not replace) and close the dialog; replace the
  single `Span`+clear-button display with a removable-chip list, one chip per selected actor.
- `marketplace-app/.../TimelineFilterMeta.java`: `ACTOR` mapping changes from `UserDto → Long` to
  `Set<UserDto> → Set<Long>`.
- `marketplace-app/.../TimelineQueryBlock.java`: update wiring for the new field value type if the
  registration signature requires it.
- Playwright coverage: extend or add a scenario exercising picking 2+ actors, verifying the
  timeline result set matches "any of the selected actors," and verifying individual chip removal.

## Related

- [improvement-056](improvement-056-userpickerfield-inline-button-gap-and-pagination-bug.md) —
  the most recent `UserPickerField` change (offset-pagination fix, code committed but issue
  lifecycle — DECISIONS.md ADR, move to `completed/issues/`, BACKLOG-ARCHIVE.md — still pending
  as of this writing); this issue touches the same component again, on top of that fix.
