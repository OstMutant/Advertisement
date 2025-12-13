package org.ost.advertisement.ui.views.advertisements.elements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER;
import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_SORT_TITLE;

import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.field.QueryTextField;
import org.ost.advertisement.ui.views.components.query.elements.inline.QueryTextInlineRow;
import org.ost.advertisement.ui.views.components.query.sort.SortIcon;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("prototype")
public class AdvertisementQueryTitleRow extends QueryTextInlineRow {

	public AdvertisementQueryTitleRow(I18nService i18n) {
		super(Parameters.builder()
			.i18n(i18n)
			.labelI18nKey(ADVERTISEMENT_SORT_TITLE)
			.sortIcon(new SortIcon(i18n))
			.filterField(new QueryTextField(QueryTextField.Parameters.builder()
				.i18n(i18n)
				.placeholderKey(ADVERTISEMENT_FILTER_TITLE_PLACEHOLDER)
				.build()))
			.build());
	}
}
