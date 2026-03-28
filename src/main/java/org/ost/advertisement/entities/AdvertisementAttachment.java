package org.ost.advertisement.entities;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

@Value
@Builder
@FieldNameConstants
@Table("advertisement_attachment")
public class AdvertisementAttachment {

    @Id
    Long id;
    Long advertisementId;
    String url;
    String filename;
    String contentType;
    Long size;

    @CreatedDate
    Instant createdAt;
}