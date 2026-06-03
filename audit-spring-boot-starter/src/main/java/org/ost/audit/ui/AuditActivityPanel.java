package org.ost.audit.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.audit.services.AuditReadService;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditActivityPanel extends Div
        implements Configurable<AuditActivityPanel, AuditActivityPanel.Parameters>,
                   Initialization<AuditActivityPanel> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @lombok.Builder.Default
        List<EntityRef>            subjects = List.of();
        Long                       actorId;
        Long                       viewerActorId;
        @lombok.Builder.Default
        List<AuditActivityRowHook<?>> bindings = List.of();
    }

    private final transient I18nService                                 i18n;
    private final transient AuditReadService                            auditReadService;
    private final transient ComponentFactory<AuditActivityListRenderer> listRendererFactory;

    @Override
    @PostConstruct
    public AuditActivityPanel init() {
        addClassName("activity-feed-list");
        return this;
    }

    @Override
    public AuditActivityPanel configure(Parameters p) {
        List<AuditActivityItemDto<AuditableSnapshot>> items = auditReadService.getForSubject(p.getSubjects(), p.getActorId());
        if (items.isEmpty()) {
            add(emptyState());
            return this;
        }
        listRendererFactory.get()
                .buildRows(items, p.getViewerActorId(), p.getBindings())
                .forEach(this::add);
        return this;
    }

    private Span emptyState() {
        Span span = new Span(i18n.get(AuditI18n.ACTIVITY_EMPTY));
        span.addClassName("activity-feed-empty");
        return span;
    }
}
