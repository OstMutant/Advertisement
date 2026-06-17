package org.ost.marketplace.ui.views.components.audit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
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

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditTimelineListRenderer {

    private final AuditDomainHook                            auditDomainHook;
    private final ComponentFactory<AuditTimelineRowRenderer> rowRendererFactory;

    List<Div> buildRows(List<AuditTimelineItemDto<AuditableSnapshot>> items, Long viewerActorId) {
        AuditTimelineRowRenderer.RowContext ctx = buildRowContext(items);
        AuditTimelineRowRenderer renderer = rowRendererFactory.get();

        List<Div> rows = new ArrayList<>(items.size());
        for (AuditTimelineItemDto<AuditableSnapshot> item : items) {
            rows.add(renderer.buildRow(item, viewerActorId, ctx));
        }
        return rows;
    }

    private AuditTimelineRowRenderer.RowContext buildRowContext(List<AuditTimelineItemDto<AuditableSnapshot>> items) {
        Set<Long> actorIds = new HashSet<>();
        Map<Long, String> displayNames = new HashMap<>();
        Map<EntityType, Set<Long>> byType = new EnumMap<>(EntityType.class);

        for (AuditTimelineItemDto<AuditableSnapshot> item : items) {
            if (item.changedByActorId() != null) actorIds.add(item.changedByActorId());
            displayNames.computeIfAbsent(item.entityRef().entityId(), _ -> {
                    AuditableSnapshot snapshot = item.snapshotData();
                    return snapshot != null
                            ? auditDomainHook.resolveDisplayName(item.entityRef().entityType(), snapshot)
                            : "";
                });
            byType.computeIfAbsent(item.entityRef().entityType(), _ -> new HashSet<>()).add(item.entityRef().entityId());
        }

        Map<Long, String> actorNames = actorIds.isEmpty() ? Map.of() : auditDomainHook.resolveNames(actorIds);

        Set<EntityRef> existingRefs = new HashSet<>();
        byType.forEach((type, ids) ->
                auditDomainHook.findExisting(type, ids).forEach(id -> existingRefs.add(new EntityRef(type, id))));

        return new AuditTimelineRowRenderer.RowContext(actorNames, displayNames, existingRefs);
    }
}
