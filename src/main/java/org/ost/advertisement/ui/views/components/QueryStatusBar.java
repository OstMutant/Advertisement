package org.ost.advertisement.ui.views.components;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.List;
import java.util.function.UnaryOperator;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.sort.SortFieldsProcessor;

public class QueryStatusBar extends HorizontalLayout {

	private final Span filterInfo = new Span();
	private final Span sortInfo = new Span();
	private final Span separator = new Span("|");

	private final I18nService i18n;
	private final UnaryOperator<String> sortLabelProvider;

	public QueryStatusBar(I18nService i18n,
						  FilterFieldsProcessor<?> filterProcessor,
						  SortFieldsProcessor sortProcessor,
						  UnaryOperator<String> sortLabelProvider) {
		this.i18n = i18n;
		this.sortLabelProvider = sortLabelProvider;

		setWidthFull();
		setSpacing(true);
		setPadding(false);
		setAlignItems(Alignment.BASELINE);
		getStyle().set("font-size", "0.9rem");
		getStyle().set("color", "#444");
		getStyle().set("margin-bottom", "8px");
		getStyle().set("border-bottom", "1px solid #ddd");
		getStyle().set("padding-bottom", "4px");

		filterInfo.getStyle().set("font-weight", "500");
		sortInfo.getStyle().set("font-weight", "500");
		separator.getStyle().set("margin", "0 8px").set("color", "#999");

		add(filterInfo, separator, sortInfo);
		update(filterProcessor, sortProcessor);
	}

	public void update(FilterFieldsProcessor<?> filterProcessor, SortFieldsProcessor sortProcessor) {
		List<String> filters = filterProcessor.getActiveFilterDescriptions();
		filterInfo.setText(filters.isEmpty()
			? i18n.get(I18nKey.QUERY_STATUS_FILTERS_NONE)
			: i18n.get(I18nKey.QUERY_STATUS_FILTERS_PREFIX) + String.join(", ", filters));

		List<String> sorts = sortProcessor.getSortDescriptions(i18n, sortLabelProvider);
		sortInfo.setText(sorts.isEmpty()
			? i18n.get(I18nKey.QUERY_STATUS_SORT_NONE)
			: i18n.get(I18nKey.QUERY_STATUS_SORT_PREFIX) + String.join(", ", sorts));
	}
}


