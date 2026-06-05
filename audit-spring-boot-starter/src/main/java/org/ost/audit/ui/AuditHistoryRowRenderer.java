package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.audit.spi.AuditActivityEnrichHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.i18n.InstantFormatter;
import org.ost.platform.core.model.ActionType;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ObjLongConsumer;
import java.util.stream.Collectors;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditHistoryRowRenderer implements Initialization<AuditHistoryRowRenderer> {

    record RowContext(Map<Long, String> actorNames) {}

    record RenderConfig(
            EntityRef                            entityRef,
            AuditableSnapshot                    currentSnapshot,
            int                                  historySize,
            boolean                              canOperate,
            ObjLongConsumer<AuditHistoryItemDto> onRestoreRequested) {}

    private final I18nService              i18n;
    private final InstantFormatter         formatter;
    private final AuditActivityRowRenderer fieldRenderer;
    private final List<AuditActivityEnrichHook> activityEnrichHookList;

    private Map<EntityType, AuditActivityEnrichHook> enrichHooks;

    @Override
    @PostConstruct
    public AuditHistoryRowRenderer init() {
        enrichHooks = activityEnrichHookList.stream().collect(Collectors.toMap(AuditActivityEnrichHook::entityType, h -> h, (a, b) -> a, () -> new EnumMap<>(EntityType.class)));
        return this;
    }

    Div buildRow(AuditHistoryItemDto h, RowContext ctx, RenderConfig cfg) {
        Div row = new Div();
        row.addClassName("entity-history-row");

        row.add(metaRow(h, ctx));

        row.add(fieldRenderer.buildHistoryFieldsList(h, cfg.entityRef()));

        boolean isTextRow = h.prevSnapshotId() != null || h.actionType() == ActionType.CREATED;
        if (cfg.canOperate() && isTextRow && (h.actionType() != ActionType.CREATED || cfg.historySize() > 1)) {
            boolean isCurrentState = Objects.equals(h.snapshotData(), cfg.currentSnapshot())
                    && mediaMatchCurrent(cfg, h.version());
            row.add(buildRowActions(h, isCurrentState, cfg.onRestoreRequested()));
        }
        return row;
    }

    private Div metaRow(AuditHistoryItemDto h, RowContext ctx) {
        Div meta = new Div(versionSpan(h.version()), actionSpan(h.actionType()),
                changedBySpan(ctx.actorNames().getOrDefault(h.actorId(), "")), timeSpan(h.createdAt()));
        meta.addClassName("entity-history-meta");
        return meta;
    }

    private Span buildCurrentStateBadge() {
        Span badge = new Span(i18n.get(AuditI18n.HISTORY_CURRENT_STATE));
        badge.addClassName("entity-history-current-badge");
        return badge;
    }

    private Button buildRestoreButton(AuditHistoryItemDto h, ObjLongConsumer<AuditHistoryItemDto> onRestore) {
        Button btn = new Button(i18n.get(AuditI18n.HISTORY_RESTORE));
        btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        btn.addClassName("entity-history-restore-btn");
        long snapId = h.snapshotId();
        btn.addClickListener(_ -> onRestore.accept(h, snapId));
        return btn;
    }

    private Component buildRowActions(AuditHistoryItemDto h, boolean isCurrentState,
                                      ObjLongConsumer<AuditHistoryItemDto> onRestore) {
        return isCurrentState ? buildCurrentStateBadge() : buildRestoreButton(h, onRestore);
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

    private boolean mediaMatchCurrent(RenderConfig cfg, int version) {
        AuditActivityEnrichHook hook = enrichHooks.get(cfg.entityRef().entityType());
        return hook == null || hook.matchesCurrent(cfg.entityRef(), version);
    }
}
