package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.core.model.EntityRef;
import org.ost.platform.core.model.EntityType;
import org.springframework.context.annotation.Scope;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditActivityListRenderer {

    private final AuditDomainHook                            auditDomainHook;
    private final ComponentFactory<AuditActivityRowRenderer> rowRendererFactory;

    List<Div> buildRows(List<AuditActivityItemDto<AuditableSnapshot>> items, Long viewerActorId,
                        List<AuditActivityRowHook<?>> bindings) {
        AuditActivityRowRenderer.RowContext ctx = buildRowContext(items);
        AuditActivityRowRenderer renderer = rowRendererFactory.get();
        Map<EntityType, AuditActivityRowHook<?>> bindingMap = bindings.stream()
                .collect(Collectors.toMap(AuditActivityRowHook::entityType, h -> h,
                        (a, _) -> a, () -> new EnumMap<>(EntityType.class)));

        List<Div> rows = new ArrayList<>(items.size());
        for (AuditActivityItemDto<AuditableSnapshot> item : items) {
            Div row = renderer.buildRow(item, viewerActorId, ctx);
            AuditActivityRowHook<?> hook = bindingMap.get(item.entityType());
            if (hook != null) {
                Component decoration = decorateCapture(hook, item);
                if (decoration != null) row.add(decoration);
            }
            rows.add(row);
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static <T extends AuditableSnapshot> Component decorateCapture(
            AuditActivityRowHook<?> hook, AuditActivityItemDto<AuditableSnapshot> item) {
        return ((AuditActivityRowHook<T>) hook).decorate((AuditActivityItemDto<T>) item);
    }

    private AuditActivityRowRenderer.RowContext buildRowContext(List<AuditActivityItemDto<AuditableSnapshot>> items) {
        Set<Long> actorIds = new HashSet<>();
        Map<Long, String> displayNames = new HashMap<>();
        Map<EntityType, Set<Long>> byType = new EnumMap<>(EntityType.class);

        for (AuditActivityItemDto<AuditableSnapshot> item : items) {
            if (item.changedByActorId() != null) actorIds.add(item.changedByActorId());
            displayNames.computeIfAbsent(item.entityId(), _ ->
                    auditDomainHook.resolveDisplayName(item.entityType(), item.snapshotData()));
            byType.computeIfAbsent(item.entityType(), _ -> new HashSet<>()).add(item.entityId());
        }

        Map<Long, String> actorNames = actorIds.isEmpty() ? Map.of() : auditDomainHook.resolveNames(actorIds);

        Set<EntityRef> existingRefs = new HashSet<>();
        byType.forEach((type, ids) ->
                auditDomainHook.findExisting(type, ids).forEach(id -> existingRefs.add(new EntityRef(type, id))));

        return new AuditActivityRowRenderer.RowContext(actorNames, displayNames, existingRefs);
    }
}
