package org.ost.advertisement.ui.views.users.processor;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.dto.filter.UserFilterDto;
import org.ost.advertisement.mappers.filters.UserFilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class UserFilterProcessor extends FilterProcessor<UserFilterDto> {

    public UserFilterProcessor(
            UserFilterMapper filterMapper,
            ValidationService<UserFilterDto> validationService) {
        super(filterMapper, validationService, UserFilterDto.empty());
    }
}
