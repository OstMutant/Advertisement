package org.ost.attachment.entities;

import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@Table("attachment_snapshot")
public class AttachmentSnapshot {

    @Id
    Long     id;
    String   entityType;
    Long     entityId;
    String[] attachmentUrls;
    String   changesSummary;
    Long     changedByActorId;
    @CreatedDate
    Instant  createdAt;
}
