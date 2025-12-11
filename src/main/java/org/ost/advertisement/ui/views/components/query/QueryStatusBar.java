package org.ost.advertisement.ui.views.components.query;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.util.List;
import java.util.function.UnaryOperator;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.filters.FilterFieldsProcessor;
import org.ost.advertisement.ui.views.components.query.sort.SortFieldsProcessor;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class QueryStatusBar<T> extends HorizontalLayout {

	private final Span filterInfo = new Span();
	private final Span sortInfo = new Span();
	private final Span separator = new Span("|");
	private final Span toggleIcon = new Span();

	private final transient I18nService i18n;
	private final transient QueryBlockLayout queryBlockLayout;
	private final transient UnaryOperator<String> sortLabelProvider;

	public QueryStatusBar(I18nService i18n,
						  QueryBlock<T> queryBlock,
						  QueryBlockLayout queryBlockLayout,
						  UnaryOperator<String> sortLabelProvider) {
		this.i18n = i18n;
		this.queryBlockLayout = queryBlockLayout;
		this.sortLabelProvider = sortLabelProvider;

		applyStyles();
		initToggleIcon();

		add(toggleIcon, filterInfo, separator, sortInfo);
		update(queryBlock.getFilterProcessor(), queryBlock.getSortProcessor());
	}

	public void update(FilterFieldsProcessor<?> filterProcessor, SortFieldsProcessor sortProcessor) {
		List<String> filters = filterProcessor.getActiveFilterDescriptions();

		filterInfo.setText(filters.isEmpty()
			? i18n.get(I18nKey.QUERY_STATUS_FILTERS_NONE)
			: i18n.get(I18nKey.QUERY_STATUS_FILTERS_PREFIX) + " " + String.join(", ", filters));

		List<String> sorts = sortProcessor.getSortDescriptions(i18n, sortLabelProvider);
		sortInfo.setText(sorts.isEmpty()
			? i18n.get(I18nKey.QUERY_STATUS_SORT_NONE)
			: i18n.get(I18nKey.QUERY_STATUS_SORT_PREFIX) + " " + String.join(", ", sorts));

		separator.setVisible(!filters.isEmpty() || !sorts.isEmpty());
	}

	public void toggleVisibility() {
		var layout = queryBlockLayout.getLayout();
		boolean nowVisible = !layout.isVisible();
		layout.setVisible(nowVisible);
		setToggleIconState(nowVisible);
	}

	private void setToggleIconState(boolean isOpen) {
		toggleIcon.setText(isOpen ? "▾" : "▸");
	}

	private void initToggleIcon() {
		toggleIcon.setText("▸");
		toggleIcon.getStyle().set("margin-right", "8px").set("font-weight", "bold");
	}

	private void applyStyles() {
		setWidthFull();
		setSpacing(true);
		setPadding(false);
		setAlignItems(Alignment.BASELINE);

		getStyle()
			.set("font-size", "0.85rem")
			.set("color", "#444")
			.set("background-color", "#f9f9f9")
			.set("border-radius", "6px")
			.set("padding", "8px 12px")
			.set("margin-bottom", "12px")
			.set("box-shadow", "0 1px 3px rgba(0,0,0,0.05)")
			.set("flex-wrap", "wrap");

		filterInfo.getStyle().set("font-weight", "500");
		sortInfo.getStyle().set("font-weight", "500");
		separator.getStyle().set("margin", "0 8px").set("color", "#999");
	}
}

