package org.ost.platform.providerprofile.dto;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.platform.providerprofile.model.ProviderKind;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** A provider profile as read by the catalog: its own fields, the display fields filled in by enrichment and the promotion badge fields. */
@Value
@Builder(toBuilder = true)
@FieldNameConstants
public class ProviderProfileDto {

    Long id;
    Long actorId;
    String actorName;
    String actorEmail;
    ProviderKind kind;
    String about;
    Long cityTaxonId;
    String cityName;
    Set<Long> categoryIds;
    List<String> categoryNames;
    Instant createdAt;
    Instant updatedAt;
    Long version;
    boolean promoted;
    PromotionReason promotionReason;
}
