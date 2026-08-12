package org.ost.advertisement.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.advertisement.model.AdKind;
import org.springframework.data.annotation.*;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
@Table("advertisement")
public class Advertisement {

    @Id
    Long id;
    String title;
    String description;
    AdKind adKind;

    @CreatedDate
    Instant createdAt;

    // write-only from Java's side -- Spring Data JDBC populates it on save; read back via raw SQL in AdvertisementRepository's ROW_MAPPER, not via this field.
    @LastModifiedDate
    Instant updatedAt;

    @CreatedBy
    Long createdBy;

    // write-only -- see updatedAt above; no DTO surfaces this column's value today.
    @LastModifiedBy
    Long updatedBy;

    @Version
    Long version;
}
