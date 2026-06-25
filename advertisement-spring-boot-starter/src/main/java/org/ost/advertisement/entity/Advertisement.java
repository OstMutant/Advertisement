package org.ost.advertisement.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
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

    @CreatedDate
    Instant createdAt;

    @LastModifiedDate
    Instant updatedAt;

    @CreatedBy
    Long createdByUserId;

    @LastModifiedBy
    Long lastModifiedByUserId;

    public boolean isNew() { return id == null; }
}
