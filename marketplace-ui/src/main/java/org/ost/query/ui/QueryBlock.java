package org.ost.query.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.ost.query.ui.elements.action.QueryActionBlock;
import org.ost.query.ui.filter.FilterProcessor;
import org.ost.query.ui.sort.SortProcessor;

public abstract class QueryBlock<T> extends VerticalLayout {

    public abstract FilterProcessor<T> getFilterProcessor();

    public abstract SortProcessor getSortProcessor();

    public abstract QueryActionBlock getQueryActionBlock();

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
