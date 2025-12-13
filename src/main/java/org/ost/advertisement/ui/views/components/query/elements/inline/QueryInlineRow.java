package org.ost.advertisement.ui.views.components.query.elements.inline;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

public class QueryInlineRow extends HorizontalLayout {

	protected QueryInlineRow() {
	}

	protected void initLayout(I18nService i18n, I18nKey labelI18nKey, SortIcon sortIcon, Component... filterFields) {
		HorizontalLayout labelAndSort = new HorizontalLayout(new Span(i18n.get(labelI18nKey)), sortIcon);
		labelAndSort.setAlignItems(Alignment.CENTER);
		labelAndSort.setSpacing(true);

		HorizontalLayout filters = new HorizontalLayout(filterFields);
		filters.setAlignItems(Alignment.END);
		filters.setSpacing(true);

		add(labelAndSort, filters);
		setWidthFull();
		setAlignItems(Alignment.CENTER);
		setSpacing(true);
		getStyle().set("gap", "12px");
	}
}
