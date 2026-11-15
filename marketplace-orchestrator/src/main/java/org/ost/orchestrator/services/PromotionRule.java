package org.ost.orchestrator.services;

import lombok.NonNull;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.promotion.model.PromotionReason;

import java.util.Set;

/** One reason for promoting an entity type: decides whether a single entity qualifies now and lists every entity that does; {@link PromotionRefreshService} applies it whenever a process reports a change. */
public interface PromotionRule {

    PromotionReason reason();

    EntityType entityType();

    boolean qualifies(@NonNull Long entityId);

    Set<Long> qualifyingIds();
}
