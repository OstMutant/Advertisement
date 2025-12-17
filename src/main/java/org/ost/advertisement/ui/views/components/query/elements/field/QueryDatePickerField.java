package org.ost.advertisement.ui.views.components.query.elements.field;

import com.vaadin.flow.component.datepicker.DatePicker;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@RequiredArgsConstructor
public class QueryDatePickerField extends DatePicker {

	@Value
	@Builder
	public static class Parameters {

		@NonNull
		I18nService i18n;
		@NonNull
		I18nKey placeholderKey;
	}

	private final transient @NonNull Parameters parameters;

	@PostConstruct
	private void initLayout() {
		setWidth("140px");
		setPlaceholder(parameters.getI18n().get(parameters.getPlaceholderKey()));
	}
}
