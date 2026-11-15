package org.ost.platform.promotion.spi;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;

import java.util.List;
import java.util.Set;

/** Promotion placements of any owning entity, generic over entity type and id; implementation lives in promotion-spring-boot-starter. */
public interface PromotionPlacementPort {

    /** Returns the promoted entries of this entity type with their reasons, for every reason known to this version. */
    List<PromotionPlacementDto> findPromoted(@NonNull EntityType entityType);

    /** Makes the rows of this reason for this entity type exactly {@code entityIds}; an empty set removes them all. */
    void replaceForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Set<Long> entityIds);

    /** Adds this entity to the rows of this reason; nothing changes when it is already there. */
    void addForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId);

    /** Removes this entity from the rows of this reason; nothing changes when it is not there. */
    void removeForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId);
}
