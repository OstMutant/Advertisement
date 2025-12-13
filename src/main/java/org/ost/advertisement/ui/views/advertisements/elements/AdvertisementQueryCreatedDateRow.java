package org.ost.advertisement.ui.views.advertisements.elements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_END;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_CREATED_AT;

import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.field.QueryDatePickerField;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryDateInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class AdvertisementQueryCreatedDateRow extends QueryDateInlineRow {

	public AdvertisementQueryCreatedDateRow(I18nService i18n) {
		super(Parameters.builder()
			.i18n(i18n)
			.labelI18nKey(ADVERTISEMENT_SORT_CREATED_AT)
			.sortIcon(new SortIcon(i18n))
			.startDate(new QueryDatePickerField(QueryDatePickerField.Parameters.builder()
				.i18n(i18n)
				.placeholderKey(ADVERTISEMENT_FILTER_CREATED_START)
				.build()))
			.endDate(new QueryDatePickerField(QueryDatePickerField.Parameters.builder()
				.i18n(i18n)
				.placeholderKey(ADVERTISEMENT_FILTER_CREATED_END)
				.build()))
			.build());
	}
}
