package org.ost.advertisement.ui.views.users.elements;

import static org.ost.advertisement.constants.I18nKey.USER_FILTER_ID_MIN;

import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.views.components.query.elements.field.QueryNumberField;

@SpringComponent
@UIScope
@SuppressWarnings("java:S2094")
public class UserQueryIdMinField extends QueryNumberField {

	public UserQueryIdMinField(I18nService i18n) {
		super(Parameters.builder()
			.i18n(i18n)
			.placeholderKey(USER_FILTER_ID_MIN)
			.build());
	}
}
