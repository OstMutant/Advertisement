package org.ost.audit.repository;

import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;

public record AuditLogRow(
        Long       id,
        EntityType entityType,
        Long       entityId,
        ActionType actionType,
        String     snapshotDataJson,
        Long       actorId,
        Instant    createdAt,
        int        version,
        Long       prevId,
        String     prevSnapshotDataJson
) {}
