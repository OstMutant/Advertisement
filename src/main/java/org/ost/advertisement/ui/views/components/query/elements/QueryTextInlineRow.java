package org.ost.advertisement.ui.views.components.query.elements;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

@Getter
public class QueryTextInlineRow extends HorizontalLayout {

	@Value
	@Builder
	public static class Parameters {

		@NonNull
		I18nService i18n;
		@NonNull
		I18nKey labelI18nKey;
		@NonNull
		SortIcon sortIcon;
		@NonNull
		TextField filterField;
	}

	private final SortIcon sortIcon;
	private final TextField filterField;
	private final transient Parameters parameters;

	public QueryTextInlineRow(@NonNull Parameters parameters) {
		this.parameters = parameters;
		this.sortIcon = parameters.getSortIcon();
		this.filterField = parameters.getFilterField();
		initLayout();
	}

	private void initLayout() {
		HorizontalLayout labelAndSort = new HorizontalLayout(
			new Span(parameters.getI18n().get(parameters.getLabelI18nKey())),
			sortIcon);
		labelAndSort.setAlignItems(Alignment.CENTER);
		labelAndSort.setSpacing(true);

		HorizontalLayout filters = new HorizontalLayout(filterField);
		filters.setAlignItems(Alignment.END);
		filters.setSpacing(true);

		add(labelAndSort, filters);
		setWidthFull();
		setAlignItems(Alignment.CENTER);
		setSpacing(true);
		getStyle().set("gap", "12px");
	}
}
