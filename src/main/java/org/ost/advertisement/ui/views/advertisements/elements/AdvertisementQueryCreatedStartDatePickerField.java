package org.ost.advertisement.ui.views.advertisements.elements;

import static org.ost.advertisement.constants.I18nKey.ADVERTISEMENT_FILTER_CREATED_START;
import static org.ost.advertisement.constants.I18nKey.USER_FILTER_CREATED_START;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.field.QueryDatePickerField;

@SpringComponent
@UIScope
@SuppressWarnings({"java:S110","java:S2094"})
public class AdvertisementQueryCreatedStartDatePickerField extends QueryDatePickerField {

	public AdvertisementQueryCreatedStartDatePickerField(I18nService i18n) {
		super(Parameters.builder()
			.i18n(i18n)
			.placeholderKey(ADVERTISEMENT_FILTER_CREATED_START)
			.build());
	}
}
