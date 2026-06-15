package org.ost.marketplace.ui.audit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.core.ComponentFactory;
import org.ost.marketplace.i18n.I18nService;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.springframework.context.annotation.Scope;

import java.util.List;


@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditTimelinePanel extends Div
        implements Configurable<AuditTimelinePanel, AuditTimelinePanel.Parameters>,
                   Initialization<AuditTimelinePanel> {

    @lombok.Builder
    @lombok.Getter
    @lombok.EqualsAndHashCode
    @lombok.ToString
    public static class Parameters {
        Long actorId;
        Long viewerActorId;
        @lombok.Builder.Default
        int  limit = 50;
    }

    private final transient I18nService                                 i18n;
    private final transient AuditPort                                   auditPort;
    private final transient ComponentFactory<AuditTimelineListRenderer> listRendererFactory;

    @Override
    @PostConstruct
    public AuditTimelinePanel init() {
        addClassName("activity-feed-list");
        return this;
    }

    @Override
    public AuditTimelinePanel configure(@NonNull Parameters p) {
        List<AuditTimelineItemDto<AuditableSnapshot>> items =
                auditPort.getTimeline(p.getActorId(), p.getLimit());
        if (items.isEmpty()) {
            add(emptyState());
            return this;
        }
        listRendererFactory.get()
                .buildRows(items, p.getViewerActorId())
                .forEach(this::add);
        return this;
    }

    private Span emptyState() {
        Span span = new Span(i18n.get(AuditI18n.ACTIVITY_EMPTY));
        span.addClassName("activity-feed-empty");
        return span;
    }
}
