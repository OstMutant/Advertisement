package org.ost.marketplace.ui.views.main.tabs.users.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.platform.user.dto.UserFilterDto;
import org.ost.ui.query.sort.CustomSort;
import org.ost.user.entity.User;
import org.ost.marketplace.ui.mappers.UserFilterMapper;
import org.ost.marketplace.i18n.I18nService;
import org.ost.ui.query.filter.ValidationService;
import org.ost.marketplace.common.I18nKey;
import org.ost.ui.query.QueryStatusBar;
import org.ost.ui.query.filter.FilterProcessor;
import org.ost.ui.query.sort.SortProcessor;
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