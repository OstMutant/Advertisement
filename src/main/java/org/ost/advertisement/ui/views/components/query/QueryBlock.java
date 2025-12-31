package org.ost.advertisement.ui.views.components.query;

import org.ost.advertisement.ui.views.components.query.action.QueryActionBlock;
import org.ost.advertisement.ui.views.components.query.filter.FilterProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortProcessor;

public interface QueryBlock<T> {

	FilterProcessor<T> getFilterProcessor();

	SortProcessor getSortProcessor();

	QueryActionBlock getQueryActionBlock();

	default void addEventListener(Runnable onApply) {
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
}
