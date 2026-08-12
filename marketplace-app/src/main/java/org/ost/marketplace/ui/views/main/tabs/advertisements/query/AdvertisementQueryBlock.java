package org.ost.marketplace.ui.views.main.tabs.advertisements.query;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.platform.advertisement.dto.AdvertisementFilterDto;
import org.ost.marketplace.services.i18n.I18nService;
import org.ost.marketplace.services.i18n.LocaleProvider;
import org.ost.marketplace.ui.query.QueryBlock;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryTextField;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.ost.orchestrator.services.TaxonCatalogService;
import org.ost.platform.advertisement.model.AdKind;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
@SuppressWarnings("java:S2065") // Vaadin Component is Serializable; transient excludes non-serializable Spring proxies
public class AdvertisementQueryBlock extends QueryBlock<AdvertisementFilterDto> {

    @Getter
    private final transient FilterProcessor<AdvertisementFilterDto> filterProcessor;
    @Getter
    @Qualifier("advertisementSortProcessor")
    private final transient SortProcessor                           sortProcessor;

    @Getter
    private QueryActionBlock queryActionBlock;

    private final transient I18nService                              i18nService;
    private final transient LocaleProvider                           localeProvider;
    private final transient TaxonCatalogService                      taxonCatalogService;

    @PostConstruct
    private void initLayout() {
        queryActionBlock = new QueryActionBlock(i18nService);
        addClassName("advertisement-query-block");
        setVisible(false);

        // Title row
        QueryTextField titleField = new QueryTextField(i18nService.get(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER));
        filterRow(i18nService, i18nService.get(ADVERTISEMENT_SORT_TITLE), titleField,
                AdvertisementSortMeta.TITLE, AdvertisementFilterMeta.TITLE);

        // Created date row
        QueryDateTimeField createdStart = new QueryDateTimeField(
                i18nService.get(ADVERTISEMENT_FILTER_DATE_CREATED_START),
                i18nService.get(ADVERTISEMENT_FILTER_TIME_CREATED_START), false);
        QueryDateTimeField createdEnd = new QueryDateTimeField(
                i18nService.get(ADVERTISEMENT_FILTER_DATE_CREATED_END),
                i18nService.get(ADVERTISEMENT_FILTER_TIME_CREATED_END), true);
        filterRow(i18nService, i18nService.get(ADVERTISEMENT_SORT_CREATED_AT), createdStart, createdEnd,
                AdvertisementSortMeta.CREATED_AT,
                AdvertisementFilterMeta.CREATED_AT_START, AdvertisementFilterMeta.CREATED_AT_END);

        // Updated date row
        QueryDateTimeField updatedStart = new QueryDateTimeField(
                i18nService.get(ADVERTISEMENT_FILTER_DATE_UPDATED_START),
                i18nService.get(ADVERTISEMENT_FILTER_TIME_UPDATED_START), false);
        QueryDateTimeField updatedEnd = new QueryDateTimeField(
                i18nService.get(ADVERTISEMENT_FILTER_DATE_UPDATED_END),
                i18nService.get(ADVERTISEMENT_FILTER_TIME_UPDATED_END), true);
        filterRow(i18nService, i18nService.get(ADVERTISEMENT_SORT_UPDATED_AT), updatedStart, updatedEnd,
                AdvertisementSortMeta.UPDATED_AT,
                AdvertisementFilterMeta.UPDATED_AT_START, AdvertisementFilterMeta.UPDATED_AT_END);

        // Categories row
        MultiSelectComboBox<TaxonDto> categoriesField = new MultiSelectComboBox<>();
        categoriesField.setPlaceholder(i18nService.get(ADVERTISEMENT_FILTER_CATEGORIES));
        categoriesField.setItemLabelGenerator(TaxonDto::getName);
        categoriesField.getElement().setAttribute("data-testid", "advertisement-filter-categories");
        categoriesField.setItems(taxonCatalogService.getAllByType(TaxonType.CATEGORY, localeProvider.getCurrentLocale()));
        filterRow(i18nService.get(ADVERTISEMENT_FILTER_CATEGORIES), categoriesField, AdvertisementFilterMeta.CATEGORY_IDS);

        // City row
        ComboBox<TaxonDto> cityField = new ComboBox<>();
        cityField.setPlaceholder(i18nService.get(ADVERTISEMENT_FILTER_CITY));
        cityField.setItemLabelGenerator(TaxonDto::getName);
        cityField.setClearButtonVisible(true);
        cityField.setItems(taxonCatalogService.getAllByType(TaxonType.CITY, localeProvider.getCurrentLocale()));
        filterRow(i18nService.get(ADVERTISEMENT_FILTER_CITY), cityField, AdvertisementFilterMeta.CITY_TAXON_ID);

        // Listing type row
        MultiSelectComboBox<AdKind> adKindField = new MultiSelectComboBox<>();
        adKindField.setPlaceholder(i18nService.get(ADVERTISEMENT_FILTER_AD_KIND));
        adKindField.setItems(AdKind.values());
        adKindField.setItemLabelGenerator(t -> i18nService.get(forAdKind(t)));
        adKindField.getElement().setAttribute("data-testid", "advertisement-filter-ad-kind");
        filterRow(i18nService.get(ADVERTISEMENT_FILTER_AD_KIND), adKindField, AdvertisementFilterMeta.AD_KINDS);

        add(queryActionBlock);
    }

}
