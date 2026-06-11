package org.ost.platform.ui.spi.audit;

import com.vaadin.flow.component.Component;
import lombok.NonNull;
import lombok.Value;
import org.ost.platform.core.model.EntityRef;

import java.util.function.LongConsumer;

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
    class ActivityParams {
        EntityRef    entityRef;
        Long         userId;
        boolean      isPrivileged;
        boolean      canOperate;
        LongConsumer onRestoreRequested;
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

    Component buildAuditActivityPanel(@NonNull ActivityParams params);

    Component buildAuditTimelinePanel(@NonNull TimelineParams params);
}
