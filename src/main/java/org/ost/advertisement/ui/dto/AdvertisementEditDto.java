package org.ost.advertisement.ui.dto;

import lombok.*;

import java.time.Instant;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AdvertisementEditDto {

    private Long id;

    private String title;
    private String description;

    private Instant createdAt;
    private Instant updatedAt;

    private Long createdByUserId;
    private String createdByUserName;
    private Long lastModifiedByUserId;
}
