package org.ost.advertisement.dto;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.advertisement.entities.EntityMarker;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
public class AdvertisementInfoDto implements EntityMarker {

    Long id;
    String title;
    String description;
    Instant createdAt;
    Instant updatedAt;
    Long createdByUserId;
    String createdByUserName;
    String createdByUserEmail;

    @Override
    public Long getOwnerUserId() {
        return createdByUserId;
    }
}

