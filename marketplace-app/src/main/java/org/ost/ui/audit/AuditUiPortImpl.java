package org.ost.ui.audit;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.spring.annotation.SpringComponent;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.ost.platform.ui.spi.audit.AuditUiPort;
import org.ost.platform.core.ComponentFactory;

@SpringComponent
@RequiredArgsConstructor
public class AuditUiPortImpl implements AuditUiPort {

    private final ComponentFactory<AuditActivityPanel>  activityPanelFactory;
    private final ComponentFactory<AuditTimelinePanel>  timelinePanelFactory;

    @Override
    public Component buildAuditActivityPanel(@NonNull ActivityParams p) {
        return activityPanelFactory.build(AuditActivityPanel.Parameters.builder()
                .entityRef(p.getEntityRef())
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
}
