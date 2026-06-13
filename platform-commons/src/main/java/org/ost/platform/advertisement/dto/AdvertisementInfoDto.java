package org.ost.platform.advertisement.dto;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
public class AdvertisementInfoDto {

    Long id;
    String title;
    String description;
    Instant createdAt;
    Instant updatedAt;
    Long createdByUserId;
    String createdByUserName;
    String createdByUserEmail;
    String mediaUrl;
    String mediaContentType;
    Integer mediaCount;

    public Long getOwnerUserId() {
        return createdByUserId;
    }
}
