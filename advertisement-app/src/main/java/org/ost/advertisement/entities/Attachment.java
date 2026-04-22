package org.ost.advertisement.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
@Table("attachment")
public class Attachment {

    @Id
    Long id;
    EntityType entityType;
    Long entityId;
    String url;
    String filename;
    String contentType;
    Long size;

    @CreatedDate
    Instant createdAt;

    Instant deletedAt;
    Long    deletedByUserId;
}
