package org.ost.audit.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.EntityHistoryDto;
import org.ost.audit.services.AuditHistoryService;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.audit.spi.ActivityEnrichHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.ComponentBuilder;
import org.ost.platform.ui.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.ObjLongConsumer;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class EntityHistoryPanel extends Div
        implements Configurable<EntityHistoryPanel, EntityHistoryPanel.Parameters>,
                   Initialization<EntityHistoryPanel> {

    @lombok.Value
    @lombok.Builder
    public static class Parameters {
        EntityType entityType;
        Long       entityId;
        Long       userId;
        boolean    isPrivileged;
        boolean                                canOperate;
        ObjLongConsumer<EntityHistoryDto>      onRestoreRequested;
        String     labelEmpty;
        String     labelCurrentState;
        String     labelRestore;
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<EntityHistoryPanel, Parameters> {
        @Getter
        private final ObjectProvider<EntityHistoryPanel> provider;
    }

    private final I18nService                                   i18n;
    private final InstantFormatter                              formatter;
    private final AuditHistoryService                           auditHistoryService;
    private final ObjectProvider<ActivityRowRenderer>   rendererProvider;
    private final ActivityEnrichHook                    activityEnrichHook;

    @Override
    @PostConstruct
    public EntityHistoryPanel init() {
        addClassName("entity-history-list");
        return this;
    }

    @Override
    public EntityHistoryPanel configure(Parameters p) {
        AuditableSnapshot currentSnapshot = auditHistoryService
                .getLastSnapshot(p.getEntityType(), p.getEntityId())
                .orElse(null);

        List<EntityHistoryDto> history = auditHistoryService
                .getEntityHistory(p.getEntityType(), p.getEntityId(), p.getUserId(), p.isPrivileged());

        if (history.isEmpty()) {
            Span empty = new Span(p.getLabelEmpty());
            empty.addClassName("entity-history-empty");
            add(empty);
            return this;
        }

        RowContext ctx = new RowContext(
                p.getEntityType(), p.getEntityId(), currentSnapshot, history.size(),
                p.isCanOperate(), p.getLabelCurrentState(), p.getLabelRestore(),
                p.getOnRestoreRequested());
        ActivityRowRenderer renderer = rendererProvider.getObject();

        for (EntityHistoryDto h : history) {
            add(buildRow(h, renderer, ctx));
        }
        return this;
    }

    private record RowContext(
            EntityType entityType, Long entityId, AuditableSnapshot currentSnapshot, int historySize,
            boolean canOperate, String labelCurrentState, String labelRestore,
            ObjLongConsumer<EntityHistoryDto> onRestoreRequested) {}

    private Div buildRow(EntityHistoryDto h, ActivityRowRenderer renderer, RowContext ctx) {
        EntityType entityType        = ctx.entityType();
        Long entityId                = ctx.entityId();
        AuditableSnapshot currentSnap  = ctx.currentSnapshot();
        int historySize              = ctx.historySize();
        boolean canOperate           = ctx.canOperate();
        String labelCurrentState     = ctx.labelCurrentState();
        String labelRestore          = ctx.labelRestore();
        ObjLongConsumer<EntityHistoryDto> onRestoreRequested = ctx.onRestoreRequested();

        Div row = new Div();
        row.addClassName("entity-history-row");

        Span versionBadge = new Span("v" + h.version());
        versionBadge.addClassName("entity-history-version");

        Span actionBadge = new Span(i18n.get(formatActionKey(h.actionType())));
        actionBadge.addClassName("entity-history-action");
        actionBadge.addClassName("entity-history-action--" + h.actionType().name().toLowerCase());

        Span changedBy = new Span(h.changedByUserName());
        changedBy.addClassName("entity-history-user");

        Span time = new Span(formatter.formatInstantHuman(h.createdAt()));
        time.addClassName("entity-history-time");

        Div meta = new Div(versionBadge, actionBadge, changedBy, time);
        meta.addClassName("entity-history-meta");
        row.add(meta);

        row.add(renderer.buildHistoryFieldsList(h, new EntityRef(entityType, entityId)));

        boolean isTextRow = h.prevSnapshotId() != null || h.actionType() == ActionType.CREATED;
        if (canOperate && isTextRow && (h.actionType() != ActionType.CREATED || historySize > 1)) {
            boolean snapshotMatches = snapshotsEqual(h.snapshotData(), currentSnap);
            boolean matchesCurrent  = snapshotMatches && mediaMatchCurrent(entityType, entityId, h.version());

            if (matchesCurrent) {
                Span badge = new Span(labelCurrentState);
                badge.addClassName("entity-history-current-badge");
                row.add(badge);
            } else {
                Button restoreBtn = new Button(labelRestore);
                restoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                restoreBtn.addClassName("entity-history-restore-btn");
                long snapId = h.snapshotId();
                restoreBtn.addClickListener(_ -> onRestoreRequested.accept(h, snapId));
                row.add(restoreBtn);
            }
        }
        return row;
    }

    private static boolean snapshotsEqual(AuditableSnapshot a, AuditableSnapshot b) {
        return java.util.Objects.equals(a, b);
    }

    private boolean mediaMatchCurrent(EntityType entityType, Long entityId, int version) {
        return activityEnrichHook.matchesCurrent(new EntityRef(entityType, entityId), version);
    }

    private static AuditMessages formatActionKey(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> AuditMessages.ACTIVITY_ACTION_CREATED;
            case UPDATED -> AuditMessages.ACTIVITY_ACTION_UPDATED;
            case DELETED -> AuditMessages.ACTIVITY_ACTION_DELETED;
        };
    }
}
