package org.ost.advertisement.ui.views.components.content;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import lombok.AllArgsConstructor;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@SpringComponent
@UIScope
@AllArgsConstructor
public class QueryBlockFactory {

	private final I18nService i18n;

	public VerticalLayout buildQueryBlockLayout(Component... components) {
		VerticalLayout layout = new VerticalLayout();
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.setVisible(false);
		layout.getStyle()
			.set("margin-top", "8px")
			.set("padding", "8px")
			.set("border", "1px solid #ddd")
			.set("border-radius", "6px")
			.set("background-color", "#fafafa")
			.set("gap", "6px");

		layout.add(components);
		return layout;
	}

	public Component createQueryBlockInlineRow(I18nKey labelI18nKey, Component sortIcon, Component... filterFields) {
		HorizontalLayout labelAndSort = new HorizontalLayout(new Span(i18n.get(labelI18nKey)), sortIcon);
		labelAndSort.setAlignItems(Alignment.CENTER);
		labelAndSort.setSpacing(true);

		HorizontalLayout filters = new HorizontalLayout(filterFields);
		filters.setAlignItems(Alignment.END);
		filters.setSpacing(true);

		HorizontalLayout row = new HorizontalLayout(labelAndSort, filters);
		row.setWidthFull();
		row.setAlignItems(Alignment.CENTER);
		row.setSpacing(true);
		row.getStyle().set("gap", "12px");

		return row;
	}
}
