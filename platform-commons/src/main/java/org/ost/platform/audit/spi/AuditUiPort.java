package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.NonNull;
import lombok.Value;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;

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
        ObjLongConsumer<AuditHistoryItemDto>      onRestoreRequested;
    }

    @Value
    @lombok.Builder
    class ProfileActivityParams {
        @lombok.Builder.Default
        List<EntityRef>            subjects = List.of();
        Long                       actorId;
        Long                       viewerActorId;
        @lombok.Builder.Default
        List<ActivityRowHook>      bindings = List.of();
    }

    @Value
    @lombok.Builder
    class SnapshotRowHookParams<T extends AuditableSnapshot> {
        @NonNull EntityType         entityType;
        @NonNull Class<T>           snapshotClass;
        @NonNull Predicate<T>       isCurrent;
        Long                        subjectEntityId;
        BiConsumer<Long, Long>      onRestore;
    }

    Component buildAuditHistoryPanel(EntityHistoryParams params);

    Component buildAuditActivityPanel(ProfileActivityParams params);

    <T extends AuditableSnapshot> ActivityRowHook snapshotRowHook(SnapshotRowHookParams<T> params);
}
