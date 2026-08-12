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
    Long cityTaxonId;

    @CreatedDate
    Instant createdAt;

    // write-only from Java's side -- Spring Data JDBC populates it on save; read back via raw SQL elsewhere, not via this field.
    @LastModifiedDate
    Instant updatedAt;

    @Version
    Long version;
}
