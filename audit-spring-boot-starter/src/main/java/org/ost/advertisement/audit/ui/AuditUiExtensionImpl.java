package org.ost.advertisement.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.audit.api.ConditionalOnAuditEnabled;
import org.ost.advertisement.audit.spi.AuditUiExtension;

@SpringComponent
@ConditionalOnAuditEnabled
@RequiredArgsConstructor
public class AuditUiExtensionImpl implements AuditUiExtension {

    private final ProfileActivityPanel.Builder userActivityBuilder;
    private final EntityHistoryPanel.Builder   historyBuilder;

    @Override
    public Component buildUserActivityPanel(UserActivityParams p) {
        return userActivityBuilder.build(ProfileActivityPanel.Parameters.builder()
                .userId(p.getUserId())
                .userName(p.getUserName())
                .userRole(p.getUserRole())
                .currentSettings(p.getCurrentSettings())
                .onRestoreUser(p.getOnRestoreUser())
                .onRestoreSettings(p.getOnRestoreSettings())
                .build());
    }

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
}
