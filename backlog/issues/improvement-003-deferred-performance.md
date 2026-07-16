# improvement-003: Deferred performance optimizations

**Type:** improvement — deferred, negligible impact at current scale
**Module:** audit-spring-boot-starter
**Priority:** low — revisit only when triggers are met
**When:** Deferred — per-item triggers below

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

### I — audit_log growth: write amplification & table size (added 2026-07-04, from external review)
Every UPDATE stores a full JSONB snapshot (all fields), so audit_log grows linearly with edit
volume regardless of how small each change is. At current scale this is irrelevant; with
public community traffic it eventually affects timeline query latency and storage.
Fix options (in escalation order): retention policy for old versions of unchanged entities →
range partitioning by `created_at` → (only if JSONB content search ever appears) GIN index.
Do NOT add a GIN index preemptively — nothing queries inside snapshot JSONB today.
Note (audit round 5): full-snapshot-per-row is also an *irreversibility* concern — migrating
to delta storage after millions of rows accumulate is a heavy, downtime-class migration;
if the retention trigger fires, evaluate delta storage in the same decision.
**Trigger:** `audit_log` exceeds ~1M rows, or timeline page latency measurably degrades.

### J — Vaadin server-side session memory under load (added 2026-07-04, audit round 5)
Vaadin keeps the component tree per session on the heap; heavy overlays (galleries,
activity panels) multiply per-session cost. At ~100k active users this is the first
subsystem to fail (heap exhaustion), long before the DB.
Mitigations when triggered: session TTL tuning, lighter/lazier overlay trees, horizontal
scaling with sticky sessions; cheap immediate hygiene already planned elsewhere
(`loading="lazy"` images, upload size caps — improvement-017, thumbnails P3 #16).
**Trigger:** sustained concurrent sessions exceed ~1-2k, or heap monitoring (once actuator
exists, P3 #17) shows session-driven growth.

### K — double query per page view: COUNT(*) + SELECT with identical filters (added 2026-07-04, audit round 6)
Every list view runs the full filter twice — once for `countByFilter`, once for the page
select (`AdvertisementRepository`, `AuditLogRepository.countTimeline`, user list). Standard
pattern, fine at current volume; at scale it doubles list-query cost.
Fix options: `COUNT(*) OVER()` window in the page query (one pass, but forces full filtered
scan), short-TTL count cache (Caffeine) per filter hash, or estimated counts for deep lists.
**Trigger:** list endpoints show measurable latency, or filtered sets exceed ~100k rows.
