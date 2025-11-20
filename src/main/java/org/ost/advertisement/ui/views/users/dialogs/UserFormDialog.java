package org.ost.advertisement.ui.views.users.dialogs;

import static java.util.Optional.ofNullable;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_BUTTON_CANCEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_BUTTON_SAVE;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_CREATED_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_EMAIL_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_ID_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_NAME_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_NAME_PLACEHOLDER;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_ROLE_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_FIELD_UPDATED_LABEL;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_NOTIFICATION_SAVE_ERROR;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_NOTIFICATION_SUCCESS;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_TITLE;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_VALIDATION_NAME_LENGTH;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_VALIDATION_NAME_REQUIRED;
import static org.ost.advertisement.constants.I18nKey.USER_DIALOG_VALIDATION_ROLE_REQUIRED;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.validator.StringLengthValidator;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;
import java.util.Arrays;
import lombok.extern.slf4j.Slf4j;
import org.ost.advertisement.entities.Role;
import org.ost.advertisement.services.I18nService;
import org.ost.advertisement.services.UserService;
import org.ost.advertisement.ui.dto.UserEditDto;
import org.ost.advertisement.ui.mappers.UserMapper;
import org.ost.advertisement.ui.views.TailwindStyle;
import org.ost.advertisement.ui.views.components.dialogs.DialogContentFactory;
import org.ost.advertisement.ui.views.components.dialogs.GenericFormDialog;
import org.ost.advertisement.ui.views.components.dialogs.LabeledField;

@SpringComponent
@UIScope
@Slf4j
public class UserFormDialog extends GenericFormDialog<UserEditDto> {

	private final UserService userService;
	private final UserMapper mapper;

	public UserFormDialog(UserService userService, I18nService i18n, UserMapper mapper) {
		super(UserEditDto.class, i18n);
		this.userService = userService;
		this.mapper = mapper;
	}

	public void open(UserEditDto user) {
		init(user);
		setTitle(USER_DIALOG_TITLE);

		TextField nameField = DialogContentFactory.textField(
			i18n, USER_DIALOG_FIELD_NAME_LABEL, USER_DIALOG_FIELD_NAME_PLACEHOLDER, 255, true
		);

		ComboBox<Role> roleCombo = DialogContentFactory.comboBox(
			i18n, USER_DIALOG_FIELD_ROLE_LABEL, Arrays.asList(Role.values()), true
		);

		binder.forField(nameField)
			.asRequired(i18n.get(USER_DIALOG_VALIDATION_NAME_REQUIRED))
			.withValidator(new StringLengthValidator(i18n.get(USER_DIALOG_VALIDATION_NAME_LENGTH), 1, 255))
			.bind(UserEditDto::getName, UserEditDto::setName);

		binder.forField(roleCombo)
			.asRequired(i18n.get(USER_DIALOG_VALIDATION_ROLE_REQUIRED))
			.bind(UserEditDto::getRole, UserEditDto::setRole);

		LabeledField idField = new LabeledField(i18n, TailwindStyle.EMAIL_LABEL);
		idField.set(USER_DIALOG_FIELD_ID_LABEL, String.valueOf(user.getId()));

		LabeledField emailField = new LabeledField(i18n, TailwindStyle.EMAIL_LABEL);
		emailField.set(USER_DIALOG_FIELD_EMAIL_LABEL, ofNullable(user.getEmail()).orElse(""));

		LabeledField createdField = new LabeledField(i18n, TailwindStyle.GRAY_LABEL);
		createdField.set(USER_DIALOG_FIELD_CREATED_LABEL, formatDate(user.getCreatedAt()));

		LabeledField updatedField = new LabeledField(i18n, TailwindStyle.GRAY_LABEL);
		updatedField.set(USER_DIALOG_FIELD_UPDATED_LABEL, formatDate(user.getUpdatedAt()));

		addContent(idField, emailField, nameField, roleCombo, createdField, updatedField);

		Button saveButton = DialogContentFactory.primaryButton(i18n, USER_DIALOG_BUTTON_SAVE);
		saveButton.addClickListener(event -> save(
			u -> userService.save(mapper.toUser(u)),
			USER_DIALOG_NOTIFICATION_SUCCESS,
			USER_DIALOG_NOTIFICATION_SAVE_ERROR
		));

		Button cancelButton = DialogContentFactory.tertiaryButton(i18n, USER_DIALOG_BUTTON_CANCEL);
		cancelButton.addClickListener(event -> close());

		addActions(saveButton, cancelButton);
		open();
	}
}
