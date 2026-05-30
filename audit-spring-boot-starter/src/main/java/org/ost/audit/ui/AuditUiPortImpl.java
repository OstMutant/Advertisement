package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.spi.AuditUiPort;

@SpringComponent
@RequiredArgsConstructor
public class AuditUiPortImpl implements AuditUiPort {

    private final AuditHistoryPanel.Builder    historyBuilder;
    private final AuditActivityPanel.Builder  profileActivityBuilder;

    @Override
    public Component buildAuditHistoryPanel(EntityHistoryParams p) {
        return historyBuilder.build(AuditHistoryPanel.Parameters.builder()
                .entityType(p.getEntityType())
                .entityId(p.getEntityId())
                .userId(p.getUserId())
                .isPrivileged(p.isPrivileged())
                .canOperate(p.isCanOperate())
                .onRestoreRequested(p.getOnRestoreRequested())
                .labelEmpty(p.getLabelEmpty())
                .labelCurrentState(p.getLabelCurrentState())
                .labelRestore(p.getLabelRestore())
                .build());
    }

    @Override
    public Component buildAuditActivityPanel(ProfileActivityParams p) {
        return profileActivityBuilder.build(AuditActivityPanel.Parameters.builder()
                .subjects(p.getSubjects())
                .actorId(p.getActorId())
                .viewerActorId(p.getViewerActorId())
                .emptyLabel(p.getEmptyLabel())
                .bindings(p.getBindings())
                .build());
    }
}
