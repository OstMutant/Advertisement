package org.ost.advertisement.ui.views.users;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_BUTTON_CANCEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_BUTTON_SAVE;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_CREATED_LABEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_EMAIL_LABEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_ID_LABEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_NAME_LABEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_NAME_PLACEHOLDER;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_ROLE_LABEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_FIELD_UPDATED_LABEL;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_NOTIFICATION_SAVE_ERROR;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_NOTIFICATION_SUCCESS;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_TITLE;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_VALIDATION_NAME_LENGTH;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_VALIDATION_NAME_REQUIRED;
import static org.ost.advertisement.constans.I18nKey.USER_DIALOG_VALIDATION_ROLE_REQUIRED;

import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.entities.User;
import org.ost.advertisement.security.utils.AuthUtil;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.GenericFormDialog;

@Slf4j
public class UserFormDialog extends GenericFormDialog<User> {

	public UserFormDialog(User user, UserService userService, I18nService i18n) {
		super(user, User.class, i18n);

		TextField nameField = createNameField();
		ComboBox<Role> roleCombo = createRoleCombo();

		binder.forField(nameField)
			.asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
			.withValidator(new StringLengthValidator(i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
			.bind(User::getName, User::setName);

		binder.forField(roleCombo)
			.asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
			.bind(User::getRole, User::setRole);

		setTitle(USER_DIALOG_TITLE);

		addContent(
			labeled(USER_DIALOG_FIELD_ID_LABEL, String.valueOf(user.getId()), TailwindStyle.EMAIL_LABEL),
			labeled(USER_DIALOG_FIELD_EMAIL_LABEL, ofNullable(user.getEmail()).orElse(""),
				TailwindStyle.EMAIL_LABEL),
			nameField,
			roleCombo,
			labeled(USER_DIALOG_FIELD_CREATED_LABEL, formatDate(user.getCreatedAt()), TailwindStyle.GRAY_LABEL),
			labeled(USER_DIALOG_FIELD_UPDATED_LABEL, formatDate(user.getUpdatedAt()), TailwindStyle.GRAY_LABEL)
		);

		addActions(
			createSaveButton(USER_DIALOG_BUTTON_SAVE,
				event -> save(dto -> userService.save(AuthUtil.getCurrentUser(), dto),
					USER_DIALOG_NOTIFICATION_SUCCESS, USER_DIALOG_NOTIFICATION_SAVE_ERROR)),
			createCancelButton(USER_DIALOG_BUTTON_CANCEL)
		);
	}

	private TextField createNameField() {
		TextField field = new TextField(i18n.get(USER_DIALOG_FIELD_NAME_LABEL));
		field.setPlaceholder(i18n.get(USER_DIALOG_FIELD_NAME_PLACEHOLDER));
		field.setRequired(true);
		field.setMaxLength(255);
		return field;
	}

	private ComboBox<Role> createRoleCombo() {
		ComboBox<Role> combo = new ComboBox<>(i18n.get(USER_DIALOG_FIELD_ROLE_LABEL));
		combo.setItems(Arrays.asList(Role.values()));
		combo.setRequired(true);
		combo.setAllowCustomValue(false);
		combo.setMinWidth("110px");
		combo.setMaxWidth("160px");
		return combo;
	}
}


