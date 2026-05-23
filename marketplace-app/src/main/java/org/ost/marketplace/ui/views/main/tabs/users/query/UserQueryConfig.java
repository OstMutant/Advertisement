package org.ost.marketplace.ui.views.main.tabs.users.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.query.ui.sort.CustomSort;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.mappers.filters.UserFilterMapper;
import org.ost.platform.core.i18n.I18nService;
import org.ost.query.ui.filter.ValidationService;
import org.ost.marketplace.common.I18nKey;
import org.ost.query.ui.QueryStatusBar;
import org.ost.query.ui.filter.FilterProcessor;
import org.ost.query.ui.sort.SortProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

@Configuration
@RequiredArgsConstructor
public class UserQueryConfig {

    private final UserFilterMapper filterMapper;
    private final ValidationService<UserFilterDto> validationService;
    private final I18nService i18nService;

    @Bean
    @UIScope
    public FilterProcessor<UserFilterDto> userFilterProcessor() {
        return new FilterProcessor<>(filterMapper, validationService, UserFilterDto.empty());
    }

    @Bean("userSortProcessor")
    @UIScope
    public SortProcessor userSortProcessor() {
        return new SortProcessor(new CustomSort(Sort.by(
                Sort.Order.desc(User.Fields.updatedAt),
                Sort.Order.desc(User.Fields.createdAt)
        )));
    }

    @Bean
    @Scope("prototype")
    public QueryStatusBar<UserFilterDto> userQueryStatusBar(UserQueryBlock queryBlock) {
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