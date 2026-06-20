package org.ost.marketplace.ui.views.main.tabs.advertisements.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementInfoDto;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.marketplace.ui.query.sort.CustomSort;
import org.ost.marketplace.ui.mappers.AdvertisementFilterMapper;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
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