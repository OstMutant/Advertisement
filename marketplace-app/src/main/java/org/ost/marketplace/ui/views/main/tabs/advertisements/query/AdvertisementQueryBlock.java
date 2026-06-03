package org.ost.marketplace.ui.views.main.tabs.advertisements.query;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.marketplace.dto.filter.AdvertisementFilterDto;
import org.ost.query.ui.QueryBlock;
import org.ost.platform.core.ComponentFactory;
import org.ost.query.ui.elements.SortIcon;
import org.ost.query.ui.elements.action.QueryActionBlock;
import org.ost.query.ui.elements.fields.QueryDateTimeField;
import org.ost.query.ui.elements.fields.QueryTextField;
import org.ost.query.ui.elements.rows.QueryInlineRow;
import org.ost.query.ui.filter.FilterProcessor;
import org.ost.query.ui.sort.SortProcessor;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.ost.marketplace.common.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementQueryBlock extends QueryBlock<AdvertisementFilterDto> {

    @Getter
    private final transient FilterProcessor<AdvertisementFilterDto> filterProcessor;
    @Getter
    @Qualifier("advertisementSortProcessor")
    private final transient SortProcessor                           sortProcessor;

    private final transient ComponentFactory<QueryTextField>      textFieldFactory;
    private final transient ComponentFactory<QueryDateTimeField>  dateTimeFieldFactory;
    private final transient ComponentFactory<QueryInlineRow>      inlineRowFactory;
    private final transient ComponentFactory<SortIcon>            sortIconFactory;

    @Getter
    private final QueryActionBlock queryActionBlock;

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
                        .labelTranslationKey(ADVERTISEMENT_SORT_TITLE).sortIcon(titleSort).filterField(titleField).build());

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
                        .labelTranslationKey(ADVERTISEMENT_SORT_CREATED_AT).sortIcon(createdSort)
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
                        .labelTranslationKey(ADVERTISEMENT_SORT_UPDATED_AT).sortIcon(updatedSort)
                        .filterField(updatedStart).filterField(updatedEnd).build());

        add(titleRow, createdRow, updatedRow, queryActionBlock);

        sortProcessor.register(AdvertisementSortMeta.TITLE,      titleSort,   queryActionBlock);
        sortProcessor.register(AdvertisementSortMeta.CREATED_AT, createdSort, queryActionBlock);
        sortProcessor.register(AdvertisementSortMeta.UPDATED_AT, updatedSort, queryActionBlock);

        filterProcessor.register(AdvertisementFilterMeta.TITLE,            titleField,   queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_START, createdStart, queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.CREATED_AT_END,   createdEnd,   queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_START, updatedStart, queryActionBlock);
        filterProcessor.register(AdvertisementFilterMeta.UPDATED_AT_END,   updatedEnd,   queryActionBlock);
    }

}
