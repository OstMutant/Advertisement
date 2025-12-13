package org.ost.advertisement.ui.views.components.query.elements.inline;

import com.vaadin.flow.component.datepicker.DatePicker;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

@Getter
public class QueryDateInlineRow extends QueryInlineRow {

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
		DatePicker startDate;
		@NonNull
		DatePicker endDate;
	}

	private final SortIcon sortIcon;
	private final DatePicker startDate;
	private final DatePicker endDate;

	public QueryDateInlineRow(@NonNull Parameters parameters) {
		sortIcon = parameters.getSortIcon();
		startDate = parameters.getStartDate();
		endDate = parameters.getEndDate();
		initLayout(parameters.getI18n(), parameters.getLabelI18nKey(), sortIcon, startDate, endDate);
	}
}
