package org.ost.platform.advertisement.dto;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Value
@Builder(toBuilder = true)
@FieldNameConstants
public class AdvertisementInfoDto {

    Long id;
    String title;
    String description;
    Instant createdAt;
    Instant updatedAt;
    Long createdBy;
    String createdByUserName;
    String createdByUserEmail;
    String mediaUrl;
    String mediaContentType;
    Integer mediaCount;
    Set<Long> categoryIds;
    List<String> categoryNames;
    Long version;

    public Long getOwnerUserId() {
        return createdBy;
    }
}
