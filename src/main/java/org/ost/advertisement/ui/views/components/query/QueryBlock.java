package org.ost.advertisement.ui.views.components.query;

import org.ost.advertisement.ui.views.components.query.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortFieldsProcessor;

public interface QueryBlock<T> {

	FilterFieldsProcessor<T> getFilterProcessor();

	SortFieldsProcessor getSortProcessor();

	QueryActionBlock getQueryActionBlock();

	default void addEventListener(Runnable onApply) {
		QueryActionBlock queryActionBlock = getQueryActionBlock();
		FilterFieldsProcessor<T> filterProcessor = getFilterProcessor();
		SortFieldsProcessor sortProcessor = getSortProcessor();

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
