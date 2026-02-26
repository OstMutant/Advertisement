package org.ost.advertisement.ui.views.advertisements.query.elements;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ost.advertisement.dto.filter.AdvertisementFilterDto;
import org.ost.advertisement.ui.views.advertisements.query.filter.meta.AdvertisementFilterMeta;
import org.ost.advertisement.ui.views.advertisements.query.filter.processor.AdvertisementFilterProcessor;
import org.ost.advertisement.ui.views.advertisements.query.sort.meta.AdvertisementSortMeta;
import org.ost.advertisement.ui.views.advertisements.query.sort.processor.AdvertisementSortProcessor;
import org.ost.advertisement.ui.views.components.query.elements.QueryBlock;
import org.ost.advertisement.ui.views.components.query.elements.QueryBlockLayout;
import org.ost.advertisement.ui.views.components.query.elements.SortIcon;
import org.ost.advertisement.ui.views.components.query.elements.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryDateTimeField;
import org.ost.advertisement.ui.views.components.query.elements.fields.QueryTextField;
import org.ost.advertisement.ui.views.components.query.elements.rows.QueryInlineRow;

import static org.ost.advertisement.constants.I18nKey.*;

@SpringComponent
@UIScope
@RequiredArgsConstructor
public class AdvertisementQueryBlock extends VerticalLayout implements QueryBlock<AdvertisementFilterDto>, QueryBlockLayout {

    @Getter
    private final transient AdvertisementFilterProcessor filterProcessor;
    @Getter
    private final transient AdvertisementSortProcessor   sortProcessor;

    private final QueryTextField.Builder     textFieldBuilder;
    private final QueryDateTimeField.Builder dateTimeFieldBuilder;
    private final QueryInlineRow.Builder     rowBuilder;
    private final SortIcon.Builder           sortIconBuilder;

    @Getter
    private final QueryActionBlock queryActionBlock;

    @PostConstruct
    private void initLayout() {
        addClassName("advertisement-query-block");
        setVisible(false);

        // Title row
        QueryTextField titleField = textFieldBuilder.build(QueryTextField.Parameters.builder()
                .placeholderKey(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER).build());
        SortIcon titleSort = sortIconBuilder.build();
        QueryInlineRow titleRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(ADVERTISEMENT_SORT_TITLE).sortIcon(titleSort).filterField(titleField).build());

        // Created date row
        QueryDateTimeField createdStart = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_CREATED_START)
                .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_CREATED_START).build());
        QueryDateTimeField createdEnd = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_CREATED_END)
                .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_CREATED_END).isEnd(true).build());
        SortIcon createdSort = sortIconBuilder.build();
        QueryInlineRow createdRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(ADVERTISEMENT_SORT_CREATED_AT).sortIcon(createdSort)
                .filterField(createdStart).filterField(createdEnd).build());

        // Updated date row
        QueryDateTimeField updatedStart = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_UPDATED_START)
                .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_UPDATED_START).build());
        QueryDateTimeField updatedEnd = dateTimeFieldBuilder.build(QueryDateTimeField.Parameters.builder()
                .datePlaceholderKey(ADVERTISEMENT_FILTER_DATE_UPDATED_END)
                .timePlaceholderKey(ADVERTISEMENT_FILTER_TIME_UPDATED_END).isEnd(true).build());
        SortIcon updatedSort = sortIconBuilder.build();
        QueryInlineRow updatedRow = rowBuilder.build(QueryInlineRow.Parameters.builder()
                .labelI18nKey(ADVERTISEMENT_SORT_UPDATED_AT).sortIcon(updatedSort)
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

    @Override
    public boolean toggleVisibility() {
        setVisible(!isVisible());
        return isVisible();
    }
}

