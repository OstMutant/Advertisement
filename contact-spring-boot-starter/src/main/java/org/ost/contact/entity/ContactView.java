package org.ost.contact.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.contact.model.ContactChannel;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** One reveal/click event -- immutable write-only table (never updated, only inserted), same shape as audit_log. */
@Value
@Builder
@FieldNameConstants
@Table("contact_view")
public class ContactView {

    @Id
    Long id;

    EntityType entityType;
    Long entityId;
    ContactChannel channel;
    Long viewerId;

    @CreatedDate
    Instant createdAt;
}
