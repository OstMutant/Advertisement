package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.Value;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.function.ObjLongConsumer;

/**
 * Port: marketplace → audit-starter.
 * Audit-starter exposes pre-built Vaadin UI panels to marketplace.
 * Marketplace calls this to embed entity history or profile activity feeds
 * without depending on audit internals.
 * Implementation: {@code AuditUiPortImpl} in audit-spring-boot-starter.
 * Injected via {@code ObjectProvider} — degrades gracefully when audit is disabled.
 */
public interface AuditUiPort {

    @Value
    @lombok.Builder
    class EntityHistoryParams {
        EntityType                             entityType;
        Long                                   entityId;
        Long                                   userId;
        boolean                                isPrivileged;
        boolean                                canOperate;
        ObjLongConsumer<EntityHistoryDto>      onRestoreRequested;
        String                                 labelEmpty;
        String                                 labelCurrentState;
        String                                 labelRestore;
    }

    @Value
    @lombok.Builder
    class ProfileActivityParams {
        EntityType                 subjectType;
        Long                       subjectId;
        Long                       viewerActorId;
        String                     emptyLabel;
        @lombok.Builder.Default
        List<ActivityRowHook>      bindings = List.of();
    }

    Component buildEntityHistoryPanel(EntityHistoryParams params);

    Component buildProfileActivityPanel(ProfileActivityParams params);
}
