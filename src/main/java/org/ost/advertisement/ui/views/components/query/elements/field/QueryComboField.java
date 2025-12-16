package org.ost.advertisement.ui.views.components.query.elements.field;

import com.vaadin.flow.component.combobox.ComboBox;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import org.ost.advertisement.constants.I18nKey;
import org.ost.advertisement.services.I18nService;

@SuppressWarnings("java:S110")
public class QueryComboField<T> extends ComboBox<T> {

	@Value
	@Builder
	public static class Parameters<T> {

		@NonNull
		I18nService i18n;
		@NonNull
		I18nKey placeholderKey;
		@NonNull
		T[] items;
	}

	private final transient Parameters<T> parameters;

	public QueryComboField(@NonNull Parameters<T> parameters) {
		this.parameters = parameters;
		initLayout();
	}

	private void initLayout() {
		setPlaceholder(parameters.getI18n().get(parameters.getPlaceholderKey()));
		setClearButtonVisible(true);
		setWidth("100%");
		setItems(parameters.getItems());
	}
}
