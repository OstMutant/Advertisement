package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.spring.annotation.SpringComponent;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.audit.services.AuditReadService;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.i18n.I18nService;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.ost.platform.ui.ComponentBuilder;
import org.ost.platform.ui.Configurable;
import org.ost.platform.ui.Initialization;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Scope;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
        List<EntityRef>       subjects = List.of();
        Long                  actorId;
        Long                  viewerActorId;
        @lombok.Builder.Default
        List<AuditActivityRowHook> bindings = List.of();
    }

    @SpringComponent
    @RequiredArgsConstructor
    public static class Builder extends ComponentBuilder<AuditActivityPanel, Parameters> {
        @Getter
        private final ObjectProvider<AuditActivityPanel> provider;
    }

    private final I18nService                              i18n;
    private final AuditReadService                         auditReadService;
    private final AuditDomainHook                          auditDomainHook;
    private final ObjectProvider<AuditActivityRowRenderer> rendererProvider;

    @Override
    @PostConstruct
    public AuditActivityPanel init() {
        addClassName("activity-feed-list");
        return this;
    }

    @Override
    public AuditActivityPanel configure(Parameters p) {
        List<AuditActivityItemDto> items = auditReadService.getForSubject(p.getSubjects(), p.getActorId());

        if (items.isEmpty()) {
            add(emptyState());
            return this;
        }

        AuditActivityRowRenderer.DisplayContext ctx = buildDisplayContext(items);
        AuditActivityRowRenderer renderer = rendererProvider.getObject();
        for (AuditActivityItemDto item : items) {
            add(buildDecoratedRow(renderer, item, p, ctx));
        }
        return this;
    }

    private AuditActivityRowRenderer.DisplayContext buildDisplayContext(List<AuditActivityItemDto> items) {
        Set<Long> actorIds = items.stream()
                .map(AuditActivityItemDto::changedByActorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> actorNames = actorIds.isEmpty() ? Map.of() : auditDomainHook.resolveNames(actorIds);

        Map<Long, String> displayNames = new HashMap<>();
        for (AuditActivityItemDto item : items) {
            displayNames.computeIfAbsent(item.entityId(), _ ->
                    auditDomainHook.resolveDisplayName(item.entityType(), item.snapshotData()));
        }

        Map<EntityType, Set<Long>> byType = items.stream()
                .collect(Collectors.groupingBy(AuditActivityItemDto::entityType,
                        Collectors.mapping(AuditActivityItemDto::entityId, Collectors.toSet())));
        Set<EntityRef> existingRefs = new HashSet<>();
        byType.forEach((type, ids) ->
                auditDomainHook.findExisting(type, ids).forEach(id -> existingRefs.add(new EntityRef(type, id))));

        return new AuditActivityRowRenderer.DisplayContext(actorNames, displayNames, existingRefs);
    }

    private Span emptyState() {
        Span span = new Span(i18n.get(AuditI18n.ACTIVITY_EMPTY));
        span.addClassName("activity-feed-empty");
        return span;
    }

    private Div buildDecoratedRow(AuditActivityRowRenderer renderer, AuditActivityItemDto item,
                                   Parameters p, AuditActivityRowRenderer.DisplayContext ctx) {
        Div row = renderer.buildRow(item, p.getViewerActorId(), ctx);
        decorateRow(row, item, p.getBindings());
        return row;
    }

    private static void decorateRow(Div row, AuditActivityItemDto item, List<AuditActivityRowHook> bindings) {
        for (AuditActivityRowHook binding : bindings) {
            if (binding.entityType() == item.entityType()) {
                Component decoration = binding.decorate(item);
                if (decoration != null) row.add(decoration);
                return;
            }
        }
    }
}
