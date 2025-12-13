package org.ost.advertisement.ui.views.components.query.elements;

import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@SuppressWarnings("java:S110")
public class QueryTextField extends TextField {

	@Value
	@Builder
	public static class Parameters {

		@NonNull
		I18nService i18n;
		@NonNull
		I18nKey placeholderKey;
	}

	public QueryTextField(@NonNull Parameters parameters) {
		setPlaceholder(parameters.getI18n().get(parameters.getPlaceholderKey()));
		setClearButtonVisible(true);
		setValueChangeMode(ValueChangeMode.EAGER);
		setWidthFull();
	}
}
