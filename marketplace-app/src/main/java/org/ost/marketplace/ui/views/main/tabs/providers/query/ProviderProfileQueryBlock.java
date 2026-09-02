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
/** Query bar for the Providers catalog -- kind/category/city filter rows only, mirroring {@link ProviderProfileFilterDto}'s narrower field set (no title/date-range rows). */
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
        filterRow(i18nService, i18nService.get(PROVIDERS_FILTER_KIND), kindField,
                ProviderProfileSortMeta.UPDATED_AT, ProviderProfileFilterMeta.KINDS);

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
