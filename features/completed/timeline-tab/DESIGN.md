# Timeline Tab — Design

## Data model changes

No schema changes. `timelinePageSize` is added to the `settings` JSONB column in `user_information`.
No Liquibase migration needed — DB is always reset clean; `@Builder.Default int timelinePageSize = 20` handles deserialization of rows that don't yet have the field.

## SPI / API changes

**platform-commons — `AuditPort`:**
```java
List<AuditTimelineItemDto<AuditableSnapshot>> getTimelinePage(
    @NonNull AuditTimelineFilterDto filter, int page, int size);
int countTimeline(@NonNull AuditTimelineFilterDto filter);
```

**platform-commons — new `AuditTimelineFilterDto`:**
```java
public record AuditTimelineFilterDto(
    Long actorId,
    Set<EntityType> entityTypes,
    Set<ActionType> actionTypes,
    Instant fromDate,
    Instant toDate
) {
    public static AuditTimelineFilterDto empty() { ... }
}
```

**platform-commons — `UserSettingsDto`:**
Add `@Min(5) @Max(100) @Builder.Default int timelinePageSize = 20`.

**platform-commons — `SettingsSnapshotDto`:**
Add `timelinePageSize` to record, `diff()`, and `allFields()`.

## Module placement

| Artifact | Module |
|---|---|
| `AuditTimelineFilterDto` | platform-commons/audit/dto |
| `AuditPort` changes | platform-commons/audit/spi |
| `UserSettingsDto`, `SettingsSnapshotDto` | platform-commons/user/dto |
| `AuditLogRepository.findTimeline/countTimeline` | audit-spring-boot-starter |
| `AuditReadService.getTimelinePage/countTimeline` | audit-spring-boot-starter |
| `DefaultAuditPort` impl | audit-spring-boot-starter |
| `TimelineView`, `TimelineQueryBlock`, `TimelineQueryConfig` | marketplace-app/tabs/timeline |

## SqlFilterBuilder for AuditLogRepository

```java
private static final SqlFilterBuilder<AuditTimelineFilterDto> FILTER = new SqlFilterBuilder<>(List.of(
    SqlBoundFilter.of("actorId",     "al.actor_id",    (m, v) -> equalsTo(m, v.actorId())),
    SqlBoundFilter.of("entityTypes", "al.entity_type", (m, v) -> inSet(m, v.entityTypes())),
    SqlBoundFilter.of("actionTypes", "al.action_type", (m, v) -> inSet(m, v.actionTypes())),
    SqlBoundFilter.of("fromDate",    "al.created_at",  (m, v) -> after(m, v.fromDate())),
    SqlBoundFilter.of("toDate",      "al.created_at",  (m, v) -> before(m, v.toDate()))
));
```

## UI patterns and component structure

- `TimelineView` — `@SpringComponent @UIScope`; wraps `QueryStatusBar`, `TimelineQueryBlock`, `PaginationBar`, `AuditTimelineListRenderer`
- `TimelineQueryBlock` — extends `QueryBlock<AuditTimelineFilterDto>`; fields: entity types (multi-select), action types (multi-select), date from/to
- Actor filter — `ComboBox<UserDto>` directly in `TimelineView` (not in QueryBlock), visible only to ADMIN/MOD; resolved to `actorId` before `AuditPort` call
- `TimelineQueryConfig` — `@Configuration` with `FilterProcessor<AuditTimelineFilterDto>`, `SortProcessor` (no sort — always DESC), `QueryStatusBar` beans
- `SettingsPaginationBinding` registered in `TimelineView.init()` with `UserSettingsDto::getTimelinePageSize`

## Integration with existing subsystems

- Reuses `AuditTimelineListRenderer` + `AuditTimelineRowRenderer` for rendering rows
- Actor resolution via `UserPort` in marketplace-app (no cross-module violation)
- `SettingsPaginationBinding` / `SettingsPaginationService` — unchanged, just registered with new extractor

## Cross-cutting concerns

- i18n keys: `MAIN_TAB_TIMELINE`, `SETTINGS_TIMELINE_PAGE_SIZE_LABEL`, filter label keys for entity types, action types, actor, dates
- `SettingsFormModeHandler`: add `timelinePageSizeField` (IntegerField), update `save()`, `discardChanges()`, `loadRestored()`, `handleRestoreFromActivity()`
- `SettingsEditDto`: add `Integer timelinePageSize`
