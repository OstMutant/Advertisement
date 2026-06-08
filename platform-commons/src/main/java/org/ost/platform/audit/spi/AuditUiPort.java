package org.ost.platform.audit.spi;

import com.vaadin.flow.component.Component;
import lombok.NonNull;
import lombok.Value;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.core.model.EntityType;

import java.util.function.BiConsumer;
import java.util.function.ObjLongConsumer;
import java.util.function.Predicate;

/**
 * Port: marketplace → audit-starter.
 * Audit-starter exposes pre-built Vaadin UI panels to marketplace.
 * Marketplace calls this to embed entity activity history or full actor timeline
 * without depending on audit internals.
 * Implementation: {@code AuditUiPortImpl} in audit-spring-boot-starter.
 * Injected via {@code ObjectProvider} — degrades gracefully when audit is disabled.
 */
public interface AuditUiPort {

    @Value
    @lombok.Builder
    class EntityActivityParams {
        EntityType                             entityType;
        Long                                   entityId;
        Long                                   userId;
        boolean                                isPrivileged;
        boolean                                canOperate;
        ObjLongConsumer<AuditHistoryItemDto>   onRestoreRequested;
    }

    @lombok.Builder
    @lombok.Getter
    @lombok.EqualsAndHashCode
    @lombok.ToString
    class TimelineParams {
        Long actorId;
        Long viewerActorId;
        @lombok.Builder.Default
        int  limit = 50;
    }

    @Value
    @lombok.Builder
    class SnapshotRowHookParams<T extends AuditableSnapshot> {
        @NonNull EntityType         entityType;
        @NonNull Predicate<T>       isCurrent;
        Long                        subjectEntityId;
        BiConsumer<Long, Long>      onRestore;
    }

    Component buildAuditActivityPanel(@NonNull EntityActivityParams params);

    Component buildAuditTimelinePanel(@NonNull TimelineParams params);

    <T extends AuditableSnapshot> AuditActivityRowHook<T> snapshotRowHook(@NonNull SnapshotRowHookParams<T> params);
}
