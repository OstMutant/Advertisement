package org.ost.advertisement.ui.views.components.query.elements.field;

import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.data.value.ValueChangeMode;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@SuppressWarnings("java:S110")
public class QueryNumberField extends NumberField {

	@Value
	@Builder
	public static class Parameters {

		@NonNull
		I18nService i18n;
		@NonNull
		I18nKey placeholderKey;
	}

	private final transient Parameters parameters;

	public QueryNumberField(@NonNull Parameters parameters) {
		this.parameters = parameters;
		initLayout();
	}

	private void initLayout() {
		setPlaceholder(parameters.getI18n().get(parameters.getPlaceholderKey()));
		setClearButtonVisible(true);
		setValueChangeMode(ValueChangeMode.EAGER);
		setWidth("100px");
	}
}
