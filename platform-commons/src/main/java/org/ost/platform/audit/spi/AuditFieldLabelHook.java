package org.ost.platform.audit.spi;

import org.ost.platform.core.model.EntityType;

/**
 * Hook: audit-starter → marketplace.
 * Translates raw field keys (as stored in {@code ChangeEntry.FieldChange#field}) into
 * human-readable display labels. Implementations may call {@code I18nService} safely
 * because this hook is always invoked from the Vaadin UI thread (inside the renderer).
 */
public interface AuditFieldLabelHook {
    boolean supports(EntityType entityType);
    String labelFor(String rawFieldKey);
}
