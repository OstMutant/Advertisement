package org.ost.marketplace.ui.query;

import com.vaadin.flow.component.AbstractField;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.marketplace.services.i18n.I18nService;
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
            String label, C field, FilterFieldMeta<I, T, ?> filterMeta) {
        add(new QueryInlineRow(label, field));
        getFilterProcessor().register(filterMeta, field, getQueryActionBlock());
    }

    protected <I, C extends AbstractField<?, I>> void filterRow(
            I18nService i18nService, String label, C field, SortFieldMeta sortMeta, FilterFieldMeta<I, T, ?> filterMeta) {
        SortIcon sortIcon = new SortIcon(i18nService);
        add(new QueryInlineRow(label, sortIcon, field));
        getSortProcessor().register(sortMeta, sortIcon, getQueryActionBlock());
        getFilterProcessor().register(filterMeta, field, getQueryActionBlock());
    }

    protected <I1, I2, C1 extends AbstractField<?, I1>, C2 extends AbstractField<?, I2>> void filterRow(
            I18nService i18nService, String label, C1 field1, C2 field2, SortFieldMeta sortMeta,
            FilterFieldMeta<I1, T, ?> filterMeta1, FilterFieldMeta<I2, T, ?> filterMeta2) {
        SortIcon sortIcon = new SortIcon(i18nService);
        add(new QueryInlineRow(label, sortIcon, field1, field2));
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
