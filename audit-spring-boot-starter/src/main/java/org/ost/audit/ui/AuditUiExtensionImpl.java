package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.ConditionalOnAuditEnabled;
import org.ost.platform.audit.spi.AuditUiExtension;

@SpringComponent
@ConditionalOnAuditEnabled
@RequiredArgsConstructor
public class AuditUiExtensionImpl implements AuditUiExtension {

    private final EntityHistoryPanel.Builder    historyBuilder;
    private final ProfileActivityPanel.Builder  profileActivityBuilder;

    @Override
    public Component buildEntityHistoryPanel(EntityHistoryParams p) {
        return historyBuilder.build(EntityHistoryPanel.Parameters.builder()
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
    public Component buildProfileActivityPanel(ProfileActivityParams p) {
        return profileActivityBuilder.build(ProfileActivityPanel.Parameters.builder()
                .subjectType(p.getSubjectType())
                .subjectId(p.getSubjectId())
                .viewerActorId(p.getViewerActorId())
                .emptyLabel(p.getEmptyLabel())
                .bindings(p.getBindings())
                .build());
    }
}
