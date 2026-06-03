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
@SuppressWarnings("rawtypes")
public class AuditUiPortImpl implements AuditUiPort {

    private final ComponentFactory componentFactory;

    @Override
    public Component buildAuditHistoryPanel(EntityHistoryParams p) {
        return componentFactory.build(AuditHistoryPanel.class, AuditHistoryPanel.Parameters.builder()
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
        return componentFactory.build(AuditActivityPanel.class, AuditActivityPanel.Parameters.builder()
                .subjects(p.getSubjects())
                .actorId(p.getActorId())
                .viewerActorId(p.getViewerActorId())
                .bindings(p.getBindings())
                .build());
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends AuditableSnapshot> AuditActivityRowHook snapshotRowHook(SnapshotRowHookParams<T> p) {
        AuditSnapshotBinder<T> binder = componentFactory.get(AuditSnapshotBinder.class);
        return binder.configure(AuditSnapshotBinder.Parameters.<T>builder()
                .entityType(p.getEntityType())
                .snapshotClass(p.getSnapshotClass())
                .isCurrent(p.getIsCurrent())
                .subjectEntityId(p.getSubjectEntityId())
                .onRestore(p.getOnRestore())
                .build());
    }
}
