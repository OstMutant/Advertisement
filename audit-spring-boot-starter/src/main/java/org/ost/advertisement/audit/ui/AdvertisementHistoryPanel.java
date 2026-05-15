package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.ConditionalOnAuditEnabled;
import org.ost.advertisement.audit.services.AuditHistoryService;
import org.ost.advertisement.events.dto.AdvertisementHistoryDto;
import org.ost.advertisement.events.model.ActionType;
import org.ost.advertisement.events.spi.AdvertisementHistoryExtension;
import org.ost.advertisement.i18n.I18nService;
import org.ost.advertisement.i18n.InstantFormatter;
import org.ost.advertisement.ui.rules.Configurable;
import org.ost.advertisement.ui.rules.ComponentBuilder;
import org.ost.advertisement.ui.rules.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

@ConditionalOnAuditEnabled
@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AdvertisementHistoryPanel extends Div
        implements Configurable<AdvertisementHistoryPanel, AdvertisementHistoryPanel.Parameters>,
                   Initialization<AdvertisementHistoryPanel> {

    @lombok.Value
    @lombok.Builder
    public static class Parameters {
        Long    adId;
        Long    userId;
        boolean isPrivileged;
        String  currentTitle;
        String  currentDesc;
        boolean                                    canOperate;
        BiConsumer<AdvertisementHistoryDto, Long>  onRestoreRequested;
        String  labelEmpty;
        String  labelCurrentState;
        String  labelRestore;
    }

    @ConditionalOnAuditEnabled
    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<AdvertisementHistoryPanel, Parameters> {
        @Getter
        private final ObjectProvider<AdvertisementHistoryPanel> provider;
    }

    private final I18nService                                  i18n;
    private final InstantFormatter                             formatter;
    private final AuditHistoryService                          auditHistoryService;
    private final ObjectProvider<ActivityRowRenderer>          rendererProvider;
    private final ObjectProvider<AdvertisementHistoryExtension> historyExtensionProvider;

    @Override
    @PostConstruct
    public AdvertisementHistoryPanel init() {
        addClassName("adv-history-list");
        return this;
    }

    @Override
    public AdvertisementHistoryPanel configure(Parameters p) {
        List<AdvertisementHistoryDto> history = auditHistoryService.getAdvertisementHistory(
                p.getAdId(), p.getUserId(), p.isPrivileged());

        if (history.isEmpty()) {
            Span empty = new Span(p.getLabelEmpty());
            empty.addClassName("adv-history-empty");
            add(empty);
            return this;
        }

        RowContext ctx = new RowContext(
                p.getAdId(), p.getCurrentTitle(), p.getCurrentDesc(), history.size(),
                p.isCanOperate(), p.getLabelCurrentState(), p.getLabelRestore(),
                p.getOnRestoreRequested());
        ActivityRowRenderer renderer = rendererProvider.getObject();

        for (AdvertisementHistoryDto h : history) {
            add(buildRow(h, renderer, ctx));
        }
        return this;
    }

    private record RowContext(
            Long adId, String currentTitle, String currentDesc, int historySize,
            boolean canOperate, String labelCurrentState, String labelRestore,
            BiConsumer<AdvertisementHistoryDto, Long> onRestoreRequested) {}

    private Div buildRow(AdvertisementHistoryDto h, ActivityRowRenderer renderer, RowContext ctx) {
        Long adId               = ctx.adId();
        String currentTitle     = ctx.currentTitle();
        String currentDesc      = ctx.currentDesc();
        int historySize         = ctx.historySize();
        boolean canOperate      = ctx.canOperate();
        String labelCurrentState = ctx.labelCurrentState();
        String labelRestore     = ctx.labelRestore();
        BiConsumer<AdvertisementHistoryDto, Long> onRestoreRequested = ctx.onRestoreRequested();
        Div row = new Div();
        row.addClassName("adv-history-row");

        Span versionBadge = new Span("v" + h.version());
        versionBadge.addClassName("adv-history-version");

        Span actionBadge = new Span(i18n.get(formatActionKey(h.actionType())));
        actionBadge.addClassName("adv-history-action");
        actionBadge.addClassName("adv-history-action--" + h.actionType().name().toLowerCase());

        Span changedBy = new Span(h.changedByUserName());
        changedBy.addClassName("adv-history-user");

        Span time = new Span(formatter.formatInstantHuman(h.createdAt()));
        time.addClassName("adv-history-time");

        Div meta = new Div(versionBadge, actionBadge, changedBy, time);
        meta.addClassName("adv-history-meta");
        row.add(meta);

        row.add(renderer.buildAdvHistoryFieldsList(h, adId));

        boolean isTextRow = h.prevSnapshotId() != null || h.actionType() == ActionType.CREATED;
        if (canOperate && isTextRow && (h.actionType() != ActionType.CREATED || historySize > 1)) {
            boolean textMatches = Objects.equals(h.title(), currentTitle)
                    && Objects.equals(h.description(), currentDesc);
            boolean matchesCurrent = textMatches && mediaMatchCurrent(adId, h.version());

            if (matchesCurrent) {
                Span badge = new Span(labelCurrentState);
                badge.addClassName("adv-history-current-badge");
                row.add(badge);
            } else {
                Button restoreBtn = new Button(labelRestore);
                restoreBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                restoreBtn.addClassName("adv-history-restore-btn");
                long snapId = h.snapshotId();
                restoreBtn.addClickListener(_ -> onRestoreRequested.accept(h, snapId));
                row.add(restoreBtn);
            }
        }
        return row;
    }

    private boolean mediaMatchCurrent(Long adId, int version) {
        AdvertisementHistoryExtension ext = historyExtensionProvider.getIfAvailable();
        return ext == null || ext.mediaMatchCurrent(adId, version);
    }

    private static AuditMessages formatActionKey(ActionType actionType) {
        return switch (actionType) {
            case CREATED -> AuditMessages.ACTIVITY_ACTION_CREATED;
            case UPDATED -> AuditMessages.ACTIVITY_ACTION_UPDATED;
            case DELETED -> AuditMessages.ACTIVITY_ACTION_DELETED;
        };
    }

}
