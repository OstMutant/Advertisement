# improvement-003: Deferred performance optimizations

**Type:** improvement — deferred, negligible impact at current scale
**Module:** audit-spring-boot-starter
**Priority:** low — revisit only when triggers are met

## Items

### A — SnapshotCodec JSON parsing centralization
Centralize `ObjectMapper.readValue` calls to eliminate JSON parsing inside projections.
**Trigger:** cursor pagination implemented or result sets grow significantly beyond 100 rows.

### B — Activity JSON deserialization per row
JSON deserialization happens per row in activity queries.
**Trigger:** cursor pagination implemented or activity feed grows beyond 20 rows per page.

### C — Snapshot equality check cache
`jsonEquals readTree` is expensive for large history lists.
Fix: add parsed snapshot cache in `AuditHistoryPanel.configure()`.
**Trigger:** history lists regularly exceed 100 items.

### D — EntityDisplayNameResolver lookup
`EntityDisplayNameResolver.supports()` uses linear scan over resolver list.
Fix: replace with map lookup.
**Trigger:** resolver count exceeds 5.

### E — Pagination hardcoded LIMIT values
Hardcoded `LIMIT 20/100` in pagination queries.
Fix: replace with cursor pagination.
**Trigger:** UX requires scrolling performance improvements.

### F — EntityType enum to string registry
Migrate `EntityType` enum to string registry/descriptor pattern for extensibility.
**Trigger:** second consumer project appears that needs custom entity types.

### G — AttachmentHistoryExtension rename
`AdvertisementHistoryExtension` carries "Advertisement" in its name but is generic over `EntityType`.
Fix: rename to `AttachmentHistoryExtension`.
**Trigger:** second entity type uses attachment history extension.

### H — EntityType.storageKey() method
S3 folder currently uses `name().toLowerCase()` for storage segment.
Fix: add typed `storageKey()` method to `EntityType` to allow custom storage paths.
**Trigger:** entity-specific storage path customization becomes necessary.
