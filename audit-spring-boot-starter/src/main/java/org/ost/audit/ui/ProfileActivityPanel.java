package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.audit.services.ActivityService;
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.platform.audit.dto.ActivityItemDto;
import org.ost.platform.audit.spi.ActivityRowHook;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.core.ui.ComponentBuilder;
import org.ost.platform.core.ui.Configurable;
import org.ost.platform.core.ui.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.List;

@SpringComponent
@ConditionalOnAuditEnabled
@Scope("prototype")
@RequiredArgsConstructor
public class ProfileActivityPanel extends Div
        implements Configurable<ProfileActivityPanel, ProfileActivityPanel.Parameters>,
                   Initialization<ProfileActivityPanel> {

    @Value
    @lombok.Builder
    public static class Parameters {
        @NonNull EntityType subjectType;
        @NonNull Long       subjectId;
        Long                viewerActorId;
        String              emptyLabel;
        @lombok.Builder.Default
        List<ActivityRowHook> bindings = List.of();
    }

    @SpringComponent
    @ConditionalOnAuditEnabled
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<ProfileActivityPanel, Parameters> {
        @Getter
        private final ObjectProvider<ProfileActivityPanel> provider;
    }

    private final ActivityService                     activityService;
    private final ObjectProvider<ActivityRowRenderer> rendererProvider;

    @Override
    @PostConstruct
    public ProfileActivityPanel init() {
        addClassName("activity-feed-list");
        return this;
    }

    @Override
    public ProfileActivityPanel configure(Parameters p) {
        List<ActivityItemDto> items = activityService.getForSubject(p.getSubjectType(), p.getSubjectId());

        if (items.isEmpty()) {
            Span empty = new Span(p.getEmptyLabel() != null ? p.getEmptyLabel() : "");
            empty.addClassName("activity-feed-empty");
            add(empty);
            return this;
        }

        ActivityRowRenderer renderer = rendererProvider.getObject();
        for (ActivityItemDto item : items) {
            Div row = renderer.buildRow(item, p.getViewerActorId());
            decorateRow(row, item, p.getBindings());
            add(row);
        }
        return this;
    }

    private static void decorateRow(Div row, ActivityItemDto item, List<ActivityRowHook> bindings) {
        for (ActivityRowHook binding : bindings) {
            if (binding.entityType() == item.entityType()) {
                Component decoration = binding.decorate(item);
                if (decoration != null) row.add(decoration);
                return;
            }
        }
    }
}
