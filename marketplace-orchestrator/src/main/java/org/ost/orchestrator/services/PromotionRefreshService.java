package org.ost.orchestrator.services;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.model.PromotionReason;
import org.ost.platform.promotion.spi.PromotionPlacementPort;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;

/** The one entry point any process calls to keep promotion placements in step with its changes: every {@link PromotionRule} of the entity type is applied, a failure never reaches the caller, and a removal is strict. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionRefreshService {

    private final List<PromotionRule>                       rules;
    private final ComponentFactory<PromotionPlacementPort>  promotionPlacementPortFactory;

    public void entityChanged(@NonNull EntityType entityType, @NonNull Long entityId) {
        promotionPlacementPortFactory.findIfAvailable().ifPresent(port -> rules.stream()
                .filter(rule -> rule.entityType() == entityType)
                .forEach(rule -> apply(port, rule, entityId)));
    }

    public void entityRemoved(@NonNull EntityType entityType, @NonNull Long entityId) {
        promotionPlacementPortFactory.findIfAvailable().ifPresent(port -> {
            for (PromotionReason reason : PromotionReason.values()) {
                port.removeForReason(entityType, reason, entityId);
            }
        });
    }

    public void recountAll() {
        promotionPlacementPortFactory.findIfAvailable().ifPresent(port -> rules.forEach(rule -> {
            var ids = rule.qualifyingIds();
            port.replaceForReason(rule.entityType(), rule.reason(), ids);
            log.info("Promotion recount: reason={}, entities={}", rule.reason(), ids.size());
        }));
    }

    @EventListener(ApplicationReadyEvent.class)
    void recountOnStartup() {
        try {
            recountAll();
        } catch (RuntimeException e) {
            log.error("Promotion recount at start-up failed", e);
        }
    }

    private void apply(PromotionPlacementPort port, PromotionRule rule, Long entityId) {
        try {
            if (rule.qualifies(entityId)) {
                port.addForReason(rule.entityType(), rule.reason(), entityId);
            } else {
                port.removeForReason(rule.entityType(), rule.reason(), entityId);
            }
        } catch (RuntimeException e) {
            log.warn("Promotion rule {} failed for {} {}", rule.reason(), rule.entityType(), entityId, e);
        }
    }
}
