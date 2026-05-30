package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.core.model.EntityType;

/**
 * Hook: audit-starter → marketplace.
 * Marketplace passes one hook per entity type when building a profile activity panel
 * ({@link AuditUiPort.ProfileActivityParams#bindings}). Audit-starter calls
 * {@link #decorate} for each row whose {@link #entityType()} matches, letting marketplace
 * attach domain-aware UI (e.g. "restore" buttons, current-state badges) without
 * the starter understanding snapshot shape.
 */
public interface ActivityRowHook {

    EntityType entityType();

    Component decorate(AuditActivityItemDto item);
}
