package org.ost.attachment.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
public class Attachment {

    Long    id;
    Long    entityId;
    String  url;
    String  filename;
    String  contentType;
    Long    size;
    Instant createdAt;
    Instant deletedAt;
    Long    deletedByUserId;
}
