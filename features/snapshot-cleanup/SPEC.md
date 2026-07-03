# Feature: Snapshot DTO Cleanup — Remove Redundant Fields

## Goal

Remove `visible` and `restorable` from snapshot JSON. Remove `isRestorable()` entirely
from Java — it is dead code. Keep `isVisible()` in Java only (no serialization).

---

## Problem

`AuditableSnapshot` interface has two default methods:
- `isVisible()` → `true`
- `isRestorable()` → `true`

Jackson serializes them into every snapshot JSON:
```json
{"@type": "advertisement", "visible": true, "restorable": true, "title": "...", ...}
```

### Why they are noise / dead code

**`visible: true` in JSON** — only `isVisible() = false` on `CategoryChangeSnapshotDto` is
meaningful (filters it from the timeline in `AuditActivityPanel`). Serializing `true` adds
no information. The Java method is still needed.

**`isRestorable()` — dead code entirely:**
- `CategoryChangeSnapshotDto` (the only override to `false`) is already filtered out by
  `isVisible()` before the renderer is reached
- In `AuditActivityRowRenderer` the real guard is `actionType`, not `isRestorable()`
- Neither the interface default nor the override is ever the deciding factor

---

## Fix

### 1. `AuditableSnapshot` — suppress JSON, remove `isRestorable()`

```java
public interface AuditableSnapshot {
    @JsonIgnore
    default boolean isVisible() { return true; }

    // isRestorable() — removed entirely
}
```

### 2. `CategoryChangeSnapshotDto` — remove `isRestorable()` override

### 3. `AuditActivityPanel` — remove `restorableCount` logic (uses `isRestorable()`)

### 4. `AuditActivityRowRenderer` — remove `isRestorable()` check from `canShowAction`

---

## Scope

| File | Change |
|------|--------|
| `platform-commons/.../audit/api/AuditableSnapshot.java` | `@JsonIgnore` on `isVisible()`, remove `isRestorable()` |
| `platform-commons/.../taxon/dto/CategoryChangeSnapshotDto.java` | Remove `isRestorable()` override |
| `marketplace-app/.../audit/AuditActivityPanel.java` | Remove `restorableCount` / `isRestorable()` usages |
| `marketplace-app/.../audit/AuditActivityRowRenderer.java` | Remove `isRestorable()` from `canShowAction` |

No schema migration needed — existing rows with `visible/restorable` fields in `audit_log`
are silently ignored by Jackson on read.
