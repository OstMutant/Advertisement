package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.Component;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;

public interface QueryBlock<T> {

	FilterFieldsProcessor<T> getFilterProcessor();

	SortFieldsProcessor getSortProcessor();

	Component getLayout();
}
