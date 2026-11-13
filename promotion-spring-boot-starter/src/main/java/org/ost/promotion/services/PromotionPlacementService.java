package org.ost.promotion.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.dto.PromotionPlacementDto;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.promotion.repository.PromotionPlacementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** Reads promoted placements and replaces the set of entities of one reason in a single transaction. */
@Service
@RequiredArgsConstructor
public class PromotionPlacementService {

    private final PromotionPlacementRepository repository;

    public List<PromotionPlacementDto> findPromoted(@NonNull EntityType entityType) {
        return repository.findPromoted(entityType);
    }

    @Transactional
    public void replaceForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Set<Long> entityIds) {
        Long[] ids = entityIds.toArray(new Long[0]);
        repository.deleteForReasonExcept(entityType, reason, ids);
        repository.insertMissing(entityType, reason, ids);
    }

    @Transactional
    public void addForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId) {
        repository.insertMissing(entityType, reason, new Long[]{entityId});
    }

    @Transactional
    public void removeForReason(@NonNull EntityType entityType, @NonNull PromotionReason reason, @NonNull Long entityId) {
        repository.deleteOne(entityType, reason, entityId);
    }
}
