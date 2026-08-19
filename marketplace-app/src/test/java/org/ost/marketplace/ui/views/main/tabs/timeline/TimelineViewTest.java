package org.ost.marketplace.ui.views.main.tabs.timeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineListRenderer;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.orchestrator.services.AuditQueryService;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.core.ComponentFactory;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimelineViewTest {

    @Mock private AuditQueryService auditQueryService;
    @Mock private AccessEvaluator access;
    @Mock private QueryStatusBar<AuditTimelineFilterDto> queryStatusBar;
    @Mock private ComponentFactory<AuditTimelineListRenderer> rendererFactory;
    @Mock private PaginationBar paginationBar;
    @Mock private SettingsPaginationBinding settingsPaginationBinding;

    private TimelineView view;

    @BeforeEach
    void setUp() {
        view = new TimelineView(auditQueryService, access, queryStatusBar, rendererFactory, paginationBar, settingsPaginationBinding);
    }

    @Test
    void refresh_nonAdminWithNoResolvedActorId_rendersEmptyAndNeverQueries() throws Exception {
        when(auditQueryService.isAvailable()).thenReturn(true);
        when(access.canView()).thenReturn(false);
        when(access.getCurrentUserId()).thenReturn(null);

        invokeRefresh();

        verify(auditQueryService, never()).getTimelinePage(any(), any(), anyInt(), anyInt());
        verify(auditQueryService, never()).countTimeline(any());
        verify(paginationBar).setTotalCount(0);
    }

    private void invokeRefresh() throws Exception {
        Method refresh = TimelineView.class.getDeclaredMethod("refresh");
        refresh.setAccessible(true);
        refresh.invoke(view);
    }
}
