package org.ost.advertisement.ui.views.components.dialogs;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.ost.advertisement.constans.I18nKey;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.ui.utils.NotificationType;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DialogContentFactory {

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

	public static <T> ComboBox<T> comboBox(I18nService i18n, I18nKey labelKey, List<T> items, boolean required) {
		ComboBox<T> combo = new ComboBox<>(i18n.get(labelKey));
		combo.setItems(items);
		combo.setRequired(required);
		combo.setAllowCustomValue(false);
		combo.setMinWidth("110px");
		combo.setMaxWidth("160px");
		return combo;
	}

	public static EmailField emailField(I18nService i18n, I18nKey labelKey,
										I18nKey placeholderKey, boolean required) {
		EmailField field = new EmailField(i18n.get(labelKey));
		field.setPlaceholder(i18n.get(placeholderKey));
		field.setRequired(required);
		field.setWidthFull();
		return field;
	}

	public static PasswordField passwordField(I18nService i18n, I18nKey labelKey,
											  I18nKey placeholderKey, boolean required) {
		PasswordField field = new PasswordField(i18n.get(labelKey));
		field.setPlaceholder(i18n.get(placeholderKey));
		field.setRequired(required);
		field.setWidthFull();
		return field;
	}

	public static Button primaryButton(I18nService i18n, I18nKey key) {
		return button(i18n, key, "primary");
	}

	public static Button tertiaryButton(I18nService i18n, I18nKey key) {
		return button(i18n, key, "tertiary");
	}

	public static Button button(I18nService i18n, I18nKey key, String... themeNames) {
		Button button = new Button(i18n.get(key));
		button.addThemeNames(themeNames);
		return button;
	}

	public static void showSuccess(I18nService i18n, I18nKey key) {
		NotificationType.SUCCESS.show(i18n.get(key));
	}

	public static void showError(I18nService i18n, I18nKey key, String details) {
		NotificationType.ERROR.show(i18n.get(key, details));
	}
}
