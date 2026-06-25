package org.ost.marketplace.ui.views.main.tabs.advertisements.query;

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
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.fields.QueryDateTimeField;
import org.ost.marketplace.ui.query.elements.fields.QueryTextField;
import org.ost.marketplace.ui.query.elements.rows.QueryInlineRow;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortProcessor;
import org.ost.platform.core.ComponentFactory;
import org.ost.platform.taxon.dto.TaxonDto;
import org.ost.platform.taxon.model.TaxonType;
import org.ost.platform.taxon.spi.TaxonPort;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.services.i18n.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementQueryBlock extends QueryBlock<AdvertisementFilterDto> {

    @Getter
    private final transient FilterProcessor<AdvertisementFilterDto> filterProcessor;
    @Getter
    @Qualifier("advertisementSortProcessor")
    private final transient SortProcessor                           sortProcessor;

    private final transient UiComponentFactory<QueryTextField>      textFieldFactory;
    private final transient UiComponentFactory<QueryDateTimeField>  dateTimeFieldFactory;
    private final transient UiComponentFactory<QueryInlineRow>      inlineRowFactory;
    private final transient UiComponentFactory<SortIcon>            sortIconFactory;

    @Getter
    private final QueryActionBlock queryActionBlock;

    private final transient I18nService                              i18nService;
    private final transient LocaleProvider                           localeProvider;
    private final transient ComponentFactory<TaxonPort>              taxonPortFactory;

    @PostConstruct
    private void initLayout() {
        addClassName("advertisement-query-block");
        setVisible(false);

        // Title row
        QueryTextField titleField = textFieldFactory.build(
                QueryTextField.Parameters.builder()
                        .placeholderKey(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER).build());
        SortIcon titleSort = sortIconFactory.get();
        QueryInlineRow titleRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(ADVERTISEMENT_SORT_TITLE).sortIcon(titleSort).filterField(titleField).build());

        // Created date row
        QueryDateTimeField createdStart = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_CREATED_START)
                        .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_CREATED_START).build());
        QueryDateTimeField createdEnd = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_CREATED_END)
                        .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_CREATED_END).isEnd(true).build());
        SortIcon createdSort = sortIconFactory.get();
        QueryInlineRow createdRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(ADVERTISEMENT_SORT_CREATED_AT).sortIcon(createdSort)
                        .filterField(createdStart).filterField(createdEnd).build());

        // Updated date row
        QueryDateTimeField updatedStart = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_UPDATED_START)
                        .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_UPDATED_START).build());
        QueryDateTimeField updatedEnd = dateTimeFieldFactory.build(
                QueryDateTimeField.Parameters.builder()
                        .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_UPDATED_END)
                        .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_UPDATED_END).isEnd(true).build());
        SortIcon updatedSort = sortIconFactory.get();
        QueryInlineRow updatedRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(ADVERTISEMENT_SORT_UPDATED_AT).sortIcon(updatedSort)
                        .filterField(updatedStart).filterField(updatedEnd).build());

        // Categories row
        MultiSelectComboBox<TaxonDto> categoriesField = new MultiSelectComboBox<>();
        categoriesField.setPlaceholder(i18nService.get(ADVERTISEMENT_FILTER_CATEGORIES));
        categoriesField.setItemLabelGenerator(TaxonDto::getName);
        taxonPortFactory.ifAvailable(port ->
                categoriesField.setItems(port.getAllByType(TaxonType.CATEGORY, localeProvider.getCurrentLocale())));
        QueryInlineRow categoriesRow = inlineRowFactory.build(
                QueryInlineRow.Parameters.builder()
                        .labelKey(ADVERTISEMENT_FILTER_CATEGORIES).filterField(categoriesField).build());

        add(titleRow, createdRow, updatedRow, categoriesRow, queryActionBlock);

        sortProcessor.register(AdvertisementSortMeta.TITLE,      titleSort,   queryActionBlock);
        sortProcessor.register(AdvertisementSortMeta.CREATED_AT, createdSort, queryActionBlock);
        sortProcessor.register(AdvertisementSortMeta.UPDATED_AT, updatedSort, queryActionBlock);

        filterProcessor.register(AdvertisementFilterMeta.TITLE,            titleField,    queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_START, createdStart,  queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_END,   createdEnd,    queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_START, updatedStart,  queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_END,   updatedEnd,    queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CATEGORY_IDS,     categoriesField, queryActionBlock);
    }

}
