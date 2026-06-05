package org.ost.audit.repository;

import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;

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
