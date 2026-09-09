package org.ost.audit.repository;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;

/**
 * Generic row shape returned by {@link AuditLogRepository}'s window-function queries, carrying the
 * resolved snapshot plus the previous row's snapshot for diff computation at read time.
 */
public record AuditLogProjection(
        Long              id,
        EntityType        entityType,
        Long              entityId,
        ActionType        actionType,
        AuditableSnapshot snapshot,
        Long              actorId,
        Instant           createdAt,
        int               version,
        Long              prevId,
        AuditableSnapshot prevSnapshot
) {}
