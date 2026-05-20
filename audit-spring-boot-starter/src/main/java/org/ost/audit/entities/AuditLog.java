package org.ost.audit.entities;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@Table("audit_log")
public class AuditLog {

    @Id
    Long    id;
    String  entityType;
    Long    entityId;
    String  actionType;
    String  snapshotData;
    String  changesSummary;
    Long    actorId;
    @CreatedDate
    Instant createdAt;
}
