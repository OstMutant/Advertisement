package org.ost.advertisement.ui.views.components.factories;

import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import org.ost.advertisement.constans.I18nKey;
import org.ost.advertisement.services.I18nService;

public class FieldFactory {

	public static TextField textField(I18nService i18n, I18nKey labelKey, I18nKey placeholderKey,
									  int maxLength, boolean required) {
		TextField field = new TextField(i18n.get(labelKey));
		field.setPlaceholder(i18n.get(placeholderKey));
		field.setMaxLength(maxLength);
		field.setRequired(required);
		return field;
	}

	public static TextArea textArea(I18nService i18n, I18nKey labelKey, I18nKey placeholderKey,
									int maxLength, boolean required) {
		TextArea area = new TextArea(i18n.get(labelKey));
		area.setPlaceholder(i18n.get(placeholderKey));
		area.setMaxLength(maxLength);
		area.setRequired(required);
		area.setHeight("120px");
		return area;
	}
}
