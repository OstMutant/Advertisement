package org.ost.marketplace.ui.views.main.tabs.timeline.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.platform.audit.dto.AuditTimelineFilterDto;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.mappers.TimelineFilterMapper;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.ost.marketplace.ui.query.sort.CustomSort;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

@Configuration
@RequiredArgsConstructor
public class TimelineQueryConfig {

    private final TimelineFilterMapper filterMapper;
    private final ValidationService<AuditTimelineFilterDto> validationService;
    private final I18nService i18nService;

    @Bean
    @UIScope
    public FilterProcessor<AuditTimelineFilterDto> timelineFilterProcessor() {
        return new FilterProcessor<>(filterMapper, validationService, AuditTimelineFilterDto.empty());
    }

    @Bean("timelineSortProcessor")
    @UIScope
    public SortProcessor timelineSortProcessor() {
        return new SortProcessor(new CustomSort(Sort.by(Sort.Order.desc("createdAt"))));
    }

    @Bean
    @Scope("prototype")
    public QueryStatusBar<AuditTimelineFilterDto> timelineQueryStatusBar(TimelineQueryBlock queryBlock) {
        return new QueryStatusBar<>(i18nService, queryBlock, new QueryStatusBar.Labels(
                I18nKey.QUERY_STATUS_FILTERS_NONE,
                I18nKey.QUERY_STATUS_FILTERS_PREFIX,
                I18nKey.QUERY_STATUS_SORT_NONE,
                I18nKey.QUERY_STATUS_SORT_PREFIX,
                I18nKey.SORT_DIRECTION_ASC,
                I18nKey.SORT_DIRECTION_DESC
        ));
    }
}
