package org.ost.advertisement.ui.views.advertisements.processor;

import com.vaadin.flow.spring.annotation.SpringComponent;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.mappers.filters.AdvertisementFilterMapper;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.springframework.context.annotation.Scope;

@SpringComponent
@Scope("prototype")
public class AdvertisementFilterProcessor extends FilterProcessor<AdvertisementFilterDto> {

    public AdvertisementFilterProcessor(
            AdvertisementFilterMapper filterMapper,
            ValidationService<AdvertisementFilterDto> validationService) {
        super(filterMapper, validationService, AdvertisementFilterDto.empty());
    }
}
