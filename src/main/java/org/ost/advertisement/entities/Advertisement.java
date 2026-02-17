package org.ost.advertisement.entities;

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
public class Advertisement implements EntityMarker {

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

    @Override
    public Long getOwnerUserId() {
        return createdByUserId;
    }
}
