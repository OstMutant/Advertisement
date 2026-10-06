package org.ost.contact.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.core.model.EntityType;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** What to show -- at most one row per owning entity (PROVIDER_PROFILE or ADVERTISEMENT), no FK, resolved only via ContactPort. */
@Value
@Builder
@FieldNameConstants
@Table("contact_info")
public class ContactInfo {

    @Id
    Long id;

    EntityType entityType;
    Long entityId;
    String phone;
    String telegram;
    String viber;

    @CreatedDate
    Instant createdAt;

    // write-only from Java's side -- Spring Data JDBC populates it on save; read back via raw SQL elsewhere, not via this field.
    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
