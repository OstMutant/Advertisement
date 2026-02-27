package org.ost.advertisement.ui.views.users.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.mappers.filters.UserFilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.query.filter.processor.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.processor.SortProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;

@Configuration
@RequiredArgsConstructor
public class UserQueryProcessorConfig {

    private final UserFilterMapper filterMapper;
    private final ValidationService<UserFilterDto> validationService;

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
}
