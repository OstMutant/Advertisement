package org.ost.marketplace.ui.views.main.tabs.users.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.filter.UserFilterDto;
import org.ost.marketplace.ui.views.components.query.processor.CustomSort;
import org.ost.marketplace.entities.User;
import org.ost.marketplace.mappers.filters.UserFilterMapper;
import org.ost.platform.core.i18n.I18nService;
import org.ost.marketplace.services.ValidationService;
import org.ost.marketplace.ui.views.components.query.QueryStatusBar;
import org.ost.marketplace.ui.views.components.query.processor.FilterProcessor;
import org.ost.marketplace.ui.views.components.query.processor.SortProcessor;
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
        return new QueryStatusBar<>(i18nService, queryBlock);
    }
}