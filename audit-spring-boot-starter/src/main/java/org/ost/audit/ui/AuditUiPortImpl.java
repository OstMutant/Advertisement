package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.platform.ui.ComponentFactory;

@SpringComponent
@RequiredArgsConstructor
public class AuditUiPortImpl implements AuditUiPort {

    private final ComponentFactory<AuditHistoryPanel>          historyPanelFactory;
    private final ComponentFactory<AuditActivityPanel>         activityPanelFactory;
    private final ComponentFactory<AuditSnapshotBinder<?>>     snapshotBinderFactory;

    @Override
    public Component buildAuditHistoryPanel(EntityHistoryParams p) {
        return historyPanelFactory.build(AuditHistoryPanel.Parameters.builder()
                .entityType(p.getEntityType())
                .entityId(p.getEntityId())
                .userId(p.getUserId())
                .isPrivileged(p.isPrivileged())
                .canOperate(p.isCanOperate())
                .onRestoreRequested(p.getOnRestoreRequested())
                .build());
    }

    @Override
    public Component buildAuditActivityPanel(ProfileActivityParams p) {
        return activityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .subjects(p.getSubjects())
                .actorId(p.getActorId())
                .viewerActorId(p.getViewerActorId())
                .bindings(p.getBindings())
                .build());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AuditableSnapshot> AuditActivityRowHook snapshotRowHook(SnapshotRowHookParams<T> p) {
        return snapshotBinderFactory.build(AuditSnapshotBinder.Parameters.<T>builder()
                .entityType(p.getEntityType())
                .snapshotClass(p.getSnapshotClass())
                .isCurrent(p.getIsCurrent())
                .subjectEntityId(p.getSubjectEntityId())
                .onRestore(p.getOnRestore())
                .build());
    }
}
