package org.ost.advertisement.ui.views.tabs.advertisements.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.AdvertisementInfoDto;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.dto.sort.CustomSort;
import org.ost.advertisement.mappers.filters.AdvertisementFilterMapper;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.ValidationService;
import org.ost.advertisement.ui.views.components.query.QueryStatusBar;
import org.ost.advertisement.ui.views.components.query.processor.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.processor.SortProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

@Configuration
@RequiredArgsConstructor
public class AdvertisementQueryConfig {

    private final AdvertisementFilterMapper filterMapper;
    private final ValidationService<AdvertisementFilterDto> validationService;
    private final I18nService i18nService;

    @Bean
    @UIScope
    public FilterProcessor<AdvertisementFilterDto> advertisementFilterProcessor() {
        return new FilterProcessor<>(filterMapper, validationService, AdvertisementFilterDto.empty());
    }

    @Bean("advertisementSortProcessor")
    @UIScope
    public SortProcessor advertisementSortProcessor() {
        return new SortProcessor(new CustomSort(Sort.by(
                Sort.Order.desc(AdvertisementInfoDto.Fields.updatedAt),
                Sort.Order.desc(AdvertisementInfoDto.Fields.createdAt)
        )));
    }

    @Bean
    @Scope("prototype")
    public QueryStatusBar<AdvertisementFilterDto> advertisementQueryStatusBar(AdvertisementQueryBlock queryBlock) {
        return new QueryStatusBar<>(i18nService, queryBlock);
    }
}