package org.ost.attachment.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
public class Attachment {

    Long       id;
    EntityType entityType;
    Long       entityId;
    String     url;
    String     filename;
    String     contentType;
    Long       size;
    Instant    createdAt;
    Instant    deletedAt;
    Long       deletedByActorId;
}
