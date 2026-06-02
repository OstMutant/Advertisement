package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import org.ost.platform.audit.dto.AuditHistoryItemDto;

import java.util.function.ObjLongConsumer;

/**
 * Hook: audit-starter → marketplace.
 * Marketplace implements this to build per-row action controls in the audit history panel
 * (e.g. "Restore" button or "Current state" badge). Injected via ObjectProvider — no-op when absent.
 */
public interface AuditHistoryRowActionsHook {
    Component buildRowActions(AuditHistoryItemDto item, boolean isCurrentState,
                              ObjLongConsumer<AuditHistoryItemDto> onRestore);
}
