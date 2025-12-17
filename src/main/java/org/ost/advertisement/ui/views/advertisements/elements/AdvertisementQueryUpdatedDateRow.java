package org.ost.advertisement.ui.views.advertisements.elements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_UPDATED_END;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_UPDATED_START;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_UPDATED_AT;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.field.QueryDatePickerField;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryDateInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;

@SpringComponent
@UIScope
public class AdvertisementQueryUpdatedDateRow extends QueryDateInlineRow {

	public AdvertisementQueryUpdatedDateRow(I18nService i18n) {
		super(Parameters.builder()
			.i18n(i18n)
			.labelI18nKey(ADVERTISEMENT_SORT_UPDATED_AT)
			.sortIcon(new SortIcon(i18n))
			.startDate(new QueryDatePickerField(QueryDatePickerField.Parameters.builder()
				.i18n(i18n)
				.placeholderKey(ADVERTISEMENT_FILTER_UPDATED_START)
				.build()))
			.endDate(new QueryDatePickerField(QueryDatePickerField.Parameters.builder()
				.i18n(i18n)
				.placeholderKey(ADVERTISEMENT_FILTER_UPDATED_END)
				.build()))
			.build());
	}
}
