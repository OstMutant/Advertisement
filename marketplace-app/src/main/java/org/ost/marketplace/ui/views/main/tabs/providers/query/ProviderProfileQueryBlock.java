package org.ost.marketplace.ui.views.main.tabs.providers.query;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.providerprofile.dto.ProviderProfileFilterDto;
import org.ost.platform.providerprofile.model.ProviderKind;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
/** Query bar for the Providers catalog -- kind/created/updated/category/city filter rows, mirroring {@link ProviderProfileFilterDto}'s field set. */
@SuppressWarnings("java:S2065") // Vaadin Component is Serializable; transient excludes non-serializable Spring proxies
public class ProviderProfileQueryBlock extends QueryBlock<ProviderProfileFilterDto> {

    private static final String DATA_TESTID = "data-testid";

    @Getter
    private final transient FilterProcessor<ProviderProfileFilterDto> filterProcessor;
    @Getter
    @Qualifier("providerProfileSortProcessor")
    private final transient SortProcessor                             sortProcessor;

    @Getter
    private QueryActionBlock queryActionBlock;

    private final transient I18nService         i18nService;
    private final transient LocaleProvider       localeProvider;
    private final transient TaxonCatalogService  taxonCatalogService;

    @PostConstruct
    private void initLayout() {
        queryActionBlock = new QueryActionBlock(i18nService);
        addClassName("provider-profile-query-block");
        setVisible(false);

        // Kind row
        MultiSelectComboBox<ProviderKind> kindField = new MultiSelectComboBox<>();
        kindField.setPlaceholder(i18nService.get(PROVIDERS_FILTER_KIND));
        kindField.setItems(ProviderKind.values());
        kindField.setItemLabelGenerator(k -> i18nService.get(forProviderKind(k)));
        kindField.getElement().setAttribute(DATA_TESTID, "provider-profile-filter-kind");
        filterRow(i18nService.get(PROVIDERS_FILTER_KIND), kindField, ProviderProfileFilterMeta.KINDS);

        // Created date row
        QueryDateTimeField createdStart = new QueryDateTimeField(
                i18nService.get(PROVIDERS_FILTER_DATE_CREATED_START),
                i18nService.get(PROVIDERS_FILTER_TIME_CREATED_START), false);
        QueryDateTimeField createdEnd = new QueryDateTimeField(
                i18nService.get(PROVIDERS_FILTER_DATE_CREATED_END),
                i18nService.get(PROVIDERS_FILTER_TIME_CREATED_END), true);
        filterRow(i18nService, i18nService.get(PROVIDERS_SORT_CREATED_AT), createdStart, createdEnd,
                ProviderProfileSortMeta.CREATED_AT,
                ProviderProfileFilterMeta.CREATED_AT_START, ProviderProfileFilterMeta.CREATED_AT_END);

        // Updated date row
        QueryDateTimeField updatedStart = new QueryDateTimeField(
                i18nService.get(PROVIDERS_FILTER_DATE_UPDATED_START),
                i18nService.get(PROVIDERS_FILTER_TIME_UPDATED_START), false);
        QueryDateTimeField updatedEnd = new QueryDateTimeField(
                i18nService.get(PROVIDERS_FILTER_DATE_UPDATED_END),
                i18nService.get(PROVIDERS_FILTER_TIME_UPDATED_END), true);
        filterRow(i18nService, i18nService.get(PROVIDERS_SORT_UPDATED_AT), updatedStart, updatedEnd,
                ProviderProfileSortMeta.UPDATED_AT,
                ProviderProfileFilterMeta.UPDATED_AT_START, ProviderProfileFilterMeta.UPDATED_AT_END);

        // Categories row
        MultiSelectComboBox<TaxonDto> categoriesField = new MultiSelectComboBox<>();
        categoriesField.setPlaceholder(i18nService.get(PROVIDERS_FILTER_CATEGORIES));
        categoriesField.setItemLabelGenerator(TaxonDto::getName);
        categoriesField.getElement().setAttribute(DATA_TESTID, "provider-profile-filter-categories");
        categoriesField.setItems(taxonCatalogService.getAllByType(TaxonType.CATEGORY, localeProvider.getCurrentLocale()));
        filterRow(i18nService.get(PROVIDERS_FILTER_CATEGORIES), categoriesField, ProviderProfileFilterMeta.CATEGORY_IDS);

        // City row
        ComboBox<TaxonDto> cityField = new ComboBox<>();
        cityField.setPlaceholder(i18nService.get(PROVIDERS_FILTER_CITY));
        cityField.setItemLabelGenerator(TaxonDto::getName);
        cityField.setClearButtonVisible(true);
        cityField.getElement().setAttribute(DATA_TESTID, "provider-profile-filter-city");
        cityField.setItems(taxonCatalogService.getAllByType(TaxonType.CITY, localeProvider.getCurrentLocale()));
        filterRow(i18nService.get(PROVIDERS_FILTER_CITY), cityField, ProviderProfileFilterMeta.CITY_TAXON_ID);

        add(queryActionBlock);
    }
}
