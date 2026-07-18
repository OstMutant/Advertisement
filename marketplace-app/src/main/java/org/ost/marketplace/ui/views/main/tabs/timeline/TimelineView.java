package org.ost.marketplace.ui.views.main.tabs.timeline;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ost.platform.audit.api.AuditableSnapshot;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.platform.audit.dto.AuditTimelineItemDto;
import org.ost.platform.audit.spi.AuditPort;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.user.dto.UserSettingsDto;
import org.ost.marketplace.services.security.AccessEvaluator;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.views.components.PaginationBar;
import org.ost.marketplace.ui.views.components.audit.AuditTimelineListRenderer;
import org.ost.marketplace.ui.views.main.tabs.timeline.query.TimelineQueryBlock;
import org.ost.marketplace.ui.views.services.pagination.SettingsPaginationBinding;

import java.util.List;
import java.util.Set;

@Slf4j
@SpringComponent
@UIScope
@RequiredArgsConstructor
public class TimelineView extends VerticalLayout {

    private final transient ComponentFactory<AuditPort>                    auditPortFactory;
    private final transient AccessEvaluator                                access;
    private final QueryStatusBar<AuditTimelineFilterDto>                   queryStatusBar;
    private final transient UiComponentFactory<AuditTimelineListRenderer>  rendererFactory;
    private final PaginationBar                                            paginationBar;
    private final transient SettingsPaginationBinding                      settingsPaginationBinding;

    private final Div feed = new Div();

    @PostConstruct
    protected void init() {
        addClassName("timeline-layout");
        setWidthFull();

        feed.addClassName("activity-feed");
        feed.setWidthFull();

        paginationBar.addClassName("timeline-pagination");

        VerticalLayout contentWrapper = new VerticalLayout(
                queryStatusBar, queryStatusBar.getQueryBlock(), feed, paginationBar);
        contentWrapper.setPadding(false);
        contentWrapper.setSpacing(false);
        contentWrapper.setWidthFull();

        add(contentWrapper);

        paginationBar.setPageChangeListener(_ -> refresh());
        queryStatusBar.getQueryBlock().addEventListener(() -> {
            paginationBar.setTotalCount(0);
            refresh();
        });

        settingsPaginationBinding.register(paginationBar, UserSettingsDto::getTimelinePageSize, this::refresh);
        refresh();
    }

    @PreDestroy
    public void destroy() {
        settingsPaginationBinding.unregister();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) refresh();
    }

    private void refresh() {
        AuditPort auditPort = auditPortFactory.getIfAvailable();
        if (auditPort == null) {
            feed.removeAll();
            paginationBar.setTotalCount(0);
            return;
        }

        int page = paginationBar.getCurrentPage();
        int size = paginationBar.getPageSize();

        TimelineQueryBlock queryBlock = (TimelineQueryBlock) queryStatusBar.getQueryBlock();
        AuditTimelineFilterDto baseFilter = queryBlock.getFilterProcessor().getOriginalFilter();
        Long currentUserId = access.getCurrentUserId();
        AuditTimelineFilterDto filter = access.canView()
                ? baseFilter
                : baseFilter.toBuilder().actorIds(currentUserId != null ? Set.of(currentUserId) : Set.of()).build();

        try {
            List<AuditTimelineItemDto<AuditableSnapshot>> items = auditPort.getTimelinePage(
                    filter, queryBlock.getSortProcessor().getOriginalSort().getSort(), page, size);
            paginationBar.setTotalCount(auditPort.countTimeline(filter));
            renderFeed(items);
        } catch (Exception ex) {
            log.error("Failed to load timeline page", ex);
            feed.removeAll();
            paginationBar.setTotalCount(0);
        } finally {
            queryStatusBar.update();
        }
    }

    private void renderFeed(List<AuditTimelineItemDto<AuditableSnapshot>> items) {
        feed.removeAll();
        rendererFactory.get().buildRows(items).forEach(feed::add);
    }
}
