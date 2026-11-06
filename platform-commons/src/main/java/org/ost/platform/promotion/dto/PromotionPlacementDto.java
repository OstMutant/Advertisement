package org.ost.platform.promotion.dto;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.model.PromotionReason;

import java.time.Instant;

/** One promotion placement: which entry, why, and until when ({@code validUntil} null means until its writer removes it). */
public record PromotionPlacementDto(
        @NonNull EntityType entityType,
        @NonNull Long entityId,
        @NonNull PromotionReason reason,
        @NonNull Instant validFrom,
        Instant validUntil
) {
}
