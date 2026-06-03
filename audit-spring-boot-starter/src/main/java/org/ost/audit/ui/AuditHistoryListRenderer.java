package org.ost.audit.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.AuditHistoryItemDto;
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
public class AuditHistoryListRenderer {

    private final AuditDomainHook                                    auditDomainHook;
    private final ComponentFactory<AuditHistoryRowRenderer>          rowRendererFactory;

    List<Div> buildRows(List<AuditHistoryItemDto> history, AuditHistoryRowRenderer.RenderConfig cfg) {
        AuditHistoryRowRenderer.RowContext ctx = new AuditHistoryRowRenderer.RowContext(resolveActorNames(history));
        AuditHistoryRowRenderer renderer = rowRendererFactory.get();
        return history.stream()
                .map(h -> renderer.buildRow(h, ctx, cfg))
                .toList();
    }

    private Map<Long, String> resolveActorNames(List<AuditHistoryItemDto> history) {
        Set<Long> ids = history.stream()
                .map(AuditHistoryItemDto::actorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : auditDomainHook.resolveNames(ids);
    }
}
