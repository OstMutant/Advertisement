package org.ost.marketplace.ui.views.main.tabs.moderation;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.services.NotificationService;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;
import org.ost.orchestrator.services.FeedbackAccessService;
import org.ost.orchestrator.services.ProviderProfileDisplayEnrichmentService;
import org.ost.orchestrator.services.ProviderProfileReadService;
import org.ost.platform.feedback.dto.ModerationQueueFilterDto;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The view is built for every session, so a caller who cannot moderate must get an empty list and no query, not an error. */
@ExtendWith(MockitoExtension.class)
class ModerationViewTest {

    @Mock private FeedbackAccessService feedbackAccessService;
    @Mock private AccessEvaluator access;
    @Mock private I18nService i18n;
    @Mock private NotificationService notificationService;
    @Mock private ProviderProfileReadService providerProfileReadService;
    @Mock private ProviderProfileDisplayEnrichmentService enrichmentService;
    @Mock private PaginationBar paginationBar;
    @Mock private QueryStatusBar<ModerationQueueFilterDto> queryStatusBar;
    @Mock private SettingsPaginationBinding settingsPaginationBinding;

    private ModerationView view;

    @BeforeEach
    void setUp() {
        view = new ModerationView(feedbackAccessService, access, i18n, notificationService, providerProfileReadService,
                enrichmentService, paginationBar, queryStatusBar, settingsPaginationBinding);
        ReflectionTestUtils.setField(view, "listContainer", new Div());
        ReflectionTestUtils.setField(view, "emptyStateLabel", new Span());
    }

    @Test
    void refresh_callerWhoCannotModerate_emptiesTheListAndNeverQueriesOrReportsAnError() {
        when(access.canView()).thenReturn(false);

        view.refreshOnTabSelect();

        verify(feedbackAccessService, never()).findModerationQueue(any(), any(), any(), anyInt(), anyInt());
        verify(notificationService, never()).error(any(), any());
        verify(paginationBar).setTotalCount(0);
    }
}
