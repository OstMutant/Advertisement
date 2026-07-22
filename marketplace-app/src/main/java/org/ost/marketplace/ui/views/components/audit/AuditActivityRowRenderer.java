package org.ost.marketplace.ui.views.components.audit;
import org.ost.marketplace.services.i18n.I18nKey;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.InstantFormatter;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityRef;
import org.ost.marketplace.ui.core.Initialization;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.views.components.buttons.UiTertiaryButton;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditActivityRowRenderer implements Initialization<AuditActivityRowRenderer> {

    record RowContext(Map<Long, String> actorNames) {}

    record RenderConfig(
            EntityRef                            entityRef,
            AuditableSnapshot                    currentSnapshot,
            int                                  historySize,
            boolean                              canOperate,
            LongConsumer onRestoreRequested) {}

    private final I18nService              i18n;
    private final InstantFormatter         formatter;
    private final AuditTimelineRowRenderer fieldRenderer;
    private final UiComponentFactory<UiTertiaryButton> tertiaryButtonFactory;

    @Override
    public AuditActivityRowRenderer init() { return this; }

    Div buildRow(@NonNull AuditActivityItemDto<? extends AuditableSnapshot> h, @NonNull RowContext ctx, @NonNull RenderConfig cfg) {
        Div row = new Div();
        row.addClassName("entity-activity-row");

        row.add(metaRow(h, ctx));

        row.add(fieldRenderer.buildActivityFieldsList(h, cfg.entityRef()));

        boolean canShowAction = h.actionType() == ActionType.UPDATED
                || h.actionType() == ActionType.RESTORED
                || (h.actionType() == ActionType.CREATED && cfg.historySize() > 1);
        if (cfg.canOperate() && canShowAction) {
            boolean isCurrentState = Objects.equals(h.snapshotData(), cfg.currentSnapshot());
            row.add(buildRowActions(h, isCurrentState, cfg.onRestoreRequested()));
        }
        return row;
    }

    private Div metaRow(AuditActivityItemDto<? extends AuditableSnapshot> h, RowContext ctx) {
        Div meta = new Div(versionSpan(h.version()), actionSpan(h.actionType()),
                changedBySpan(ctx.actorNames().getOrDefault(h.actorId(), "")), timeSpan(h.createdAt()));
        meta.addClassName("entity-activity-meta");
        return meta;
    }

    private Span buildCurrentStateBadge() {
        Span badge = new Span(i18n.get(I18nKey.AUDIT_HISTORY_CURRENT_STATE));
        badge.addClassName("entity-activity-current-badge");
        return badge;
    }

    private Button buildRestoreButton(AuditActivityItemDto<? extends AuditableSnapshot> h, LongConsumer onRestore) {
        UiTertiaryButton btn = tertiaryButtonFactory.build(
                UiTertiaryButton.Parameters.builder().labelKey(I18nKey.AUDIT_HISTORY_RESTORE).build());
        btn.addThemeVariants(ButtonVariant.LUMO_SMALL);
        btn.addClassName("entity-activity-restore-btn");
        btn.addClickListener(_ -> onRestore.accept(h.snapshotId()));
        return btn;
    }

    private Component buildRowActions(AuditActivityItemDto<? extends AuditableSnapshot> h, boolean isCurrentState,
                                      LongConsumer onRestore) {
        return isCurrentState ? buildCurrentStateBadge() : buildRestoreButton(h, onRestore);
    }

    private static Span versionSpan(int version) {
        Span span = new Span("v" + version);
        span.addClassName("entity-activity-version");
        return span;
    }

    private Span actionSpan(ActionType actionType) {
        Span span = new Span(i18n.get(I18nKey.forAction(actionType)));
        span.addClassName("entity-activity-action");
        span.addClassName("entity-activity-action--" + actionType.name().toLowerCase());
        return span;
    }

    private static Span changedBySpan(String userName) {
        Span span = new Span(userName);
        span.addClassName("entity-activity-user");
        return span;
    }

    private Span timeSpan(Instant createdAt) {
        Span span = new Span(formatter.formatInstantHuman(createdAt));
        span.addClassName("entity-activity-time");
        return span;
    }

}
