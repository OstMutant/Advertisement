package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.core.model.EntityType;

/**
 * Binding: audit-starter → marketplace.
 * Marketplace passes one binding per entity type when building a profile activity panel
 * ({@link AuditUiExtension.ProfileActivityParams#bindings}). Audit-starter calls
 * {@link #decorate} for each row whose {@link #entityType()} matches, letting marketplace
 * attach domain-aware UI (e.g. "restore" buttons, current-state badges) without
 * the starter understanding snapshot shape.
 */
public interface ActivityRowBinding {

    EntityType entityType();

    Component decorate(ActivityItemDto item);
}
