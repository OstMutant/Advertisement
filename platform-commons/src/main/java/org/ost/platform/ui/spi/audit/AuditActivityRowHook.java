package org.ost.platform.ui.spi.audit;

import com.vaadin.flow.component.Component;
import lombok.NonNull;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.model.EntityType;

/**
 * Hook: audit-starter → marketplace.
 * Marketplace passes one hook per entity type when building a profile activity panel.
 * Audit-starter calls {@link #decorate} for each row whose {@link #entityType()} matches,
 * letting marketplace attach domain-aware UI (e.g. "restore" buttons, current-state badges)
 * without the starter understanding snapshot shape.
 */
public interface AuditActivityRowHook<T extends AuditableSnapshot> {

    EntityType entityType();

    Component decorate(@NonNull AuditTimelineItemDto<T> item);
}
