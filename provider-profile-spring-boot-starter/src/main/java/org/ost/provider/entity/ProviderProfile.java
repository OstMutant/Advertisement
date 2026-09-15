package org.ost.provider.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/** A provider's catalog profile -- kind, city, and about text -- created lazily on the actor's first "become a provider" save, at most one row per actor. */
@Value
@Builder
@FieldNameConstants
@Table("provider_profile")
public class ProviderProfile {

    @Id
    Long id;

    Long actorId;
    ProviderKind kind;
    String about;

    @CreatedDate
    Instant createdAt;

    // write-only from Java's side -- Spring Data JDBC populates it on save; read back via raw SQL elsewhere, not via this field.
    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
