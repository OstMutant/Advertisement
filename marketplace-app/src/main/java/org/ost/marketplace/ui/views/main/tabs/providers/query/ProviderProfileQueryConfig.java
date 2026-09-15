package org.ost.marketplace.ui.views.main.tabs.providers.query;

import com.vaadin.flow.spring.annotation.UIScope;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.ui.mappers.ProviderProfileFilterMapper;
import org.ost.marketplace.ui.query.QueryStatusBar;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.filter.ValidationService;
import org.ost.marketplace.ui.query.sort.CustomSort;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.ost.platform.providerprofile.dto.ProviderProfileDto;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.data.domain.Sort;

/** Declares the {@link org.ost.marketplace.ui.query.filter.FilterProcessor}/{@link org.ost.marketplace.ui.query.sort.SortProcessor}/{@link org.ost.marketplace.ui.query.QueryStatusBar} beans backing {@link ProviderProfileQueryBlock}. */
@Configuration
@RequiredArgsConstructor
public class ProviderProfileQueryConfig {

    private final ProviderProfileFilterMapper filterMapper;
    private final ValidationService<ProviderProfileFilterDto> validationService;
    private final I18nService i18nService;

    @Bean
    @UIScope
    public FilterProcessor<ProviderProfileFilterDto> providerProfileFilterProcessor() {
        return new FilterProcessor<>(filterMapper, validationService, ProviderProfileFilterDto.empty());
    }

    @Bean("providerProfileSortProcessor")
    @UIScope
    public SortProcessor providerProfileSortProcessor() {
        return new SortProcessor(new CustomSort(Sort.by(
                Sort.Order.desc(ProviderProfileDto.Fields.updatedAt),
                Sort.Order.desc(ProviderProfileDto.Fields.createdAt)
        )));
    }

    @Bean
    @Scope("prototype")
    public QueryStatusBar<ProviderProfileFilterDto> providerProfileQueryStatusBar(ProviderProfileQueryBlock queryBlock) {
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
