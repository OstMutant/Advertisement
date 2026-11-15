package org.ost.platform.promotion.dto;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.model.PromotionReason;

/** One promotion placement: which entry is promoted and why. */
public record PromotionPlacementDto(
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @NonNull PromotionReason reason
) {
}
