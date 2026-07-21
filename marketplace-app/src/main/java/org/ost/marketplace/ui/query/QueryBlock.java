package org.ost.marketplace.ui.query;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.marketplace.services.i18n.I18nKey;
import org.ost.marketplace.ui.core.UiComponentFactory;
import org.ost.marketplace.ui.query.elements.SortIcon;
import org.ost.marketplace.ui.query.elements.action.QueryActionBlock;
import org.ost.marketplace.ui.query.elements.rows.QueryInlineRow;
import org.ost.marketplace.ui.query.filter.FilterFieldMeta;
import org.ost.marketplace.ui.query.filter.FilterProcessor;
import org.ost.marketplace.ui.query.sort.SortFieldMeta;
import org.ost.marketplace.ui.query.sort.SortProcessor;

public abstract class QueryBlock<T> extends VerticalLayout {

    public abstract FilterProcessor<T> getFilterProcessor();

    public abstract SortProcessor getSortProcessor();

    public abstract QueryActionBlock getQueryActionBlock();

    protected <I, C extends AbstractField<?, I>> void filterRow(
            UiComponentFactory<QueryInlineRow> rowFactory,
            I18nKey labelKey, C field, FilterFieldMeta<I, T, ?> filterMeta) {
        add(rowFactory.build(QueryInlineRow.Parameters.builder()
                .labelKey(labelKey).filterField(field).build()));
        getFilterProcessor().register(filterMeta, field, getQueryActionBlock());
    }

    protected <I, C extends AbstractField<?, I>> void filterRow(
            UiComponentFactory<QueryInlineRow> rowFactory, UiComponentFactory<SortIcon> sortIconFactory,
            I18nKey labelKey, C field, SortFieldMeta sortMeta, FilterFieldMeta<I, T, ?> filterMeta) {
        SortIcon sortIcon = sortIconFactory.get();
        add(rowFactory.build(QueryInlineRow.Parameters.builder()
                .labelKey(labelKey).sortIcon(sortIcon).filterField(field).build()));
        getSortProcessor().register(sortMeta, sortIcon, getQueryActionBlock());
        getFilterProcessor().register(filterMeta, field, getQueryActionBlock());
    }

    protected <I1, I2, C1 extends AbstractField<?, I1>, C2 extends AbstractField<?, I2>> void filterRow(
            UiComponentFactory<QueryInlineRow> rowFactory, UiComponentFactory<SortIcon> sortIconFactory,
            I18nKey labelKey, C1 field1, C2 field2, SortFieldMeta sortMeta,
            FilterFieldMeta<I1, T, ?> filterMeta1, FilterFieldMeta<I2, T, ?> filterMeta2) {
        SortIcon sortIcon = sortIconFactory.get();
        add(rowFactory.build(QueryInlineRow.Parameters.builder()
                .labelKey(labelKey).sortIcon(sortIcon).filterField(field1).filterField(field2).build()));
        getSortProcessor().register(sortMeta, sortIcon, getQueryActionBlock());
        getFilterProcessor().register(filterMeta1, field1, getQueryActionBlock());
        getFilterProcessor().register(filterMeta2, field2, getQueryActionBlock());
    }

    public void addEventListener(Runnable onApply) {
        QueryActionBlock queryActionBlock = getQueryActionBlock();
        FilterProcessor<T> filterProcessor = getFilterProcessor();
        SortProcessor sortProcessor = getSortProcessor();

        Runnable combined = () -> {
            if (onApply != null) {
                onApply.run();
            }
            filterProcessor.refreshItemsFilter();
            sortProcessor.refreshItemsColor();
            queryActionBlock.updateDirtyState(filterProcessor.isFilterChanged() || sortProcessor.isSortingChanged());
        };

        queryActionBlock.addEventListener(() -> {
            if (!filterProcessor.validate()) {
                return;
            }
            filterProcessor.updateFilter();
            sortProcessor.updateSorting();
            combined.run();
        }, () -> {
            filterProcessor.clearFilter();
            sortProcessor.clearSorting();
            combined.run();
        });
    }

    public boolean toggleVisibility() {
        setVisible(!isVisible());
        return isVisible();
    }
}
