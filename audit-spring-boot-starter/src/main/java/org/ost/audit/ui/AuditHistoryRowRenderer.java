package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.audit.spi.AuditHistoryRowActionsHook;

import java.util.List;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.ObjLongConsumer;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditHistoryRowRenderer {

    record RowContext(
            EntityType entityType, Long entityId, AuditableSnapshot currentSnapshot, int historySize,
            boolean canOperate, ObjLongConsumer<AuditHistoryItemDto> onRestoreRequested,
            Map<Long, String> actorNames) {}

    private final I18nService                                i18n;
    private final InstantFormatter                           formatter;
    private final AuditActivityRowRenderer                   fieldRenderer;
    private final List<AuditActivityEnrichHook>              activityEnrichHooks;
    private final ObjectProvider<AuditHistoryRowActionsHook> rowActionsHook;

    public Div buildRow(AuditHistoryItemDto h, RowContext ctx) {
        Div row = new Div();
        row.addClassName("entity-history-row");

        Div meta = new Div(versionSpan(h.version()), actionSpan(h.actionType()),
                changedBySpan(ctx.actorNames().getOrDefault(h.actorId(), "")), timeSpan(h.createdAt()));
        meta.addClassName("entity-history-meta");
        row.add(meta);

        row.add(fieldRenderer.buildHistoryFieldsList(h, new EntityRef(ctx.entityType(), ctx.entityId())));

        boolean isTextRow = h.prevSnapshotId() != null || h.actionType() == ActionType.CREATED;
        if (ctx.canOperate() && isTextRow && (h.actionType() != ActionType.CREATED || ctx.historySize() > 1)) {
            boolean isCurrentState = snapshotsEqual(h.snapshotData(), ctx.currentSnapshot())
                    && mediaMatchCurrent(ctx.entityType(), ctx.entityId(), h.version());
            rowActionsHook.ifAvailable(hook -> {
                Component actions = hook.buildRowActions(h, isCurrentState, ctx.onRestoreRequested());
                if (actions != null) row.add(actions);
            });
        }
        return row;
    }

    private static Span versionSpan(int version) {
        Span span = new Span("v" + version);
        span.addClassName("entity-history-version");
        return span;
    }

    private Span actionSpan(ActionType actionType) {
        Span span = new Span(i18n.get(AuditI18n.forAction(actionType)));
        span.addClassName("entity-history-action");
        span.addClassName("entity-history-action--" + actionType.name().toLowerCase());
        return span;
    }

    private static Span changedBySpan(String userName) {
        Span span = new Span(userName);
        span.addClassName("entity-history-user");
        return span;
    }

    private Span timeSpan(Instant createdAt) {
        Span span = new Span(formatter.formatInstantHuman(createdAt));
        span.addClassName("entity-history-time");
        return span;
    }

    private static boolean snapshotsEqual(AuditableSnapshot a, AuditableSnapshot b) {
        return Objects.equals(a, b);
    }

    private boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version) {
        EntityRef ref = new EntityRef(entityType, entityId);
        return activityEnrichHooks.stream()
                .filter(h -> h.entityType() == entityType)
                .findFirst()
                .map(h -> h.matchesCurrent(ref, version))
                .orElse(true);
    }

}
