package org.ost.advertisement.ui.views.components.query;

import com.vaadin.flow.component.Component;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;

public interface QueryBlock<T> {

	Component getLayout();

	FilterFieldsProcessor<T> getFilterProcessor();

	SortFieldsProcessor getSortProcessor();

	QueryActionBlock getQueryActionBlock();

	default void eventProcessor(Runnable onApply) {

		QueryActionBlock queryActionBlock = getQueryActionBlock();
		FilterFieldsProcessor<T> filterProcessor = getFilterProcessor();
		SortFieldsProcessor sortProcessor = getSortProcessor();

		Runnable combined = () -> {
			onApply.run();
			filterProcessor.refreshFilter();
			sortProcessor.refreshSorting();
			queryActionBlock.setChanged(filterProcessor.isFilterChanged() || sortProcessor.isSortingChanged());
		};

		queryActionBlock.eventProcessor(() -> {
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
