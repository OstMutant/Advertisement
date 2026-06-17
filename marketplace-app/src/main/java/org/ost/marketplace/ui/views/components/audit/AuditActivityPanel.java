package org.ost.marketplace.ui.views.components.audit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.function.LongConsumer;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditActivityPanel extends Div
        implements Configurable<AuditActivityPanel, AuditActivityPanel.Parameters>,
                   Initialization<AuditActivityPanel> {

    @lombok.Value
    @lombok.Builder
    public static class Parameters {
        EntityRef                            entityRef;
        Long                                 userId;
        boolean                              isPrivileged;
        boolean                              canOperate;
        LongConsumer onRestoreRequested;
    }

    private final transient I18nService                                i18n;
    private final transient AuditPort                                  auditPort;
    private final transient ComponentFactory<AuditActivityListRenderer> listRendererFactory;

    @Override
    @PostConstruct
    public AuditActivityPanel init() {
        addClassName("entity-activity-list");
        return this;
    }

    @Override
    public AuditActivityPanel configure(@NonNull Parameters p) {
        AuditableSnapshot currentSnapshot = auditPort
                .getLastSnapshot(p.getEntityRef().entityType(), p.getEntityRef().entityId())
                .orElse(null);
        List<AuditActivityItemDto<? extends AuditableSnapshot>> items = auditPort
                .getEntityActivity(p.getEntityRef().entityType(), p.getEntityRef().entityId(), p.getUserId(), p.isPrivileged());
        if (items.isEmpty()) {
            add(emptyState());
            return this;
        }
        AuditActivityRowRenderer.RenderConfig cfg = new AuditActivityRowRenderer.RenderConfig(
                p.getEntityRef(), currentSnapshot,
                items.size(), p.isCanOperate(), p.getOnRestoreRequested());
        listRendererFactory.get()
                .buildRows(items, cfg)
                .forEach(this::add);
        return this;
    }

    private Span emptyState() {
        Span span = new Span(i18n.get(AuditI18n.HISTORY_EMPTY));
        span.addClassName("entity-activity-empty");
        return span;
    }
}
