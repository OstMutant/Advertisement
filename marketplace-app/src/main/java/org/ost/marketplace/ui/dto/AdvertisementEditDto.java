package org.ost.marketplace.ui.dto;

import lombok.*;
import org.ost.platform.advertisement.model.AdKind;

import java.time.Instant;
import java.util.Set;

@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class AdvertisementEditDto implements EditDto {

    private Long id;

    private String title;
    private String description;
    private AdKind adKind;

    private Instant createdAt;
    private Instant updatedAt;

    private Long createdBy;
    private String createdByUserName;
    private Long updatedBy;

    private Set<Long> categoryIds;
    private Long cityTaxonId;

    private Long version;
}
