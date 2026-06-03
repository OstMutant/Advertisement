package org.ost.audit.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.ost.audit.services.AuditReadService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.ObjLongConsumer;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditHistoryPanel extends Div
        implements Configurable<AuditHistoryPanel, AuditHistoryPanel.Parameters>,
                   Initialization<AuditHistoryPanel> {

    @lombok.Value
    @lombok.Builder
    public static class Parameters {
        EntityType                           entityType;
        Long                                 entityId;
        Long                                 userId;
        boolean                              isPrivileged;
        boolean                              canOperate;
        ObjLongConsumer<AuditHistoryItemDto> onRestoreRequested;
    }

    private final transient I18nService                                i18n;
    private final transient AuditReadService                           auditReadService;
    private final transient ComponentFactory<AuditHistoryListRenderer> listRendererFactory;

    @Override
    @PostConstruct
    public AuditHistoryPanel init() {
        addClassName("entity-history-list");
        return this;
    }

    @Override
    public AuditHistoryPanel configure(Parameters p) {
        AuditableSnapshot currentSnapshot = auditReadService
                .getLastSnapshot(p.getEntityType(), p.getEntityId())
                .orElse(null);
        List<AuditHistoryItemDto> history = auditReadService
                .getEntityHistory(p.getEntityType(), p.getEntityId(), p.getUserId(), p.isPrivileged());
        if (history.isEmpty()) {
            add(emptyState());
            return this;
        }
        AuditHistoryRowRenderer.RenderConfig cfg = new AuditHistoryRowRenderer.RenderConfig(
                new EntityRef(p.getEntityType(), p.getEntityId()), currentSnapshot,
                history.size(), p.isCanOperate(), p.getOnRestoreRequested());
        listRendererFactory.get()
                .buildRows(history, cfg)
                .forEach(this::add);
        return this;
    }

    private Span emptyState() {
        Span span = new Span(i18n.get(AuditI18n.HISTORY_EMPTY));
        span.addClassName("entity-history-empty");
        return span;
    }
}
