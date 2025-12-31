package org.ost.advertisement.ui.views.components.query.elements.cell;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QueryVerticalLayout extends VerticalLayout {

	protected void initLayout(Component... filterFields) {
		add(filterFields);
		setPadding(false);
		setSpacing(false);
		setMargin(false);
		getStyle().set("gap", "4px");
	}
}
