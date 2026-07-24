package org.ost.marketplace.ui.views.components.audit;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditActivityItemDto;
import org.ost.platform.audit.spi.AuditDomainHook;
import org.ost.platform.core.ComponentFactory;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@SpringComponent
@Scope("prototype")
@RequiredArgsConstructor
public class AuditActivityListRenderer {

    private final AuditDomainHook                                    auditDomainHook;
    private final ComponentFactory<AuditActivityRowRenderer>          rowRendererFactory;

    List<Div> buildRows(List<AuditActivityItemDto<? extends AuditableSnapshot>> items, AuditActivityRowRenderer.RenderConfig cfg) {
        AuditActivityRowRenderer.RowContext ctx = new AuditActivityRowRenderer.RowContext(resolveActorNames(items));
        AuditActivityRowRenderer renderer = rowRendererFactory.get();
        return items.stream()
                .map(h -> renderer.buildRow(h, ctx, cfg))
                .toList();
    }

    private Map<Long, String> resolveActorNames(List<AuditActivityItemDto<? extends AuditableSnapshot>> items) {
        Set<Long> ids = items.stream()
                .map(AuditActivityItemDto::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : auditDomainHook.resolveNames(ids);
    }
}
