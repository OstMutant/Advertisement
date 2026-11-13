package org.ost.promotion.spi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.ost.promotion.services.PromotionPlacementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Pure delegation to {@link PromotionPlacementService} -- no business logic of its own, per this project's {@code *PortImpl} convention. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionPlacementPortImpl implements PromotionPlacementPort {

    private final PromotionPlacementService service;

    @Override
    public List<PromotionPlacementDto> findPromoted(@NonNull EntityType entityType) {
        return service.findPromoted(entityType);
    }

    @Override
    @Transactional
    public void replaceForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Set<Long> entityIds) {
        service.replaceForReason(entityType, reason, entityIds);
    }

    @Override
    @Transactional
    public void addForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId) {
        service.addForReason(entityType, reason, entityId);
    }

    @Override
    @Transactional
    public void removeForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId) {
        service.removeForReason(entityType, reason, entityId);
    }
}
