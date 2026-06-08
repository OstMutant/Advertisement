package org.ost.audit.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.spi.AuditActivityRowHook;
import org.ost.platform.audit.spi.AuditUiPort;
import org.ost.platform.core.ComponentFactory;

@SpringComponent
@RequiredArgsConstructor
public class AuditUiPortImpl implements AuditUiPort {

    private final ComponentFactory<AuditActivityPanel>      activityPanelFactory;
    private final ComponentFactory<AuditTimelinePanel>      timelinePanelFactory;
    private final ComponentFactory<AuditSnapshotBinder<?>>  snapshotBinderFactory;

    @Override
    public Component buildAuditActivityPanel(@NonNull EntityActivityParams p) {
        return activityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityType(p.getEntityType())
                .entityId(p.getEntityId())
                .userId(p.getUserId())
                .isPrivileged(p.isPrivileged())
                .canOperate(p.isCanOperate())
                .onRestoreRequested(p.getOnRestoreRequested())
                .build());
    }

    @Override
    public Component buildAuditTimelinePanel(@NonNull TimelineParams p) {
        return timelinePanelFactory.build(AuditTimelinePanel.Parameters.builder()
                .actorId(p.getActorId())
                .viewerActorId(p.getViewerActorId())
                .limit(p.getLimit())
                .build());
    }

    @Override
    public <T extends AuditableSnapshot> AuditActivityRowHook<T> snapshotRowHook(@NonNull SnapshotRowHookParams<T> p) {
        return snapshotBinderFactory.buildAs(AuditSnapshotBinder.Parameters.<T>builder()
                .entityType(p.getEntityType())
                .isCurrent(p.getIsCurrent())
                .subjectEntityId(p.getSubjectEntityId())
                .onRestore(p.getOnRestore())
                .build());
    }
}
